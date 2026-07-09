package com.luka.lbdb.querying.vritualEntitiesTests;

import com.luka.lbdb.querying.QueryTestUtils;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class TermTests {
    @Test
    public void testIsOperatorWithNulls() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Scan ts = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
        ts.next();

        Term t1 = new Term(
                new FieldNameExpression("t1_intField3"),
                TermOperator.IS,
                new ConstantExpression(NullConstant.INSTANCE)
        );
        assertTrue(t1.isSatisfied(ts));

        Term t2 = new Term(
                new FieldNameExpression("t1_stringField1"),
                TermOperator.IS,
                new ConstantExpression(NullConstant.INSTANCE)
        );
        assertFalse(t2.isSatisfied(ts));
    }

    @Test
    public void testNullPoisoningStandardOperators() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Scan ts = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
        ts.next();

        Term t1 = new Term(
                new FieldNameExpression("t1_intField3"),
                TermOperator.EQUALS,
                new FieldNameExpression("t1_intField3")
        );
        assertFalse(t1.isSatisfied(ts));

        Term t2 = new Term(
                new FieldNameExpression("t1_intField3"),
                TermOperator.GREATER_THAN,
                new ConstantExpression(new IntConstant(5))
        );
        assertFalse(t2.isSatisfied(ts));
    }

    @Test
    public void testComplexExpressionWithinTerm() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Scan ts = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
        ts.next();

        Expression math = new BinaryArithmeticExpression(
                new BinaryArithmeticExpression(
                        new FieldNameExpression("t1_intField1"),
                        ArithmeticOperator.ADD,
                        new ConstantExpression(new IntConstant(10))
                ),
                ArithmeticOperator.DIV,
                new ConstantExpression(new IntConstant(2))
        );

        Term t = new Term(math, TermOperator.GREATER_THAN, new FieldNameExpression("t1_intField1"));
        assertTrue(t.isSatisfied(ts));
    }

    @Test
    public void testStringAndBooleanLogic() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Scan ts = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());

        while (ts.next()) {
            Term t1 = new Term(
                    new FieldNameExpression("t1_stringField1"),
                    TermOperator.NOT_EQUALS,
                    new FieldNameExpression("t1_stringField2")
            );
            assertTrue(t1.isSatisfied(ts));

            Term t2 = new Term(
                    new FieldNameExpression("t1_boolField1"),
                    TermOperator.EQUALS,
                    new ConstantExpression(new BooleanConstant(true))
            );
            assertTrue(t2.isSatisfied(ts));
        }
    }

    @Test
    public void testNumericComparisons() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Scan ts = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
        ts.next();

        Term t1 = new Term(
                new FieldNameExpression("t1_intField2"),
                TermOperator.GREATER_OR_EQUAL,
                new ConstantExpression(new IntConstant(1))
        );
        assertTrue(t1.isSatisfied(ts));

        while(ts.next()) {
            Term t2 = new Term(
                    new FieldNameExpression("t1_intField1"),
                    TermOperator.LESS_THAN,
                    new FieldNameExpression("t1_intField2")
            );

            assertTrue(t2.isSatisfied(ts));
        }
    }

    @Test
    public void testEquatesLogic() {
        Expression f1 = new FieldNameExpression("t1_intField1");
        Expression f2 = new FieldNameExpression("t1_intField2");
        Constant c10 = new IntConstant(10);
        Expression e10 = new ConstantExpression(c10);

        Term t1 = new Term(f1, TermOperator.EQUALS, e10);
        assertEquals(c10, t1.equatesWithConstant("t1_intField1").get());
        assertEquals(Optional.empty(), t1.equatesWithFieldName("t1_intField1"));

        Term t2 = new Term(e10, TermOperator.EQUALS, f1);
        assertEquals(c10, t2.equatesWithConstant("t1_intField1").get());

        Term t3 = new Term(f1, TermOperator.EQUALS, f2);
        assertEquals("t1_intField2", t3.equatesWithFieldName("t1_intField1").get());
        assertEquals("t1_intField1", t3.equatesWithFieldName("t1_intField2").get());
        assertEquals(Optional.empty(), t3.equatesWithConstant("t1_intField1"));

        Term t4 = new Term(f1, TermOperator.GREATER_THAN, e10);
        assertEquals(Optional.empty(), t4.equatesWithConstant("t1_intField1"));
    }

    @Test
    public void testScanConsistencyWithEquates() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        String targetField = "t1_intField1";
        Constant targetVal = new IntConstant(42);
        Term term = new Term(
                new FieldNameExpression(targetField),
                TermOperator.EQUALS,
                new ConstantExpression(targetVal)
        );

        Constant equated = term.equatesWithConstant(targetField).get();
        assertEquals(targetVal, equated);

        try (TableScan ts = new TableScan(testData.tx(), "table1", testData.layouts().getFirst())) {
            int matchCount = 0;
            while (ts.next()) {
                boolean satisfied = term.isSatisfied(ts);
                int actualVal = ts.getValue(targetField).asInt();

                if (satisfied) {
                    assertEquals(targetVal.asInt(), actualVal);
                    matchCount++;
                } else {
                    assertNotEquals(targetVal.asInt(), actualVal);
                }
            }
            assertEquals(1, matchCount);
        }
    }
}
