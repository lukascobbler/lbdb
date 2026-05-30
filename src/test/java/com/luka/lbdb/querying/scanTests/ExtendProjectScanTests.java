package com.luka.lbdb.querying.scanTests;

import com.luka.lbdb.querying.QueryTestUtils;
import com.luka.lbdb.querying.scanDefinitions.UpdateScan;
import com.luka.lbdb.querying.scanTypes.readOnly.ExtendProjectScan;
import com.luka.lbdb.querying.scanTypes.readOnly.SelectReadOnlyScan;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendProjectScanTests {
    @Test
    public void testAccessingExtendedField() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression expr = new BinaryArithmeticExpression(
                new FieldNameExpression("t1_intField1"),
                ArithmeticOperator.ADD,
                new ConstantExpression(new IntConstant(100))
        );

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             ExtendProjectScan scan = new ExtendProjectScan(tableScan, Map.of(
                     "calculatedField", expr,
                     "t1_intField1", new FieldNameExpression("t1_intField1")
             ))) {

            scan.beforeFirst();

            int i = 0;
            while(scan.next()) {
                assertTrue(scan.hasField("calculatedField"));
                assertEquals(100 + i, scan.getValue("calculatedField").asInt());

                assertTrue(scan.hasField("t1_intField1"));
                assertEquals(i, scan.getValue("t1_intField1").asInt());
                i++;
            }

            assertTrue(i > 0);
        }
    }

    @Test
    public void testExtendedFieldInPredicate() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression expr = new BinaryArithmeticExpression(
                new FieldNameExpression("t1_intField1"),
                ArithmeticOperator.MUL,
                new ConstantExpression(new IntConstant(2))
        );

        Term t1 = new Term(
                new FieldNameExpression("doubledVal"),
                TermOperator.EQUALS,
                new ConstantExpression(new IntConstant(20))
        );
        Term t2 = new Term(
                new FieldNameExpression("t1_boolField1"),
                TermOperator.EQUALS,
                new ConstantExpression(new BooleanConstant(true))
        );
        Predicate pred = new Predicate(t1, t2);

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             ExtendProjectScan extendScan = new ExtendProjectScan(tableScan, Map.of(
                     "doubledVal", expr,
                     "t1_boolField1", new FieldNameExpression("t1_boolField1"),
                     "t1_intField1", new FieldNameExpression("t1_intField1")
             ));
             SelectReadOnlyScan scan = new SelectReadOnlyScan(extendScan, pred)) {

            scan.beforeFirst();
            assertTrue(scan.next());

            assertEquals(20, scan.getValue("doubledVal").asInt());
            assertEquals(10, scan.getValue("t1_intField1").asInt());
            assertFalse(scan.next());
        }
    }

    @Test
    public void testExtendWithNullValues() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression expr = new FieldNameExpression("t1_intField3");

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             ExtendProjectScan scan = new ExtendProjectScan(tableScan, Map.of(
                     "copyOffNull", expr,
                     "t1_intField1", new FieldNameExpression("t1_intField1")
             ))) {

            scan.beforeFirst();
            assertTrue(scan.next());

            assertEquals(NullConstant.INSTANCE, scan.getValue("copyOffNull"));

            Term t1 = new Term(
                    new FieldNameExpression("copyOffNull"),
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

    @Test
    public void testExtendWithNullValueLiteral() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression expr = new ConstantExpression(NullConstant.INSTANCE);

        try (UpdateScan tableScan = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());
             ExtendProjectScan scan = new ExtendProjectScan(tableScan, Map.of("nullLiteral", expr))) {

            scan.beforeFirst();
            assertTrue(scan.next());

            assertEquals(NullConstant.INSTANCE, scan.getValue("nullLiteral"));
        }
    }
}