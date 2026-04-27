package com.luka.lbdb.querying.virtualEntities.expression;

import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/// Expressions that the database virtual machine evaluates on concrete table
/// rows. This interface is an abstraction over all types of expressions that
/// the database can evaluate, and it is needed for constructing expression ASTs
/// whose exact structure isn't known at compile time.
public sealed interface Expression permits
        BinaryArithmeticExpression, ConstantExpression, FieldNameExpression,
        UnaryArithmeticExpression, WildcardExpression {
    /// @return The constant evaluation of an expression over some scan.
    Constant evaluate(Scan scan);

    /// @return Whether this expression does apply to this schema.
    default boolean appliesTo(Schema schema) {
        return switch (this) {
            case ConstantExpression c -> true;
            case WildcardExpression w -> true;
            case FieldNameExpression f -> schema.hasField(f.qualifiedName());
            case BinaryArithmeticExpression b -> b.left().appliesTo(schema) && b.right().appliesTo(schema);
            case UnaryArithmeticExpression u -> u.operand().appliesTo(schema);
        };
    }

    /// @return True if the expression and all its sub-expressions evaluate to a constant value,
    /// independent of any table fields.
    default boolean isConstant() {
        return switch (this) {
            case ConstantExpression c -> true;
            case FieldNameExpression f -> false;
            case WildcardExpression w -> false;
            case BinaryArithmeticExpression b -> b.left().isConstant() && b.right().isConstant();
            case UnaryArithmeticExpression u -> u.operand().isConstant();
        };
    }

    /// @return The type of this expression AST for a given schema.
    default DatabaseType type(Schema schema) {
        return switch (this) {
            case ConstantExpression c -> c.constant().type();
            case FieldNameExpression f -> schema.type(f.qualifiedName());
            case BinaryArithmeticExpression b -> {
                DatabaseType leftT = b.left().type(schema);
                DatabaseType rightT = b.right().type(schema);
                if (leftT == DatabaseType.INT && rightT == DatabaseType.INT) {
                    yield DatabaseType.INT;
                }
                throw new RuntimeExecutionException("Arithmetic requires numeric types");
            }
            case UnaryArithmeticExpression u -> {
                DatabaseType operandT = u.operand().type(schema);
                if (operandT == DatabaseType.INT) {
                    yield DatabaseType.INT;
                }
                throw new RuntimeExecutionException("Arithmetic requires numeric types");
            }
            case WildcardExpression w ->
                    throw new RuntimeExecutionException("Wildcard has no single type");
        };
    }

    /// @return The runtime length needed for the longest operand
    /// in the expression AST for a given schema.
    default int length(Schema schema) {
        return switch (this) {
            case ConstantExpression c -> c.constant().length();
            case FieldNameExpression f -> schema.runtimeLength(f.qualifiedName());
            case BinaryArithmeticExpression b -> Math.max(b.left().length(schema), b.right().length(schema));
            case UnaryArithmeticExpression u -> u.operand().length(schema);
            case WildcardExpression w ->
                    throw new RuntimeExecutionException("Wildcard has no runtimeLength");
        };
    }

    /// @return True if any of the fields in the expression AST is nullable.
    default boolean isNullable(Schema schema) {
        return switch (this) {
            case ConstantExpression c -> c.constant().isNull();
            case FieldNameExpression f -> schema.isNullable(f.qualifiedName());
            case BinaryArithmeticExpression b -> b.left().isNullable(schema) || b.right().isNullable(schema);
            case UnaryArithmeticExpression u -> u.operand().isNullable(schema);
            case WildcardExpression w ->
                    throw new RuntimeExecutionException("Wildcard isn't nullable");
        };
    }

    /// @return All fields (field name expressions) mentioned in the whole expression AST.
    default Set<String> getFields() {
        return switch (this) {
            case FieldNameExpression f -> Set.of(f.toString());
            case UnaryArithmeticExpression u -> u.operand().getFields();
            case BinaryArithmeticExpression b -> {
                Set<String> set = new HashSet<>(b.left().getFields());
                set.addAll(b.right().getFields());
                yield set;
            }
            case ConstantExpression c -> Set.of();
            case WildcardExpression w -> Set.of();
        };
    }

    /// @return Whether any of the expressions in the AST is a wildcard expression.
    default boolean hasWildCard() {
        return switch (this) {
            case WildcardExpression w -> true;
            case UnaryArithmeticExpression u -> u.operand().hasWildCard();
            case BinaryArithmeticExpression b -> b.right().hasWildCard() || b.left().hasWildCard();
            default -> false;
        };
    }

    /// @return A new expression that has every field name fully qualified according
    /// to the aliases map. Does not qualify wildcards as that requires knowing more
    /// about the state of the schema.
    default Expression qualify(Map<String, String> aliases) {
        return switch (this) {
            case FieldNameExpression f -> {
                if (f.rangeVariable().isEmpty() && aliases.containsKey(f.fieldName())) {
                    yield new FieldNameExpression(f.fieldName(), aliases.get(f.fieldName()));
                }
                yield f;
            }
            case BinaryArithmeticExpression b ->
                    new BinaryArithmeticExpression(
                            b.left().qualify(aliases),
                            b.op(),
                            b.right().qualify(aliases)
                    );
            case UnaryArithmeticExpression u ->
                    new UnaryArithmeticExpression(
                            u.op(),
                            u.operand().qualify(aliases)
                    );
            case ConstantExpression c -> c;
            case WildcardExpression w -> w;
        };
    }
}
