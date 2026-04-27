package com.luka.lbdb.planning.plan.planTypes.readOnly;

import com.luka.lbdb.parsing.statement.select.ProjectionFieldInfo;
import com.luka.lbdb.planning.plan.ExplainData;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanTypes.readOnly.ExtendProjectScan;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;

import java.util.*;

/// Plan for the "generalized projection" relational algebra operator.
/// Read-only operations only.
public class ExtendProjectPlan implements Plan<Scan> {
    private final Plan<Scan> childPlan;
    private final Map<String, Expression> fieldInfos;
    private final Schema inputSchema;
    private final Schema outputSchema = new Schema();

    /// Requires the plan whose columns will be extended / removed and a list of fields
    /// that should be projected. That list can contain simple and complex expressions,
    /// and they can be renamed or not.
    public ExtendProjectPlan(Plan<Scan> childPlan, List<ProjectionFieldInfo> projectionFieldInfoList) {
        this.childPlan = childPlan;
        inputSchema = childPlan.outputSchema();
        fieldInfos = new HashMap<>();

        for (ProjectionFieldInfo projectionFieldInfo : projectionFieldInfoList) {
            DatabaseType type = projectionFieldInfo.expression().type(childPlan.outputSchema());
            int runtimeLength = projectionFieldInfo.expression().length(childPlan.outputSchema());
            boolean isNullable = projectionFieldInfo.expression().isNullable(childPlan.outputSchema());

            outputSchema.addField(projectionFieldInfo.name(), type, runtimeLength, isNullable);
            fieldInfos.put(projectionFieldInfo.name(), projectionFieldInfo.expression());
        }
    }

    @Override
    public Scan open() {
        return new ExtendProjectScan(childPlan.open(), fieldInfos);
    }

    /// Projection does not add change the number of block accesses.
    ///
    /// @return The blocks accessed of the subplan.
    @Override
    public int blocksAccessed() {
        return childPlan.blocksAccessed();
    }

    /// Projection does not add any new record.
    ///
    /// @return The records output of the subplan.
    @Override
    public int recordsOutput() {
        return childPlan.recordsOutput();
    }

    /// If the expression isn't in the projection, it has 0 distinct values.
    ///
    /// If the expression has no fields, it has 1 distinct value because it's
    /// constant.
    ///
    /// If the expression has a single field, return that field's distinct values
    /// since most transformations on a single field will not change the distribution.
    ///
    /// If there is more than one field, the expression is probably unique for
    /// every row.
    ///
    /// @return The distinct number of values for a field, only if it's in the
    /// projection list.
    @Override
    public int distinctValues(String fieldName) {
        Expression expr = fieldInfos.get(fieldName);

        if (expr == null) return 0;

        Set<String> referencedFields = expr.getFields();

        if (referencedFields.isEmpty()) return 1;

        if (referencedFields.size() == 1) {
            String childField = referencedFields.iterator().next();
            return childPlan.distinctValues(childField);
        }

        return childPlan.recordsOutput();
    }

    /// If the expression isn't in the projection, it has 0 NULL values.
    ///
    /// If the expression isn't nullable, it has 0 NULL values.
    ///
    /// If the expression is equal to the NULL constant, it has a null value
    /// for every row, and if its equal to some other constant, it has 0 NULL
    /// values.
    ///
    /// If the expression has a single field, return that field's null value count
    /// since most transformations on a single field will not change the distribution.
    ///
    /// If there is more than one field, the expression probably has a NULL value
    /// for at least the maximum null value count of all of its fields.
    ///
    /// @return The null value count for a field, only if it's in the projection list.
    @Override
    public int nullValues(String fieldName) {
        Expression expr = fieldInfos.get(fieldName);

        if (expr == null) return 0;
        if (!expr.isNullable(inputSchema)) return 0;

        Set<String> referencedFields = expr.getFields();

        if (referencedFields.isEmpty()) {
            try {
                return (expr.evaluate(null).isNull()) ? childPlan.recordsOutput() : 0;
            } catch (RuntimeExecutionException e) {
                return 0;
            }
        }

        if (referencedFields.size() == 1) {
            String childField = referencedFields.iterator().next();
            return childPlan.nullValues(childField);
        }

        int maxNulls = 0;
        for (String f : referencedFields) {
            maxNulls = Math.max(maxNulls, childPlan.nullValues(f));
        }
        return maxNulls;
    }

    /// The output schema after this plan consists only of the passed projection
    /// fields, since only they should be retuned in a query result set.
    ///
    /// @return The schema with only projected fields.
    @Override
    public Schema outputSchema() {
        return outputSchema;
    }

    @Override
    public void explainPlan(List<ExplainData> previousExplanations, int ident) {
        previousExplanations.add(new ExplainData(
                ident, this.getClass().getSimpleName(),
                blocksAccessed(),
                recordsOutput(),
                ""
        ));

        childPlan.explainPlan(previousExplanations, ident + 1);
    }
}
