package com.luka.lbdb.planning.plan.planTypes.readOnly;

import com.luka.lbdb.planning.plan.ExplainData;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanTypes.readOnly.RenameScan;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Plan for the "rename" relational algebra operator. Read-only operations only.
public class RenamePlan implements Plan<Scan> {
    private final Plan<Scan> childPlan;
    private final Map<String, String> fieldMapping;
    private final Schema outputSchema = new Schema();

    /// Requires the plan that contains old field names, and a mapping from new -> old for renaming.
    /// New -> old is chosen instead of old -> new, because the rename scan has an easier time mapping
    /// from new to old, instead from old to new.
    public RenamePlan(Plan<Scan> childPlan, Map<String, String> fieldMapping) {
        this.childPlan = childPlan;
        this.fieldMapping = fieldMapping;

        Schema oldSchema = childPlan.outputSchema();

        for (String oldFieldName : oldSchema.getFields()) {
            DatabaseType fieldType = oldSchema.type(oldFieldName);
            int runtimeLength = oldSchema.runtimeLength(oldFieldName);
            boolean isNullable = oldSchema.isNullable(oldFieldName);

            if (fieldMapping.containsValue(oldFieldName)) {
                Optional<String> newNameForOldName = fieldMapping.entrySet().stream()
                        .filter(e -> e.getValue().equals(oldFieldName))
                        .map(Map.Entry::getKey)
                        .findFirst();

                //noinspection OptionalGetWithoutIsPresent because there will be a mapping from old -> new for sure
                outputSchema.addField(newNameForOldName.get(), fieldType, runtimeLength, isNullable);
            } else {
                outputSchema.addField(oldFieldName, fieldType, runtimeLength, isNullable);
            }
        }
    }

    @Override
    public Scan open() {
        return new RenameScan(childPlan.open(), fieldMapping);
    }

    /// Since renaming is only a wrapper for accessing fields through a new
    /// name, block accesses stay the same.
    ///
    /// @return The number of blocks from the subplan.
    @Override
    public int blocksAccessed() {
        return childPlan.blocksAccessed();
    }

    /// Since renaming is only a wrapper for accessing fields through a new
    /// name, number of output records stays the same.
    ///
    /// @return The records from the subplan.
    @Override
    public int recordsOutput() {
        return childPlan.recordsOutput();
    }

    /// If the requested field name has a new name, returns the subplan's distinct count
    /// with the old name; if the requested field name is one of the old names, returns
    /// 0 because that field doesn't exist anymore; if it is none of the previous two,
    /// the field isn't renamed and still exists as-is in the subplan.
    ///
    /// @return The number of distinct values for a field, rename-aware.
    @Override
    public int distinctValues(String fieldName) {
        if (fieldMapping.containsKey(fieldName)) {
            return childPlan.distinctValues(fieldMapping.get(fieldName));
        }
        if (fieldMapping.containsValue(fieldName)) {
            return 0;
        }

        return childPlan.distinctValues(fieldName);
    }

    /// If the requested field name has a new name, returns the subplan's null value count
    /// with the old name; if the requested field name is one of the old names, returns
    /// 0 because that field doesn't exist anymore; if it is none of the previous two,
    /// the field isn't renamed and still exists as-is in the subplan.
    ///
    /// @return The number of null values for a field, rename-aware.
    @Override
    public int nullValues(String fieldName) {
        if (fieldMapping.containsKey(fieldName)) {
            return childPlan.nullValues(fieldMapping.get(fieldName));
        }
        if (fieldMapping.containsValue(fieldName)) {
            return 0;
        }

        return childPlan.nullValues(fieldName);
    }

    /// The output schema after renaming is essentially the same
    /// as the schema from the subplan, but fields have new names.
    ///
    /// @return The schema with renaming applied.
    @Override
    public Schema outputSchema() {
        return outputSchema;
    }

    @Override
    public void explainPlan(List<ExplainData> previousExplanations, int ident) {
        previousExplanations.add(new ExplainData(
                ident,
                this.getClass().getSimpleName(),
                blocksAccessed(),
                recordsOutput(),
                ""
        ));

        childPlan.explainPlan(previousExplanations, ident + 1);
    }
}