package com.luka.lbdb.benchmarkTests.productOrderTests;

import com.luka.lbdb.benchmarkTests.BenchmarkTestUtils;
import com.luka.lbdb.db.settings.BufferStrategy;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.metadataManagement.StatisticsMetadataManager;
import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.StringConstant;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.testUtils.TestUtils;
import com.luka.lbdb.transactionManagement.BufferStatistics;
import com.luka.lbdb.transactionManagement.Transaction;
import com.luka.lbdbclient.TablePrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named = "benchmark", matches = ".*")
public class AbstractProductOrderBenchmarkTest {
    private static final int NUM_REPEATS = 10;
    private static final Schema outputSchema;
    private final ProductOrderTestType productOrderTestType;

    static {
        outputSchema = new Schema();
        outputSchema.addStringField("Order", 16, false);
        outputSchema.addIntField("PID", false);
        outputSchema.addIntField("Number of reads by B(Tr) = B(Tl) + (floor(RPB(Tl)) * B(Tl) * B(Td))", false);
        outputSchema.addIntField("Number repeats", false);
        outputSchema.addIntField("Number of records in both tables", false);
        outputSchema.addIntField("Median number of reads", false);
        outputSchema.addBooleanField("Naive buffer replacement strategy", false);
        outputSchema.addIntField("Median time (ms)", false);
    }

    protected AbstractProductOrderBenchmarkTest(ProductOrderTestType productOrderTestType) {
        this.productOrderTestType = productOrderTestType;
    }

    @Test
    public void testStrategy() throws Exception {
        // must be on-disk
        Path tmpDir = TestUtils.setUpTempDirectory(TestUtils.TmpDirType.DISK);

        BufferStrategy bufferStrategy = BufferStrategy.LRU;
        String isNaive = System.getProperty("naive");

        if (isNaive != null) {
            bufferStrategy = BufferStrategy.NAIVE;
        }

        LBDBSettings settings = new LBDBSettings();
        settings.bufferStrategy = bufferStrategy;

        var testData = BenchmarkTestUtils.initializeBigAndSmallFullTables(tmpDir, settings, productOrderTestType.numRecords);

        StatisticsMetadataManager sm = (StatisticsMetadataManager)
                TestUtils.getPrivateField(testData.db().getMetadataManager(), "statisticsMetadataManager");
        Method refreshStatisticsMethod = StatisticsMetadataManager.class.getDeclaredMethod("refreshStatistics", Transaction.class);
        refreshStatisticsMethod.setAccessible(true);

        refreshStatisticsMethod.invoke(sm, testData.tx());
        int numBlocksBig = testData.db().getMetadataManager()
                        .getStatisticsInfo("big", testData.layoutList().get(0), testData.tx()).numBlocks();
        int numBlocksSmall = testData.db().getMetadataManager()
                        .getStatisticsInfo("small", testData.layoutList().get(1), testData.tx()).numBlocks();
        testData.tx().commit();
        testData.db().getTransactionManager().resetInMemoryState();
        testData.db().getTransactionManager().resetBufferStatistics();

        double[] times = new double[NUM_REPEATS];
        BufferStatistics[] bufStat = new BufferStatistics[NUM_REPEATS];

        for (int i = 0; i < NUM_REPEATS; i++) {
            testData.db().getTransactionManager().resetBufferStatistics();
            testData.db().getTransactionManager().resetInMemoryState();
            times[i] = BenchmarkTestUtils.createAndRunPlan(productOrderTestType.query, testData.db());
            bufStat[i] = testData.db().getTransactionManager().getBufferStatistics();
        }

        Arrays.sort(times);
        int timesMedian = (int) ((times[(NUM_REPEATS / 2) - 1] + times[NUM_REPEATS / 2]) / 2.0);
        Arrays.sort(bufStat, Comparator.comparingInt(BufferStatistics::numReads));
        int readsMedian = (int)
                ((bufStat[(NUM_REPEATS / 2) - 1].numReads() + bufStat[NUM_REPEATS / 2].numReads()) / 2.0);

        int formulaPrediction;
        if (productOrderTestType == ProductOrderTestType.BIG_SMALL) {
            formulaPrediction = formulaPrediction(
                    numBlocksBig,
                    numBlocksSmall,
                    testData.db().getTransactionManager().getBlockSize() /
                            testData.layoutList().getFirst().recordLength()
            );
        } else {
            formulaPrediction = formulaPrediction(
                    numBlocksSmall,
                    numBlocksBig,
                    testData.db().getTransactionManager().getBlockSize() /
                            testData.layoutList().get(1).recordLength()
            );
        }

        System.out.println(TablePrinter.print(
                outputSchema,
                List.of(List.of(
                        new StringConstant(productOrderTestType.toString()),
                        new IntConstant((int) ProcessHandle.current().pid()),
                        new IntConstant(formulaPrediction),
                        new IntConstant(NUM_REPEATS),
                        new IntConstant(productOrderTestType.numRecords),
                        new IntConstant(readsMedian),
                        new BooleanConstant(bufferStrategy == BufferStrategy.NAIVE),
                        new IntConstant(timesMedian)
                ))
        ));
    }

    private int formulaPrediction(int bLeft, int bRight, int recordsPerBlockLeft) {
        return bLeft + (recordsPerBlockLeft * bLeft * bRight);
    }
}

