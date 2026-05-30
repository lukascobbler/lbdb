package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.planning.planner.PartialEvaluator;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.expression.*;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParsePredicateTests {
    private Predicate parse(String query) {
        ParserContext ctx = new ParserContext(query);
        return new ParsePredicate(ctx).parse();
    }

    @Test
    public void testSimpleEquality() {
        Predicate pred = parse("age = 25");
        Term term = pred.getTerms().getFirst();

        assertEquals(TermOperator.EQUALS, term.getTermOperator());
        assertEquals("age", assertInstanceOf(FieldNameExpression.class, term.getLhs()).fieldName());
        assertEquals(25, assertInstanceOf(ConstantExpression.class, term.getRhs()).constant().asInt());
    }

    @Test
    public void testNotEqualOperator() {
        Predicate pred = parse("status != 'deleted'");
        Term term = pred.getTerms().getFirst();

        assertEquals(TermOperator.NOT_EQUALS, term.getTermOperator());
        assertEquals("status", assertInstanceOf(FieldNameExpression.class, term.getLhs()).fieldName());
        assertEquals("deleted", assertInstanceOf(ConstantExpression.class, term.getRhs()).constant().asString());
    }

    @Test
    public void testGreaterAndLessOperators() {
        Term gt = parse("balance > 0").getTerms().getFirst();
        assertEquals(TermOperator.GREATER_THAN, gt.getTermOperator());

        Term lt = parse("price < 100").getTerms().getFirst();
        assertEquals(TermOperator.LESS_THAN, lt.getTermOperator());
    }

    @Test
    public void testGreaterEqualAndLessEqualOperators() {
        Term ge = parse("score >= 50").getTerms().getFirst();
        assertEquals(TermOperator.GREATER_OR_EQUAL, ge.getTermOperator());

        Term le = parse("rank <= 10").getTerms().getFirst();
        assertEquals(TermOperator.LESS_OR_EQUAL, le.getTermOperator());
    }

    @Test
    public void testIsOperatorWithNull() {
        Predicate pred = parse("middle_name IS NULL");
        Term term = pred.getTerms().getFirst();

        assertEquals(TermOperator.IS, term.getTermOperator());
        assertInstanceOf(FieldNameExpression.class, term.getLhs());
        assertEquals(NullConstant.INSTANCE, assertInstanceOf(ConstantExpression.class, term.getRhs()).constant());
    }

    @Test
    public void testArithmeticInPredicate() {
        // (x + 5) * 2 != y / 2
        Predicate pred = parse("(x + 5) * 2 != y / 2");
        Term term = pred.getTerms().getFirst();

        assertEquals(TermOperator.NOT_EQUALS, term.getTermOperator());
        assertInstanceOf(BinaryArithmeticExpression.class, term.getLhs());
        assertInstanceOf(BinaryArithmeticExpression.class, term.getRhs());
    }

    @Test
    public void testUnaryOperatorInPredicate() {
        Predicate pred = parse("-val = 10");
        Term term = pred.getTerms().getFirst();

        UnaryArithmeticExpression left = assertInstanceOf(UnaryArithmeticExpression.class, term.getLhs());
        assertEquals(ArithmeticOperator.SUB, left.op());
    }

    @Test
    public void testBasicAndConjunction() {
        Predicate pred = parse("a = 1 AND b = 2");
        List<Term> terms = pred.getTerms();

        assertEquals(2, terms.size());
        assertEquals("a", assertInstanceOf(FieldNameExpression.class, terms.get(0).getLhs()).fieldName());
        assertEquals("b", assertInstanceOf(FieldNameExpression.class, terms.get(1).getLhs()).fieldName());
    }

    @Test
    public void testMultipleAndConjunctions() {
        Predicate pred = parse("a = 1 AND b = 2 AND c = 3 AND d = 4");
        assertEquals(4, pred.getTerms().size());
    }

    @Test
    public void testAndWithComplexExpressions() {
        Predicate pred = parse("x + 1 > 10 AND y IS NULL");
        List<Term> terms = pred.getTerms();

        assertEquals(2, terms.size());
        assertEquals(TermOperator.GREATER_THAN, terms.get(0).getTermOperator());
        assertEquals(TermOperator.IS, terms.get(1).getTermOperator());
    }

    @Test
    public void singleTermPredicate() {
        Predicate pred = parse("x > 5");
        List<Term> terms = pred.getTerms();

        assertEquals(1, terms.size());
        assertEquals(TermOperator.GREATER_THAN, terms.getFirst().getTermOperator());
    }

    @Test
    public void testMissingRightHandSideThrowsException() {
        assertThrows(ParsingException.class, () -> parse("a ="));
    }

    @Test
    public void testMissingOperatorThrowsException() {
        assertThrows(ParsingException.class, () -> parse("a 5"));
    }

    @Test
    public void testDanglingAndThrowsException() {
        assertThrows(ParsingException.class, () -> parse("a = 1 AND"));
    }

    @Test
    public void testInvalidComparisonOperator() {
        assertThrows(ParsingException.class, () -> parse("a + 5"));
    }

    @Test
    public void testPredicateWithOnlyWildcardFails() {
        assertThrows(ParsingException.class, () -> parse("*"));
    }

    @Test
    public void testPrecedenceOfAnd() {
        Predicate pred = parse("a = 1 AND b = 2 + 3");
        Term secondTerm = pred.getTerms().get(1);

        BinaryArithmeticExpression rhs = assertInstanceOf(BinaryArithmeticExpression.class, secondTerm.getRhs());
        assertEquals(ArithmeticOperator.ADD, rhs.op());
    }

    @Test
    public void testPredicateFolding() {
        Predicate pred = parse("a = 1 + 5 - 6 AND b >= 2 + 3 - c AND d IS 14 + (-14)");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred); 

        assertEquals("a = 0 AND b >= (5 - c) AND d IS 0", foldedPredicate.toString());
    }

    @Test
    public void testPredicateFoldingComplex() {
        Predicate pred = parse(
                "score * 1 = 100 AND " +
                        "status = 'active' AND " +
                        "2 * 5 > val - 0 AND " +
                        "x - x != 1"
        );
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);

        assertEquals("score = 100 AND status = 'active' AND 10 > val", foldedPredicate.toString());
    }

    @Test
    public void testArithmeticIdentityFolding() {
        Predicate pred = parse("a + 0 = 5 AND 1 * b = 10 AND c - 0 != 0");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("a = 5 AND b = 10 AND c != 0", foldedPredicate.toString());
    }

    @Test
    public void testConstantFoldingRecursion() {
        Predicate pred = parse("(1 + 2) * (10 / 5) = 6");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("", foldedPredicate.toString());
    }

    @Test
    public void testSignReductionFolding() {
        Predicate pred = parse("a + (-b) = 0 AND x - (-y) = 10");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("(a - b) = 0 AND (x + y) = 10", foldedPredicate.toString());
    }

    @Test
    public void testZeroFoldingIdentities() {
        Predicate pred = parse("(a + b) * 0 = 0 AND x - x = 5 AND -y + y = 0");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("0 = 5", foldedPredicate.toString());
    }

    @Test
    public void testUnaryCancellationAndRedundancy() {
        Predicate pred = parse("--x = +y AND +(-z) < 0");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("x = y AND -(z) < 0", foldedPredicate.toString());
    }

    @Test
    public void testMultiplicationByNegativeOne() {
        Predicate pred = parse("x * -1 = -5");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("-(x) = -5", foldedPredicate.toString());
    }

    @Test
    public void testNestedPartialFolding() {
        Predicate pred = parse("(a + (5 - 5)) * 1 = b + (-0)");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("a = b", foldedPredicate.toString());
    }

    @Test
    public void testComplexMixedPredicateFolding() {
        Predicate pred = parse(
                "((10 + 20) * 2) > (val * 1) AND " +
                        "--(counter) = (other + 0) AND " +
                        "status IS 'active' AND " +
                        "0 = (x * 0)"
        );
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("60 > val AND counter = other AND status IS 'active'", foldedPredicate.toString());
    }

    @Test
    public void testDeepUnaryFolding() {
        Predicate pred = parse("-(-(-(x))) = -10");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("-(x) = -10", foldedPredicate.toString());
    }

    @Test
    public void testFieldEqualityFolding() {
        Predicate pred = parse("(a * b) - (a * b) = 0");
        Predicate foldedPredicate = (Predicate) PartialEvaluator.evaluate(pred);
        assertEquals("", foldedPredicate.toString());
    }
}
