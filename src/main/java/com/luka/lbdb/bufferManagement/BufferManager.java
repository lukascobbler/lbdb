package com.luka.lbdb.bufferManagement;

import com.luka.lbdb.bufferManagement.exceptions.BufferAbortException;
import com.luka.lbdb.db.settings.BufferStrategy;
import com.luka.lbdb.fileManagement.BlockId;
import com.luka.lbdb.fileManagement.FileManager;
import com.luka.lbdb.logManagement.LogManager;

import java.util.Arrays;
import java.util.Comparator;

/// The `BufferManager` object is responsible for writing user data
/// to blocks in a DB file. Prevents pinned (currently in memory and in use)
/// blocks from being dropped. Aims for performance optimal dropping of unpinned
/// blocks.
public class BufferManager {
    private final Buffer[] bufferPool;
    private final int numMaxBuffers;
    private int numAvailableBuffers;
    private final ChooseUnpinnedBufferStrategy chooseUnpinnedBufferStrategy;
    private int clockPosition = 0;

    public static final long MAX_TIME = 10_000;

    /// Initializes a buffer manager with a predefined number of buffers i.e. pages
    /// that the clients can use to interact with blocks in DB files.
    public BufferManager(FileManager fileManager, LogManager logManager, int numBuffers, BufferStrategy bufferStrategy) {
        bufferPool = new Buffer[numBuffers];
        numAvailableBuffers = numBuffers;
        numMaxBuffers = numBuffers;
        chooseUnpinnedBufferStrategy = new ChooseUnpinnedBufferStrategy(bufferStrategy);
        for (int i = 0; i < numBuffers; i++) {
            bufferPool[i] = new Buffer(fileManager, logManager, i);
        }
    }

    /// @return Number of currently available buffers in the buffer manager.
    public synchronized int available() {
        return numAvailableBuffers;
    }

    /// Flushes all buffers that have a particular transaction number.
    /// Method is `synchronized` because the variable containing all buffers
    /// can only be accessed by one thread at a time.
    public synchronized void flushAll(int transactionNumber) {
        Arrays.stream(bufferPool)
                .filter(b -> b.modifyingTransaction() == transactionNumber)
                .forEach(Buffer::flush);
    }

    /// Flushes all buffers in the system.
    /// Method is `synchronized` because the variable containing all buffers
    /// can only be accessed by one thread at a time.
    public synchronized void flushAll() {
        Arrays.stream(bufferPool).forEach(Buffer::flush);
    }

    /// Reduces the number of pins on a buffer. If a buffer becomes completely
    /// unpinned after the operation, the number of available buffers is increased
    /// and all threads that are trying to pin a buffer are notified that one has
    /// become available.
    public synchronized void unpin(Buffer buffer) {
        buffer.unpin();
        if (!buffer.isPinned()) {
            numAvailableBuffers++;
            notifyAll();
            buffer.setUnpinnedTimeToNow();
        }
    }

    /// Pins a buffer to a block id if there is one available within a maximal
    /// time period (`MAX_TIME`).
    ///
    /// @return The pinned buffer.
    ///
    /// @throws BufferAbortException if the buffer
    /// wasn't successfully pinned in the defined time constraints.
    public synchronized Buffer pin(BlockId blockId) {
        try {
            long timestamp = System.currentTimeMillis();
            Buffer buffer = tryToPin(blockId);
            while (buffer == null && !waitingTooLong(timestamp)) {
                wait(MAX_TIME);
                buffer = tryToPin(blockId);
            }
            if (buffer == null) {
                throw new BufferAbortException();
            }

            buffer.setReadInTimeToNow();

            return buffer;
        } catch (InterruptedException e) {
            throw new BufferAbortException();
        }
    }

    /// Checks if the system has waited too long for a buffer to become available.
    ///
    /// @return Whether the thread has waited for too long on the buffer.
    private boolean waitingTooLong(long startTime) {
        return System.currentTimeMillis() - startTime > MAX_TIME;
    }

    /// Try to pin a buffer to the block id. If the buffer with the matching block id
    /// is found in the buffer pool (meaning it is already pinned or not yet replaced),
    /// then its pin counter is increased and it's returned.
    /// Else, if no buffer matches the block id, then some unpinned buffer ***B*** is chosen
    /// (according to one of the strategies) to store the new block's contents (***B*** is flushed beforehand).
    ///
    /// @return A buffer that will be pinned to the passed block id, or `null` if there is no
    /// such buffer.
    private Buffer tryToPin(BlockId blockId) {
        Buffer buffer = findExistingBuffer(blockId);
        if (buffer == null) {
            buffer = chooseUnpinnedBufferStrategy.chooseUnpinnedBuffer();
            if (buffer == null) {
                return null;
            }

            // when the buffer's contents are replaced, the clock
            // position will point to the next buffer in the list
            clockPosition = (buffer.getPosition() + 1) % numMaxBuffers;

            buffer.assignToBlock(blockId);
        }
        if (!buffer.isPinned()) {
            // only reduce the number of available buffers if this one is not yet in use
            numAvailableBuffers--;
        }
        buffer.pin();
        return buffer;
    }

    /// If the block from the requested block id is already in memory
    /// (it's mapped to a buffer in the buffer pool), this function returns it. The block may
    /// or may not be pinned. If it is pinned, it can be pinned again by a different client
    /// and if it's unpinned it means the buffer hasn't yet been flushed to its respective block.
    ///
    /// @return A buffer mapped to the passed block id, if it is in memory, else 'null'.
    private Buffer findExistingBuffer(BlockId blockId) {
        return Arrays.stream(bufferPool)
                .filter(b -> b.getBlockId() != null && b.getBlockId().equals(blockId))
                .findFirst()
                .orElse(null);
    }

    /// Strategies for choosing the unpinned block in some buffer whose contents are
    /// about to be replaced by pinning a different block id to it.
    /// The six types of strategies are:
    /// - Naive
    /// - FIFO
    /// - LRU
    /// - Clock
    /// - First unmodified
    /// - LRM
    class ChooseUnpinnedBufferStrategy {
        BufferStrategy bufferStrategy;

        public ChooseUnpinnedBufferStrategy(BufferStrategy bufferStrategy) {
            this.bufferStrategy = bufferStrategy;
        }

        /// @return The buffer whose block is unpinned and can be safely replaced with new
        /// block's contents.
        public Buffer chooseUnpinnedBuffer() {
            return switch (bufferStrategy) {
                case NAIVE -> naive();
                case FIFO -> fifo();
                case LRU -> lru();
                case CLOCK -> clock();
                case FIRST_UNMODIFIED -> firstUnmodified();
                case LRM -> lrm();
            };
        }

        /// This strategy has terrible performance as it does not
        /// take into account any information from its environment.
        ///
        /// @return The first buffer in the buffer pool that is not pinned.
        private Buffer naive() {
            return Arrays.stream(bufferPool)
                    .filter(b -> !b.isPinned())
                    .findFirst()
                    .orElse(null);
        }

        /// This strategy is much better than the naive strategy,
        /// but it still has a critical flaw in the context of SQL databases because
        /// some pages that were loaded practically when the system started are probably
        /// still in use (pages that contain catalog blocks).
        ///
        /// @return The buffer that was earliest loaded.
        private Buffer fifo() {
            return Arrays.stream(bufferPool)
                    .filter(b -> !b.isPinned())
                    .min(Comparator.comparing(Buffer::getReadInTime))
                    .orElse(null);
        }

        /// This strategy is as good as the FIFO strategy, but does not
        /// expose the main flaw of it, since frequently used blocks will
        /// still be present even if they are loaded a long time ago,
        /// even if currently unpinned.
        ///
        /// @return The buffer that was the earliest unpinned.
        private Buffer lru() {
            return Arrays.stream(bufferPool)
                    .filter(b -> !b.isPinned())
                    .min(Comparator.comparing(Buffer::getUnpinnedTime))
                    .orElse(null);
        }

        /// This strategy, uses a known SQL database fact that commonly
        /// used pages be pinned when the algorithm encounters them.
        /// The buffers are chosen in a round-robin fashion, with the pinned
        /// buffers being skipped. The starting point is the position of the
        /// last unpinned buffer.
        ///
        /// @return The first unpinned buffer in the pool, starting with the
        /// current clock position.
        private Buffer clock() {
            for (int i = 0; i < numMaxBuffers; i++) {
                int index = (i + clockPosition) % numMaxBuffers;

                if (bufferPool[index].isPinned()) {
                    continue;
                }

                return bufferPool[index];
            }

            return null;
        }

        /// Unmodified buffers are probably okay to be reused, however
        /// maybe not in some circumstances where some SQL algorithm will
        /// create read-only intermediate buffers for its internal use.
        ///
        /// @return The first buffer that was not modified, else if all buffers
        /// are modified, it will return a buffer according to the naive strategy.
        private Buffer firstUnmodified() {
            return Arrays.stream(bufferPool)
                    .filter(b -> b.modifyingTransaction() == -1 && !b.isPinned())
                    .findFirst()
                    .orElseGet(this::naive);
        }

        /// Buffers that were modified but not used for a long time will probably
        /// not be modified again, but may still be used for some purpose.
        ///
        /// @return The buffer that was earliest modified, else if no buffers
        /// were modified, it will return a buffer according to the naive strategy.
        private Buffer lrm() {
            return Arrays.stream(bufferPool)
                    .filter(b -> b.modifyingTransaction() != -1 && !b.isPinned())
                    .min(Comparator.comparing(Buffer::getLsn))
                    .orElseGet(this::naive);
        }
    }
}