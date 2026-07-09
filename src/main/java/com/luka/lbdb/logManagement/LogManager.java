package com.luka.lbdb.logManagement;

import com.luka.lbdb.fileManagement.BlockId;
import com.luka.lbdb.fileManagement.FileManager;
import com.luka.lbdb.fileManagement.Page;
import com.luka.lbdb.fileManagement.exceptions.FileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.stream.Stream;

/// The `LogManager` object is used for writing logs
/// to the log file when necessary. It is responsible for
/// initializing the log file, and keeping it consistent.
public class LogManager {
    private final FileManager fileManager;
    private final String logFile;
    private final Page logPage;
    private BlockId currentBlockId;
    private int latestLSN = 0;
    private int lastSavedLSN = 0;

    /// Initializes a log manager with a given log file path.
    /// Initialization consists of creating a permanently allocated
    /// log page the size of system's block size and appending
    /// an empty block to the log file if it's empty or reading
    /// the last block into memory if it's not empty.
    public LogManager(FileManager fileManager, String logFile) {
        this.fileManager = fileManager;
        this.logFile = logFile;

        byte[] b = new byte[fileManager.getBlockSize()];
        logPage = new Page(b);

        int logSize = fileManager.lengthInBlocks(logFile);

        if (logSize == 0) {
            // if the log file is empty, initialize it with a block
            // containing the integer with a value of the boundary of
            // that block
            currentBlockId = appendNewBlock();
        } else {
            // if the log file is not empty, read the last block into a page
            currentBlockId = new BlockId(logFile, logSize - 1);
            fileManager.read(currentBlockId, logPage);
        }
    }

    /// Flushes all records in memory, only if the
    /// record with the provided log sequence number is
    /// not already flushed.
    public void flush(int lsn) {
        if (lsn >= lastSavedLSN) {
            flush();
        }
    }

    /// Flushes all records to the log file.
    ///
    /// @return An iterator over records starting from the
    /// current block id.
    public Iterator<byte[]> iterator() {
        flush();
        return new LogIterator(fileManager, currentBlockId);
    }

    /// Appends a new log record to the log file.
    /// Appending is done in reverse - bytes are inserted
    /// from right to left, so that the iterator can read them
    /// from the newest to the oldest log record.
    /// The method is synchronous because only one thread can
    /// be calling it at a time.
    ///
    /// The algorithm implementation is as follows:
    /// * get boundary of block
    /// * calculate bytes needed for the new log record to be written
    /// (log record length + integer length)
    /// * if boundary - bytes needed for the new log record is
    /// less than the size of an integer (because an additional integer
    /// is needed to store new log record byte length),
    /// flush the block to disk and get a new one as the last block
    /// * position the new log record at the byte:
    /// boundary - bytes length of the new record
    /// * place the bytes into the page using regular page API
    /// (which automatically puts the bytes and an integer
    /// that contains the length of those bytes)
    /// * update the block boundary to be the first byte
    /// where the newly written log record starts
    /// * update the latest log sequence number
    ///
    /// @return The log sequence number representing
    /// the flushed record.
    public synchronized int append(byte[] logRecord) {
        int boundary = logPage.getInt(0);
        int recordSize = logRecord.length;
        int bytesNeeded = recordSize + Integer.BYTES;

        if (boundary - bytesNeeded < Integer.BYTES) {
            flush();
            currentBlockId = appendNewBlock();
            boundary = logPage.getInt(0);
        }

        int recordPosition = boundary - bytesNeeded;
        logPage.setBytes(recordPosition, logRecord);
        logPage.setInt(0, recordPosition);
        latestLSN += 1;
        return latestLSN;
    }

    /// Moves the current log file in the archive directory, giving
    /// it the name of the latest log file number + 1 and creates a new
    /// log file.
    public void archiveLogFile() {
        Path dbDir = fileManager.getDbDirectory();
        Path archiveDir = dbDir.resolve("log_archive");

        try {
            if (!Files.exists(archiveDir)) {
                Files.createDirectories(archiveDir);
            }
        } catch (IOException e) {
            throw new FileException("Couldn't initialize archive directory");
        }

        try (Stream<Path> archiveDirFiles = Files.list(archiveDir)) {
            fileManager.closeFile(logFile);

            int fileCount = archiveDirFiles.toList().size();
            Path newLogFileName = archiveDir.resolve("log_file_" + fileCount);

            Files.move(dbDir.resolve(logFile), newLogFileName, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileException("Couldn't move old log file to the archive");
        }

        currentBlockId = appendNewBlock();
    }

    /// Appends an empty block to the log file. Puts an integer at the
    /// start of the last block, representing the boundary of this block.
    /// Flushes the current log page into the block.
    /// Must be called with an empty current log page.
    ///
    /// @return The new BlockId representing the last block
    /// in the log file.
    private BlockId appendNewBlock() {
        BlockId newBlockId = fileManager.append(logFile);
        logPage.setInt(0, fileManager.getBlockSize());
        fileManager.write(newBlockId, logPage);
        return newBlockId;
    }

    /// Writes the log page to the current block id
    /// and updates the `lastSavedLSN` to be equal
    /// to the `latestLSN`.
    private void flush() {
        fileManager.write(currentBlockId, logPage);
        lastSavedLSN = latestLSN;
    }
}
