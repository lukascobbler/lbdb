package com.luka.lbdb.querying.scanTests;

import com.luka.lbdb.querying.QueryTestUtils;
import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.scanDefinitions.UpdateScan;
import com.luka.lbdb.querying.scanTypes.readOnly.RenameScan;
import com.luka.lbdb.querying.scanTypes.update.SelectScan;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.expression.ConstantExpression;
import com.luka.lbdb.querying.virtualEntities.expression.FieldNameExpression;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RenameScanTests {
    @Test
    public void testAccessingFieldWithNewName() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             RenameScan scan = new RenameScan(tableScan, Map.of("renamedInt", "t1_intField1"))) {

            scan.beforeFirst();
            assertTrue(scan.next());

            assertTrue(scan.hasField("renamedInt"));
            assertEquals(0, scan.getValue("renamedInt").asInt());

            assertTrue(scan.hasField("t1_intField2"));
            assertEquals(1, scan.getValue("t1_intField2").asInt());
        }
    }

    @Test
    public void testOldNameIsNoLongerAccessible() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             RenameScan scan = new RenameScan(tableScan, Map.of("newStringName", "t1_stringField1"))) {

            scan.beforeFirst();
            assertTrue(scan.next());

            assertFalse(scan.hasField("t1_stringField1"));

            assertThrows(RuntimeExecutionException.class, () -> scan.getValue("t1_stringField1").asString());
        }
    }

    @Test
    public void testRenamingWithPredicateFilter() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Term t1 = new Term(
                new FieldNameExpression("t1_intField1"),
                TermOperator.GREATER_THAN,
                new ConstantExpression(new IntConstant(100))
        );
        Term t2 = new Term(
                new FieldNameExpression("t1_intField2"),
                TermOperator.LESS_THAN,
                new ConstantExpression(new IntConstant(110))
        );
        Predicate pred = new Predicate(t1, t2);

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             SelectScan selectScan = new SelectScan(tableScan, pred);
             RenameScan scan = new RenameScan(selectScan, Map.of("description", "t1_stringField1"))) {

            scan.beforeFirst();

            int count = 0;
            while (scan.next()) {
                assertTrue(scan.getValue("description").asString().startsWith("str"));
                count++;
            }
            assertEquals(8, count);
        }
    }

    @Test
    public void testNullCheckThroughRename() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             RenameScan scan = new RenameScan(tableScan, Map.of("nullableInt", "t1_intField3"))) {

            scan.beforeFirst();
            assertTrue(scan.next());

            assertEquals(NullConstant.INSTANCE, scan.getValue("nullableInt"));

            Term t1 = new Term(
                    new FieldNameExpression("nullableInt"),
                    TermOperator.IS,
                    new ConstantExpression(NullConstant.INSTANCE)
            );
            Term t2 = new Term(
                    new FieldNameExpression("t1_intField1"),
                    TermOperator.EQUALS,
                    new ConstantExpression(new IntConstant(0))
            );
            Predicate pred = new Predicate(t1, t2);

            assertTrue(pred.evaluate(scan).asBoolean());
        }
    }
}
