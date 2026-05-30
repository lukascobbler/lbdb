package com.luka.lbdb.planning.planner.plannerDefinitions;

import com.luka.lbdb.metadataManagement.MetadataManager;
import com.luka.lbdb.metadataManagement.exceptions.TableNotFoundException;
import com.luka.lbdb.parsing.statement.SelectStatement;
import com.luka.lbdb.parsing.statement.select.ProjectionFieldInfo;
import com.luka.lbdb.parsing.statement.select.SingleSelection;
import com.luka.lbdb.parsing.statement.select.TableInfo;
import com.luka.lbdb.planning.exceptions.*;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.planning.planner.PartialEvaluator;
import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.Evaluatable;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.querying.virtualEntities.expression.FieldNameExpression;
import com.luka.lbdb.querying.virtualEntities.expression.WildcardExpression;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.transactionManagement.Transaction;

import java.util.*;
import java.util.stream.Collectors;

/// Abstraction over all implementations of query planners which performs
/// all semantic checks and prepares the planner for query execution. Any
/// implementation of query planning only needs to extend this class and
/// define the execution part.
public abstract class QueryPlanner {
    protected final MetadataManager metadataManager;

    /// A query planner needs the system's metadata to validate queries against.
    public QueryPlanner(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
    }

    // Public API, representing the operations that the query planner is able to execute checked

    /// Validates every aspect of a query statement, expands wildcard operators, gives fields
    /// fully qualified names and folds constant expressions.
    /// Checks for:
    /// - tables existing
    /// - expands the wildcards to their equivalent fields
    /// - checks for wildcards usage in expressions
    /// - checks that each actual table field name appears exactly
    /// once across all joined tables and the predicate
    /// - checks that each virtual field name appears exactly
    /// once across all joined tables and the predicate
    /// - checks types of comparing expressions in predicates
    /// - checks for unionized selects having the same number and same typed parameters
    ///
    /// @return A valid query plan validated against the system's metadata.
    /// @throws PlanValidationException on various failed checks.
    public Plan<Scan> createValidatedPlan(SelectStatement selectStatement, Transaction transaction)
        throws PlanValidationException {

        SelectStatement foldedExpressionsStatement = foldAllExpressions(selectStatement);
        SelectStatement checkedStatement = checkStatement(foldedExpressionsStatement, transaction);

        return createPlan(checkedStatement, transaction);
    }

    /// Executes a query plan, returning the list of tuples in the final query.
    ///
    /// @return The list of tuples for a given query plan.
    public List<List<Constant>> executePlan(Plan<Scan> queryPlan)
            throws PlanValidationException {
        List<List<Constant>> tuples = new ArrayList<>();
        List<String> fields = queryPlan.outputSchema().getFields();

        try (Scan scan = queryPlan.open()) {
            scan.beforeFirst();

            while (scan.next()) {
                tuples.add(fields.stream().map(scan::getValue).toList());
            }
        }

        return tuples;
    }

    // Internal API, that runs actual plan creations

    /// Internal API that extenders of this class must implement. Does the actual
    /// plan creation and cost estimation.
    ///
    /// @return Cost-aware plan for the given query.
    protected abstract Plan<Scan> createPlan(SelectStatement selectStatement, Transaction transaction);

    // Private API, for checking statements

    /// @return The expanded and checked select statement.
    /// @throws PlanValidationException on various failed query checks.
    private SelectStatement checkStatement(SelectStatement selectStatement, Transaction transaction) throws PlanValidationException {
        List<SingleSelection> expandedSingleSelections = new ArrayList<>();
        List<DatabaseType> firstTableFieldTypes = null;

        for (SingleSelection singleSelection : selectStatement.unionizedSelections()) {
            ValidationContext ctx = new ValidationContext();
            buildSchemaAndAliases(singleSelection, ctx, transaction);

            List<ProjectionFieldInfo> expandedProjectionFields = expandProjections(singleSelection, ctx);
            Predicate qualifiedPredicate = validateAndQualifyPredicate(singleSelection.predicate(), ctx);

            Set<String> uniqueProjectionNames = expandedProjectionFields.stream().map(ProjectionFieldInfo::name).collect(Collectors.toSet());
            if (uniqueProjectionNames.size() != expandedProjectionFields.size() &&
                    selectStatement.unionizedSelections().size() > 1) {
                // to allow this, a whole rework of schemas must be done, where they need to
                // remember their field position
                throw new PlanValidationException("Same named fields mustn't exist when using unions.");
            }

            List<DatabaseType> currentTypes = getProjectionTypes(expandedProjectionFields, ctx.unifiedSchema);
            if (firstTableFieldTypes == null) {
                firstTableFieldTypes = currentTypes;
            } else {
                if (!firstTableFieldTypes.equals(currentTypes)) throw new PlanValidationException("UNION columns do not match.");
            }

            if (singleSelection.tables().isEmpty()) {
                if (expandedProjectionFields.stream().anyMatch(i -> !i.evaluatable().isConstant())) {
                    throw new PlanValidationException("Constant selects' fields must all be constant");
                }

                if (qualifiedPredicate.getTerms().stream().anyMatch(t -> !t.getLhs().isConstant() || !t.getRhs().isConstant())) {
                    throw new PlanValidationException("Constant selects' predicate must not contain non-constant values");
                }
            }

            expandedSingleSelections.add(new SingleSelection(
                    List.copyOf(expandedProjectionFields),
                    singleSelection.tables(),
                    qualifiedPredicate
            ));
        }

        return new SelectStatement(expandedSingleSelections);
    }

    /// Builds the unified schema and fully qualified names for all fields
    /// that aren't in multiple tables. Checks for views' schemas, tables existing
    /// and duplicated table aliases.
    private void buildSchemaAndAliases(SingleSelection selection, ValidationContext ctx, Transaction transaction) {
        Set<String> seenUnqualified = new HashSet<>();
        Set<String> uniqueQualifiers = new HashSet<>();

        for (TableInfo tableInfo : selection.tables()) {
            String qualifier = tableInfo.rangeVariableName().orElse(tableInfo.tableName());

            if (!uniqueQualifiers.add(qualifier)) {
                throw new PlanValidationException("Duplicate table alias name: " + qualifier);
            }

            try {
                Schema tableSchema = metadataManager.getLayout(tableInfo.tableName(), transaction).getSchema();
                ctx.tableSchemas.put(qualifier, tableSchema);

                for (String field : tableSchema.getFields()) {
                    ctx.unifiedSchema.addField(qualifier + "." + field,
                            tableSchema.type(field), tableSchema.runtimeLength(field), tableSchema.isNullable(field));

                    if (!seenUnqualified.add(field)) {
                        ctx.ambiguousUnqualified.add(field);
                    }
                }
            } catch (TableNotFoundException e) {
                throw new PlanValidationException("Table not found: " + tableInfo.tableName());
            }
        }

        for (String field : seenUnqualified) {
            if (!ctx.ambiguousUnqualified.contains(field)) {
                ctx.tableSchemas.entrySet().stream()
                        .filter(entry -> entry.getValue().hasField(field))
                        .findFirst()
                        .ifPresent(entry -> ctx.implicitAliases.put(field, entry.getKey()));
            }
        }
    }

    /// Expands all wildcard operators to their equivalent fields, or if it's
    /// a nonqualified wildcard, expands it to the unified schema's fields.
    /// Checks for inappropriate usages of wildcards. Checks if any of the
    /// fields used in projection expressions is ambiguous or non-existent.
    /// Gives a fully qualified name to every fieldName.
    ///
    /// @return A list of projection fields that don't include any wildcards and
    /// are sanitized of any incorrect fields.
    private List<ProjectionFieldInfo> expandProjections(SingleSelection selection, ValidationContext ctx) {
        List<ProjectionFieldInfo> expanded = new ArrayList<>();

        for (ProjectionFieldInfo projField : selection.projectionFields()) {
            switch (projField.evaluatable()) {
                case WildcardExpression(Optional<String> rangeVariableName) -> {
                    if (rangeVariableName.isPresent()) {
                        String qualifier = rangeVariableName.get();
                        if (!ctx.tableSchemas.containsKey(qualifier)) throw new PlanValidationException("Unknown table alias: " + qualifier);

                        for (String fieldName : ctx.tableSchemas.get(qualifier).getFields()) {
                            expanded.add(new ProjectionFieldInfo(
                                    fieldName,
                                    new FieldNameExpression(fieldName, rangeVariableName)
                            ));
                        }
                    } else {
                        for (var tableSchemaEntry : ctx.tableSchemas.entrySet()) {
                            for (String fieldName : tableSchemaEntry.getValue().getFields()) {
                                String correctFieldName = ctx.ambiguousUnqualified.contains(fieldName) ?
                                        tableSchemaEntry.getKey() + "." + fieldName : fieldName;
                                expanded.add(new ProjectionFieldInfo(
                                        correctFieldName,
                                        new FieldNameExpression(fieldName, tableSchemaEntry.getKey())
                                ));
                            }
                        }
                    }
                }
                case Evaluatable e when e.hasWildCard() -> throw new PlanValidationException("Wildcard operator used in an expression.");
                case Evaluatable e -> {
                    Evaluatable qualifiedExpr = e.qualify(ctx.implicitAliases);
                    expanded.add(new ProjectionFieldInfo(projField.name(), qualifiedExpr));

                    for (String f : qualifiedExpr.getFields()) {
                        ctx.validateFieldExists(f, "SELECT clause");
                    }
                }
            }
        }

        return expanded;
    }

    /// Checks if any of the fields used in the predicate is ambiguous or non-existent.
    /// Gives a fully qualified name to every fieldName. Checks for types
    ///
    /// @return A sanitized and fieldName qualified predicate.
    private Predicate validateAndQualifyPredicate(Predicate predicate, ValidationContext ctx) {
        Predicate qualifiedPredicate = new Predicate();

        for (Term term : predicate.getTerms()) {
            Expression qualLhs = term.getLhs().qualify(ctx.implicitAliases);
            Expression qualRhs = term.getRhs().qualify(ctx.implicitAliases);

            Set<String> termFields = new HashSet<>(qualLhs.getFields());
            termFields.addAll(qualRhs.getFields());

            for (String f : termFields) {
                ctx.validateFieldExists(f, "WHERE clause");
            }

            if (qualLhs.type(ctx.unifiedSchema) != qualRhs.type(ctx.unifiedSchema) &&
                    (qualLhs.type(ctx.unifiedSchema) != DatabaseType.NULL &&
                            qualRhs.type(ctx.unifiedSchema) != DatabaseType.NULL)) {
                throw new PlanValidationException("Different types are compared in the 'WHERE' predicate.");
            }

            qualifiedPredicate.getTerms().add(new Term(qualLhs, term.getTermOperator(), qualRhs));
        }

        return qualifiedPredicate;
    }

    /// @return The type checked list of all expressions' types.
    private List<DatabaseType> getProjectionTypes(List<ProjectionFieldInfo> projections, Schema unifiedSchema) {
        try {
            return projections.stream()
                    .map(p -> p.evaluatable().type(unifiedSchema))
                    .toList();
        } catch (RuntimeExecutionException e) {
            throw new PlanValidationException(e.getMessage());
        }
    }

    /// Shared class for common data needed between different validators of queries.
    private static class ValidationContext {
        final Schema unifiedSchema = new Schema();
        final Map<String, Schema> tableSchemas = new LinkedHashMap<>();
        final Set<String> ambiguousUnqualified = new HashSet<>();
        final Map<String, String> implicitAliases = new HashMap<>();

        /// @throws PlanValidationException if a fieldName is ambiguous or missing.
        void validateFieldExists(String fieldName, String contextMessage) {
            if (!unifiedSchema.hasField(fieldName)) {
                if (ambiguousUnqualified.contains(fieldName)) {
                    throw new PlanValidationException(String.format(
                            "Ambiguous field: '%s' (%s)",
                            fieldName, contextMessage
                    ));
                }
                throw new PlanValidationException(String.format(
                        "Field '%s' does not exist (%s)",
                        fieldName, contextMessage
                ));
            }
        }
    }

    /// Folds all projection expressions and the predicate, thus saving CPU cycles on
    /// the system virtual machine.
    private SelectStatement foldAllExpressions(SelectStatement selectStatement) {
        List<SingleSelection> expandedSingleSelections = new ArrayList<>();

        for (SingleSelection singleSelection : selectStatement.unionizedSelections()) {
            List<ProjectionFieldInfo> foldedProjectionFields = new ArrayList<>();
            Predicate foldedPredicate;

            try {
                foldedPredicate = (Predicate) PartialEvaluator.evaluate(singleSelection.predicate());
                for (ProjectionFieldInfo projectionFieldInfo : singleSelection.projectionFields()) {
                    foldedProjectionFields.add(
                            new ProjectionFieldInfo(
                                    projectionFieldInfo.name(),
                                    PartialEvaluator.evaluate(projectionFieldInfo.evaluatable())
                            ));
                }
            } catch (RuntimeExecutionException e) {
                throw new PlanValidationException(e.getMessage());
            }

            expandedSingleSelections.add(new SingleSelection(
                    List.copyOf(foldedProjectionFields),
                    singleSelection.tables(),
                    foldedPredicate
            ));
        }

        return new SelectStatement(expandedSingleSelections);
    }
}
