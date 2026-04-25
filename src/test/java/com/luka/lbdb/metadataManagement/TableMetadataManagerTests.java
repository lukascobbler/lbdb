package com.luka.lbdb.metadataManagement;

import com.luka.lbdb.fileManagement.FileManager;
import com.luka.lbdb.metadataManagement.exceptions.TableDuplicateNameException;
import com.luka.lbdb.metadataManagement.exceptions.TableNotFoundException;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.db.LBDB;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.records.Layout;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.transactionManagement.Transaction;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TableMetadataManagerTests {
    // the test from the book Fig 7.2, modified so
    // asserts are used and prints and randoms removed
    @Test
    public void testTableCreationAndLayoutRetrival() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();

        LBDB LBDB = new LBDB(tmpDir);
        Transaction tx = LBDB.getTransactionManager().getOrCreateTransaction(-1);

        Schema sch = new Schema();
        sch.addIntField("A", false);
        sch.addStringField("B", 9, false);
        LBDB.getMetadataManager().createTable("testTable", sch, tx);

        Layout layout = LBDB.getMetadataManager().getLayout("testTable", tx);

        assertEquals(sch, layout.getSchema());
        assertEquals(40, layout.recordLength());

        tx.commit();
    }

    // the test from the book Fig 7.5
    @Test
    public void testTableCatalog() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();

        LBDB LBDB = new LBDB(tmpDir);
        Transaction tx = LBDB.getTransactionManager().getOrCreateTransaction(-1);

        Layout layout = LBDB.getMetadataManager().getLayout("tablecatalog", tx);
        TableScan tableCatalogScan = new TableScan(tx, "tablecatalog", layout);

        try (tableCatalogScan) {
            while (tableCatalogScan.next()) {
                String tableName = tableCatalogScan.getValue("tablename").asString();
                int size = tableCatalogScan.getValue("slotsize").asInt();
                System.out.println("Table name: " + tableName + ", table size: " + size);
            }
        }

        layout = LBDB.getMetadataManager().getLayout("fieldcatalog", tx);
        TableScan fieldCatalogScan = new TableScan(tx, "fieldcatalog", layout);

        try (fieldCatalogScan) {
            while (fieldCatalogScan.next()) {
                int tableId = fieldCatalogScan.getValue("tableid").asInt();
                String fieldName = fieldCatalogScan.getValue("fieldname").asString();
                int type = fieldCatalogScan.getValue("type").asInt();
                int length = fieldCatalogScan.getValue("runtimelength").asInt();
                int offset = fieldCatalogScan.getValue("offset").asInt();
                System.out.println("Table Id: " + tableId + ", field name: " +
                        fieldName + ", field type: " + type + ", runtimeLength: " + length + ", offset: " + offset);
            }
        }

        tx.commit();
    }

    @Test
    public void testDuplicateTableName() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();

        LBDB LBDB = new LBDB(tmpDir);
        Transaction tx = LBDB.getTransactionManager().getOrCreateTransaction(-1);
        MetadataManager metadataManager = LBDB.getMetadataManager();

        Schema schema = new Schema();
        schema.addIntField("field", false);

        metadataManager.createTable("tbl1", schema, tx);
        assertThrowsExactly(TableDuplicateNameException.class, () -> metadataManager.createTable("tbl1", schema, tx));

        tx.commit();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testCreateTableInsertRecordRollback(boolean undoOnlyRecovery) throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();

        LBDBSettings settings = new LBDBSettings();
        settings.UNDO_ONLY_RECOVERY = undoOnlyRecovery;

        LBDB LBDB = new LBDB(tmpDir, settings);
        Transaction tx = LBDB.getTransactionManager().getOrCreateTransaction(-1);
        MetadataManager metadataManager = LBDB.getMetadataManager();

        FileManager fm = (FileManager) TestUtils.getPrivateField(LBDB.getTransactionManager(), "fileManager");

        Schema schema = new Schema();
        schema.addIntField("field", false);

        metadataManager.createTable("tbl1", schema, tx);
        Layout tableLayout = metadataManager.getLayout("tbl1", tx);

        TableScan tableScan = new TableScan(tx, "tbl1", tableLayout);

        try (tableScan) {
            for (int i = 0; i < 1000; i++) {
                tableScan.insert();
                tableScan.setValue("field", new IntConstant(100));
            }
        }

        tx.rollback();

        Transaction tx2 = LBDB.getTransactionManager().getOrCreateTransaction(-1);

        assertThrowsExactly(TableNotFoundException.class, () -> metadataManager.getLayout("tbl1", tx2));
        assertFalse(TestUtils.fileExists(tmpDir, "tbl1.table"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testCreateTableInsertRecordRollbackCreateTableSameName(boolean undoOnlyRecovery) throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();

        LBDBSettings settings = new LBDBSettings();
        settings.UNDO_ONLY_RECOVERY = undoOnlyRecovery;

        LBDB LBDB = new LBDB(tmpDir, settings);
        Transaction tx = LBDB.getTransactionManager().getOrCreateTransaction(-1);
        MetadataManager metadataManager = LBDB.getMetadataManager();

        FileManager fm = (FileManager) TestUtils.getPrivateField(LBDB.getTransactionManager(), "fileManager");

        Schema schema = new Schema();
        schema.addIntField("field", false);

        metadataManager.createTable("tbl1", schema, tx);
        Layout tableLayout = metadataManager.getLayout("tbl1", tx);

        TableScan tableScanOld = new TableScan(tx, "tbl1", tableLayout);

        try (tableScanOld) {
            for (int i = 0; i < 1000; i++) {
                tableScanOld.insert();
                tableScanOld.setValue("field", new IntConstant(100));
            }
        }

        tx.rollback();

        Transaction tx2 = LBDB.getTransactionManager().getOrCreateTransaction(-1);

        assertThrowsExactly(TableNotFoundException.class, () -> metadataManager.getLayout("tbl1", tx2));
        assertFalse(TestUtils.fileExists(tmpDir, "tbl1.table"));

        schema.addIntField("field1", false);
        schema.addIntField("field2", false);
        schema.addIntField("field3", false);
        schema.addIntField("field4", false);
        metadataManager.createTable("tbl1", schema, tx2);

        tableLayout = metadataManager.getLayout("tbl1", tx2);

        TableScan tableScanNew = new TableScan(tx2, "tbl1", tableLayout);

        try (tableScanNew) {
            for (int i = 0; i < 1000; i++) {
                tableScanNew.insert();
                tableScanNew.setValue("field", new IntConstant(100));
                tableScanNew.setValue("field1", new IntConstant(100));
                tableScanNew.setValue("field2", new IntConstant(100));
                tableScanNew.setValue("field3", new IntConstant(100));
                tableScanNew.setValue("field4", new IntConstant(100));
            }
        }

        tx2.commit();

        Transaction tx3 = LBDB.getTransactionManager().getOrCreateTransaction(-1);

        TableScan tableScanNewRead = new TableScan(tx3, "tbl1", tableLayout);

        int count = 0;
        try (tableScanNewRead) {
            while (tableScanNewRead.next()) {
                count += 1;
                assertEquals(100, tableScanNewRead.getValue("field").asInt());
                assertEquals(100, tableScanNewRead.getValue("field1").asInt());
                assertEquals(100, tableScanNewRead.getValue("field2").asInt());
                assertEquals(100, tableScanNewRead.getValue("field3").asInt());
                assertEquals(100, tableScanNewRead.getValue("field4").asInt());
            }
        }

        assertEquals(1000, count);
    }
}
