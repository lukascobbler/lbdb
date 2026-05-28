package com.luka.lbdb.planning;

import com.luka.lbdb.metadataManagement.MetadataManager;
import com.luka.lbdb.metadataManagement.StatisticsMetadataManager;
import com.luka.lbdb.parsing.parser.Parser;
import com.luka.lbdb.parsing.statement.*;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.planning.planner.Planner;
import com.luka.lbdb.planning.planner.plannerDefinitions.QueryPlanner;
import com.luka.lbdb.planning.planner.plannerDefinitions.UpdatePlanner;
import com.luka.lbdb.planning.planner.plannerTypes.BasicUpdatePlanner;
import com.luka.lbdb.planning.planner.plannerTypes.BetterQueryPlanner;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanDefinitions.UpdateScan;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.constant.StringConstant;
import com.luka.lbdb.records.Layout;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.db.LBDB;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.transactionManagement.Transaction;
import com.luka.lbdb.testUtils.TestUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Initialization methods that encapsulate data needed for plan tests.
public class PlanTestUtils {

    /// Helper record for test initialization functions.
    public record PlanTestData(LBDB db, Transaction tx, List<Layout> layouts) { }

    /// Executes only the checks for a given update statement.
    ///
    /// @return 0 because the statement won't be run.
    public static int checkUpdateStatement(PlanTestData testData, String query, String checkMethodName)
            throws Throwable {
        QueryPlanner dummyQueryPlanner = new QueryPlanner(testData.db().getMetadataManager()) {
            @Override protected Plan<Scan> createPlan(SelectStatement selectStatement, Transaction transaction) { return null; }
        };
        UpdatePlanner dummyPlanner = new UpdatePlanner(testData.db().getMetadataManager()) {
            @Override protected int executeUpdate(UpdateStatement u, Transaction t) { return 0; }
            @Override protected int executeInsert(InsertStatement i, Transaction t) { return 0; }
            @Override protected int executeDelete(DeleteStatement d, Transaction t) { return 0; }
            @Override protected int executeCreateTable(CreateTableStatement ct, Transaction t) { return 0; }
            @Override protected int executeCreateIndex(CreateIndexStatement ci, Transaction t) { return 0; }
            @Override public void resetLastInsertion() { }
        };

        Statement updateStatement = new Parser(query).parse();

        Method relMethod = switch (updateStatement) {
            case CreateIndexStatement cis -> UpdatePlanner.class.getDeclaredMethod(
                    checkMethodName, CreateIndexStatement.class, Transaction.class);
            case CreateTableStatement cts -> UpdatePlanner.class.getDeclaredMethod(
                    checkMethodName, CreateTableStatement.class, Transaction.class);
            case DeleteStatement ds -> UpdatePlanner.class.getDeclaredMethod(
                    checkMethodName, DeleteStatement.class, Transaction.class);
            case ExplainStatement es -> UpdatePlanner.class.getDeclaredMethod(
                    checkMethodName, ExplainStatement.class, Transaction.class);
            case InsertStatement is -> UpdatePlanner.class.getDeclaredMethod(
                    checkMethodName, InsertStatement.class, Transaction.class);
            case SelectStatement ss -> UpdatePlanner.class.getDeclaredMethod(
                    checkMethodName, SelectStatement.class, Transaction.class);
            case UpdateStatement us -> UpdatePlanner.class.getDeclaredMethod(
                    checkMethodName, UpdateStatement.class, Transaction.class);
            default -> throw new IllegalStateException("Unexpected value: " + updateStatement);
        };

        relMethod.setAccessible(true);
        MethodHandle handle = MethodHandles.lookup().unreflect(relMethod);

        return (int) handle.invoke(dummyPlanner, updateStatement, testData.tx());
    }

    /// Executes only the checks for a given statement.
    ///
    /// @return The checked and expanded select statement.
    public static SelectStatement resultingCheckedSelectStatement(PlanTestData testData, String query)
            throws Throwable {
        QueryPlanner dummyPlanner = new QueryPlanner(testData.db().getMetadataManager()) {
            /// Unused for check tests.
            @Override protected Plan<Scan> createPlan(SelectStatement s, Transaction t) { return null; }
        };

        SelectStatement selectStatement = (SelectStatement) new Parser(query).parse();

        Method relMethod = QueryPlanner.class.getDeclaredMethod("checkStatement", SelectStatement.class, Transaction.class);
        relMethod.setAccessible(true);
        MethodHandle handle = MethodHandles.lookup().unreflect(relMethod);

        return (SelectStatement) handle.invoke(dummyPlanner, selectStatement, testData.tx());
    }

    /// @return The same data with a new transaction.
    public static PlanTestData newTransaction(PlanTestData oldTransaction) {
        return new PlanTestData(
                oldTransaction.db(),
                oldTransaction.db().getTransactionManager().getOrCreateTransaction(-1),
                oldTransaction.layouts()
        );
    }

    /// Executes the statement.
    ///
    /// @return The number of rows affected.
    public static int executeUpdate(PlanTestData testData, String query) {
        Planner p = new Planner(
                new BetterQueryPlanner(testData.db().getMetadataManager()),
                new BasicUpdatePlanner(testData.db().getMetadataManager(), new HashMap<>()),
                testData.db.getTransactionManager()
        );

        return p.executeUpdate(query, testData.tx());
    }

    /// Creates a plan for the statement.
    ///
    /// @return The checked and expanded select statement.
    public static Plan<Scan> createQueryPlan(PlanTestData testData, String query) {
        Planner p = new Planner(
                new BetterQueryPlanner(testData.db().getMetadataManager()),
                new BasicUpdatePlanner(testData.db().getMetadataManager(), new HashMap<>()),
                testData.db.getTransactionManager()
        );

        return p.createQueryPlan(query, testData.tx());
    }

    /// Default settings.
    public static PlanTestData initializeThreeEmptyTables(Path tmpDir) {
        return initializeThreeEmptyTables(tmpDir, new LBDBSettings());
    }

    /// Creates three tables: "table1", "table2", "table3", with four fields of each type
    /// for the first two tables where the third fieldName of each type is nullable.
    /// For the third table, only one duplicate exists.
    public static PlanTestData initializeThreeEmptyTables(Path tmpDir, LBDBSettings settings) {
        LBDB LBDB = new LBDB(tmpDir, settings);
        Transaction tx = LBDB.getTransactionManager().getOrCreateTransaction(-1);

        Schema sch1 = new Schema();
        sch1.addIntField("t1_intfield1", false);
        sch1.addIntField("t1_intfield2", false);
        sch1.addIntField("t1_intfield3", true);
        sch1.addIntField("sameint", false);
        sch1.addStringField("t1_stringfield1", 100, false);
        sch1.addStringField("t1_stringfield2", 100, false);
        sch1.addStringField("t1_stringfield3", 100, true);
        sch1.addStringField("samestring", 100, false);
        sch1.addBooleanField("t1_boolfield1", false);
        sch1.addBooleanField("t1_boolfield2", false);
        sch1.addBooleanField("t1_boolfield3", true);
        sch1.addBooleanField("samebool", false);

        Schema sch2 = new Schema();
        sch2.addIntField("t2_intfield1", false);
        sch2.addIntField("t2_intfield2", false);
        sch2.addIntField("t2_intfield3", true);
        sch2.addIntField("sameint", false);
        sch2.addStringField("t2_stringfield1", 100, false);
        sch2.addStringField("t2_stringfield2", 100, false);
        sch2.addStringField("t2_stringfield3", 100, true);
        sch2.addStringField("samestring", 100, false);
        sch2.addBooleanField("t2_boolfield1", false);
        sch2.addBooleanField("t2_boolfield2", false);
        sch2.addBooleanField("t2_boolfield3", true);
        sch2.addBooleanField("samebool", false);

        Schema sch3 = new Schema();
        sch3.addIntField("sameint", false);

        LBDB.getMetadataManager().createTable("table1", sch1, tx);
        LBDB.getMetadataManager().createTable("table2", sch2, tx);
        LBDB.getMetadataManager().createTable("table3", sch3, tx);

        Layout layout1 = LBDB.getMetadataManager().getLayout("table1", tx);
        Layout layout2 = LBDB.getMetadataManager().getLayout("table2", tx);
        Layout layout3 = LBDB.getMetadataManager().getLayout("table3", tx);

        return new PlanTestData(LBDB, tx, Arrays.asList(layout1, layout2, layout3));
    }

    /// Default settings.
    public static PlanTestData initializeThreeFullTables(Path tmpDir) {
        return initializeThreeFullTables(tmpDir, new LBDBSettings());
    }

    /// Creates three tables: "table1", "table2", "table3", with four fields of each type
    /// for the first two tables where the third fieldName of each type is nullable and set
    /// to null and the fourth fieldName of each type is named the same in both tables
    /// and has 50/100 same values. For the third table, only one duplicate exists.
    public static PlanTestData initializeThreeFullTables(Path tmpDir, LBDBSettings settings) {
        PlanTestData initializedTables = initializeThreeEmptyTables(tmpDir, settings);

        UpdateScan tableScan1 = new TableScan(
                initializedTables.tx, "table1", initializedTables.layouts.get(0));

        try (tableScan1) {
            tableScan1.beforeFirst();
            for (int i = 0; i < 250; i++) {
                tableScan1.insert();
                tableScan1.setValue("t1_intfield1", new IntConstant(i));
                tableScan1.setValue("t1_intfield2", new IntConstant(i + 1));
                tableScan1.setValue("t1_intfield3", NullConstant.INSTANCE);
                tableScan1.setValue("sameint", new IntConstant(i + 50));
                tableScan1.setValue("t1_stringfield1", new StringConstant("str" + i));
                tableScan1.setValue("t1_stringfield2", new StringConstant("str" + i + 1));
                tableScan1.setValue("t1_stringfield3", NullConstant.INSTANCE);
                tableScan1.setValue("samestring", new StringConstant("str" + i + 10));
                tableScan1.setValue("t1_boolfield1", new BooleanConstant(true));
                tableScan1.setValue("t1_boolfield2", new BooleanConstant(false));
                tableScan1.setValue("t1_boolfield3", NullConstant.INSTANCE);
                tableScan1.setValue("samebool", new BooleanConstant(i < 50));
            }
        }

        UpdateScan tableScan2 = new TableScan(
                initializedTables.tx, "table2", initializedTables.layouts.get(1));

        try (tableScan2) {
            tableScan2.beforeFirst();
            for (int i = 0; i < 250; i++) {
                tableScan2.insert();
                tableScan2.setValue("t2_intfield1", new IntConstant(i));
                tableScan2.setValue("t2_intfield2", new IntConstant(i + 1));
                tableScan2.setValue("t2_intfield3", NullConstant.INSTANCE);
                tableScan2.setValue("sameint", new IntConstant(i));
                tableScan2.setValue("t2_stringfield1", new StringConstant("str" + i));
                tableScan2.setValue("t2_stringfield2", new StringConstant("str" + i + 1));
                tableScan2.setValue("t2_stringfield3", NullConstant.INSTANCE);
                tableScan2.setValue("samestring", new StringConstant("str" + i + 50));
                tableScan2.setValue("t2_boolfield1", new BooleanConstant(true));
                tableScan2.setValue("t2_boolfield2", new BooleanConstant(false));
                tableScan2.setValue("t2_boolfield3", NullConstant.INSTANCE);
                tableScan2.setValue("samebool", new BooleanConstant(true));
            }
        }

        UpdateScan tableScan3 = new TableScan(
                initializedTables.tx, "table3", initializedTables.layouts.get(2));

        try (tableScan3) {
            tableScan3.beforeFirst();
            for (int i = 0; i < 250; i++) {
                tableScan3.insert();
                tableScan3.setValue("sameint", new IntConstant(i));
            }
        }

        return initializedTables;
    }

    /// Helper method to refresh the statistics of the system early.
    public static void refreshStatistics(PlanTestData testData) throws Exception {
        MetadataManager metadataManager = testData.db().getMetadataManager();

        StatisticsMetadataManager sm = (StatisticsMetadataManager)
                TestUtils.getPrivateField(metadataManager, "statisticsMetadataManager");
        Method refreshStatisticsMethod = StatisticsMetadataManager.class.getDeclaredMethod("refreshStatistics", Transaction.class);
        refreshStatisticsMethod.setAccessible(true);

        refreshStatisticsMethod.invoke(sm, testData.tx());
    }

    /// Helper method to verify whether the estimation of a plan tree is
    /// within 1.5 of the magnitude of the estimation. Be aware that this method
    /// actually goes through the whole scan.
    public static <T extends Scan> void verifyRecordOutputEstimation(PlanTestData data, Plan<T> plan) {
        int recordCount = 0;
        try (Scan s = plan.open()) {
            s.beforeFirst();
            while (s.next()) {
                recordCount += 1;
            }
        }

        int estimation = plan.recordsOutput();
        int estimationMagnitude = (int) Math.pow(10, (String.valueOf(estimation).length() - 1));
        double delta = estimationMagnitude * 1.5;
        assertEquals(estimation, recordCount, delta, "delta: " + delta);
    }
}
