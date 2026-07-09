package com.luka.lbdb.querying.scanTypes.readOnly;

import com.luka.lbdb.querying.scanDefinitions.BinaryScan;
import com.luka.lbdb.querying.scanDefinitions.DiffSchemaJoinContextScan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;

/// A product scan represents the "cross product" relational algebra operator.
/// It is a binary table read-only scan. The user specifies two child scans
/// that will have a cross product as a result of this scan.
public class ProductScan extends BinaryScan {
    private final DiffSchemaJoinContextScan joinContext;
    private boolean isOuterValid = false;

    /// A product scan requires two child scans to create a product on.
    /// The first scan is the outer scan, while the second scan is
    /// the inner scan.
    public ProductScan(Scan childScan1, Scan childScan2) {
        super(childScan1, childScan2);
        joinContext = new DiffSchemaJoinContextScan(childScan1, childScan2);
    }

    /// A product scan is positioned before the first "combined" record
    /// if the outer scan is positioned at the first record, and if the
    /// inner scan is positioned before the first record.
    @Override
    public void beforeFirst() {
        childScan1.beforeFirst();
        isOuterValid = childScan1.next();
        if (isOuterValid) {
            childScan2.beforeFirst();
        }
    }
    /// A product scan is positioned after the last "combined" record
    /// if the outer scan is positioned at the last record, and if the
    /// inner scan is positioned after the last record.
    @Override
    public void afterLast() {
        childScan1.afterLast();
        isOuterValid = childScan1.previous();
        if (isOuterValid) {
            childScan2.afterLast();
        }
    }

    /// A product scan for every record of the outer scan, goes over
    /// all records in the inner scan and produces the "combined" record.
    ///
    /// @return True if there are more "combined" records, false if both
    /// the outer and inner scans are exhausted.
    @Override
    public boolean next() {
        if (!isOuterValid) {
            return false;
        }

        if (childScan2.next()) {
            return true;
        }

        isOuterValid = childScan1.next();
        if (isOuterValid) {
            childScan2.beforeFirst();
            return childScan2.next();
        }

        return false;
    }

    /// A product scan for every record of the outer scan, goes over
    /// all records in the inner scan and produces the "combined" record.
    ///
    /// @return True if there are more "combined" records, false if both
    /// the outer and inner scans are at the start.
    @Override
    public boolean previous() {
        if (!isOuterValid) {
            return false;
        }

        if (childScan2.previous()) {
            return true;
        }

        isOuterValid = childScan1.previous();
        if (isOuterValid) {
            childScan2.afterLast();
            return childScan2.previous();
        }

        return false;
    }

    /// @return True if either scan has the field.
    @Override
    public boolean hasField(String fieldName) {
        return joinContext.hasField(fieldName);
    }

    /// @return The constant from the scan that has that field.
    @Override
    public Constant getValue(String fieldName) {
        return joinContext.getValue(fieldName);
    }
}
