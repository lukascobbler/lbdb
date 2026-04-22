package com.luka.lbdb.bufferManagement;

import com.luka.lbdb.bufferManagement.exceptions.BufferPinException;
import com.luka.lbdb.fileManagement.BlockId;
import com.luka.lbdb.fileManagement.FileManager;
import com.luka.lbdb.fileManagement.Page;
import com.luka.lbdb.logManagement.LogManager;

/// A Buffer is an in-memory one-to-one mapping of a specific block.
public class Buffer {
    private final FileManager fileManager;
    private final LogManager logManager;
    private final Page contents;

    private BlockId blockId = null;
    private int pins = 0;
    private int transactionNumber = -1;
    private int lsn = -1;

    private final int position;
    private long readInTime = -1;
    private long unpinnedTime = -1;

    /// Initializes a buffer by initializing the page using the system's
    /// block size. File and log managers required for flushing i.e. writing to disk.
    /// On initialization, the buffer is not pinned to any block, it is unmodified
    /// (modifying transaction id is equal to -1) and the log sequence number
    /// does not correspond to any log (the value is -1).
    public Buffer(FileManager fileManager, LogManager logManager, int position) {
        this.fileManager = fileManager;
        this.logManager = logManager;
        contents = new Page(fileManager.getBlockSize());
        this.position = position;
    }

    /// @return The page that contains the contents of the buffer.
    public Page getContents() {
        return contents;
    }

    /// @return Corresponding block id of the buffer.
    public BlockId getBlockId() {
        return blockId;
    }

    /// Sets the modified status of the buffer to the transaction that last
    /// modified it, and the log sequence number corresponding to that modification.
    /// If the log sequence number is negative, that means no log record was
    /// written for that modification.
    public void setModified(int transactionNumber, int lsn) {
        this.transactionNumber = transactionNumber;
        if (lsn >= 0) {
            this.lsn = lsn;
        }
    }

    /// Sets the modified status of the buffer like it's never been
    /// touched by any transaction.
    public void setUnmodified() {
        transactionNumber = -1;
        lsn = -1;
    }

    /// @return Whether the buffer is pinned to a block or not.
    public boolean isPinned() {
        return pins > 0;
    }

    /// @return The number of the transaction that last modified the buffer,
    /// -1 indicating that the buffer is unmodified.
    public int modifyingTransaction() {
        return transactionNumber;
    }

    /// Reads the specified block into a buffer.
    public void assignToBlock(BlockId blockId) {
        flush();
        this.blockId = blockId;
        fileManager.read(blockId, contents);
        pins = 0;
    }

    /// Writes out the modifications to a block to the disk but only if the contents
    /// were modified. Creates a log of the modifications, and sets the state of the
    /// buffer to unmodified (the modifying transaction number becomes -1).
    public void flush() {
        if (transactionNumber >= 0) {
            logManager.flush(lsn);
            if (blockId.blockNum() < fileManager.lengthInBlocks(blockId.filename())) {
                // since append block operations on a file are eager, meaning they get
                // written out to the log and the file immediately upon happening, the
                // undoing of them (and reducing the file) will happen before the undoing
                // of update operations, so the update operations won't have their block
                // to undo to therefore it's unnecessary to even flush the block ids that
                // have a block number greater than the last block number in the file
                fileManager.write(blockId, contents);
            }
            transactionNumber = -1;
        }
    }

    /// Pins the buffer to its block.
    ///
    /// @throws BufferPinException if the buffer is not assigned
    /// to any block id.
    public void pin() {
        if (blockId == null) {
            throw new BufferPinException();
        }
        pins++;
    }

    /// Unpins the buffer from its block.
    ///
    /// @throws BufferPinException if the buffer is not assigned
    /// to any block id.
    public void unpin() {
        if (blockId == null) {
            throw new BufferPinException();
        }
        if (pins == 0) {
            return;
        }
        pins--;
    }

    public long getUnpinnedTime() {
        return unpinnedTime;
    }

    public void setUnpinnedTimeToNow() {
        this.unpinnedTime = System.currentTimeMillis();
    }

    public long getReadInTime() {
        return readInTime;
    }

    public void setReadInTimeToNow() {
        this.readInTime = System.currentTimeMillis();
    }

    public int getPosition() {
        return position;
    }

    public int getLsn() {
        return lsn;
    }
}
