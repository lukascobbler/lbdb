package com.luka.lbdb.benchmarkTests.bufferStrategyTests.continuousReads;

import com.luka.lbdb.benchmarkTests.bufferStrategyTests.AbstractROBufferStrategyBenchmarkTest;
import com.luka.lbdb.benchmarkTests.bufferStrategyTests.BufferTestType;
import com.luka.lbdb.db.settings.BufferStrategy;
import org.junit.jupiter.api.Tag;

@Tag("CONTINUOUS_READS")
public class FirstUnmodifiedContinuousReadsROBufferBenchmarkTest extends AbstractROBufferStrategyBenchmarkTest {
    public FirstUnmodifiedContinuousReadsROBufferBenchmarkTest() {
        super(BufferStrategy.FIRST_UNMODIFIED, BufferTestType.CONTINUOUS_READS);
    }
}