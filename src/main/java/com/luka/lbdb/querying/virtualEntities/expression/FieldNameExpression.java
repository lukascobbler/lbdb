package com.luka.lbdb.querying.virtualEntities.expression;

import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/// A field name expression represents an expression wrapper over some field
/// in a table.
public record FieldNameExpression(String fieldName, Optional<String> rangeVariable) implements Expression {
    /// A constructor that initializes the range variable name to be nothing
    /// by default.
    public FieldNameExpression(String fieldName) {
        this(fieldName, Optional.empty());
    }

    /// A constructor that initializes the range variable to a non-empty optional.
    public FieldNameExpression(String fieldName, String rangeVariableName) {
        this(fieldName, Optional.of(rangeVariableName));
    }

    /// @return The full qualified name with the optional range variable.
    public String qualifiedName() {
        return rangeVariable
                .map(rangeVar -> rangeVar + "." + fieldName)
                .orElse(fieldName);
    }

    /// @return The constant value for the field name in the current scan position.
    @Override
    public Constant evaluate(Scan scan) {
        return scan.getValue(qualifiedName());
    }

    @Override
    public @NotNull String toString() {
        return qualifiedName();
    }
}
