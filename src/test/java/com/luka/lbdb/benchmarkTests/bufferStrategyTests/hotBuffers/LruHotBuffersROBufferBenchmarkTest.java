package com.luka.lbdb.benchmarkTests.bufferStrategyTests.hotBuffers;

import com.luka.lbdb.benchmarkTests.bufferStrategyTests.AbstractROBufferStrategyBenchmarkTest;
import com.luka.lbdb.benchmarkTests.bufferStrategyTests.BufferTestType;
import com.luka.lbdb.db.settings.BufferStrategy;
import org.junit.jupiter.api.Tag;

@Tag("HOT_BUFFERS")
public class LruHotBuffersROBufferBenchmarkTest extends AbstractROBufferStrategyBenchmarkTest {
    public LruHotBuffersROBufferBenchmarkTest() {
        super(BufferStrategy.LRU, BufferTestType.HOT_BUFFERS);
    }
}