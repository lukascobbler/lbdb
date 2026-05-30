package com.luka.lbdb.planning.planner;

import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.virtualEntities.Evaluatable;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.querying.virtualEntities.term.Term;

import java.util.ArrayList;
import java.util.List;

/// A set of algorithms that performs obvious calculations on evaluatables
/// thus reducing the need for them to be calculated on the
/// database virtual machine.
public class PartialEvaluator {
    /// Reduces the number of steps for a given evaluatable.
    /// For example: `3 + 5` is a trivial operation that can
    /// be calculated before the expression goes further down the
    /// pipeline where it's more expensive to calculate it.
    ///
    /// @return The partially evaluated evaluatable.
    /// @throws RuntimeExecutionException if division by zero is performed
    /// or if overflowing occurs.
    public static Evaluatable evaluate(Evaluatable evaluatable) {
        return switch (evaluatable) {
            case BinaryArithmeticExpression(Expression left, ArithmeticOperator op, Expression right) ->
                    foldBinary((Expression) evaluate(left), op, (Expression) evaluate(right));
            case UnaryArithmeticExpression(ArithmeticOperator op, Expression operand) ->
                    foldUnary(op, (Expression) evaluate(operand));
            case Predicate predicate -> foldPredicate(predicate);
            default -> evaluatable;
        };
    }

    /// Simple predicate folding, with short-circuiting if an obvious false term
    /// is found.
    ///
    /// @return The possibly short-circuited evaluatable.
    private static Predicate foldPredicate(Predicate predicate) {
        List<Term> foldedTerms = new ArrayList<>();

        for (Term term : predicate.getTerms()) {
            Expression lhs = (Expression) evaluate(term.getLhs());
            Expression rhs = (Expression) evaluate(term.getRhs());

            if (lhs instanceof ConstantExpression && rhs instanceof ConstantExpression) {
                Term foldedTerm = new Term(lhs, term.getTermOperator(), rhs);
                
                if (!foldedTerm.isSatisfied(null)) {
                    return new Predicate(foldedTerm);
                }
            } else {
                foldedTerms.add(new Term(lhs, term.getTermOperator(), rhs));
            }
        }

        return new Predicate(foldedTerms);
    }

    /// Performs checks if both expressions are constant, does the
    /// arithmetic operation and returns the constant expression.
    /// Also, it checks for simple operations like multiplying by 1,
    /// multiplying by zero, subtracting value from itself,
    /// adding zero and in that case the calculation
    /// isn't even done, the result is just returned.
    ///
    /// @return The folded binary arithmetic expression.
    /// @throws RuntimeExecutionException if division by zero is performed
    /// or if overflowing occurs.
    private static Expression foldBinary(Expression left, ArithmeticOperator op, Expression right) {
        if (right instanceof ConstantExpression(Constant rVal) && rVal.isNull()) {
            return new ConstantExpression(NullConstant.INSTANCE);
        }

        if (left instanceof ConstantExpression(Constant lVal) && lVal.isNull()) {
            return new ConstantExpression(NullConstant.INSTANCE);
        }

        if (left instanceof ConstantExpression(Constant lVal) &&
                right instanceof ConstantExpression(Constant rVal)) {

            try {
                int l = lVal.asInt();
                int r = rVal.asInt();

                int result = switch (op) {
                    case ADD -> Math.addExact(l, r);
                    case SUB -> Math.subtractExact(l, r);
                    case MUL -> Math.multiplyExact(l, r);
                    case DIV -> {
                        if (l == Integer.MIN_VALUE && r == -1) {
                            throw new ArithmeticException("integer overflow");
                        }
                        yield l / r;
                    }
                    case POWER -> {
                        double p = Math.pow(l, r);
                        if (p > Integer.MAX_VALUE || p < Integer.MIN_VALUE || Double.isInfinite(p)) {
                            throw new ArithmeticException("integer overflow");
                        }
                        yield (int) p;
                    }
                };

                return new ConstantExpression(new IntConstant(result));
            } catch (ArithmeticException e) {
                throw new RuntimeExecutionException(e.getMessage());
            }
        }

        if (op == ArithmeticOperator.ADD && right instanceof
                UnaryArithmeticExpression(var rOp, var rInner) && rOp == ArithmeticOperator.SUB) {
            return foldBinary(left, ArithmeticOperator.SUB, rInner);
        }

        if (op == ArithmeticOperator.SUB && right instanceof
                UnaryArithmeticExpression(var rOp, var rInner) && rOp == ArithmeticOperator.SUB) {
            return foldBinary(left, ArithmeticOperator.ADD, rInner);
        }

        if (op == ArithmeticOperator.MUL) {
            if (isExactConstant(left, 1)) return right;
            if (isExactConstant(right, 1)) return left;
            if (isExactConstant(left, 0) || isExactConstant(right, 0))
                return new ConstantExpression(new IntConstant(0));
            if (isExactConstant(right, -1)) return foldUnary(ArithmeticOperator.SUB, left);
        }

        if (op == ArithmeticOperator.ADD) {
            if (left instanceof UnaryArithmeticExpression(var lOp, var lInner) && lOp == ArithmeticOperator.SUB) {
                if (lInner.equals(right)) {
                    return new ConstantExpression(new IntConstant(0));
                }
            }

            if (isExactConstant(left, 0)) return right;
            if (isExactConstant(right, 0)) return left;
        }

        if (op == ArithmeticOperator.SUB) {
            if (isExactConstant(right, 0)) return left;
            if (left.equals(right)) {
                return new ConstantExpression(new IntConstant(0));
            }
        }

        return new BinaryArithmeticExpression(left, op, right);
    }

    /// Performs the obvious unary arithmetic operations
    /// like double negation, removing the + unary operation
    /// and constant folding.
    ///
    /// @return The folded unary arithmetic expression.
    private static Expression foldUnary(ArithmeticOperator op, Expression operand) {
        if (operand instanceof ConstantExpression(Constant c)) {
            return new ConstantExpression(new IntConstant(
                    op == ArithmeticOperator.SUB ? -c.asInt() : c.asInt()
            ));
        }

        if (op == ArithmeticOperator.ADD) {
            return operand;
        }

        if (op == ArithmeticOperator.SUB && operand instanceof UnaryArithmeticExpression(var innerOp, Expression innerOperand)) {
            if (innerOp == ArithmeticOperator.SUB) {
                return innerOperand;
            }
        }

        return new UnaryArithmeticExpression(op, operand);
    }

    /// @return Whether the expression is exactly `val`.
    private static boolean isExactConstant(Expression e, int val) {
        return e instanceof ConstantExpression(Constant c) && c.asInt() == val;
    }
}