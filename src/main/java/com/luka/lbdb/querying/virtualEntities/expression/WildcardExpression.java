package com.luka.lbdb.querying.virtualEntities.expression;

import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record WildcardExpression(Optional<String> rangeVariable) implements Expression {
    /// Initialization with no range variable.
    public WildcardExpression() {
        this(Optional.empty());
    }

    /// Initialization with a range variable.
    public WildcardExpression(String rangeVariableName) {
        this(Optional.of(rangeVariableName));
    }

    /// A wildcard expression can't be evaluated.
    ///
    /// @throws RuntimeExecutionException on every scan.
    @Override
    public Constant evaluate(Scan scan) {
        throw new RuntimeExecutionException("Wildcard operators can't be evaluated");
    }

    @Override
    public @NotNull String toString() {
        return rangeVariable.map(s -> s + ".").orElse("") + "*";
    }
}
