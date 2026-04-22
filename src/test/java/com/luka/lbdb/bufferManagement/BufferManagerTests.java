package com.luka.lbdb.bufferManagement;

import com.luka.lbdb.bufferManagement.exceptions.BufferPinException;
import com.luka.lbdb.db.settings.BufferStrategy;
import com.luka.lbdb.fileManagement.BlockId;
import com.luka.lbdb.fileManagement.FileManager;
import com.luka.lbdb.logManagement.LogManager;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;

public class BufferManagerTests {
    @Test
    public void testInitialAvailableBuffers() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        FileManager fileManager = new FileManager(tmpDir, 512);
        LogManager logManager = new LogManager(fileManager, "test.log");
        BufferManager bufferManager = new BufferManager(fileManager, logManager, 3, BufferStrategy.LRU);

        assertEquals(3, bufferManager.available());
    }

    @Test
    public void testPinDecreasesAvailableBuffers() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        FileManager fileManager = new FileManager(tmpDir, 512);
        LogManager logManager = new LogManager(fileManager, "test.log");
        BufferManager bufferManager = new BufferManager(fileManager, logManager, 3, BufferStrategy.LRU);

        BlockId block1 = new BlockId("test.dat", 0);
        Buffer buffer1 = bufferManager.pin(block1);

        assertNotNull(buffer1);
        assertTrue(buffer1.isPinned());
        assertEquals(2, bufferManager.available());
    }

    @Test
    public void testUnpinIncreasesAvailableBuffers() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        FileManager fileManager = new FileManager(tmpDir, 512);
        LogManager logManager = new LogManager(fileManager, "test.log");
        BufferManager bufferManager = new BufferManager(fileManager, logManager, 3, BufferStrategy.LRU);

        BlockId block1 = new BlockId("test.dat", 0);
        Buffer buffer1 = bufferManager.pin(block1);

        bufferManager.unpin(buffer1);

        assertFalse(buffer1.isPinned());
        assertEquals(3, bufferManager.available());
    }

    @Test
    public void testPinExistingBlockReturnsSameBuffer() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        FileManager fileManager = new FileManager(tmpDir, 512);
        LogManager logManager = new LogManager(fileManager, "test.log");
        BufferManager bufferManager = new BufferManager(fileManager, logManager, 3, BufferStrategy.LRU);

        BlockId block1 = new BlockId("test.dat", 0);
        Buffer buffer1 = bufferManager.pin(block1);
        Buffer buffer2 = bufferManager.pin(block1);

        assertSame(buffer1, buffer2);
        assertEquals(2, bufferManager.available());
    }

    @Test
    public void testBufferReplacementReusesUnpinnedBuffer() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        FileManager fileManager = new FileManager(tmpDir, 512);
        LogManager logManager = new LogManager(fileManager, "test.log");
        BufferManager bufferManager = new BufferManager(fileManager, logManager, 3, BufferStrategy.LRU);

        BlockId block1 = new BlockId("test.dat", 0);
        BlockId block2 = new BlockId("test.dat", 1);
        BlockId block3 = new BlockId("test.dat", 2);
        BlockId block4 = new BlockId("test.dat", 3);

        Buffer buffer1 = bufferManager.pin(block1);
        Buffer buffer2 = bufferManager.pin(block2);
        Buffer buffer3 = bufferManager.pin(block3);

        assertEquals(0, bufferManager.available());

        bufferManager.unpin(buffer2);
        assertEquals(1, bufferManager.available());

        Buffer buffer4 = bufferManager.pin(block4);

        assertEquals(0, bufferManager.available());
        assertEquals(block4, buffer4.getBlockId());
        assertSame(buffer2, buffer4);
    }

    @Test
    public void testFlushAllByTransaction() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        FileManager fileManager = new FileManager(tmpDir, 512);
        LogManager logManager = new LogManager(fileManager, "test.log");
        BufferManager bufferManager = new BufferManager(fileManager, logManager, 3, BufferStrategy.LRU);

        BlockId block1 = new BlockId("test.dat", 0);
        BlockId block2 = new BlockId("test.dat", 1);

        Buffer buffer1 = bufferManager.pin(block1);
        Buffer buffer2 = bufferManager.pin(block2);

        int transactionId = 100;
        buffer1.setModified(transactionId, 1);
        buffer2.setModified(transactionId, 2);

        assertEquals(transactionId, buffer1.modifyingTransaction());
        assertEquals(transactionId, buffer2.modifyingTransaction());

        bufferManager.flushAll(transactionId);

        assertEquals(-1, buffer1.modifyingTransaction());
        assertEquals(-1, buffer2.modifyingTransaction());
    }

    @Test
    public void testBufferUnpinException() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        FileManager fileManager = new FileManager(tmpDir, 512);
        LogManager logManager = new LogManager(fileManager, "test.log");
        Buffer buffer = new Buffer(fileManager, logManager, 0);

        assertThrows(BufferPinException.class, buffer::unpin);
    }
}
