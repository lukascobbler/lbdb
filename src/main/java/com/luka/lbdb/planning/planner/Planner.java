package com.luka.lbdb.planning.planner;

import com.luka.lbdb.network.protocol.response.EmptySet;
import com.luka.lbdb.network.protocol.response.ErrorResponse;
import com.luka.lbdb.network.protocol.response.QuerySet;
import com.luka.lbdb.network.protocol.response.Response;
import com.luka.lbdb.parsing.exceptions.ParseException;
import com.luka.lbdb.parsing.parser.Parser;
import com.luka.lbdb.parsing.statement.*;
import com.luka.lbdb.parsing.statement.transaction.TransactionAction;
import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.planning.exceptions.PlanValidationException;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.planning.planner.plannerDefinitions.QueryPlanner;
import com.luka.lbdb.planning.planner.plannerDefinitions.UpdatePlanner;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.transactionManagement.Transaction;
import com.luka.lbdb.transactionManagement.TransactionManager;

/// The main entry point class for executing / creating plans. Does not
/// have any special logic, it is just a wrapper over the system's query and
/// update planners.
public class Planner {
    private final QueryPlanner queryPlanner;
    private final UpdatePlanner updatePlanner;
    private final TransactionManager transactionManager;

    /// A planner needs some concrete implementation of the query and update
    /// planners.
    public Planner(QueryPlanner queryPlanner, UpdatePlanner updatePlanner, TransactionManager transactionManager) {
        this.queryPlanner = queryPlanner;
        this.updatePlanner = updatePlanner;
        this.transactionManager = transactionManager;
    }

    /// Generalized execution of any query, regardless of its type. Should not
    /// be used for JDBC, instead it should be used for CLI clients that display
    /// all data regardless.
    ///
    /// @return A response of the executed query, containing all data that the
    /// user needs, like the error, the number of rows affected or the actual
    /// table data.
    public Response execute(String queryOrUpdateQuery, int sessionId) {
        boolean isAutoCommit = !transactionManager.isManual(sessionId);
        Transaction t = transactionManager.getOrCreateTransaction(sessionId);

        try {
            Response r = switch (new Parser(queryOrUpdateQuery).parse()) {
                case TransactionStatement(TransactionAction action) -> switch (action) {
                    case START_TRANSACTION -> {
                        if (isAutoCommit) {
                            transactionManager.promoteToManual(sessionId, t);
                            isAutoCommit = false;
                            yield new EmptySet(0);
                        } else yield new ErrorResponse("Transaction already started");
                    }
                    case COMMIT -> {
                        if (!isAutoCommit) {
                            t.commit();
                            transactionManager.clearSession(sessionId);
                            yield new EmptySet(0);
                        } else yield new ErrorResponse("Transaction not started");
                    }
                    case ROLLBACK -> {
                        if (!isAutoCommit) {
                            t.rollback();
                            transactionManager.clearSession(sessionId);
                            updatePlanner.resetLastInsertion();
                            yield new EmptySet(0);
                        } else yield new ErrorResponse("Transaction not started");
                    }
                };
                case CreateIndexStatement ci -> new EmptySet(updatePlanner.executeCreateIndexValidated(ci, t));
                case CreateTableStatement ct -> new EmptySet(updatePlanner.executeCreateTableValidated(ct, t));
                case UpdateStatement u -> new EmptySet(updatePlanner.executeUpdateValidated(u, t));
                case DeleteStatement d -> new EmptySet(updatePlanner.executeDeleteValidated(d, t));
                case InsertStatement i -> new EmptySet(updatePlanner.executeInsertValidated(i, t));
                case ExplainStatement e when e.explainingStatement() instanceof SelectStatement s ->
                        new QuerySet(ExplainStatement.ExplainStatementSchema(), queryPlanner.createValidatedPlan(s, t).explainTuples());
                case ExplainStatement e -> new ErrorResponse("Explaining of non-select statements sadly isn't supported.");
                case SelectStatement s -> {
                    var plan = queryPlanner.createValidatedPlan(s, t);
                    yield new QuerySet(plan.outputSchema(), queryPlanner.executePlan(plan, t));
                }
            };

            if (isAutoCommit) t.commit();
            return r;
        } catch (Exception e) {
            if (isAutoCommit) t.rollback();

            String errType = switch (e) {
                case ParseException parse -> "(parse)";
                case PlanValidationException plan -> "(plan)";
                case RuntimeExecutionException runtime -> "(runtime)";
                default -> "";
            };

            return new ErrorResponse(errType + ": " + e.getMessage());
        }
    }

    /// Creates a query plan, but does not execute or process it.
    ///
    /// @return The plan for a `SELECT` query.
    public Plan<Scan> createQueryPlan(String query, Transaction transaction) throws PlanValidationException {
        Parser parser = new Parser(query);

        switch (parser.parse()) {
            case SelectStatement selectStatement -> {
                return queryPlanner.createValidatedPlan(selectStatement, transaction);
            }
            default -> throw new PlanValidationException("The given statement is not a query statement");
        }
    }

    /// Creates a plan for any modification query and executes it.
    ///
    /// @return The number of rows affected.
    public int executeUpdate(String updateQuery, Transaction transaction) throws PlanValidationException {
        Parser parser = new Parser(updateQuery);

        return switch (parser.parse()) {
            case CreateIndexStatement createIndexStatement ->
                updatePlanner.executeCreateIndexValidated(createIndexStatement, transaction);
            case CreateTableStatement createTableStatement ->
                updatePlanner.executeCreateTableValidated(createTableStatement, transaction);
            case DeleteStatement deleteStatement ->
                updatePlanner.executeDeleteValidated(deleteStatement, transaction);
            case InsertStatement insertStatement ->
                updatePlanner.executeInsertValidated(insertStatement, transaction);
            case UpdateStatement updateStatement ->
                updatePlanner.executeUpdateValidated(updateStatement, transaction);
            default -> throw new PlanValidationException("The given statement is not an update statement.");
        };
    }
}
