package com.luka.lbdb.querying.scanTypes.update;

import com.luka.lbdb.querying.scanDefinitions.UnaryUpdateScan;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.scanDefinitions.UpdateScan;

/// A select scan represents the "selection" relational algebra operator.
/// It is a unary table update scan. The user specifies the predicate that
/// must be matched for every row of the underlying scan.
public class SelectScan extends UnaryUpdateScan {
    private final Predicate predicate;

    /// A select scan requires the match predicate and a
    /// child scan that must also be able to update data because
    /// a select scan can.
    public SelectScan(UpdateScan scan, Predicate predicate) {
        super(scan);
        this.predicate = predicate;
    }

    /// Advances the scan forwards until the predicate is matched
    /// for the current record.
    ///
    /// @return True if there are more records in this scan, false
    /// if the scan is exhausted.
    @Override
    public boolean next() {
        while (childScan.next()) {
            if (predicate.evaluate(childScan).asBoolean()) {
                return true;
            }
        }

        return false;
    }

    /// Retreats the scan backwards until the predicate is matched
    /// for the current record.
    ///
    /// @return True if there are more rows in this scan, false
    /// if the scan is at the first record.
    @Override
    public boolean previous() {
        while (childScan.previous()) {
            if (predicate.evaluate(childScan).asBoolean()) {
                return true;
            }
        }

        return false;
    }
}
