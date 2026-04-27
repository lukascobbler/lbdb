package com.luka.lbdb.querying.scanDefinitions;

import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.records.RecordId;

/// A scan class that defines default functionality for all scans
/// that can update records and build on top of **one** child scan,
/// which are the scans that operate on one table and do not add column
/// information. All scans that operate on one table and can modify
/// data should extend this class and redefine only needed behaviors.
public abstract class UnaryUpdateScan extends UpdateScan {
    protected final UpdateScan childScan;

    public UnaryUpdateScan(UpdateScan childScan) { this.childScan = childScan; }

    // default implementations for a typical scan
    @Override public void beforeFirst() { childScan.beforeFirst(); }
    @Override public void afterLast() { childScan.afterLast(); }
    @Override public boolean next() { return childScan.next(); }
    @Override public boolean previous() { return childScan.previous(); }
    @Override public boolean hasField(String fieldName) { return childScan.hasField(fieldName); }
    @Override public void close() { childScan.close(); }
    // default implementations for a typical update scan
    @Override public void insert() { childScan.insert(); }
    @Override public void delete() { childScan.delete(); }
    @Override public RecordId getRecordId() { return childScan.getRecordId(); }
    @Override public void moveToRecord(RecordId rid) { childScan.moveToRecord(rid); }

    // default getter implementations
    @Override public Constant getValue(String fieldName) { return childScan.getValue(fieldName); }

    // default setter implementations
    @Override public void setValue(String fieldName, Constant value) { childScan.setValue(fieldName, value); }
}