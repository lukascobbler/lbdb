package com.luka.lbdb.benchmarkTests.bufferStrategyTests.hotBuffers;

import com.luka.lbdb.benchmarkTests.bufferStrategyTests.AbstractROBufferStrategyBenchmarkTest;
import com.luka.lbdb.benchmarkTests.bufferStrategyTests.BufferTestType;
import com.luka.lbdb.db.settings.BufferStrategy;
import org.junit.jupiter.api.Tag;

@Tag("HOT_BUFFERS")
public class LrmHotBuffersROBufferBenchmarkTest extends AbstractROBufferStrategyBenchmarkTest {
    public LrmHotBuffersROBufferBenchmarkTest() {
        super(BufferStrategy.LRM, BufferTestType.HOT_BUFFERS);
    }
}
