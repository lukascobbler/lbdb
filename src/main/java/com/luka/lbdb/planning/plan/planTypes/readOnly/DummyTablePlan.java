package com.luka.lbdb.planning.plan.planTypes.readOnly;

import com.luka.lbdb.parsing.statement.select.ProjectionFieldInfo;
import com.luka.lbdb.planning.plan.ExplainData;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanTypes.readOnly.DummyTableScan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.records.schema.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A dummy table plan is a plan that doesn't have any physical data, but is
/// still a data source. It is a special type of plan that isn't related to
/// any relational algebra operator. Read-only operations only.
public class DummyTablePlan implements Plan<Scan> {
    private final Schema outputSchema = new Schema();
    private final Map<String, Constant> fields;

    /// A dummy table plan needs the data that will simulate a table.
    public DummyTablePlan(List<ProjectionFieldInfo> projectionFieldInfoList) {
        fields = new HashMap<>();
        for (ProjectionFieldInfo info : projectionFieldInfoList) {
            outputSchema.addField(
                    info.name(),
                    info.evaluatable().type(null),
                    info.evaluatable().length(null),
                    info.evaluatable().isNullable(null)
            );

            fields.put(info.name(), info.evaluatable().evaluate(null));
        }
    }

    @Override
    public Scan open() {
        return new DummyTableScan(fields);
    }

    /// @return 0 because all rows are virtual and there will be no
    /// disk access.
    @Override
    public int blocksAccessed() {
        return 0;
    }

    /// @return 1 because there is only one virtual row per constant
    /// selection.
    @Override
    public int recordsOutput() {
        return 1;
    }

    /// @return 1 for every field because all fields have 1 distinct value
    /// in one row.
    @Override
    public int distinctValues(String fieldName) {
        return 1;
    }

    /// @return 1 if the field is nullable, 0 if the field isn't nullable.
    @Override
    public int nullValues(String fieldName) {
        if (outputSchema.isNullable(fieldName)) {
            return 1;
        } else {
            return 0;
        }
    }

    /// @return The schema matching the virtual row.
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
    }
}
