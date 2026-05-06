package com.luka.lbdb.planning.planner;

import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.querying.virtualEntities.term.Term;

import java.util.Set;

import static com.luka.lbdb.querying.virtualEntities.term.TermOperator.*;

/// Encapsulates logic for calculating a reduction factor of a term.
public class ReductionFactorCalculator {
    // Magic number constants that represent different selectivity values.
    // Taken from the "Access Path Selection in a Relational Database Management System"
    // paper, describing how the System R optimizes query execution.

    private static final double NO_ROWS_MATCH = Double.MAX_VALUE;
    private static final double ALL_ROWS_MATCH = 1.0;
    private static final double INEQUALITY_MATCH = 3.0;
    private static final double COMPLEX_EXPRESSION_MATCH = 10.0;

    /// A reduction factor of a predicate is the multiplication
    /// of reduction factors of all terms that it holds.
    ///
    /// @return The total reduction factor of all predicates.
    public static <T extends Scan> double calculatePredicateReductionFactor(Predicate p, Plan<T> plan) {
        double totalFactor = 1.0;
        for (Term term : p.getTerms()) {
            totalFactor *= calculateTermReductionFactor(term, plan);
            if (totalFactor > Double.MAX_VALUE) {
                return Double.MAX_VALUE;
            }
        }
        return totalFactor;
    }

    /// Calculating the reduction factor can be broken down into these categories:
    /// - two constants
    /// - a field and a constant
    /// - two fields
    /// - three+ fields
    ///
    /// Each of these categories has some specific values regarding distinct values and
    /// null values. This is an estimation and should be treated like that.
    ///
    /// @return The calculated reduction factor for this term.
    public static <T extends Scan> double calculateTermReductionFactor(Term t, Plan<T> plan) {
        Set<String> leftFields = t.getLhs().getFields();
        Set<String> rightFields = t.getRhs().getFields();

        int leftFieldCount = leftFields.size();
        int rightFieldCount = rightFields.size();

        double totalRowCount = plan.recordsOutput();
        if (totalRowCount == 0) return ALL_ROWS_MATCH;

        // constant comparisons
        if (leftFieldCount == 0 && rightFieldCount == 0) {
            Constant leftValue = t.getLhs().evaluate(null);
            Constant rightValue = t.getRhs().evaluate(null);

            boolean isLeftNull = leftValue instanceof NullConstant;
            boolean isRightNull = rightValue instanceof NullConstant;

            if (t.getTermOperator() == IS) {
                // NULL IS NULL        -> all rows match
                // NULL IS C           -> no rows match
                return (isLeftNull == isRightNull) ? ALL_ROWS_MATCH : NO_ROWS_MATCH;
            } else if (t.getTermOperator() == IS_NOT) {
                // NULL IS NOT NULL    -> no rows match
                // NULL IS NOT C       -> all rows match
                return (isLeftNull != isRightNull) ? ALL_ROWS_MATCH : NO_ROWS_MATCH;
            } else {
                // NULL = C            -> no rows match
                if (isLeftNull || isRightNull) return NO_ROWS_MATCH;

                // C = C               -> all rows match
                // C > C ...           -> no rows match
                return leftValue.compareTo(rightValue) == 0 ? ALL_ROWS_MATCH : NO_ROWS_MATCH;
            }
        }

        // field to constant comparisons
        if (leftFieldCount == 1 && rightFieldCount == 0 || leftFieldCount == 0 && rightFieldCount == 1) {
            String field = leftFieldCount == 1 ? leftFields.iterator().next() : rightFields.iterator().next();
            Expression constExpr = leftFieldCount == 1 ? t.getRhs() : t.getLhs();

            double nullRowCount = plan.nullValues(field);
            double nonNullRowCount = Math.max(0, totalRowCount - nullRowCount);
            double distinctValues = Math.max(1, plan.distinctValues(field));

            boolean isNullConst = constExpr.evaluate(null).isNull();

            if (t.getTermOperator() == IS && isNullConst) {
                // F IS NULL            -> reduction factor based on number of null values
                return safeCalculateReductionFactor(totalRowCount, nullRowCount);
            } else if (t.getTermOperator() == IS_NOT && isNullConst) {
                // F IS NOT NULL        -> reduction factor based on number of non-null values
                return safeCalculateReductionFactor(totalRowCount, nonNullRowCount);
            }

            // F = NULL                 -> no rows match
            // F > NULL ...             -> no rows match
            if (isNullConst) return NO_ROWS_MATCH;

            return switch (t.getTermOperator()) {
                case EQUALS, IS ->
                    // F = C        -> reduction factor based of non-null values and distinct values of the field
                    // F IS C       -> reduction factor based of non-null values and distinct values of the field
                    safeCalculateReductionFactor(totalRowCount, nonNullRowCount / distinctValues);
                case LESS_THAN, GREATER_THAN, LESS_OR_EQUAL, GREATER_OR_EQUAL ->
                    // F > C...     -> reduction factor based on number of non-null values
                    //                 and the System R inequality heuristic
                    safeCalculateReductionFactor(totalRowCount, nonNullRowCount / INEQUALITY_MATCH);
                case NOT_EQUALS, IS_NOT ->
                    // F IS NOT C   -> reduction factor based on number of non-null values
                    // F != C          and the System R complex expression heuristic
                    safeCalculateReductionFactor(totalRowCount, nonNullRowCount / COMPLEX_EXPRESSION_MATCH);
            };
        }

        // field to field comparisons
        if (leftFieldCount == 1 && rightFieldCount == 1) {
            String leftField = leftFields.iterator().next();
            String rightField = rightFields.iterator().next();

            // estimation: the total number of non-null rows (regarding these two fields) is the
            // total number of rows minus the field that has more nulls
            double nonNullRowCount = Math.max(
                    0,
                    totalRowCount - Math.max(plan.nullValues(leftField), plan.nullValues(rightField))
            );

            double distinctValuesL = Math.max(1, plan.distinctValues(leftField));
            double distinctValuesR = Math.max(1, plan.distinctValues(rightField));

            return switch (t.getTermOperator()) {
                case EQUALS, IS ->
                    // F1 = F2      -> reduction factor based on non-null values and the field with more distinct values
                    // F1 IS F2     -> reduction factor based on non-null values and the field with more distinct values
                    safeCalculateReductionFactor(
                            totalRowCount,
                            nonNullRowCount / Math.max(distinctValuesL, distinctValuesR)
                    );
                case LESS_THAN, GREATER_THAN, LESS_OR_EQUAL, GREATER_OR_EQUAL ->
                    // F1 > F2 ...  -> reduction factor based on non-null values
                    //                 and the System R inequality heuristic
                    safeCalculateReductionFactor(totalRowCount, nonNullRowCount / INEQUALITY_MATCH);
                case NOT_EQUALS, IS_NOT ->
                    // F1 != F2     -> reduction factor based on number of non-null values
                    //                 and the System R complex expression heuristic
                    safeCalculateReductionFactor(totalRowCount, nonNullRowCount / COMPLEX_EXPRESSION_MATCH);
            };
        }

        // complex comparisons involving three+ fields
        double maxNulls = 0;
        for (String f : leftFields) maxNulls = Math.max(maxNulls, plan.nullValues(f));
        for (String f : rightFields) maxNulls = Math.max(maxNulls, plan.nullValues(f));

        double nonNullRowCount = Math.max(0, totalRowCount - maxNulls);

        return switch (t.getTermOperator()) {
            case EQUALS, IS ->
                // F1 = F2 + F3 -> reduction factor based on number of non-null values
                //                 and the System R complex expression heuristic
                safeCalculateReductionFactor(totalRowCount, nonNullRowCount / COMPLEX_EXPRESSION_MATCH);
            case LESS_THAN, GREATER_THAN, LESS_OR_EQUAL, GREATER_OR_EQUAL, NOT_EQUALS, IS_NOT ->
                // F1 > F2 + F3 -> reduction factor based on non-null values
                //                 and the System R inequality heuristic
                safeCalculateReductionFactor(totalRowCount, nonNullRowCount / INEQUALITY_MATCH);
        };
    }

    /// Helper method to stay within row calculation bounds which is
    /// between `NO_ROWS_MATCH` and `ALL_ROWS_MATCH`.
    ///
    /// @return The checked division of total rows and output rows.
    /// Should be interpreted as "selectivity" i.e. the
    private static double safeCalculateReductionFactor(double totalRows, double outputRows) {
        if (totalRows <= 0) return ALL_ROWS_MATCH;
        if (outputRows <= 0) return NO_ROWS_MATCH;
        if (outputRows >= totalRows) return ALL_ROWS_MATCH;
        return totalRows / outputRows;
    }
}
