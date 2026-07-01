package com.luka.lbdb.benchmarkTests.bufferStrategyTests.continuousReads;

import com.luka.lbdb.benchmarkTests.bufferStrategyTests.AbstractROBufferStrategyBenchmarkTest;
import com.luka.lbdb.benchmarkTests.bufferStrategyTests.BufferTestType;
import com.luka.lbdb.db.settings.BufferStrategy;
import org.junit.jupiter.api.Tag;

@Tag("CONTINUOUS_READS")
public class LrmContinuousReadsROBufferBenchmarkTest extends AbstractROBufferStrategyBenchmarkTest {
    public LrmContinuousReadsROBufferBenchmarkTest() {
        super(BufferStrategy.LRM, BufferTestType.CONTINUOUS_READS);
    }
}
