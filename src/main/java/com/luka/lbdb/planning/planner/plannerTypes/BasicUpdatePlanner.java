package com.luka.lbdb.planning.planner.plannerTypes;

import com.luka.lbdb.metadataManagement.MetadataManager;
import com.luka.lbdb.parsing.statement.*;
import com.luka.lbdb.parsing.statement.update.NewFieldExpressionAssignment;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.planning.plan.planTypes.update.SelectPlan;
import com.luka.lbdb.planning.plan.planTypes.update.TablePlan;
import com.luka.lbdb.planning.planner.plannerDefinitions.UpdatePlanner;
import com.luka.lbdb.querying.scanDefinitions.UpdateScan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.records.RecordId;
import com.luka.lbdb.transactionManagement.Transaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Concrete implementation of an update planner, that creates
/// plans that modify data, and executes them on the spot. As the
/// name implies, the planner is pretty basic, ensuring only correctness
/// and no optimizations.
public class BasicUpdatePlanner extends UpdatePlanner {
    private final Map<String, RecordId> lastInsertions;

    /// An update planner needs a metadata manager for direct table
    /// access, and a map of where is the last insertion for a given
    /// table.
    public BasicUpdatePlanner(MetadataManager metadataManager, Map<String, RecordId> lastInsertions) {
        super(metadataManager);
        this.lastInsertions = lastInsertions;
    }

    /// Inserts a new record from the position of the lastly inserted record.
    /// This insertion strategy allows faster insertion performance, but wastes
    /// space of earlier deleted records until the server restarts.
    ///
    /// @return The number of inserted rows.
    @Override
    protected int executeInsert(InsertStatement insertStatement, Transaction transaction) {
        Plan<UpdateScan> plan = new TablePlan(transaction, insertStatement.tableName(), metadataManager);

        Optional<RecordId> lastInsertionForTable = getLastInsertion(insertStatement.tableName());
        List<String> fields = insertStatement.allTuplesValueInfo().fieldNames();

        try (UpdateScan insertScan = plan.open()) {
            lastInsertionForTable.ifPresent(insertScan::moveToRecord);

            for (List<Constant> tuple : insertStatement.allTuplesValueInfo().newTuples()) {
                insertScan.insert();

                for (int i = 0; i < fields.size(); i++) {
                    insertScan.setValue(fields.get(i), tuple.get(i));
                }
            }

            setLastInsertion(insertStatement.tableName(), insertScan.getRecordId());
        }

        return insertStatement.allTuplesValueInfo().newTuples().size();
    }

    /// Updates all records that match a predicate. Only provided fields' will be changed.
    ///
    /// @return The number of rows that matched the predicate.
    @Override
    protected int executeUpdate(UpdateStatement updateStatement, Transaction transaction) {
        Plan<UpdateScan> plan = new TablePlan(transaction, updateStatement.tableName(), metadataManager);
        plan = new SelectPlan(plan, updateStatement.predicate());

        int count = 0;
        try (UpdateScan updateScan = plan.open()) {
            while (updateScan.next()) {
                count++;

                for (NewFieldExpressionAssignment newFieldValue : updateStatement.newValues()) {
                    Constant computedValue = newFieldValue.newValueExpression().evaluate(updateScan);
                    updateScan.setValue(newFieldValue.fieldName(), computedValue);
                }
            }
        }

        return count;
    }

    /// Deletes all records that match a predicate.
    ///
    /// @return The number of rows that matched the predicate.
    @Override
    protected int executeDelete(DeleteStatement deleteStatement, Transaction transaction) {
        Plan<UpdateScan> plan = new TablePlan(transaction, deleteStatement.tableName(), metadataManager);
        plan = new SelectPlan(plan, deleteStatement.predicate());

        int count = 0;
        try (UpdateScan deleteScan = plan.open()) {
            while (deleteScan.next()) {
                deleteScan.delete();
                count++;
            }
        }

        return count;
    }

    /// Puts the new table's metadata in the system - its name and schema.
    ///
    /// @return 0, because no rows were changed.
    @Override
    protected int executeCreateTable(CreateTableStatement createTableStatement, Transaction transaction) {
        metadataManager.createTable(
                createTableStatement.tableName(),
                createTableStatement.schema(),
                transaction
        );
        return 0;
    }

    /// Puts the new index's metadata in the system - its name, table, field and type.
    ///
    /// @return 0, because no rows were changed.
    @Override
    protected int executeCreateIndex(CreateIndexStatement createIndexStatement, Transaction transaction) {
        metadataManager.createIndex(
                createIndexStatement.indexName(),
                createIndexStatement.tableName(),
                createIndexStatement.fieldName(),
                createIndexStatement.type(),
                transaction
        );
        return 0;
    }

    /// Synchronously gets the last insert record id position from a given table.
    private synchronized Optional<RecordId> getLastInsertion(String tableName) {
        return Optional.ofNullable(lastInsertions.get(tableName));
    }

    /// Synchronously sets the last insert record id position for a given table.
    private synchronized void setLastInsertion(String tableName, RecordId recordId) {
        lastInsertions.put(tableName, recordId);
    }
}
