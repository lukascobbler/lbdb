package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.querying.virtualEntities.Evaluatable;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParseEvaluatableTests {
    private Evaluatable parse(String query) {
        ParserContext ctx = new ParserContext(query);
        return new ParseEvaluatable(ctx).parse();
    }

    @Test
    public void testParseSimpleExpression() {
        Evaluatable result = parse("a + 5");
        assertInstanceOf(Expression.class, result);
        assertEquals("(a + 5)", result.toString());
    }

    @Test
    public void testParseSimplePredicate() {
        Evaluatable result = parse("a > 5");
        assertInstanceOf(Predicate.class, result);
        assertEquals("a > 5", result.toString());
    }

    @Test
    public void testParseCompoundPredicate() {
        Evaluatable result = parse("a > 5 AND b = 3");
        assertInstanceOf(Predicate.class, result);
        assertEquals("a > 5 AND b = 3", result.toString());
    }

    @Test
    public void testParsePredicateWithIsNotNull() {
        Evaluatable result = parse("a IS NOT NULL AND b = 3");
        assertInstanceOf(Predicate.class, result);
        assertEquals("a IS NOT NULL AND b = 3", result.toString());
    }

    @Test
    public void testParsePredicateWithIsNull() {
        Evaluatable result = parse("a IS NULL AND b = 3");
        assertInstanceOf(Predicate.class, result);
        assertEquals("a IS NULL AND b = 3", result.toString());
    }
}
