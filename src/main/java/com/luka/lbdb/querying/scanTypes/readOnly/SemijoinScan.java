package com.luka.lbdb.querying.scanTypes.readOnly;

import com.luka.lbdb.querying.scanDefinitions.BinaryScan;
import com.luka.lbdb.querying.scanDefinitions.DiffSchemaJoinContextScan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;

/// A semijoin scan represents the "semi join" relational algebra operator.
/// It is a binary table read-only scan. The user specifies two tables and
/// a predicate, where the result of this scan are the rows from the outer
/// scan that match the predicate for at least one row in the inner scan.
public class SemijoinScan extends BinaryScan {
    private final Predicate predicate;
    private final DiffSchemaJoinContextScan diffSchemaJoinContextScan;

    /// A semijoin scan requires two child scans and a predicate to
    /// form the final result set. The first scan is the outer scan,
    /// while the second scan is the inner scan.
    public SemijoinScan(Scan childScan1, Scan childScan2, Predicate predicate) {
        super(childScan1, childScan2);
        this.predicate = predicate;
        diffSchemaJoinContextScan = new DiffSchemaJoinContextScan(childScan1, childScan2);
    }

    /// Setting the semi join scan before the first element only requires
    /// setting the outer scan before the first element.
    @Override
    public void beforeFirst() {
        childScan1.beforeFirst();
    }

    /// Setting the semi join scan after the last element only requires
    /// setting the outer scan after the last element.
    @Override
    public void afterLast() {
        childScan1.afterLast();
    }

    /// Advancing the semi join scan to the next record requires checking
    /// that for the current record in the outer scan, at least one record in the
    /// inner scan matches the predicate. This method advances the outer scan until
    /// that condition is matched.
    ///
    /// @return True if there is more records in the outer scan that have
    /// at least one matching predicate for the inner scan, false otherwise.
    @Override
    public boolean next() {
        while (childScan1.next()) {
            childScan2.beforeFirst();
            while (childScan2.next()) {
                if (predicate.evaluate(diffSchemaJoinContextScan).asBoolean()) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Retreating the semi join scan to the previous record requires checking
    /// that for the current record in the outer scan, at least one record in the
    /// inner scan matches the predicate. This method advances the outer scan until
    /// that condition is matched.
    ///
    /// @return True if there is more records in the outer scan that have
    /// at least one matching predicate for the inner scan, false otherwise.
    @Override
    public boolean previous() {
        while (childScan1.previous()) {
            childScan2.beforeFirst();
            while (childScan2.next()) {
                if (predicate.evaluate(diffSchemaJoinContextScan).asBoolean()) {
                    return true;
                }
            }
        }
        return false;
    }

    /// @return True, if the outer scan has a matching field name.
    @Override
    public boolean hasField(String fieldName) {
        return childScan1.hasField(fieldName);
    }

    /// @return The constant for the given field name in the outer scan.
    @Override
    public Constant getValue(String fieldName) {
        return childScan1.getValue(fieldName);
    }
}
