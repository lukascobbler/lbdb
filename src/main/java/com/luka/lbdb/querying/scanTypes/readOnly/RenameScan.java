package com.luka.lbdb.querying.scanTypes.readOnly;

import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanDefinitions.UnaryScan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;

import java.util.Map;
import java.util.function.Function;

/// A rename read only scan represents the "rename" relational algebra operator.
/// It is a unary table read-only scan. The user specifies the map from new field
/// names to old field names and from this scan upward, renamed fields can only
/// be accessed through the new name.
public class RenameScan extends UnaryScan {
    private final Map<String, String> fieldMapping;

    /// A rename scan requires the field name mapping and a
    /// child scan.
    public RenameScan(Scan updateScan, Map<String, String> newToOldMapper) {
        super(updateScan);
        this.fieldMapping = newToOldMapper;
    }

    /// The field exists if it's either the new name for
    /// the renamed field, or any other field name.
    ///
    /// @return True if the renamed or any other field exists.
    @Override
    public boolean hasField(String fieldName) {
        String translated = map(fieldName);
        return translated != null && super.hasField(translated);
    }

    /// Maps the field name to the new one and calls the super class'
    /// retrieval function because this operation only adds the logic
    /// for field rename, it does not retrieve the actual value in any
    /// other way.
    ///
    /// @return The constant for the corresponding renamed or any other field.
    @Override
    public Constant getValue(String fieldName) {
        return wrapGetMapping(fieldName, super::getValue);
    }

    /// Does the actual mapping from the new field name to the old field name.
    private String map(String fieldName) {
        if (!fieldMapping.containsKey(fieldName)) {
            if (fieldMapping.containsValue(fieldName)) return null;
            return fieldName;
        }
        return fieldMapping.get(fieldName);
    }

    /// Maps the field name, and if its non-existent it errors out.
    /// Otherwise, it calls the passed function and returns its result.
    ///
    /// @return The result of the passed function.
    /// @throws RuntimeExecutionException if the old field name is passed as
    /// an argument.
    private <T> T wrapGetMapping(String fieldName, Function<String, T> sourceOperation) {
        String translated = map(fieldName);
        if (translated == null) {
            throw new RuntimeExecutionException("Field '" + fieldName + "' not found in the runtime");
        }
        return sourceOperation.apply(translated);
    }
}