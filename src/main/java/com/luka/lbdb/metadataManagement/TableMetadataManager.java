package com.luka.lbdb.metadataManagement;

import com.luka.lbdb.metadataManagement.exceptions.TableDuplicateNameException;
import com.luka.lbdb.metadataManagement.exceptions.TableNotFoundException;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.StringConstant;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.Layout;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.transactionManagement.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/// `TableMetadataManager` worries about what tables exist in the DBMS,
/// and what fields do these tables contain. It uses two internal metadata
/// tables called catalogs ("tablecatalog" and "fieldcatalog") to memorize this information.
public class TableMetadataManager {
    public static final int MAX_NAME_LENGTH = 32;
    private final Layout tableCatalogLayout, fieldCatalogLayout;
    private final AtomicInteger nextTableNum;

    /// Instantiates a new `TableMetadataManager` that has always in-memory layouts
    /// for catalog tables to improve efficiency. If the system is initialized for the first time
    /// (noted by `isNew`), the manager creates catalog tables as well.
    public TableMetadataManager(boolean isNew, Transaction transaction, AtomicInteger nextTableNum) {
        Schema tableCatalogSchema = new Schema();
        tableCatalogSchema.addIntField("tableid", false);
        tableCatalogSchema.addStringField("tablename", MAX_NAME_LENGTH, false);
        tableCatalogSchema.addIntField("slotsize", false);
        tableCatalogLayout = new Layout(tableCatalogSchema, transaction.blockSize());

        Schema fieldCatalogSchema = new Schema();
        fieldCatalogSchema.addIntField("type", false);
        fieldCatalogSchema.addIntField("runtimelength", false);
        fieldCatalogSchema.addIntField("offset", false);
        fieldCatalogSchema.addIntField("tableid", false);
        fieldCatalogSchema.addStringField("fieldname", MAX_NAME_LENGTH, false);
        fieldCatalogSchema.addBooleanField("nullable", false);
        fieldCatalogLayout = new Layout(fieldCatalogSchema, transaction.blockSize());

        this.nextTableNum = nextTableNum;

        if (isNew) {
            this.nextTableNum.set(1);
            createTable("tablecatalog", tableCatalogSchema, transaction);
            createTable("fieldcatalog", fieldCatalogSchema, transaction);
        } else {
            this.nextTableNum.set(getLatestTableIdNum(transaction));
        }
    }

    /// Creates a table with `tableName` that is defined by `schema` in current transaction.
    /// Internally, it adds a new row to the table catalog, and adds a new row for every field
    /// of that table in the field catalog.
    ///
    /// @throws TableDuplicateNameException if the table already exists with the same name in the system.
    public void createTable(String tableName, Schema schema, Transaction transaction) {
        TableScan tableCatalogDuplicateScan = new TableScan(transaction, "tablecatalog", tableCatalogLayout);

        try (tableCatalogDuplicateScan) {
            while (tableCatalogDuplicateScan.next()) {
                if (tableCatalogDuplicateScan.getValue("tablename").asString().equals(tableName)) {
                    throw new TableDuplicateNameException();
                }
            }
        }

        Layout layout = new Layout(schema, transaction.blockSize());
        int tableIdNum = nextTableIdNum();

        TableScan tableCatalogScan = new TableScan(transaction, "tablecatalog", tableCatalogLayout);
        try (tableCatalogScan) {
            tableCatalogScan.insert();
            tableCatalogScan.setValue("tableid", new IntConstant(tableIdNum));
            tableCatalogScan.setValue("tablename", new StringConstant(tableName));
            tableCatalogScan.setValue("slotsize", new IntConstant(layout.recordLength()));
        }

        TableScan fieldCatalogScan = new TableScan(transaction, "fieldcatalog", fieldCatalogLayout);
        try (fieldCatalogScan) {
            for (String fieldName : schema.getFields()) {
                fieldCatalogScan.insert();
                fieldCatalogScan.setValue("tableid", new IntConstant(tableIdNum));
                fieldCatalogScan.setValue("fieldname", new StringConstant(fieldName));
                fieldCatalogScan.setValue("nullable", new BooleanConstant(schema.isNullable(fieldName)));
                fieldCatalogScan.setValue("type", new IntConstant(schema.type(fieldName).sqlType));
                fieldCatalogScan.setValue("runtimelength", new IntConstant(schema.runtimeLength(fieldName)));
                fieldCatalogScan.setValue("offset", new IntConstant(layout.getOffset(fieldName)));
            }
        }
    }

    /// Finds a table in the table catalog and retrieves it's record size. Finds all
    /// fields of that table in the field catalog, and retrieves their offsets, types and
    /// lengths. Reconstructs a layout from all that information.
    ///
    /// @return The layout matching the table name passed.
    /// @throws TableNotFoundException if the table was not found.
    public Layout getLayout(String tableName, Transaction transaction) {
        int size = -1;
        int tableId = -1;
        TableScan tableCatalogScan = new TableScan(transaction, "tablecatalog", tableCatalogLayout);

        try (tableCatalogScan) {
            while (tableCatalogScan.next()) {
                if (tableCatalogScan.getValue("tablename").asString().equals(tableName)) {
                    size = tableCatalogScan.getValue("slotsize").asInt();
                    tableId = tableCatalogScan.getValue("tableid").asInt();
                    break;
                }
            }
        }

        if (size == -1) {
            throw new TableNotFoundException();
        }

        Schema schema = new Schema();
        Map<String, Integer> offsets = new HashMap<>();

        TableScan fieldCatalogScan = new TableScan(transaction, "fieldcatalog", fieldCatalogLayout);

        try (fieldCatalogScan) {
            while (fieldCatalogScan.next()) {
                if (fieldCatalogScan.getValue("tableid").asInt() == tableId) {
                    String fieldName = fieldCatalogScan.getValue("fieldname").asString();
                    int sqlType = fieldCatalogScan.getValue("type").asInt();
                    int fieldLength = fieldCatalogScan.getValue("runtimelength").asInt();
                    int fieldOffset = fieldCatalogScan.getValue("offset").asInt();
                    boolean fieldNullable = fieldCatalogScan.getValue("nullable").asBoolean();
                    offsets.put(fieldName, fieldOffset);
                    schema.addField(fieldName, DatabaseType.get(sqlType), fieldLength, fieldNullable);
                }
            }
        }

        return new Layout(schema, offsets, size);
    }

    /// Removes the metadata for the field, thereby making the field
    /// not accessible anymore by any means.
    public void removeField(String tableName, String fieldName, Transaction transaction) {
        int tableId = -1;
        TableScan tableCatalogScan = new TableScan(transaction, "tablecatalog", tableCatalogLayout);

        try (tableCatalogScan) {
            while (tableCatalogScan.next()) {
                if (tableCatalogScan.getValue("tablename").asString().equals(tableName)) {
                    tableId = tableCatalogScan.getValue("tableid").asInt();
                    break;
                }
            }
        }

        if (tableId == -1) {
            throw new TableNotFoundException();
        }

        TableScan fieldCatalogScan = new TableScan(transaction, "fieldcatalog", fieldCatalogLayout);

        try (fieldCatalogScan) {
            while (fieldCatalogScan.next()) {
                if (
                    fieldCatalogScan.getValue("tableid").asInt() == tableId &&
                    fieldCatalogScan.getValue("fieldname").asString().equals(fieldName)
                ) {
                    fieldCatalogScan.delete();
                    return;
                }
            }
        }
    }

    /// @return The largest table id number. Should be run only on startup.
    private int getLatestTableIdNum(Transaction transaction) {
        int maxTableNumber = -1;

        TableScan tableCatalogScan = new TableScan(transaction, "tablecatalog", tableCatalogLayout);

        try (tableCatalogScan) {
            while (tableCatalogScan.next()) {
                int currentTableId = tableCatalogScan.getValue("tableid").asInt();
                if (currentTableId > maxTableNumber) {
                    maxTableNumber = currentTableId;
                }
            }
        }

        return maxTableNumber;
    }

    /// @return The table id number representing the next
    /// table id in the system.
    private int nextTableIdNum() {
        return nextTableNum.incrementAndGet();
    }
}
