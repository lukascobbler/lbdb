package com.luka.lbdb.transactionManagement;

import com.luka.lbdb.bufferManagement.BufferManager;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.fileManagement.FileManager;
import com.luka.lbdb.logManagement.LogManager;
import com.luka.lbdb.transactionManagement.concurrencyManagement.LockTable;
import com.luka.lbdb.transactionManagement.recoveryManagement.logRecordTypes.QuiescentCheckpointRecord;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/// The main entry point for starting new transactions. Worries about which session
/// is mapped to what transaction, while allowing anonymous transactions i.e. transactions
/// that do not persist across multiple queries or commands.
public class TransactionManager {
    private final FileManager fileManager;
    private final LogManager logManager;
    private final BufferManager bufferManager;
    private final LockTable lockTable;
    private final AtomicInteger nextTransactionNum = new AtomicInteger(0);
    private final LBDBSettings settings;

    private final Map<Integer, Transaction> manualTransactions = new ConcurrentHashMap<>();
    private final Set<Transaction> allTransactions = ConcurrentHashMap.newKeySet();

    private final ReadWriteLock systemLock = new ReentrantReadWriteLock();
    private volatile boolean acceptingNewTransactions = true;

    private final ReentrantLock activeTxLock = new ReentrantLock();
    private final Condition noActiveTransactions = activeTxLock.newCondition();

    /// Since a transaction manager is responsible for creating new transactions,
    /// it needs the file manager to interact with the system, and a settings object
    /// to configure the transactions.
    public TransactionManager(FileManager fileManager, LBDBSettings settings) {
        this.fileManager = fileManager;
        this.settings = settings;
        logManager = new LogManager(fileManager, settings.LOG_FILE);
        bufferManager = new BufferManager(fileManager, logManager, settings.BUFFER_POOL_SIZE, settings.bufferStrategy);
        lockTable = new LockTable();
    }

    /// @return A transaction mapped to a given session id, or an anonymous
    /// transaction if the session id has no active transaction.
    /// @throws IllegalStateException if the system is currently writing a checkpoint,
    /// or is being shut down, during which no new transactions must be created.
    public Transaction getOrCreateTransaction(int sessionId) {
        systemLock.readLock().lock();
        try {
            Transaction existing = manualTransactions.get(sessionId);
            if (existing != null) return existing;

            if (!acceptingNewTransactions) {
                throw new IllegalStateException(
                        "Server is performing a critical operation. No new transactions are allowed at the moment"
                );
            }

            return createAndTrackTransaction();
        } finally {
            systemLock.readLock().unlock();
        }
    }

    /// @return True if some transaction belongs to a session which means
    /// that it must be manually commited by the user.
    public boolean isManual(int sessionId) {
        return manualTransactions.containsKey(sessionId);
    }

    /// Given a session, promotes that session's transaction into a
    /// manual transaction where the user must close it.
    public void promoteToManual(int sessionId, Transaction tx) {
        manualTransactions.put(sessionId, tx);
    }

    /// Unmaps the given session from any transaction. Used after
    /// commiting or rolling back.
    public void clearSession(int sessionId) {
        manualTransactions.remove(sessionId);
    }

    /// Waits for all transactions to finish and writes a checkpoint.
    public synchronized void writeCheckpoint(boolean terminal) {
        acceptingNewTransactions = false;
        System.out.println("Starting system checkpoint, waiting for all transactions to finish...");
        waitForAllTransactionsToFinish();
        try {
            int lastTxNum = nextTransactionNum.get();
            int lsn = QuiescentCheckpointRecord.writeToLog(logManager, lastTxNum);
            logManager.flush(lsn);

            System.out.println("Checkpoint written.");
        } finally {
            acceptingNewTransactions = !terminal;
            systemLock.writeLock().unlock();
        }
    }

    /// Blocking function that will wait until there are no more tracked
    /// transactions in the system. Can wait indefinitely.
    public void waitForAllTransactionsToFinish() {
        activeTxLock.lock();
        try {
            while (!allTransactions.isEmpty()) {
                noActiveTransactions.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            activeTxLock.unlock();
        }

        systemLock.writeLock().lock();
    }

    /// Creates a new anonymous function from the system's settings and
    /// begins tracking it.
    ///
    /// @return A newly created anonymous function.
    private Transaction createAndTrackTransaction() {
        Transaction tx = new Transaction(
                fileManager, logManager, bufferManager, lockTable,
                settings.UNDO_ONLY_RECOVERY,
                nextTransactionNum,
                this::onTransactionCompleted
        );

        activeTxLock.lock();
        try { allTransactions.add(tx); }
        finally { activeTxLock.unlock(); }
        return tx;
    }

    /// A callback that must be called for every completed transaction,
    /// to stop tracking it and blocking the system from shutting down.
    private void onTransactionCompleted(Transaction tx) {
        activeTxLock.lock();
        try {
            allTransactions.remove(tx);
            if (allTransactions.isEmpty()) {
                noActiveTransactions.signalAll();
            }
        } finally {
            activeTxLock.unlock();
        }
    }
}
