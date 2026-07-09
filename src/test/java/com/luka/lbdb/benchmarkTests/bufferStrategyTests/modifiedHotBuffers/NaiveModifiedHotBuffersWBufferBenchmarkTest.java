package com.luka.lbdb.benchmarkTests.bufferStrategyTests.modifiedHotBuffers;

import com.luka.lbdb.benchmarkTests.bufferStrategyTests.AbstractWBufferStrategyBenchmarkTest;
import com.luka.lbdb.benchmarkTests.bufferStrategyTests.BufferTestType;
import com.luka.lbdb.db.settings.BufferStrategy;
import org.junit.jupiter.api.Tag;

@Tag("HOT_BUFFERS_M")
public class NaiveModifiedHotBuffersWBufferBenchmarkTest extends AbstractWBufferStrategyBenchmarkTest {
    public NaiveModifiedHotBuffersWBufferBenchmarkTest() {
        super(BufferStrategy.NAIVE, BufferTestType.HOT_BUFFERS_M);
    }
}