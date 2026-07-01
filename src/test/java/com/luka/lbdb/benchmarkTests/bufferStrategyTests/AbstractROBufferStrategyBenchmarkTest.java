package com.luka.lbdb.benchmarkTests.bufferStrategyTests;

import com.luka.lbdb.benchmarkTests.BenchmarkTestUtils;
import com.luka.lbdb.db.settings.BufferStrategy;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.StringConstant;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.testUtils.TestUtils;
import com.luka.lbdb.transactionManagement.BufferStatistics;
import com.luka.lbdbclient.TablePrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named = "benchmark", matches = ".*")
public abstract class AbstractROBufferStrategyBenchmarkTest {
    private static final int NUM_REPEATS = 10;
    private static final int maxStrategyNameLength;
    private static final Schema outputSchema;
    private final BufferTestType testType;
    protected final LBDBSettings settings;

    static {
        outputSchema = new Schema();
        outputSchema.addStringField("Strategy", 16, false);
        outputSchema.addIntField("Read", false);
        outputSchema.addIntField("Write", false);
        outputSchema.addIntField("Hit", false);
        outputSchema.addIntField("Time (ms)", false);

        maxStrategyNameLength = Arrays.stream(BufferStrategy.values())
                .map(BufferStrategy::toString)
                .map(String::length)
                .max(Comparator.comparingInt(Integer::intValue)).orElseThrow();
    }

    protected AbstractROBufferStrategyBenchmarkTest(BufferStrategy strategy, BufferTestType testType) {
        LBDBSettings baseSettings = new LBDBSettings();
        baseSettings.BUFFER_POOL_SIZE = 15;
        baseSettings.bufferStrategy = strategy;
        this.settings = baseSettings;
        this.testType = testType;
    }

    @Test
    public void testStrategy() throws IOException {
        // must be on-disk
        Path tmpDir = TestUtils.setUpTempDirectory(TestUtils.TmpDirType.DISK);

        var testData = BenchmarkTestUtils.initializeTwoBigFullTables(tmpDir, settings, testType.numRecords1, testType.numRecords2);
        testData.tx().commit();
        testData.db().getTransactionManager().resetInMemoryState();
        testData.db().getTransactionManager().resetBufferStatistics();

        double[] times = new double[NUM_REPEATS];
        BufferStatistics[] bufStat = new BufferStatistics[NUM_REPEATS];

        // warm-up loop
        for (int i = 0; i < 2; i++) {
            BenchmarkTestUtils.createAndRunPlan(testType.query, testData.db());
        }

        for (int i = 0; i < NUM_REPEATS; i++) {
            testData.db().getTransactionManager().resetBufferStatistics();
            times[i] = BenchmarkTestUtils.createAndRunPlan(testType.query, testData.db());
            bufStat[i] = testData.db().getTransactionManager().getBufferStatistics();
        }

        Arrays.sort(times);
        int timesMedian = (int) ((times[(NUM_REPEATS / 2) - 1] + times[NUM_REPEATS / 2]) / 2.0);
        Arrays.sort(bufStat, Comparator.comparingInt(BufferStatistics::numCacheHits));
        int hitsMedian = (int)
                ((bufStat[(NUM_REPEATS / 2) - 1].numCacheHits() + bufStat[NUM_REPEATS / 2].numCacheHits()) / 2.0);
        Arrays.sort(bufStat, Comparator.comparingInt(BufferStatistics::numReads));
        int readsMedian = (int)
                ((bufStat[(NUM_REPEATS / 2) - 1].numReads() + bufStat[NUM_REPEATS / 2].numReads()) / 2.0);
        Arrays.sort(bufStat, Comparator.comparingInt(BufferStatistics::numWrites));
        int writesMedian = (int)
                ((bufStat[(NUM_REPEATS / 2) - 1].numWrites() + bufStat[NUM_REPEATS / 2].numWrites()) / 2.0);

        System.out.println(TablePrinter.print(
                outputSchema,
                List.of(List.of(
                        new StringConstant(String.format("%-" + maxStrategyNameLength + "s", settings.bufferStrategy.toString())),
                        new IntConstant(readsMedian),
                        new IntConstant(writesMedian),
                        new IntConstant(hitsMedian),
                        new IntConstant(timesMedian)
                ))
        ));
    }
}

