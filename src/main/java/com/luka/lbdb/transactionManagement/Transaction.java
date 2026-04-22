package com.luka.lbdb.transactionManagement;

import com.luka.lbdb.bufferManagement.Buffer;
import com.luka.lbdb.bufferManagement.BufferManager;
import com.luka.lbdb.fileManagement.BlockId;
import com.luka.lbdb.fileManagement.FileManager;
import com.luka.lbdb.logManagement.LogManager;
import com.luka.lbdb.transactionManagement.concurrencyManagement.ConcurrencyManager;
import com.luka.lbdb.transactionManagement.concurrencyManagement.LockTable;
import com.luka.lbdb.transactionManagement.recoveryManagement.RecoveryManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/// A transaction represents one unit of work in the database.
/// All operations must be performed transactionally, even if they
/// consist of one database query or update. A `Transaction` object
/// manages its own buffers and locks.
public class Transaction {
    private final AtomicInteger nextTransactionNum;

    private static final int END_OF_FILE = -1;

    private final RecoveryManager recoveryManager;
    private final ConcurrencyManager concurrencyManager;
    private final BufferManager bufferManager;
    private final FileManager fileManager;

    private int transactionNumber;
    private final BufferList myBuffers;

    private final Consumer<Transaction> onFinish;

    /// Initializes a transaction with a transaction id that is pooled
    /// from a global number that all transactions share. Creates a
    /// concurrency manager and a recovery manager tied to this transaction.
    /// Recovery type is chosen, but should be the same for every transaction in
    /// the system.
    public Transaction(FileManager fileManager, LogManager logManager,
                       BufferManager bufferManager, LockTable lockTable,
                       boolean undoOnlyRecovery, AtomicInteger nextTransactionNum,
                       Consumer<Transaction> onFinish) {
        this.nextTransactionNum = nextTransactionNum;
        this.bufferManager = bufferManager;
        this.fileManager = fileManager;
        this.concurrencyManager = new ConcurrencyManager(lockTable);
        this.onFinish = onFinish;

        transactionNumber = nextTransactionNumber();
        myBuffers = new BufferList(bufferManager);

        this.recoveryManager = new RecoveryManager(
                this, transactionNumber, logManager, bufferManager, undoOnlyRecovery);
    }

    /// Commits the transaction by commiting to the recovery file,
    /// releasing all held locks on all block ids and unpinning all
    /// buffers held by the transaction.
    public void commit() {
        recoveryManager.commit();
        concurrencyManager.release();
        myBuffers.unpinAll();
        onFinish.accept(this);
    }

    /// Rolls back the transaction by rolling back from the recovery file,
    /// releasing all held locks on all block ids and unpinning all
    /// buffers held by the transaction.
    public void rollback() {
        recoveryManager.rollback();
        concurrencyManager.release();
        myBuffers.unpinAll();
        onFinish.accept(this);
    }

    /// Recovers the whole system including this transaction. Sets the transaction id
    /// to the latest transaction id + 1.
    public void recover() {
        bufferManager.flushAll(transactionNumber);
        nextTransactionNum.set(recoveryManager.recover());
        this.transactionNumber = nextTransactionNumber();
    }

    /// Pin a block id from this transaction.
    public void pin(BlockId blockId) {
        concurrencyManager.lockShared(blockId);
        myBuffers.pin(blockId);
    }

    /// Unpin a block id from this transaction.
    public void unpin(BlockId blockId) {
        myBuffers.unpin(blockId);
    }

    /// Gets an integer from a block id managed by the transaction.
    /// Firstly, shared locking of the block id is performed, then the
    /// integer is returned.
    ///
    /// @return An integer from a given block id at a given offset from a buffer
    /// managed by this transaction.
    public int getInt(BlockId blockId, int offset) {
        Buffer buffer = myBuffers.getBuffer(blockId);
        return buffer.getContents().getInt(offset);
    }

    /// Gets a string from a block id managed by the transaction.
    /// Firstly, shared locking of the block id is performed, then the
    /// string is returned.
    ///
    /// @return A string from a given block id at a given offset from a buffer
    /// managed by this transaction.
    public String getString(BlockId blockId, int offset) {
        Buffer buffer = myBuffers.getBuffer(blockId);
        return buffer.getContents().getString(offset);
    }

    /// Gets a boolean from a block id managed by the transaction.
    /// Firstly, shared locking of the block id is performed, then the
    /// string is returned.
    ///
    /// @return A boolean from a given block id at a given offset from a buffer
    /// managed by this transaction.
    public boolean getBoolean(BlockId blockId, int offset) {
        Buffer buffer = myBuffers.getBuffer(blockId);
        return buffer.getContents().getBoolean(offset);
    }

    /// Sets an integer value to a block id on an offset.
    /// Firstly, exclusive locking of the block id is performed, then the
    /// value is set. The parameter `okToLog` indicates if the value will be
    /// written out to the recovery log (false when undoing the transaction).
    /// Finally, the buffer is set to be modified with this transaction's id
    /// and the LSN returned by the recovery manager if the set was logged.
    public void setInt(BlockId blockId, int offset, int value, boolean okToLog) {
        concurrencyManager.lockExclusive(blockId);
        Buffer buffer = myBuffers.getBuffer(blockId);
        int lsn = -1;
        if (okToLog) {
            lsn = recoveryManager.setInt(buffer, offset, value);
        }
        buffer.getContents().setInt(offset, value);
        buffer.setModified(transactionNumber, lsn);
    }

    /// Sets a string value to a block id on an offset.
    /// Firstly, exclusive locking of the block id is performed, then the
    /// value is set. The parameter `okToLog` indicates if the value will be
    /// written out to the recovery log (false when undoing the transaction).
    /// Finally, the buffer is set to be modified with this transaction's id
    /// and the LSN returned by the recovery manager if the set was logged.
    public void setString(BlockId blockId, int offset, String value, boolean okToLog) {
        concurrencyManager.lockExclusive(blockId);
        Buffer buffer = myBuffers.getBuffer(blockId);
        int lsn = -1;
        if (okToLog) {
            lsn = recoveryManager.setString(buffer, offset, value);
        }
        buffer.getContents().setString(offset, value);
        buffer.setModified(transactionNumber, lsn);
    }

    /// Sets a boolean value to a block id on an offset.
    /// Firstly, exclusive locking of the block id is performed, then the
    /// value is set. The parameter `okToLog` indicates if the value will be
    /// written out to the recovery log (false when undoing the transaction).
    /// Finally, the buffer is set to be modified with this transaction's id
    /// and the LSN returned by the recovery manager if the set was logged.
    public void setBoolean(BlockId blockId, int offset, boolean value, boolean okToLog) {
        concurrencyManager.lockExclusive(blockId);
        Buffer buffer = myBuffers.getBuffer(blockId);
        int lsn = -1;
        if (okToLog) {
            lsn = recoveryManager.setBoolean(buffer, offset, value);
        }
        buffer.getContents().setBoolean(offset, value);
        buffer.setModified(transactionNumber, lsn);
    }

    /// Firstly, shared locking of the whole file is performed, then the
    /// length in blocks is returned.
    ///
    /// @return The size of the file.
    public int lengthInBlocks(String filename) {
        BlockId dummyBlock = new BlockId(filename, END_OF_FILE);
        concurrencyManager.lockShared(dummyBlock);
        return fileManager.lengthInBlocks(filename);
    }

    /// Firstly, exclusive locking of the whole file is performed, then a
    /// new block is appended. The parameter `okToLog` indicates if the value will be
    /// written out to the recovery log (false when undoing the transaction).
    /// Caution when using because the global state (the whole file) is immediately
    /// modified, unlike the standard set methods where everything is done in-memory,
    /// and it is preserved on disk only when buffers are flushed.
    ///
    /// @return The newly appended block id.
    public BlockId appendEmptyBlock(String filename, boolean okToLog) {
        BlockId dummyBlock = new BlockId(filename, END_OF_FILE);
        concurrencyManager.lockExclusive(dummyBlock);
        if (okToLog) {
            recoveryManager.appendBlock(filename);
        }
        return fileManager.append(filename);
    }

    /// Firstly, exclusive locking of the whole file is performed, then a
    /// block from the end of the file is truncated.
    /// Caution when using because the global state (the whole file) is immediately
    /// modified, unlike the standard set methods where everything is done in-memory,
    /// and it is preserved on disk only when buffers are flushed.
    /// This is the undo operation to the block append operation.
    /// Do not use this as a truncate operation because data loss may occur.
    /// Requires buffer to be pinned.
    public void undoAppendBlock(BlockId blockId) {
        BlockId dummyBlock = new BlockId(blockId.filename(), END_OF_FILE);
        concurrencyManager.lockExclusive(dummyBlock);

        Buffer buffer = myBuffers.getBuffer(blockId);
        buffer.setUnmodified();

        fileManager.truncate(blockId.filename());
    }

    /// @return The block size of the system from the file manager.
    public int blockSize() {
        return fileManager.getBlockSize();
    }

    /// @return The number of available buffers from the buffer
    /// manager.
    public int availableBuffers() {
        return bufferManager.available();
    }

    /// @return The transaction number representing the next
    /// transaction in the system.
    private int nextTransactionNumber() {
        return nextTransactionNum.incrementAndGet();
    }

    public int getTransactionNumber() {
        return transactionNumber;
    }
}
