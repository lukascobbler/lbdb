package com.luka.lbdb.querying.scanTypes.readOnly;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanDefinitions.UnaryScan;
import com.luka.lbdb.querying.virtualEntities.Evaluatable;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;

import java.util.Map;

/// An extend project scan represents the "generalized projection" relational algebra operator
/// (removes and adds columns) plus a rename (renames that expression's result as a field name).
/// It is a unary table read-only scan. The user specifies the list of projection expressions
/// and the names for them.
public class ExtendProjectScan extends UnaryScan {
    private final Map<String, Evaluatable> projections;

    /// An extend project scan requires the expressions that will be
    /// evaluated for every row, and names for them. Each expression
    /// will be treated as a field from this scan upwards, and
    /// a child scan.
    public ExtendProjectScan(Scan childScan, Map<String, Evaluatable> projections) {
        super(childScan);
        this.projections = projections;
    }

    /// This scan has a field if its super scan has a field, or
    /// if the given field name equals the name of the named expression.
    ///
    /// @return True if the field exists, from all fields of the super
    /// scan or if the field equals the name of the named expression.
    @Override
    public boolean hasField(String fieldName) {
        return projections.containsKey(fieldName);
    }

    /// For the named expression, its result is calculated on the child
    /// scan and returned, and for every other field, the result is just
    /// the child scan's result.
    ///
    /// @return The constant for the corresponding named expression or any other field.
    @Override
    public Constant getValue(String fieldName) {
        if (projections.containsKey(fieldName)) {
            return projections.get(fieldName).evaluate(childScan);
        }

        return super.getValue(fieldName);
    }
}
