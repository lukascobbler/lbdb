package com.luka.lbdb.querying.scanTypes.readOnly;

import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;

import java.util.Map;

/// A table scan is a special type of scan because it has
/// no child scans, and is always at the bottom of the scan evaluation
/// tree. It gets data from the given in-memory virtual row.
/// It does not extend the default scan implementations because
/// it is completely standalone.
public class DummyTableScan extends Scan {
    int position = 0;
    private final Map<String, Constant> values;

    /// A dummy table scan needs the field names and the constant
    /// values those field names map to.
    public DummyTableScan(Map<String, Constant> values) {
        this.values = values;
    }

    /// Since there is for sure only one known row, the position "0" can
    /// indicate that row, "-1" can indicate the position before it, and
    /// "1" can indicate the position after it.
    @Override
    public void beforeFirst() {
        position = -1;
    }

    /// Since there is for sure only one known row, the position "0" can
    /// indicate that row, "-1" can indicate the position before it, and
    /// "1" can indicate the position after it.
    @Override
    public void afterLast() {
        position = 1;
    }

    /// Advances the scan to the first row, or if the scan is already positioned
    /// at the first row, does nothing.
    ///
    /// @return Whether the virtual row is next.
    @Override
    public boolean next() {
        if (position >= 0) return false;
        position++;
        return true;
    }

    /// Advances the scan to the first row, or if the scan is already positioned
    /// at the first row, does nothing.
    ///
    /// @return Whether the virtual row is previous.
    @Override
    public boolean previous() {
        if (position <= 0) return false;
        position--;
        return true;
    }

    /// @return True if any of the virtual fields names'
    /// equal the given field name.
    @Override
    public boolean hasField(String fieldName) {
        return values.containsKey(fieldName);
    }

    /// A virtual scan has no resources to release, so
    /// this method is empty.
    @Override
    public void close() { }

    /// @return The value from the virtual row.
    @Override
    public Constant getValue(String fieldName) {
        return values.get(fieldName);
    }
}
