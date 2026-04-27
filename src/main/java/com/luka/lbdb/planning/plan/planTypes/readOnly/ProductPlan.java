package com.luka.lbdb.planning.plan.planTypes.readOnly;

import com.luka.lbdb.planning.plan.ExplainData;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanTypes.readOnly.ProductScan;
import com.luka.lbdb.records.schema.Schema;

import java.util.List;

/// Plan for the "cross product" relational algebra operator.
/// Read-only operations only.
public class ProductPlan implements Plan<Scan> {
    private final Plan<Scan> childPlan1, childPlan2;
    private final Schema outputSchema = new Schema();

    /// Requires two subplans that will create the "combined record" which is a
    /// record that has all fields of both subplans. Assumes that no two fields
    /// will exist in both scans.
    public ProductPlan(Plan<Scan> childPlan1, Plan<Scan> childPlan2) {
        this.childPlan1 = childPlan1;
        this.childPlan2 = childPlan2;
        outputSchema.addAll(childPlan1.outputSchema());
        outputSchema.addAll(childPlan2.outputSchema());
    }

    @Override
    public Scan open() {
        return new ProductScan(childPlan1.open(), childPlan2.open());
    }

    /// Estimates the total number of blocks accessed needed to give all "combined records".
    /// Be aware that this formula is not symmetric, meaning swapping the left and right
    /// subplans will give different results. Since it is needed to go over every block
    /// of the right subplan for one **record** (not block) of the left subplan, it is
    /// better to place the subplan that has more records in one block to be the left subplan.
    /// That way, more records will be processed in per block access. This formula will be
    /// symmetric if both subplans have the same number of records per block.
    ///
    /// @return  The total number of blocks accessed for the product operation where the
    /// first subplan is the left subplan, and the second subplan is the right
    /// subplan.
    @Override
    public int blocksAccessed() {
        return childPlan1.blocksAccessed() + (childPlan1.recordsOutput() * childPlan2.blocksAccessed());
    }

    /// Each record in the first subplan will be repeated for every
    /// record in the second subplan, so the total number of records
    /// is the multiplication of those two values.
    ///
    /// @return The number of "combined records".
    @Override
    public int recordsOutput() {
        return childPlan1.recordsOutput() * childPlan2.recordsOutput();
    }

    /// Since a product plan does not add new distinct values, and the
    /// values of the first subplan that repeat for every row of the
    /// second subplan aren't unique, it just delegates the calculation
    /// of distinct values to the subplan containing the field.
    ///
    /// @return The number of distinct values for a field.
    @Override
    public int distinctValues(String fieldName) {
        if (childPlan1.outputSchema().hasField(fieldName)) {
            return childPlan1.distinctValues(fieldName);
        } else {
            return childPlan2.distinctValues(fieldName);
        }
    }

    /// A product plan does not add new distinct values, but counting
    /// null values doesn't count distinctly, instead it counts all null
    /// values. One record in the left subplan that has a null value will
    /// generate right subplan's record output of null values in the final
    /// result. The logic holds true if we swap the left and right subplans
    /// as well.
    ///
    /// @return The total number of null values for a field, "combined record"
    /// aware.
    @Override
    public int nullValues(String fieldName) {
        if (childPlan1.outputSchema().hasField(fieldName)) {
            return childPlan1.nullValues(fieldName) * childPlan2.recordsOutput();
        } else {
            return childPlan2.nullValues(fieldName) * childPlan1.recordsOutput();
        }
    }

    /// The output schema is the schema containing fields of both
    /// subplans.
    ///
    /// @return The schema describing the "combined record".
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

        childPlan1.explainPlan(previousExplanations, ident + 1);
        childPlan2.explainPlan(previousExplanations, ident + 1);
    }
}
