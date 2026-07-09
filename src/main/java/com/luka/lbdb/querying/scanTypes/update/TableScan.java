package com.luka.lbdb.querying.scanTypes.update;

import com.luka.lbdb.fileManagement.BlockId;
import com.luka.lbdb.querying.scanDefinitions.UpdateScan;
import com.luka.lbdb.querying.virtualEntities.constant.*;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.Layout;
import com.luka.lbdb.records.RecordId;
import com.luka.lbdb.records.RecordPage;
import com.luka.lbdb.records.exceptions.DatabaseTypeNotImplementedException;
import com.luka.lbdb.transactionManagement.Transaction;

/// A table scan is a special type of update scan because it has
/// no child scans, and is always at the bottom of the scan evaluation
/// tree. It gets the data stored on the disk, rather than operating
/// on intermediate results. It does not extend the default scan
/// implementations because it is completely standalone.
public class TableScan extends UpdateScan {
    private final Transaction transaction;
    private final Layout layout;
    private final String filename;
    private RecordPage recordPage;
    private int currentRecord;

    /// A table scan is defined for a table, it's layout and for a given transaction.
    public TableScan(Transaction transaction, String tableName, Layout layout) {
        this.transaction = transaction;
        this.layout = layout;
        filename = tableName + ".table";
        if (transaction.lengthInBlocks(filename) == 0) {
            moveToNewBlock();
        } else {
            moveToBlock(0);
        }
    }

    /// Actual resource release, i.e. unpinning the buffers of record
    /// pages. Function is idempotent so calling it multiple times is
    /// okay.
    @Override
    public void close() {
        if (recordPage != null) transaction.unpin(recordPage.getBlockId());
    }

    /// Move the scan to before the first record in the first block.
    public void beforeFirst() {
        moveToBlock(0);
    }

    /// Move the scan after the last record in the last block.
    public void afterLast() {
        moveToBlock(transaction.lengthInBlocks(filename) - 1);
        currentRecord = recordPage.numRecords();
    }

    /// Move the scan to the next record, indicating whether it exists or not.
    /// If the current record page has no more records, move to the next
    /// block and point a new record page to that block.
    ///
    /// @return True if the end of the table isn't reached, otherwise false.
    public boolean next() {
        currentRecord = recordPage.nextAfter(currentRecord);
        while (currentRecord < 0) {
            if (atLastBlock()) {
                return false;
            }
            moveToBlock(recordPage.getBlockId().blockNum() + 1);
            currentRecord = recordPage.nextAfter(currentRecord);
        }

        return true;
    }

    /// Move the scan to the previous record, indicating whether it exists or not.
    /// If the current record page has no more records, move to the previous
    /// block and point a new record page to that block.
    ///
    /// @return True if the beginning of the table isn't reached, otherwise false.
    public boolean previous() {
        currentRecord = recordPage.nextBefore(currentRecord);
        while (currentRecord < 0) {
            if (atFirstBlock()) {
                return false;
            }
            moveToBlock(recordPage.getBlockId().blockNum() - 1);
            currentRecord = recordPage.nextBefore(recordPage.numRecords());
        }

        return true;
    }

    /// @return Whether the table the scan is defined for has some field name.
    public boolean hasField(String fieldName) {
        return layout.getSchema().hasField(fieldName);
    }

    /// Generic value getter. All representable states in the database
    /// are abstracted over the `Constant` record which can include special
    /// cases like null values. The caller does not need to know the type
    /// of the field.
    ///
    /// @return A constant which can be of any special DB type or any type
    /// that the database implements.
    /// @throws DatabaseTypeNotImplementedException if the system does not implement
    /// the type of the field name.
    public Constant getValue(String fieldName) {
        if (recordPage.isNull(currentRecord, fieldName)) {
            return NullConstant.INSTANCE;
        }
        switch (layout.getSchema().type(fieldName)) {
            case DatabaseType.INT -> { return new IntConstant(recordPage.getInt(currentRecord, fieldName)); }
            case DatabaseType.VARCHAR -> { return new StringConstant(recordPage.getString(currentRecord, fieldName)); }
            case DatabaseType.BOOLEAN -> { return new BooleanConstant(recordPage.getBoolean(currentRecord, fieldName)); }
            default -> throw new DatabaseTypeNotImplementedException();
        }
    }

    /// Generic value setter. All representable states in the database
    /// are abstracted over the `Constant` record which can include special
    /// cases like null values. The caller does not need to know the type
    /// of the field.
    ///
    /// @throws DatabaseTypeNotImplementedException if the system does not implement
    /// the type of the field name.
    public void setValue(String fieldName, Constant value) {
        if (value instanceof NullConstant) {
            recordPage.setNull(currentRecord, fieldName);
            return;
        }
        switch (layout.getSchema().type(fieldName)) {
            case DatabaseType.INT -> recordPage.setInt(currentRecord, fieldName, value.asInt());
            case DatabaseType.VARCHAR -> recordPage.setString(currentRecord, fieldName, value.asString());
            case DatabaseType.BOOLEAN -> recordPage.setBoolean(currentRecord, fieldName, value.asBoolean());
            default -> throw new DatabaseTypeNotImplementedException();
        }
    }

    /// Inserts a new record in the table at the end.
    public void insert() {
        currentRecord = recordPage.insertAfter(currentRecord);
        while (currentRecord < 0) {
            if (atLastBlock()) {
                moveToNewBlock();
            } else {
                moveToBlock(recordPage.getBlockId().blockNum() + 1);
            }
            currentRecord = recordPage.insertAfter(currentRecord);
        }
    }

    /// Deletes the current record.
    public void delete() {
        recordPage.delete(currentRecord);
    }

    /// Move to explicitly defined record.
    public void moveToRecord(RecordId recordId) {
        close();
        BlockId blockId = new BlockId(filename, recordId.blockNum());
        recordPage = new RecordPage(transaction, blockId, layout);
        currentRecord = recordId.record();
    }

    /// @return The record id information about the current scan position.
    public RecordId getRecordId() {
        return new RecordId(recordPage.getBlockId().blockNum(), currentRecord);
    }

    /// Adds a new block at the end of the file for the table that the scan
    /// is defined for. Resets the current record of that block to -1, so the
    /// sequential operations can start from the start of the block.
    private void moveToNewBlock() {
        close();
        BlockId blockId = transaction.appendEmptyBlock(filename, true);
        recordPage = new RecordPage(transaction, blockId, layout);
        recordPage.format();
        currentRecord = -1;
    }

    /// Moves to the provided block number.
    /// Resets the current record of that block to -1, so the
    /// sequential operations can start from the start of the block.
    private void moveToBlock(int blockNumber) {
        close();
        BlockId blockId = new BlockId(filename, blockNumber);
        recordPage = new RecordPage(transaction, blockId, layout);
        currentRecord = -1;
    }

    /// @return Whether the scan is at the last block for the table file
    /// that the scan is defined for.
    private boolean atLastBlock() {
        return recordPage.getBlockId().blockNum() == transaction.lengthInBlocks(filename) - 1;
    }

    /// @return Whether the scan is at the first block for the table file
    /// that the scan is defined for.
    private boolean atFirstBlock() {
        return recordPage.getBlockId().blockNum() == 0;
    }
}
