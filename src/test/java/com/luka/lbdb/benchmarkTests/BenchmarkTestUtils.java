package com.luka.lbdb.benchmarkTests;

import com.luka.lbdb.db.LBDB;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.network.protocol.response.ErrorResponse;
import com.luka.lbdb.network.protocol.response.Response;
import com.luka.lbdb.querying.scanDefinitions.UpdateScan;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.constant.StringConstant;
import com.luka.lbdb.records.Layout;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.transactionManagement.Transaction;

import java.nio.file.Path;
import java.util.List;

public class BenchmarkTestUtils {
    /// Helper record for test initialization functions.
    public record BenchmarkTestData(LBDB db, Transaction tx, List<Layout> layoutList) { }

    /// Creates a table with 3000 records, useful for benchmark tests of different algorithms.
    public static BenchmarkTestData initializeTwoBigFullTables(Path tmpDir, LBDBSettings settings, int numRecords, int numRecordsSecond) {
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

        LBDB.getMetadataManager().createTable("table1", sch1, tx);

        Layout layout1 = LBDB.getMetadataManager().getLayout("table1", tx);

        UpdateScan tableScan1 = new TableScan(tx, "table1", layout1);

        try (tableScan1) {
            tableScan1.beforeFirst();
            for (int i = 0; i < numRecords; i++) {
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

        Schema sch2 = new Schema();
        sch2.addStringField("t2_stringfield1", 100, false);
        sch2.addStringField("t2_stringfield2", 100, false);
        sch2.addStringField("t2_stringfield3", 100, true);

        LBDB.getMetadataManager().createTable("table2", sch2, tx);

        Layout layout2 = LBDB.getMetadataManager().getLayout("table2", tx);

        UpdateScan tableScan2 = new TableScan(tx, "table2", layout2);

        try (tableScan2) {
            tableScan2.beforeFirst();
            for (int i = 0; i < numRecordsSecond; i++) {
                tableScan2.insert();
                tableScan2.setValue("t2_stringfield1", new StringConstant("str" + i));
                tableScan2.setValue("t2_stringfield2", new StringConstant("str" + i + 1));
                tableScan2.setValue("t2_stringfield3", NullConstant.INSTANCE);
            }
        }

        return new BenchmarkTestData(LBDB, tx, List.of(layout1, layout2));
    }

    /// Creates a table with 3000 records, useful for benchmark tests of different algorithms.
    public static BenchmarkTestData initializeBigAndSmallFullTables(Path tmpDir, LBDBSettings settings, int numRecords) {
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

        LBDB.getMetadataManager().createTable("big", sch1, tx);

        Layout layout1 = LBDB.getMetadataManager().getLayout("big", tx);

        UpdateScan tableScan1 = new TableScan(tx, "big", layout1);

        try (tableScan1) {
            tableScan1.beforeFirst();
            for (int i = 0; i < numRecords; i++) {
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

        Schema sch2 = new Schema();
        sch2.addBooleanField("t2_boolfield1", false);

        LBDB.getMetadataManager().createTable("small", sch2, tx);

        Layout layout2 = LBDB.getMetadataManager().getLayout("small", tx);

        UpdateScan tableScan2 = new TableScan(tx, "small", layout2);

        try (tableScan2) {
            tableScan2.beforeFirst();
            for (int i = 0; i < numRecords; i++) {
                tableScan2.insert();
                tableScan2.setValue("t2_boolfield1", new BooleanConstant(true));
            }
        }

        return new BenchmarkTestData(LBDB, tx, List.of(layout1, layout2));
    }

    /// Runs a query's plan.
    ///
    /// @return The number of ms it took to run the plan.
    public static double createAndRunPlan(String query, LBDB db) {
        long start = System.nanoTime();

        Response r = db.getPlanner().execute(query, -1);

        if (r instanceof ErrorResponse) {
            throw new IllegalStateException("The query did not run successfully, error: " + r);
        }

        long end = System.nanoTime();

        return (end - start) / 1_000_000.0;
    }
}
