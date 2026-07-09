package com.luka.lbdb.planning.planner.plannerTypes;

import com.luka.lbdb.metadataManagement.MetadataManager;
import com.luka.lbdb.parsing.statement.SelectStatement;
import com.luka.lbdb.parsing.statement.select.SingleSelection;
import com.luka.lbdb.parsing.statement.select.TableInfo;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.planning.plan.planTypes.readOnly.*;
import com.luka.lbdb.planning.planner.plannerDefinitions.QueryPlanner;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.transactionManagement.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Concrete implementation of a query planner. Creates plan trees that are mathematically correct
/// according to the relational algebra operators applied to the data. Being mathematically correct,
/// however, doesn't imply that it's fast or memory efficient. Predicate pushdown or indexing isn't
/// done at all. A plan for an example query
/// ```sql
/// SELECT t1int, t2int, t3int
/// FROM table1 t1, table2 t2, table3 t3
/// WHERE t1int > t2int
/// UNION ALL
/// SELECT t4int, t5int, NULL
/// FROM table4 t4, table5 t5
/// WHERE t4int > t5int;
/// ```
/// looks like this:
/// ```
///                                            ┌──────────┐
///                                            │ UnionAll │
///                                            └────┬┬────┘
///                          _______________________││______________________
///                         ││                                             ││
///                    ┌────┴┴────┐                                   ┌────┴┴────┐
///                    │  Extend  │                                   │  Rename  │
///                    └────┬┬────┘                                   └────┬┬────┘
///                    ┌────┴┴────┐                                   ┌────┴┴────┐
///                    │  Select  │                                   │  Extend  │
///                    └────┬┬────┘                                   └────┬┬────┘
///                    ┌────┴┴────┐                                   ┌────┴┴────┐
///                    │  Product │                                   │  Select  │
///                    └────┬┬────┘                                   └────┬┬────┘
///            _____________││_____________                           ┌────┴┴────┐
///           ││                          ││                          │  Product │
///      ┌────┴┴────┐                ┌────┴┴────┐                     └────┬┬────┘
///      │  Rename  │                │  Product │                    ______││______
///      └────┬┬────┘                └────┬┬────┘                   ││            ││
///      ┌────┴┴────┐               ______││______             ┌────┴┴────┐  ┌────┴┴────┐
///      │TableScan │              ││            ││            │  Rename  │  │  Rename  │
///      └──────────┘         ┌────┴┴────┐  ┌────┴┴────┐       └────┬┬────┘  └────┬┬────┘
///                           │  Rename  │  │  Rename  │       ┌────┴┴────┐  ┌────┴┴────┐
///                           └────┬┬────┘  └────┬┬────┘       │TableScan │  │TableScan │
///                           ┌────┴┴────┐  ┌────┴┴────┐       └──────────┘  └──────────┘
///                           │TableScan │  │TableScan │
///                           └──────────┘  └──────────┘
/// ```
///
/// More plan trees can be generated with the `EXPLAIN` SQL keyword.
public class BetterQueryPlanner extends QueryPlanner {
    /// An update planner needs a metadata manager for direct table
    /// access.
    public BetterQueryPlanner(MetadataManager metadataManager) {
        super(metadataManager);
    }

    /// Joins query parts that don't involve unions, with unions. Since
    /// unions have the smallest precedence, they are done at the end.
    ///
    /// Unions that involve two tables look like this:
    /// ```
    ///        ┌──────────┐
    ///        │ UnionAll │
    ///        └────┬┬────┘
    ///       _____ ││______
    ///      ││            ││
    /// ┌────┴┴────┐  ┌────┴┴────┐
    /// │  Extend  │  │  Rename  │
    /// └────┬┬────┘  └────┬┬────┘
    ///     ....      ┌────┴┴────┐
    ///               │  Extend  │
    ///               └────┬┬────┘
    ///                   ....
    /// ```
    ///
    /// Unions that involve more than two tables have a recurring subplan
    /// that repeats for every additional union and look like this:
    /// ```
    ///           ┌──────────┐
    ///           │ UnionAll │
    ///           └────┬┬────┘
    ///       ________ ││_________
    ///      ││                  ││
    /// ┌────┴┴────┐        ┌────┴┴────┐
    /// │  Rename  │        │ UnionAll │
    /// └────┬┬────┘        └────┬┬────┘
    /// ┌────┴┴────┐             ││
    /// │  Extend  │             ││
    /// └────┬┬────┘       _____ ││______
    ///     ....          ││            ││
    ///              ┌────┴┴────┐  ┌────┴┴────┐
    ///              │  Extend  │  │  Rename  │
    ///              └────┬┬────┘  └────┬┬────┘
    ///                  ....      ┌────┴┴────┐
    ///                            │  Extend  │
    ///                            └────┬┬────┘
    ///                                ....
    /// ```
    ///
    /// Notice that every union has one rename at the right. That rename
    /// is used to match the projected fields' names of every subsequent
    /// unionized select statement to the first select statement's field
    /// names. This is done because the union operator allows different
    /// projection field names, but access only through the first select
    /// field names.
    @Override
    protected Plan<Scan> createPlan(SelectStatement selectStatement, Transaction transaction) {
        List<SingleSelection> selections = selectStatement.unionizedSelections();

        Plan<Scan> plan = createSingleSelectionPlan(selections.getFirst(), transaction);
        List<String> originalProjectionVariableNames = plan.outputSchema().getFields();

        for (int i = 1; i < selections.size(); i++) {
            Plan<Scan> nextPlan = createSingleSelectionPlan(selections.get(i), transaction);

            List<String> nextPlanProjectionVariableNames = nextPlan.outputSchema().getFields();

            Map<String, String> fieldNameMapping = new HashMap<>();
            for (int j = 0; j < originalProjectionVariableNames.size(); j++) {
                String originalPlanProjectionVariableName = originalProjectionVariableNames.get(j);
                String nextPlanProjectionVariableName = nextPlanProjectionVariableNames.get(j);

                fieldNameMapping.put(originalPlanProjectionVariableName, nextPlanProjectionVariableName);
            }

            nextPlan = new RenamePlan(nextPlan, fieldNameMapping);

            plan = new UnionAllPlan(plan, nextPlan);
        }

        return plan;
    }

    /// Focuses on creating the output of the subplan that doesn't
    /// involve unions. Generally looks like this:
    /// ```
    ///         ┌──────────┐
    ///         │  Extend  │
    ///         └────┬┬────┘
    ///         ┌────┴┴────┐
    ///         │  Select  │
    ///         └────┬┬────┘
    ///         ┌────┴┴────┐
    ///         │  Product │
    ///         └────┬┬────┘
    ///        ______││______
    ///       ││            ││
    ///  ┌────┴┴────┐  ┌────┴┴────┐
    ///  │  Rename  │  │  Rename  │
    ///  └────┬┬────┘  └────┬┬────┘
    ///  ┌────┴┴────┐  ┌────┴┴────┐
    ///  │TableScan │  │TableScan │
    ///  └──────────┘  └──────────┘
    /// ```
    /// At the bottom are table plans and interfaces to them, after
    /// that are all products that involve two or more tables.
    ///
    /// After creating the source of data, selection is performed to filter out all
    /// rows that don't match the query predicate (optional, selection doesn't
    /// need to exist if there is no predicate), and finally, projection is
    /// done. Projection removes any rows that weren't mentioned and adds all
    /// new virtual rows.
    ///
    /// @return A partial plan of the query that doesn't involve unions.
    private Plan<Scan> createSingleSelectionPlan(SingleSelection singleSelection, Transaction transaction) {
        Plan<Scan> plan = getDataSourcePlan(singleSelection, transaction);

        if (!singleSelection.predicate().getTerms().isEmpty()) {
            plan = new SelectReadOnlyPlan(plan, singleSelection.predicate());
        }

        return new ExtendProjectPlan(plan, singleSelection.projectionFields());
    }

    /// Products that involve two tables look like this:
    /// ```
    ///        ┌──────────┐
    ///        │  Product │
    ///        └────┬┬────┘
    ///       ______││______
    ///      ││            ││
    /// ┌────┴┴────┐  ┌────┴┴────┐
    /// │  Rename  │  │  Rename  │
    /// └────┬┬────┘  └────┬┬────┘
    ///     ....          ....
    /// ```
    ///
    /// Products that involve more than two tables have a recurring subtree
    /// that repeats for every additional product and look like this:
    /// ```
    ///         ┌──────────┐
    ///         │  Product │
    ///         └────┬┬────┘
    ///        ______││______
    ///       ││            ││
    ///  ┌────┴┴────┐  ┌────┴┴────┐
    ///  │  Rename  │  │  Product │
    ///  └────┬┬────┘  └────┬┬────┘
    ///      ....      ┌────┴┴────┐
    ///                │  Rename  │
    ///                └────┬┬────┘
    ///                    ....
    /// ```
    ///
    /// The planner tries to use create a product plan that will need less block
    /// accesses, but it heavily depends on table order in the query. This operation
    /// alone can't create optimal plans, but is slightly better than blindly joining
    /// two tables.
    ///
    /// If there are no tables mentioned, the query is called a constant query and the
    /// underlying scan will contain only one row consisting only of constant values.
    /// The data source query tree will look like this:
    /// ```
    /// ┌───────────┐
    /// │ DummyScan │
    /// └───────────┘
    /// ```
    ///
    /// @return The data source plan.
    private Plan<Scan> getDataSourcePlan(SingleSelection singleSelection, Transaction transaction) {
        if (singleSelection.tables().isEmpty()) {
            return new DummyTablePlan(singleSelection.projectionFields());
        }

        List<Plan<Scan>> differentTablePlans = new ArrayList<>();

        for (TableInfo tableInfo : singleSelection.tables()) {
            Plan<Scan> initialPlan = fullyQualifiedTablePlan(transaction, tableInfo);
            differentTablePlans.add(initialPlan);
        }

        Plan<Scan> plan = differentTablePlans.removeFirst();

        for (Plan<Scan> nextPlan : differentTablePlans) {
            Plan<Scan> p1 = new ProductPlan(nextPlan, plan);
            Plan<Scan> p2 = new ProductPlan(plan, nextPlan);
            plan = p1.blocksAccessed() < p2.blocksAccessed() ? p1 : p2;
        }

        return plan;
    }

    /// Creates an interface to a table by fully qualifying all the table's fields.
    /// It does that by wrapping a table plan / scan in rename plan blocks like this:
    /// ```
    /// ┌──────────┐
    /// │  Rename  │
    /// └────┬┬────┘
    /// ┌────┴┴────┐
    /// │TableScan │
    /// └──────────┘
    /// ```
    ///
    /// This allows access to fields that have the same name in multiple tables.
    ///
    /// @return A table plan wrapped in a rename plan, fully qualifying all table
    /// fields from the rename plan upwards.
    private Plan<Scan> fullyQualifiedTablePlan(Transaction transaction, TableInfo tableInfo) {
        String tableName = tableInfo.tableName();
        Plan<Scan> initialPlan = new TableReadOnlyPlan(transaction, tableName, metadataManager);

        String tableQualifier = tableInfo.qualifier();
        Map<String, String> fieldNameMapping = new HashMap<>();

        // renaming all table scan's fields to be fully qualified
        // because they are fully qualified in the statement
        for (String noQualificationFieldName : initialPlan.outputSchema().getFields()) {
            String fullyQualifiedFieldName = tableQualifier + "." + noQualificationFieldName;
            fieldNameMapping.put(fullyQualifiedFieldName, noQualificationFieldName);
        }

        initialPlan = new RenamePlan(initialPlan, fieldNameMapping);
        return initialPlan;
    }
}
