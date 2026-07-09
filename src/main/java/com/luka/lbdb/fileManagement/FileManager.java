package com.luka.lbdb.fileManagement;

import com.luka.lbdb.fileManagement.exceptions.FileException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.*;

/// The `FileManager` is the main API interface to the operating
/// system's files. Also called a Pager. Works exclusively with
/// blocks and pages, not direct bytes.
public class FileManager {
    private final Path dbDirectory;
    private final int blockSize;
    private final boolean isNew;
    private final Map<String, FileChannel> openFiles = new HashMap<>();
    private final AtomicInteger reads = new AtomicInteger(0);
    private final AtomicInteger writes = new AtomicInteger(0);

    /// The constructor initializes the directory where the database files will
    /// be stored by removing all files that start with "temp", or if the directory
    /// doesn't exist, it creates it.
    ///
    /// @throws FileException if the directory couldn't be initialized properly.
    public FileManager(Path dbDirectory, int blockSize) {
        this.dbDirectory = dbDirectory;
        this.blockSize = blockSize;
        this.isNew = !Files.exists(dbDirectory);

        try {
            if (isNew) Files.createDirectories(dbDirectory);
        } catch (IOException e) {
            throw new FileException("Could not initialize directory: " + e.getMessage());
        }
    }

    /// Reads the given block, from the provided block id,
    /// into the provided page. Method is `synchronized` because only one
    /// access to any of the files at the same time.
    ///
    /// @return The number of bytes read.
    /// @throws FileException if the block couldn't be read.
    public synchronized int read(BlockId blockId, Page page) {
        try {
            FileChannel fc = getFile(blockId.filename());

            reads.incrementAndGet();
            return fc.read(page.contents(), (long) blockId.blockNum() * blockSize);
        } catch (IOException e) {
            throw new FileException("cannot read block " + blockId);
        }
    }

    /// Writes into the given block, from the provided block id,
    /// from the provided page. Method is `synchronized` because only one
    /// access to any of the files at the same time.
    ///
    /// @return The number of bytes written.
    /// @throws FileException if writing is done beyond current file size.
    /// Blocks need to be appended first.
    public synchronized int write(BlockId blockId, Page page) {
        try {
            FileChannel fc = getFile(blockId.filename());

            if (fc.size() <= (long) blockId.blockNum() * blockSize) {
                throw new FileException("cannot write to block with index " +
                        "greater than length of blocks for file " + blockId.filename());
            }

            writes.incrementAndGet();
            return fc.write(page.contents(), (long) blockId.blockNum() * blockSize);
        } catch (IOException e) {
            throw new FileException("cannot write block " + blockId);
        }
    }

    /// Appends a new empty block of the system's block size
    /// to the end of the file. Method is `synchronized` because only one
    /// access to any of the files at the same time.
    ///
    /// @return The block id that corresponds to the newly written empty
    /// block of the given file.
    /// @throws FileException if the block couldn't be appended.
    public synchronized BlockId append(String filename) {
        int newBlockNum = lengthInBlocks(filename);
        BlockId blockId = new BlockId(filename, newBlockNum);
        ByteBuffer buf = ByteBuffer.allocate(blockSize);
        try {
            FileChannel fc = getFile(blockId.filename());
            fc.write(buf, (long) blockId.blockNum() * blockSize);
        } catch (IOException e) {
            throw new FileException("cannot append block " + blockId);
        }

        return blockId;
    }

    /// Truncates a file by removing the last block,
    /// regardless of its contents. Method is `synchronized`
    /// because only one access to any of the files at the same time.
    /// If the file is to be at 0 bytes after truncation, remove it.
    ///
    /// @throws FileException if the file couldn't be truncated.
    public synchronized void truncate(String filename) {
        try {
            FileChannel fc = getFile(filename);
            long length = fc.size();
            long newLength = length > blockSize ? length - blockSize : 0;
            fc.truncate(newLength);

            if (newLength == 0) {
                removeFile(filename);
            }
        } catch (IOException e) {
            throw new FileException("Cannot truncate file " + filename);
        }
    }

    /// @return The number of blocks that the provided file consists of.
    /// @throws FileException if the file couldn't be accessed.
    public int lengthInBlocks(String filename) {
        try {
            FileChannel fc = getFile(filename);
            return (int) (fc.size() / blockSize);
        } catch (IOException e) {
            throw new FileException("Cannot access " + filename);
        }
    }

    /// Removes a file managed by this database.
    /// @throws FileException if the file couldn't be deleted or is not managed by this database.
    public synchronized void removeFile(String filename) {
        FileChannel fc = openFiles.remove(filename);

        if (fc == null) {
            throw new FileException("Cannot remove file not managed by this database: " + filename);
        }

        try {
            fc.close();
            Files.deleteIfExists(dbDirectory.resolve(filename));
        } catch (IOException e) {
            throw new FileException("Failed to delete file: " + filename);
        }
    }

    /// Closes a file managed by this database.
    /// @throws FileException if the file couldn't be closed or is not managed by this database.
    public synchronized void closeFile(String filename) {
        FileChannel fc = openFiles.remove(filename);

        if (fc == null) {
            throw new FileException("Cannot close file not managed by this database: " + filename);
        }

        try {
            fc.close();
        } catch (IOException e) {
            throw new FileException("Failed to close file: " + filename);
        }
    }

    /// @return The main directory of the system.
    public Path getDbDirectory() {
        return dbDirectory;
    }

    /// @return Block size of the file manager.
    /// Also called the system's block size.
    public int getBlockSize() {
        return blockSize;
    }

    /// @return A boolean representing if the database folder
    /// was created for the first time in this file manager
    /// initialization.
    public boolean isNew() {
        return isNew;
    }

    /// @return A two sized array of integers, where the first
    /// element represents the number of reads, and the second
    /// number of writes to blocks.
    public int[] getBlockStatistics() {
        return new int[] { reads.get(), writes.get() };
    }

    /// Resets the counters for read and write block statistics.
    public void resetBlockStatistics() {
        reads.set(0);
        writes.set(0);
    }

    /// The implementation of this function creates a file if it doesn't
    /// exist in the map of currently opened files.
    /// Each file that is created is opened with "rws", meaning it is open
    /// for reading, writing and a note to the operating system that each file
    /// write must be immediate, skipping any buffers in between.
    /// Each created file is also inserted in the open files map.
    ///
    /// @return The file corresponding to the filename, from the file manager's
    /// open files map.
    private FileChannel getFile(String filename) throws IOException {
        FileChannel fc = openFiles.get(filename);

        if (fc == null) {
            Path path = dbDirectory.resolve(filename);
            fc = FileChannel.open(path, CREATE, READ, WRITE, SYNC);
            openFiles.put(filename, fc);
        }

        return fc;
    }
}
