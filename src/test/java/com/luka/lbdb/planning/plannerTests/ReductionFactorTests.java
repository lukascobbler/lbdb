package com.luka.lbdb.planning.plannerTests;

import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.planning.planner.ReductionFactorCalculator;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class ReductionFactorTests {
    private static final double NO_ROWS_MATCH = Double.MAX_VALUE;
    private static final double ALL_ROWS_MATCH = 1.0;

    @Mock
    Plan<Scan> planMock;

    private Expression expressionFive;
    private Expression expressionTen;
    private Expression expressionNull;

    @BeforeEach
    public void setUp() {
        Constant intConstantFive = new IntConstant(5);
        Constant intConstantTen = new IntConstant(10);

        Constant nullConstant = NullConstant.INSTANCE;

        expressionFive = new ConstantExpression(intConstantFive);
        expressionTen = new ConstantExpression(intConstantTen);
        expressionNull = new ConstantExpression(nullConstant);
    }

    @Test
    public void calculateReductionFactorZeroTotalRowsReturnsAllRowsMatch() {
        lenient().when(planMock.recordsOutput()).thenReturn(0);

        Term term = new Term(expressionFive, TermOperator.EQUALS, expressionTen);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(ALL_ROWS_MATCH, result);
    }

    @Test
    public void calculateReductionFactorNullIsNullReturnsAllRowsMatch() {
        lenient().when(planMock.recordsOutput()).thenReturn(100);

        Term term = new Term(expressionNull, TermOperator.IS, expressionNull);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(ALL_ROWS_MATCH, result);
    }

    @Test
    public void calculateReductionFactorNullIsConstantReturnsNoRowsMatch() {
        lenient().when(planMock.recordsOutput()).thenReturn(100);

        Term term = new Term(expressionNull, TermOperator.IS, expressionFive);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(NO_ROWS_MATCH, result);
    }

    @Test
    public void calculateReductionFactorNullEqualsConstantReturnsNoRowsMatch() {
        lenient().when(planMock.recordsOutput()).thenReturn(100);

        Term term = new Term(expressionNull, TermOperator.EQUALS, expressionFive);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(NO_ROWS_MATCH, result);
    }

    @Test
    public void calculateReductionFactorConstantEqualsSameConstantReturnsAllRowsMatch() {
        lenient().when(planMock.recordsOutput()).thenReturn(100);

        Term term = new Term(expressionFive, TermOperator.EQUALS, expressionFive);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(ALL_ROWS_MATCH, result);
    }

    @Test
    public void calculateReductionFactorConstantEqualsDifferentConstantReturnsNoRowsMatch() {
        lenient().when(planMock.recordsOutput()).thenReturn(100);

        Term term = new Term(expressionFive, TermOperator.EQUALS, expressionTen);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(NO_ROWS_MATCH, result);
    }

    @Test
    public void calculateReductionFactorFieldIsNullReturnsNullFactor() {
        String fieldName = "fieldA";
        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(fieldName)).thenReturn(20);

        Expression fieldExpression = new FieldNameExpression(fieldName);
        Term term = new Term(fieldExpression, TermOperator.IS, expressionNull);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(5.0, result);
    }

    @Test
    public void calculateReductionFactorFieldEqualsNullReturnsNoRowsMatch() {
        String fieldName = "fieldA";
        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(fieldName)).thenReturn(20);
        lenient().when(planMock.distinctValues(fieldName)).thenReturn(5);

        Expression fieldExpression = new FieldNameExpression(fieldName);
        Term term = new Term(fieldExpression, TermOperator.EQUALS, expressionNull);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(NO_ROWS_MATCH, result);
    }

    @Test
    public void calculateReductionFactorFieldEqualsConstantReturnsDistinctFactor() {
        String fieldName = "fieldA";
        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(fieldName)).thenReturn(10);
        lenient().when(planMock.distinctValues(fieldName)).thenReturn(9);

        Expression fieldExpression = new FieldNameExpression(fieldName);
        Term term = new Term(fieldExpression, TermOperator.EQUALS, expressionFive);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(10.0, result);
    }

    @Test
    public void calculateReductionFactorFieldGreaterThanConstantReturnsInequalityFactor() {
        String fieldName = "fieldA";
        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(fieldName)).thenReturn(10);
        lenient().when(planMock.distinctValues(fieldName)).thenReturn(9);

        Expression fieldExpression = new FieldNameExpression(fieldName);
        Term term = new Term(fieldExpression, TermOperator.GREATER_THAN, expressionFive);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(100.0 / 30.0, result, 0.0001);
    }

    @Test
    public void calculateReductionFactorFieldNotEqualsConstantReturnsComplexFactor() {
        String fieldName = "fieldA";
        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(fieldName)).thenReturn(10);
        lenient().when(planMock.distinctValues(fieldName)).thenReturn(9);

        Expression fieldExpression = new FieldNameExpression(fieldName);
        Term term = new Term(fieldExpression, TermOperator.NOT_EQUALS, expressionFive);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(100.0 / 9.0, result, 0.0001);
    }

    @Test
    public void calculateReductionFactorFieldEqualsFieldReturnsMaxDistinctFactor() {
        String leftFieldName = "fieldA";
        String rightFieldName = "fieldB";

        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(leftFieldName)).thenReturn(10);
        lenient().when(planMock.nullValues(rightFieldName)).thenReturn(20);
        lenient().when(planMock.distinctValues(leftFieldName)).thenReturn(5);
        lenient().when(planMock.distinctValues(rightFieldName)).thenReturn(8);

        Expression leftExpression = new FieldNameExpression(leftFieldName);
        Expression rightExpression = new FieldNameExpression(rightFieldName);
        Term term = new Term(leftExpression, TermOperator.EQUALS, rightExpression);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(10.0, result);
    }

    @Test
    public void calculateReductionFactorFieldGreaterThanFieldReturnsInequalityFactor() {
        String leftFieldName = "fieldA";
        String rightFieldName = "fieldB";

        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(leftFieldName)).thenReturn(10);
        lenient().when(planMock.nullValues(rightFieldName)).thenReturn(20);
        lenient().when(planMock.distinctValues(leftFieldName)).thenReturn(5);
        lenient().when(planMock.distinctValues(rightFieldName)).thenReturn(8);

        Expression leftExpression = new FieldNameExpression(leftFieldName);
        Expression rightExpression = new FieldNameExpression(rightFieldName);
        Term term = new Term(leftExpression, TermOperator.GREATER_THAN, rightExpression);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(100.0 / (80.0 / 3.0), result, 0.0001);
    }

    @Test
    public void calculateReductionFactorComplexThreeFieldsEqualsReturnsComplexFactor() {
        String fieldOne = "fieldA";
        String fieldTwo = "fieldB";
        String fieldThree = "fieldC";

        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(fieldOne)).thenReturn(10);
        lenient().when(planMock.nullValues(fieldTwo)).thenReturn(15);
        lenient().when(planMock.nullValues(fieldThree)).thenReturn(20);

        Expression leftExpression = new FieldNameExpression(fieldOne);
        Expression rightExpression = new BinaryArithmeticExpression(
                new FieldNameExpression(fieldTwo),
                ArithmeticOperator.ADD,
                new FieldNameExpression(fieldThree)
        );

        Term term = new Term(leftExpression, TermOperator.EQUALS, rightExpression);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(12.5, result);
    }

    @Test
    public void calculateReductionFactorComplexThreeFieldsGreaterThanReturnsInequalityFactor() {
        String fieldOne = "fieldA";
        String fieldTwo = "fieldB";
        String fieldThree = "fieldC";

        lenient().when(planMock.recordsOutput()).thenReturn(100);
        lenient().when(planMock.nullValues(fieldOne)).thenReturn(5);
        lenient().when(planMock.nullValues(fieldTwo)).thenReturn(0);
        lenient().when(planMock.nullValues(fieldThree)).thenReturn(10);

        Expression leftExpression = new BinaryArithmeticExpression(
                new FieldNameExpression(fieldOne),
                ArithmeticOperator.ADD,
                new FieldNameExpression(fieldTwo)
        );
        Expression rightExpression = new FieldNameExpression(fieldThree);

        Term term = new Term(leftExpression, TermOperator.GREATER_THAN, rightExpression);

        double result = ReductionFactorCalculator.calculateTermReductionFactor(term, planMock);

        assertEquals(100.0 / 30.0, result, 0.0001);
    }
}
