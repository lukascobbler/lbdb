package com.luka.lbdb.benchmarkTests.bufferStrategyTests.modifiedContinuousReads;

import com.luka.lbdb.benchmarkTests.bufferStrategyTests.AbstractWBufferStrategyBenchmarkTest;
import com.luka.lbdb.benchmarkTests.bufferStrategyTests.BufferTestType;
import com.luka.lbdb.db.settings.BufferStrategy;
import org.junit.jupiter.api.Tag;

@Tag("CONTINUOUS_READS_M")
public class FirstUnmodifiedModifiedContinuousReadsWBufferBenchmarkTest extends AbstractWBufferStrategyBenchmarkTest {
    public FirstUnmodifiedModifiedContinuousReadsWBufferBenchmarkTest() {
        super(BufferStrategy.FIRST_UNMODIFIED, BufferTestType.CONTINUOUS_READS_M);
    }
}