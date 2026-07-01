package com.luka.lbdb.benchmarkTests.bufferStrategyTests.modifiedContinuousReads;

import com.luka.lbdb.benchmarkTests.bufferStrategyTests.AbstractWBufferStrategyBenchmarkTest;
import com.luka.lbdb.benchmarkTests.bufferStrategyTests.BufferTestType;
import com.luka.lbdb.db.settings.BufferStrategy;
import org.junit.jupiter.api.Tag;

@Tag("CONTINUOUS_READS_M")
public class LruModifiedContinuousReadsWBufferBenchmarkTest extends AbstractWBufferStrategyBenchmarkTest {
    public LruModifiedContinuousReadsWBufferBenchmarkTest() {
        super(BufferStrategy.LRU, BufferTestType.CONTINUOUS_READS_M);
    }
}