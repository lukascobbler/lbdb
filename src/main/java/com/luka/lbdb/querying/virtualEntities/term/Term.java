package com.luka.lbdb.querying.virtualEntities.term;

import com.luka.lbdb.planning.planner.PartialEvaluator;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.records.schema.Schema;

import java.util.Objects;
import java.util.Optional;

/// The term class represents the logic for comparison operators
/// between two expressions. It also has logic for how much will
/// the result of the given comparison affect the query.
public class Term {
    private Expression lhs, rhs;
    private final TermOperator termOperator;

    /// An expression comparison is done between two expressions and the
    /// operator that is between them.
    public Term(Expression lhs, TermOperator termOperator, Expression rhs) {
        this.lhs = lhs;
        this.termOperator = termOperator;
        this.rhs = rhs;
    }

    /// Both expressions are evaluated to their constant form
    /// and the term operator is applied between them via the `Comparable`
    /// Java interface. If the `IS` operator is used, comparing NULL
    /// values is allowed, but for any other operator, comparing
    /// between NULLs returns false.
    ///
    /// @return Whether two expressions satisfy the operator defined between
    /// them for a given scan.
    public boolean isSatisfied(Scan scan) {
        Constant rhsValue = rhs.evaluate(scan);
        Constant lhsValue = lhs.evaluate(scan);

        if (termOperator == TermOperator.IS) {
            return lhsValue.equals(rhsValue);
        }

        if (termOperator == TermOperator.IS_NOT) {
            return !lhsValue.equals(rhsValue);
        }

        if (lhsValue.isNull() || rhsValue.isNull()) {
            return false;
        }

        return switch (termOperator) {
            case EQUALS -> lhsValue.equals(rhsValue);
            case NOT_EQUALS -> !lhsValue.equals(rhsValue);
            case GREATER_THAN -> lhsValue.compareTo(rhsValue) > 0;
            case LESS_THAN -> lhsValue.compareTo(rhsValue) < 0;
            case GREATER_OR_EQUAL -> lhsValue.compareTo(rhsValue) >= 0;
            case LESS_OR_EQUAL -> lhsValue.compareTo(rhsValue) <= 0;
            default -> throw new IllegalStateException("unreachable code");
        };
    }

    /// @return True if both expressions apply to the given schema.
    public boolean appliesTo(Schema schema) {
        return lhs.appliesTo(schema) && rhs.appliesTo(schema);
    }

    /// Checks for "Field = Constant" or "Constant = Field" cases
    /// and if that is true, returns the constant.
    ///
    /// @return The constant that some field equates to. `Optional.empty()`
    /// in any other case.
    public Optional<Constant> equatesWithConstant(String fieldName) {
        if (termOperator != TermOperator.EQUALS) return Optional.empty();

        return switch (new Pair(lhs, rhs)) {
            case Pair(FieldNameExpression exp, ConstantExpression(Constant c))
                    when exp.qualifiedName().equals(fieldName) -> Optional.of(c);
            case Pair(ConstantExpression(Constant c), FieldNameExpression exp)
                    when exp.qualifiedName().equals(fieldName) -> Optional.of(c);
            default -> Optional.empty();
        };
    }

    /// Checks for "Field1 = Field2" or "Field2 = Field1" cases
    /// and if that is true, returns the field that the requested
    /// field equals to.
    ///
    /// @return The field that the requested field equals to. `Optional.empty()`
    /// in any other case.
    public Optional<String> equatesWithFieldName(String fieldName) {
        if (termOperator != TermOperator.EQUALS) return Optional.empty();

        return switch (new Pair(lhs, rhs)) {
            case Pair(FieldNameExpression exp1, FieldNameExpression exp2)
                    when exp1.qualifiedName().equals(fieldName) -> Optional.of(exp2.qualifiedName());
            case Pair(FieldNameExpression exp1, FieldNameExpression exp2)
                    when exp2.qualifiedName().equals(fieldName) -> Optional.of(exp1.qualifiedName());
            default -> Optional.empty();
        };
    }

    /// Folds the expressions contained in the term object.
    public void foldExpressions() {
        lhs = PartialEvaluator.evaluate(lhs);
        rhs = PartialEvaluator.evaluate(rhs);
    }

    public TermOperator getTermOperator() {
        return termOperator;
    }

    public Expression getLhs() {
        return lhs;
    }

    public Expression getRhs() {
        return rhs;
    }

    @Override
    public String toString() {
        return lhs.toString() + " " + termOperator.toString() + " " + rhs.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Term term = (Term) o;
        return Objects.equals(lhs, term.lhs) && Objects.equals(rhs, term.rhs) && termOperator == term.termOperator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, termOperator, rhs);
    }

    /// Helper record for easier comparisons.
    private record Pair(Expression left, Expression right) { }
}
