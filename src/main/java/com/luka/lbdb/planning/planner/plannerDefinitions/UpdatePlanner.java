package com.luka.lbdb.planning.planner.plannerDefinitions;

import com.luka.lbdb.metadataManagement.MetadataManager;
import com.luka.lbdb.metadataManagement.exceptions.*;
import com.luka.lbdb.parsing.statement.*;
import com.luka.lbdb.parsing.statement.insert.AllTuplesValueInfo;
import com.luka.lbdb.parsing.statement.update.NewFieldExpressionAssignment;
import com.luka.lbdb.planning.exceptions.PlanValidationException;
import com.luka.lbdb.planning.planner.PartialEvaluator;
import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.Layout;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.records.exceptions.RecordTooLongException;
import com.luka.lbdb.transactionManagement.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Abstraction over all implementations of update planners which performs
/// all semantic checks and prepares the planner for update execution. Any
/// implementation of update planning only needs to extend this class and
/// define the execution part.
public abstract class UpdatePlanner {
    protected final MetadataManager metadataManager;

    /// An update planner needs the system's metadata to validate queries against.
    public UpdatePlanner(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
    }

    // Public API, representing the operations that the update planner is able to execute checked

    /// Validates every aspect of an update statement and folds constant expressions.
    /// Checks for:
    /// - tables and fields existing
    /// - nullability of fields
    /// - types of fields
    ///
    /// @return The number of rows affected after the statement execution.
    /// @throws PlanValidationException on various failed checks.
    public int executeUpdateValidated(UpdateStatement updateStatement, Transaction transaction) {
        Layout tableLayout = getTableLayout(updateStatement.tableName(), transaction);

        List<NewFieldExpressionAssignment> foldedNewExprs = new ArrayList<>();
        for (NewFieldExpressionAssignment newFieldValue : updateStatement.newValues()) {
            if (!tableLayout.getSchema().hasField(newFieldValue.fieldName())) {
                throw new PlanValidationException(String.format(
                        "Field '%s' doesn't exist in the table",
                        newFieldValue.fieldName()
                ));
            }

            Expression foldedExpr;
            try {
                foldedExpr = (Expression) PartialEvaluator.evaluate(newFieldValue.newValueExpression());
            } catch (RuntimeExecutionException e) {
                throw new PlanValidationException(e.getMessage());
            }

            Set<String> exprFields = new HashSet<>(foldedExpr.getFields());

            for (String f : exprFields) {
                if (!tableLayout.getSchema().hasField(f)) {
                    throw new PlanValidationException(String.format("Field '%s' doesn't exist in the table", f));
                }
            }

            if (!tableLayout.getSchema().isNullable(newFieldValue.fieldName())
                    && foldedExpr.type(tableLayout.getSchema()) == DatabaseType.NULL) {
                throw new PlanValidationException(
                        String.format("Field '%s' isn't nullable", newFieldValue.fieldName())
                );
            }

            if (tableLayout.getSchema().type(newFieldValue.fieldName()) != foldedExpr.type(tableLayout.getSchema())
                    && newFieldValue.newValueExpression().type(tableLayout.getSchema()) != DatabaseType.NULL) {
                throw new PlanValidationException(String.format(
                        "Expression '%s' has the wrong type",
                        newFieldValue.newValueExpression())
                );
            }

            foldedNewExprs.add(new NewFieldExpressionAssignment(newFieldValue.fieldName(), foldedExpr));
        }

        Predicate foldedPredicate = validateAndFoldPredicate(updateStatement.predicate(), tableLayout.getSchema());

        UpdateStatement foldedUpdateStatement = new UpdateStatement(
                updateStatement.tableName(),
                foldedNewExprs,
                foldedPredicate
        );

        return executeUpdate(foldedUpdateStatement, transaction);
    }

    /// Validates every aspect of an insert statement and folds constant expressions.
    /// Checks for (for all tuples):
    /// - tables and fields existing
    /// - correct number of fields existing
    /// - nullability of fields
    /// - types of fields
    ///
    /// @return The number of rows affected after the statement execution.
    /// @throws PlanValidationException on various failed checks.
    public int executeInsertValidated(InsertStatement insertStatement, Transaction transaction) {
        Layout tableLayout = getTableLayout(insertStatement.tableName(), transaction);
        Schema tableSchema = tableLayout.getSchema();

        List<String> fields;
        if (insertStatement.allTuplesValueInfo().implicitFieldNames()) fields = tableSchema.getFields();
        else fields = insertStatement.allTuplesValueInfo().fieldNames();

        Set<String> fieldSet = new HashSet<>(fields);
        if (fieldSet.size() != fields.size()) {
            throw new PlanValidationException("Duplicate fields in insert statement");
        }

        Set<String> schemaFields = new HashSet<>(tableSchema.getFields());
        if (!fieldSet.equals(schemaFields)) {
            throw new PlanValidationException("Every table field must be present");
        }

        for (String field : fields) {
            if (!tableLayout.getSchema().hasField(field)) {
                throw new PlanValidationException(
                        String.format("Field '%s' doesn't exist in the table", field)
                );
            }
        }

        List<List<Constant>> newTuples = insertStatement.allTuplesValueInfo().newTuples();

        for (List<Constant> tuple : newTuples) {
            if (fields.size() != tuple.size()) {
                throw new PlanValidationException("Incorrect number of values in a tuple");
            }

            for (int i = 0; i < fields.size(); i++) {
                if (!tableSchema.isNullable(fields.get(i)) && tuple.get(i).type() == DatabaseType.NULL) {
                    throw new PlanValidationException(
                            String.format("Field '%s' isn't nullable", fields.get(i))
                    );
                }

                if (tableSchema.type(fields.get(i)) != tuple.get(i).type() && tuple.get(i).type() != DatabaseType.NULL) {
                    throw new PlanValidationException(
                            String.format("Field '%s' has the wrong type", fields.get(i))
                    );
                }
            }
        }

        return executeInsert(
                new InsertStatement(
                        insertStatement.tableName(),
                        new AllTuplesValueInfo(fields, newTuples, false)
                ),
                transaction
        );
    }

    /// If the transaction rolled back, new inserts must start from the beginning because
    /// it cannot be known how much blocks did the rolled back transaction advance the
    /// last reset by.
    public abstract void resetLastInsertion();

    /// Validates every aspect of a delete statement and folds constant expressions.
    /// Checks for:
    /// - tables and fields existing
    ///
    /// @return The number of rows affected after the statement execution.
    /// @throws PlanValidationException on various failed checks.
    public int executeDeleteValidated(DeleteStatement deleteStatement, Transaction transaction) {
        Layout tableLayout = getTableLayout(deleteStatement.tableName(), transaction);

        DeleteStatement foldedDeleteStatement = new DeleteStatement(
                deleteStatement.tableName(),
                validateAndFoldPredicate(deleteStatement.predicate(), tableLayout.getSchema())
        );

        return executeDelete(foldedDeleteStatement, transaction);
    }

    /// Validates every aspect of a create table statement.
    /// Checks for:
    /// - table already existing
    /// - maximum record size
    ///
    /// @return The number of rows affected after the statement execution.
    /// @throws PlanValidationException on various failed checks.
    public int executeCreateTableValidated(CreateTableStatement createTableStatement, Transaction transaction) {
        try {
            return executeCreateTable(createTableStatement, transaction);
        } catch (TableDuplicateNameException e) {
            throw new PlanValidationException(String.format(
                    "Table '%s' already exists",
                    createTableStatement.tableName()
            ));
        } catch (RecordTooLongException e) {
            throw new PlanValidationException(String.format(
                    "Record size for table '%s' is above the limit (%sB > %dB)",
                    createTableStatement.tableName(),
                    e.getMessage(),
                    transaction.blockSize()
            ));
        }
    }

    /// Validates every aspect of a create index statement.
    /// Checks for:
    /// - tables and fields existing
    /// - same name index already existing
    /// - same field index already existing
    ///
    /// @return The number of rows affected after the statement execution.
    /// @throws PlanValidationException on various failed checks.
    public int executeCreateIndexValidated(CreateIndexStatement createIndexStatement, Transaction transaction) {
        try {
            return executeCreateIndex(createIndexStatement, transaction);
        } catch (TableNotFoundException e) {
            throw new PlanValidationException(String.format(
                    "Table '%s' does not exist",
                    createIndexStatement.tableName()
            ));
        } catch (IndexTableIncorrectException e) {
            throw new PlanValidationException(String.format(
                    "Table '%s' does not have the fieldName '%s'",
                    createIndexStatement.tableName(),
                    createIndexStatement.fieldName()
            ));
        } catch (IndexDuplicateNameException e) {
            throw new PlanValidationException(String.format(
                    "Index with name '%s' already exists",
                    createIndexStatement.indexName()
            ));
        } catch (IndexAlreadyExistsException e) {
            throw new PlanValidationException(String.format(
                    "Index on fieldName '%s' already exists",
                    createIndexStatement.fieldName()
            ));
        }
    }

    // Internal API, that runs actual plan creations

    /// Knows for sure that the update statement is valid and executes it without any check.
    ///
    /// @return The number of rows affected after the statement execution.
    protected abstract int executeUpdate(UpdateStatement updateStatement, Transaction transaction);

    /// Knows for sure that the insert statement is valid and executes it without any check.
    ///
    /// @return The number of rows affected after the statement execution.
    protected abstract int executeInsert(InsertStatement insertStatement, Transaction transaction);

    /// Knows for sure that the delete statement is valid and executes it without any check.
    ///
    /// @return The number of rows affected after the statement execution.
    protected abstract int executeDelete(DeleteStatement deleteStatement, Transaction transaction);

    /// Knows for sure that the create table statement is valid and executes it without any check.
    ///
    /// @return The number of rows affected after the statement execution.
    protected abstract int executeCreateTable(CreateTableStatement createTableStatement, Transaction transaction);

    /// Knows for sure that the create index statement is valid and executes it without any check.
    ///
    /// @return The number of rows affected after the statement execution.
    protected abstract int executeCreateIndex(CreateIndexStatement createIndexStatement, Transaction transaction);

    // Private API, for checking statements

    /// Gets the table layout for validating fields against some
    /// statement.
    ///
    /// @return The layout of the table.
    /// @throws PlanValidationException if the table doesn't exist.
    private Layout getTableLayout(String tableName, Transaction transaction) {
        try {
            return metadataManager.getLayout(tableName, transaction);
        } catch (TableNotFoundException e) {
            throw new PlanValidationException(String.format(
                    "Table '%s' does not exist",
                    tableName
            ));
        }
    }

    /// Validates the predicate against a schema.
    ///
    /// @throws PlanValidationException if the predicate isn't valid.
    private Predicate validateAndFoldPredicate(Predicate predicate, Schema schema) {
        Predicate foldedPredicate;
        try {
            foldedPredicate = (Predicate) PartialEvaluator.evaluate(predicate);
        } catch (RuntimeExecutionException e) {
            throw new PlanValidationException(e.getMessage());
        }

        for (Term term : foldedPredicate.getTerms()) {
            Expression lhs = term.getLhs();
            Expression rhs = term.getRhs();

            Set<String> termFields = new HashSet<>(lhs.getFields());
            termFields.addAll(rhs.getFields());

            for (String f : termFields) {
                if (!schema.hasField(f)) {
                    throw new PlanValidationException(String.format("Field '%s' doesn't exist in the table", f));
                }
            }

            if (lhs.type(schema) != rhs.type(schema)
                    && lhs.type(schema) != DatabaseType.NULL && rhs.type(schema) != DatabaseType.NULL) {
                throw new PlanValidationException("Different types are compared in the 'WHERE' predicate.");
            }
        }

        return foldedPredicate;
    }
}
