package com.luka.lbdb.querying.scanTypes.readOnly;

import com.luka.lbdb.querying.scanDefinitions.BinaryScan;
import com.luka.lbdb.querying.scanDefinitions.DiffSchemaJoinContextScan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;

/// An antijoin scan represents the "anti join" relational algebra operator.
/// It is a binary table read-only scan. The user specifies two tables and
/// a predicate, where the result of this scan are the rows from the outer
/// scan that don't match the predicate for any row in the inner scan.
public class AntijoinScan extends BinaryScan {
    private final Predicate predicate;
    private final DiffSchemaJoinContextScan diffSchemaJoinContextScan;

    /// An antijoin scan requires two child scans and a predicate to
    /// form the final result set. The first scan is the outer scan,
    /// while the second scan is the inner scan.
    public AntijoinScan(Scan childScan1, Scan childScan2, Predicate predicate) {
        super(childScan1, childScan2);
        this.predicate = predicate;
        diffSchemaJoinContextScan = new DiffSchemaJoinContextScan(childScan1, childScan2);
    }

    /// Setting the anti join scan before the first element only requires
    /// setting the outer scan before the first element.
    @Override
    public void beforeFirst() {
        childScan1.beforeFirst();
    }

    /// Setting the anti join scan after the last element only requires
    /// setting the outer scan after the last element.
    @Override
    public void afterLast() {
        childScan1.afterLast();
    }

    /// Advancing the anti join scan to the next record requires checking
    /// that for the current record in the outer scan, no record in the inner
    /// scan matches the predicate. This method advances the outer scan until
    /// that condition is matched.
    ///
    /// @return True if there is more records in the outer scan that have
    /// no matching predicate for the inner scan, false otherwise.
    @Override
    public boolean next() {
        while (childScan1.next()) {
            childScan2.beforeFirst();
            boolean foundMatch = false;

            while (childScan2.next()) {
                if (predicate.evaluate(diffSchemaJoinContextScan).asBoolean()) {
                    foundMatch = true;
                    break;
                }
            }

            if (!foundMatch) {
                return true;
            }
        }
        return false;
    }

    /// Retreating the anti join scan to the previous record requires checking
    /// that for the current record in the outer scan, no record in the inner
    /// scan matches the predicate. This method retreats the outer scan until
    /// that condition is matched.
    ///
    /// @return True if there is more records in the outer scan that have
    /// no matching predicate for the inner scan, false if the first record
    /// is reached.
    @Override
    public boolean previous() {
        while (childScan1.previous()) {
            childScan2.beforeFirst();
            boolean foundMatch = false;

            while (childScan2.next()) {
                if (predicate.evaluate(diffSchemaJoinContextScan).asBoolean()) {
                    foundMatch = true;
                    break;
                }
            }

            if (!foundMatch) {
                return true;
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
