package com.luka.lbdb.querying.vritualEntitiesTests;

import com.luka.lbdb.planning.planner.PartialEvaluator;
import com.luka.lbdb.querying.QueryTestUtils;
import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.scanTypes.update.TableScan;
import com.luka.lbdb.querying.virtualEntities.constant.*;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ExpressionTests {
    @Test
    public void testConstantEvaluationExpression() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression eInt = new ConstantExpression(new IntConstant(843));
        Expression eString = new ConstantExpression(new StringConstant("test"));
        Expression eBool = new ConstantExpression(new BooleanConstant(true));
        Expression eNull = new ConstantExpression(NullConstant.INSTANCE);

        Scan ts1 = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());

        try (ts1) {
            while (ts1.next()) {
                assertEquals(843, eInt.evaluate(ts1).asInt());
                assertEquals("test", eString.evaluate(ts1).asString());
                assertTrue(eBool.evaluate(ts1).asBoolean());
                assertEquals(NullConstant.INSTANCE, eNull.evaluate(ts1));
            }
        }
    }

    @Test
    public void testFieldNameExpression() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression eFieldNameInt1 = new FieldNameExpression("t1_intField1");
        Expression eFieldNameInt2 = new FieldNameExpression("t1_intField2");
        Expression eFieldNameInt3 = new FieldNameExpression("t1_intField3");
        Expression eFieldNameString1 = new FieldNameExpression("t1_stringField1");
        Expression eFieldNameString2 = new FieldNameExpression("t1_stringField2");
        Expression eFieldNameString3 = new FieldNameExpression("t1_stringField3");
        Expression eFieldNameBool1 = new FieldNameExpression("t1_boolField1");
        Expression eFieldNameBool2 = new FieldNameExpression("t1_boolField2");
        Expression eFieldNameBool3 = new FieldNameExpression("t1_boolField3");

        Scan ts1 = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());

        int i = 0;
        try (ts1) {
            while (ts1.next()) {
                assertEquals(i, eFieldNameInt1.evaluate(ts1).asInt());
                assertEquals(i + 1, eFieldNameInt2.evaluate(ts1).asInt());
                assertEquals(NullConstant.INSTANCE, eFieldNameInt3.evaluate(ts1));
                assertEquals("str" + i, eFieldNameString1.evaluate(ts1).asString());
                assertEquals("str" + i + 1, eFieldNameString2.evaluate(ts1).asString());
                assertEquals(NullConstant.INSTANCE, eFieldNameString3.evaluate(ts1));
                assertTrue(eFieldNameBool1.evaluate(ts1).asBoolean());
                assertFalse(eFieldNameBool2.evaluate(ts1).asBoolean());
                assertEquals(NullConstant.INSTANCE, eFieldNameBool3.evaluate(ts1));

                i += 1;
            }
        }
    }

    @Test
    public void testArithmeticExpressionConstant() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression eInt1 = new ConstantExpression(new IntConstant(1));
        Expression eInt2 = new ConstantExpression(new IntConstant(2));
        Expression ar1 = new BinaryArithmeticExpression(eInt1, ArithmeticOperator.ADD, eInt2);

        Scan ts1 = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());

        try (ts1) {
            while (ts1.next()) {
                assertEquals(3, ar1.evaluate(ts1).asInt());
            }
        }
    }

    @Test
    public void testArithmeticExpressionFieldNameSimple() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Expression eInt1 = new ConstantExpression(new IntConstant(500));
        Expression eInt2 = new FieldNameExpression("t1_intField2");
        Expression ar1 = new BinaryArithmeticExpression(eInt1, ArithmeticOperator.ADD, eInt2);

        Scan ts1 = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());

        int i = 0;
        try (ts1) {
            while (ts1.next()) {
                assertEquals(500 + i + 1, ar1.evaluate(ts1).asInt());

                i += 1;
            }
        }
    }

    @Test
    public void testArithmeticExpressionFieldNameComplex() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        // ((f1 * 10) + (f2 - 5)) / -2
        Expression complexExpr = new BinaryArithmeticExpression(
                new BinaryArithmeticExpression(
                        new BinaryArithmeticExpression(
                                new FieldNameExpression("t1_intField1"),
                                ArithmeticOperator.MUL,
                                new ConstantExpression(new IntConstant(10))
                        ),
                        ArithmeticOperator.ADD,
                        new BinaryArithmeticExpression(
                                new FieldNameExpression("t1_intField2"),
                                ArithmeticOperator.SUB,
                                new ConstantExpression(new IntConstant(5))
                        )
                ),
                ArithmeticOperator.DIV,
                new UnaryArithmeticExpression(
                        ArithmeticOperator.SUB,
                        new ConstantExpression(new IntConstant(2))
                )
        );

        Scan ts1 = new TableScan(testData.tx(), "table1", testData.layouts().getFirst());

        try (ts1) {
            int i = 0;
            while (ts1.next()) {
                int expected = ((i * 10) + ((i + 1) - 5)) / -2;
                Constant result = complexExpr.evaluate(ts1);
                assertEquals(expected, result.asInt());
                i++;
            }
        }
    }

    @Test
    public void testComplexFoldingAndEvaluation1() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        // Target: ((t1_intField1 + (10 + 5)) * (20 / 10)) - (t1_intField1 + 15)
        // 1. (10 + 5) -> 15
        // 2. (20 / 10) -> 2
        // 3. ((t1_intField1 + 15) * 2) - (t1_intField1 + 15)

        Expression f1 = new FieldNameExpression("t1_intField1");

        Expression const10 = new ConstantExpression(new IntConstant(10));
        Expression const5 = new ConstantExpression(new IntConstant(5));
        Expression innerAdd = new BinaryArithmeticExpression(const10, ArithmeticOperator.ADD, const5);

        Expression leftWithField = new BinaryArithmeticExpression(f1, ArithmeticOperator.ADD, innerAdd);

        Expression const20 = new ConstantExpression(new IntConstant(20));
        Expression const10_2 = new ConstantExpression(new IntConstant(10));
        Expression innerDiv = new BinaryArithmeticExpression(const20, ArithmeticOperator.DIV, const10_2);

        Expression branch1 = new BinaryArithmeticExpression(leftWithField, ArithmeticOperator.MUL, innerDiv);

        Expression branch2 = new BinaryArithmeticExpression(
                new FieldNameExpression("t1_intField1"),
                ArithmeticOperator.ADD,
                new ConstantExpression(new IntConstant(15))
        );

        Expression totalExpr = new BinaryArithmeticExpression(branch1, ArithmeticOperator.SUB, branch2);

        Expression foldedExpr = (Expression) PartialEvaluator.evaluate(totalExpr);

        String expectedString = "(((t1_intField1 + 15) * 2) - (t1_intField1 + 15))";
        assertEquals(expectedString, foldedExpr.toString());

        try (TableScan ts1 = new TableScan(testData.tx(), "table1", testData.layouts().getFirst())) {
            int i = 0;
            while (ts1.next()) {
                int expectedValue = i + 15;

                Constant resultTotal = totalExpr.evaluate(ts1);
                Constant resultFolded = foldedExpr.evaluate(ts1);
                assertEquals(expectedValue, resultTotal.asInt());
                assertEquals(expectedValue, resultFolded.asInt());
                i++;
            }
        }
    }

    @Test
    public void testComplexFoldingAndEvaluation2() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        // Target: (-((t1_intField1 + (10 + 5)) * (20 / 20))) - (-(t1_intField1 + 15))
        // 1. (10 + 5) -> 15
        // 2. (20 / 10) -> 1
        // 3. (-((t1_intField1 + 15) * 1)) - (-(t1_intField1 + 15))
        // 4. (-(t1_intField1 + 15)) - (-(t1_intField1 + 15))
        // 5. 0

        Expression f1 = new FieldNameExpression("t1_intField1");

        Expression const10 = new ConstantExpression(new IntConstant(10));
        Expression const5 = new ConstantExpression(new IntConstant(5));
        Expression innerAdd = new BinaryArithmeticExpression(const10, ArithmeticOperator.ADD, const5);

        Expression leftWithField = new BinaryArithmeticExpression(f1, ArithmeticOperator.ADD, innerAdd);

        Expression const20 = new ConstantExpression(new IntConstant(20));
        Expression const10_2 = new ConstantExpression(new IntConstant(20));
        Expression innerDiv = new BinaryArithmeticExpression(const20, ArithmeticOperator.DIV, const10_2);

        Expression branch1 = new UnaryArithmeticExpression(
                ArithmeticOperator.SUB,
                new BinaryArithmeticExpression(leftWithField, ArithmeticOperator.MUL, innerDiv)
        );

        Expression branch2 = new UnaryArithmeticExpression(
                ArithmeticOperator.SUB,
                new BinaryArithmeticExpression(
                        new FieldNameExpression("t1_intField1"),
                        ArithmeticOperator.ADD,
                        new ConstantExpression(new IntConstant(15))
                )
        );

        Expression totalExpr = new BinaryArithmeticExpression(branch1, ArithmeticOperator.SUB, branch2);

        Expression foldedExpr = (Expression) PartialEvaluator.evaluate(totalExpr);

        String expectedString = "0";
        assertEquals(expectedString, foldedExpr.toString());

        try (TableScan ts1 = new TableScan(testData.tx(), "table1", testData.layouts().getFirst())) {
            int i = 0;
            while (ts1.next()) {
                int expectedValue = 0;

                Constant resultTotal = totalExpr.evaluate(ts1);
                Constant resultFolded = foldedExpr.evaluate(ts1);
                assertEquals(expectedValue, resultTotal.asInt());
                assertEquals(expectedValue, resultFolded.asInt());
                i++;
            }
        }
    }

    @Test
    public void testUnaryFolding() {
        Expression field = new FieldNameExpression("val");
        Expression const5 = new ConstantExpression(new IntConstant(5));

        Expression unaryMinusConst = new UnaryArithmeticExpression(ArithmeticOperator.SUB, const5);
        Expression foldedConst = (Expression) PartialEvaluator.evaluate(unaryMinusConst);
        assertEquals("-5", foldedConst.toString());
        assertInstanceOf(ConstantExpression.class, foldedConst);

        Expression unaryPlusField = new UnaryArithmeticExpression(ArithmeticOperator.ADD, field);
        Expression foldedPlus = (Expression) PartialEvaluator.evaluate(unaryPlusField);
        assertEquals("val", foldedPlus.toString());
        assertInstanceOf(FieldNameExpression.class, foldedPlus);

        Expression doubleNegation = new UnaryArithmeticExpression(ArithmeticOperator.SUB,
                new UnaryArithmeticExpression(ArithmeticOperator.SUB, field));
        Expression foldedDoubleNeg = (Expression) PartialEvaluator.evaluate(doubleNegation);
        assertEquals("val", foldedDoubleNeg.toString());

        Expression tripleNegation = new UnaryArithmeticExpression(ArithmeticOperator.SUB, doubleNegation);
        Expression foldedTripleNeg = (Expression) PartialEvaluator.evaluate(tripleNegation);
        assertEquals("-(val)", foldedTripleNeg.toString());
    }

    @Test
    public void testAdditiveInverseFolding() {
        Expression x = new FieldNameExpression("x");

        Expression xPlusNegX = new BinaryArithmeticExpression(
                x,
                ArithmeticOperator.ADD,
                new UnaryArithmeticExpression(ArithmeticOperator.SUB, x)
        );
        assertEquals("0", PartialEvaluator.evaluate(xPlusNegX).toString());

        Expression negXPlusX = new BinaryArithmeticExpression(
                new UnaryArithmeticExpression(ArithmeticOperator.SUB, x),
                ArithmeticOperator.ADD,
                x
        );
        assertEquals("0", PartialEvaluator.evaluate(negXPlusX).toString());

        Expression complex = new BinaryArithmeticExpression(x,
                ArithmeticOperator.ADD,
                new ConstantExpression(new IntConstant(5))
        );
        Expression complexInverse = new BinaryArithmeticExpression(
                new UnaryArithmeticExpression(ArithmeticOperator.SUB, complex),
                ArithmeticOperator.ADD, complex);
        assertEquals("0", PartialEvaluator.evaluate(complexInverse).toString());
    }

    @Test
    public void testUnaryBinaryInteraction() {
        Expression x = new FieldNameExpression("x");
        Expression y = new FieldNameExpression("y");

        Expression addNeg = new BinaryArithmeticExpression(x, ArithmeticOperator.ADD,
                new UnaryArithmeticExpression(ArithmeticOperator.SUB, y));
        Expression foldedAddNeg = (Expression) PartialEvaluator.evaluate(addNeg);
        assertEquals("(x - y)", foldedAddNeg.toString());

        Expression subNeg = new BinaryArithmeticExpression(x, ArithmeticOperator.SUB,
                new UnaryArithmeticExpression(ArithmeticOperator.SUB, y));
        Expression foldedSubNeg = (Expression) PartialEvaluator.evaluate(subNeg);
        assertEquals("(x + y)", foldedSubNeg.toString());

        Expression mulNegOne = new BinaryArithmeticExpression(x, ArithmeticOperator.MUL,
                new ConstantExpression(new IntConstant(-1)));
        Expression foldedMulNegOne = (Expression) PartialEvaluator.evaluate(mulNegOne);
        assertEquals("-(x)", foldedMulNegOne.toString());
    }

    @Test
    public void testNoFolding1() {
        Expression left = new FieldNameExpression("x");
        Expression right = new FieldNameExpression("y");

        Expression diffFields = new BinaryArithmeticExpression(left, ArithmeticOperator.SUB, right);
        Expression foldedDiff = (Expression) PartialEvaluator.evaluate(diffFields);
        assertEquals("(x - y)", foldedDiff.toString());
        assertFalse(foldedDiff instanceof ConstantExpression);

        Expression unaryField = new UnaryArithmeticExpression(ArithmeticOperator.SUB, left);
        Expression foldedUnary = (Expression) PartialEvaluator.evaluate(unaryField);
        assertEquals("-(x)", foldedUnary.toString());
        assertInstanceOf(UnaryArithmeticExpression.class, foldedUnary);
    }

    @Test
    public void testNoFolding2() {
        Expression left = new FieldNameExpression("x");
        Expression right = new FieldNameExpression("y");

        Expression negXPlusY = new BinaryArithmeticExpression(
                new UnaryArithmeticExpression(ArithmeticOperator.SUB, left),
                ArithmeticOperator.ADD,
                right
        );
        Expression result = (Expression) PartialEvaluator.evaluate(negXPlusY);

        assertNotEquals("0", result.toString());
        assertInstanceOf(BinaryArithmeticExpression.class, result);
    }

    @Test
    public void testStringFoldingBinary() {
        Expression left = new ConstantExpression(new StringConstant("a"));
        Expression right = new ConstantExpression(new StringConstant("b"));

        Expression xPlusY = new BinaryArithmeticExpression(left, ArithmeticOperator.ADD, right);

        assertThrowsExactly(RuntimeExecutionException.class, () -> PartialEvaluator.evaluate(xPlusY));
    }

    @Test
    public void testStringFoldingUnary() {
        Expression x = new ConstantExpression(new StringConstant("a"));
        Expression y = new ConstantExpression(new StringConstant("b"));

        Expression negX = new UnaryArithmeticExpression(
                ArithmeticOperator.SUB,
                x
        );

        assertThrowsExactly(RuntimeExecutionException.class, () -> PartialEvaluator.evaluate(negX));
    }

    @Test
    public void appliesToSchema() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        QueryTestUtils.QueryTestData testData = QueryTestUtils.initializeOneFullTable(tmpDir);

        Schema schema = testData.layouts().getFirst().getSchema();

        Expression e1 = new FieldNameExpression("t1_intField1");
        assertTrue(e1.appliesTo(schema));

        Expression e2 = new FieldNameExpression("garbage_field");
        assertFalse(e2.appliesTo(schema));

        Expression e3 = new ConstantExpression(new IntConstant(42));
        assertTrue(e3.appliesTo(schema));

        Expression complexSuccess = new BinaryArithmeticExpression(
                new BinaryArithmeticExpression(
                        new FieldNameExpression("t1_intField1"),
                        ArithmeticOperator.ADD,
                        new FieldNameExpression("t1_intField2")
                ),
                ArithmeticOperator.MUL,
                new ConstantExpression(new IntConstant(10))
        );
        assertTrue(complexSuccess.appliesTo(schema));

        Expression complexFailure = new BinaryArithmeticExpression(
                new FieldNameExpression("t1_intField1"),
                ArithmeticOperator.ADD,
                new FieldNameExpression("hidden_field")
        );
        assertFalse(complexFailure.appliesTo(schema));
    }

    @Test
    public void testComplexExpressionType() {
        Schema schema = new Schema();
        schema.addIntField("int", false);

        Expression e = new BinaryArithmeticExpression(
                new BinaryArithmeticExpression(
                        new ConstantExpression(new IntConstant(5)),
                        ArithmeticOperator.SUB,
                        new FieldNameExpression("int")
                ),
                ArithmeticOperator.DIV,
                new FieldNameExpression("int")
        );

        assertEquals(DatabaseType.INT, e.type(schema));
    }

    @Test
    public void testExpressionLength() {
        Schema schema = new Schema();
        schema.addStringField("s1", 100, false);
        schema.addStringField("s2", 200, false);

        Expression e = new BinaryArithmeticExpression(
                new BinaryArithmeticExpression(
                        new ConstantExpression(new StringConstant("a".repeat(10000))),
                        ArithmeticOperator.SUB,
                        new FieldNameExpression("s1")
                ),
                ArithmeticOperator.DIV,
                new FieldNameExpression("s2")
        );

        assertEquals(10000, e.length(schema));
    }

    @Test
    public void testExpressionNullabilityFieldName() {
        Schema schema = new Schema();
        schema.addIntField("i1", true);
        schema.addIntField("i2", false);

        Expression e = new BinaryArithmeticExpression(
                new BinaryArithmeticExpression(
                        new ConstantExpression(new IntConstant(100)),
                        ArithmeticOperator.SUB,
                        new FieldNameExpression("i1")
                ),
                ArithmeticOperator.DIV,
                new FieldNameExpression("i2")
        );

        assertTrue(e.isNullable(schema));
    }

    @Test
    public void testExpressionNullabilityConstant() {
        Schema schema = new Schema();
        schema.addIntField("i1", true);
        schema.addIntField("i2", false);

        Expression e = new BinaryArithmeticExpression(
                new BinaryArithmeticExpression(
                        new ConstantExpression(NullConstant.INSTANCE),
                        ArithmeticOperator.SUB,
                        new FieldNameExpression("i2")
                ),
                ArithmeticOperator.DIV,
                new FieldNameExpression("i2")
        );

        assertTrue(e.isNullable(schema));
    }

    @Test
    public void testNullValueType() {
        Schema schema = new Schema();
        schema.addIntField("int", true);

        Expression e = new ConstantExpression(NullConstant.INSTANCE);

        assertEquals(DatabaseType.NULL, e.type(schema));
    }
}
