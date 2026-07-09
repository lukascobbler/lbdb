package com.luka.lbdb.querying.scanDefinitions;

import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.records.RecordId;

/// An update scan is the backbone of how queries update table data.
/// Update scans define value setter functions based on field names,
/// as well as general modification functions like insert and delete.
///
/// All update scans mut have a relation with physical table records, meaning
/// that records that the update scans produce must be mappable to a
/// physical table record. This is true because a scan record that doesn't have
/// a mapping to a physical record doesn't know about its layout and therefore
/// can't update it.
///
/// The `UpdateScan` class extends `Scan` and has all of its methods because
/// if a scan is able to update data, it can retrieve the data as well.
public abstract class UpdateScan extends Scan {
    // public API general update scan features that concrete scans must implement

    /// Insert a new record at the end.
    public abstract void insert();

    /// Delete the currently positioned record.
    public abstract void delete();

    /// Only update scans can get record ids because regular
    /// scans can contain records whose data doesn't entirely exist on
    /// a physical disk, so getting their record ids may be disingenuous.
    ///
    /// @return The record id of the currently positioned record.
    public abstract RecordId getRecordId();

    /// Move to the specified record. Only update scans can navigate to
    /// specific record ids because regular scans can contain records whose
    /// data doesn't entirely exist on a physical disk, so navigating to their
    /// record ids may be disingenuous.
    public abstract void moveToRecord(RecordId rid);

    // public API scan setters

    /// Sets a constant value for the field name.
    public abstract void setValue(String fieldName, Constant value);
}