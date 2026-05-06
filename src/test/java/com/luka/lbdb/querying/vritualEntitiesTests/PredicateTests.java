package com.luka.lbdb.querying.vritualEntitiesTests;

import com.luka.lbdb.planning.planner.ReductionFactorCalculator;
import com.luka.lbdb.querying.QueryTestUtils;
import com.luka.lbdb.planning.plan.ExplainData;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PredicateTests {
    @Test
    public void testPredicateLogicAndSubPredicates() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Schema sch = testData.layouts().getFirst().getSchema();
        Scan ts = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
        ts.next();

        Predicate emptyPred = new Predicate();
        assertTrue(emptyPred.isSatisfied(ts));

        Term t1 = new Term(
                new FieldNameExpression("t1_intField1"),
                TermOperator.EQUALS,
                new ConstantExpression(new IntConstant(0))
        );
        Term t2 = new Term(
                new FieldNameExpression("t1_intField2"),
                TermOperator.EQUALS,
                new ConstantExpression(new IntConstant(1))
        );
        Predicate p1 = new Predicate(t1);
        p1.conjoinWith(new Predicate(t2));
        assertTrue(p1.isSatisfied(ts));

        Term t3 = new Term(
                new FieldNameExpression("t1_intField1"),
                TermOperator.EQUALS,
                new ConstantExpression(new IntConstant(99))
        );
        p1.conjoinWith(new Predicate(t3));
        assertFalse(p1.isSatisfied(ts));

        Predicate mixedPred = new Predicate(t1);
        Term tExt = new Term(
                new FieldNameExpression("otherTableField"),
                TermOperator.EQUALS,
                new ConstantExpression(new IntConstant(5))
        );
        mixedPred.conjoinWith(new Predicate(tExt));

        Predicate sub = mixedPred.selectSubPredicate(sch);
        List<Term> terms = sub.getTerms();
        assertEquals(1, terms.size());
        assertFalse(terms.getFirst().equatesWithFieldName("t1_intField1").isPresent());
        //noinspection OptionalGetWithoutIsPresent
        assertEquals(new IntConstant(0), terms.getFirst().equatesWithConstant("t1_intField1").get());

        ts.close();
    }

    @Test
    public void testJoinSubPredicate() {
        Schema s1 = new Schema(); s1.addIntField("f1", false);
        Schema s2 = new Schema(); s2.addIntField("f2", false);

        Term joinTerm = new Term(
                new FieldNameExpression("f1"),
                TermOperator.EQUALS,
                new FieldNameExpression("f2")
        );
        Term selectTerm = new Term(
                new FieldNameExpression("f1"),
                TermOperator.EQUALS,
                new ConstantExpression(new IntConstant(10))
        );

        Predicate p = new Predicate(joinTerm);
        p.conjoinWith(new Predicate(selectTerm));

        Predicate result = p.joinSubPredicate(s1, s2);
        List<Term> terms = result.getTerms();

        assertEquals(1, terms.size());
        assertNotNull(terms.getFirst().equatesWithFieldName("f1"));
    }

    @Test
    public void testReductionFactorOverflow() {
        Plan<Scan> plan = new Plan<>() {
            @Override public Scan open() { return null; }
            @Override public int blocksAccessed() { return 0; }
            @Override public int recordsOutput() { return 1000; }
            @Override public int distinctValues(String fieldName) { return 1000; }
            @Override public Schema outputSchema() { return null; }
            @Override public int nullValues(String fieldName) { return 0; }
            @Override public void explainPlan(List<ExplainData> previousExplanations, int ident) { }
        };

        Predicate p = new Predicate();
        for (int i = 0; i < 1000; i++) {
            Term t = new Term(
                    new FieldNameExpression("f" + i),
                    TermOperator.EQUALS,
                    new ConstantExpression(new IntConstant(i))
            );
            p.conjoinWith(new Predicate(t));
        }

        assertEquals(Double.MAX_VALUE, ReductionFactorCalculator.calculatePredicateReductionFactor(p, plan));
    }
}
