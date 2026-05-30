package com.luka.lbdb.querying.scanTypes.readOnly;

import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanDefinitions.UnaryScan;
import com.luka.lbdb.querying.virtualEntities.Predicate;

/// A select read only scan represents the "selection" relational algebra operator.
/// It is a unary table read-only scan. The user specifies the predicate that
/// must be matched for every row of the underlying scan.
public class SelectReadOnlyScan extends UnaryScan {
    private final Predicate predicate;

    /// A select scan requires the match predicate and a
    /// child scan.
    public SelectReadOnlyScan(Scan scan, Predicate predicate) {
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