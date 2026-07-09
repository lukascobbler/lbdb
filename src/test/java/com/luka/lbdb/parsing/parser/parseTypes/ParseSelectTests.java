package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.parsing.statement.SelectStatement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParseSelectTests {
    private SelectStatement parse(String query) {
        ParserContext ctx = new ParserContext(query);
        return new ParseSelect(ctx).parse();
    }

    @Test
    public void parsePlain() {
        String query = "SELECT a, b FROM c";
        assertEquals(query + ";", parse(query).toString());
    }

    @Test
    public void parseComplexPlain() {
        String query = "SELECT a, b, c FROM d, e WHERE a < 3";
        assertEquals(query + ";", parse(query).toString());
    }

    @Test
    public void parseExpressioned() {
        String query = "SELECT a * 5 - 14, b - 3 / 7, c FROM t WHERE a < 3";
        String expected = "SELECT ((a * 5) - 14), (b - (3 / 7)), c FROM t WHERE a < 3";
        assertEquals(expected + ";", parse(query).toString());
    }

    @Test
    public void parseExpressionedAs() {
        String query = "SELECT a * 5 - 14 AS f1, b - 3 / 7 AS f2, c AS f3, d FROM t";
        String expected = "SELECT ((a * 5) - 14) AS f1, (b - (3 / 7)) AS f2, c AS f3, d FROM t;";
        assertEquals(expected, parse(query).toString());
    }

    @Test
    public void parseExpressionedAsMissingNewFieldName() {
        assertThrows(ParsingException.class, () -> parse(
                "SELECT a * 5 - 14 AS , b - 3 / 7 AS f2, c AS f3, d FROM t"));
    }

    @Test
    public void parseWithJoinSpec() {
        // example from the book
        SelectStatement selectStatement1 = parse(
        "SELECT SName, DName FROM STUDENT, DEPT WHERE MajorId = Did AND GradYear = 2020"
        );
        SelectStatement selectStatement2 = parse(
        "SELECT DName, SName FROM STUDENT JOIN DEPT ON MajorId = Did WHERE GradYear = 2020"
        );

        assertEquals(selectStatement1, selectStatement2);
    }

    @Test
    public void parseWithJoinSpecMultiple() {
        SelectStatement selectStatement1 = parse(
    "SELECT SName, DName FROM STUDENT, DEPT, COURSE WHERE MajorId = Did AND Did = CourseId"
        );
        SelectStatement selectStatement2 = parse(
    "SELECT DName, SName FROM STUDENT JOIN DEPT ON MajorId = Did JOIN COURSE ON Did = CourseId"
        );

        assertEquals(selectStatement1, selectStatement2);
    }

    @Test
    public void parseWithJoinSpecMultipleMixed() {
        SelectStatement selectStatement1 = parse(
                "SELECT SName, DName FROM STUDENT, DEPT, COURSE, ENROLLMENT WHERE MajorId = Did AND Did = CourseId"
        );
        SelectStatement selectStatement2 = parse(
                "SELECT DName, SName FROM STUDENT JOIN DEPT ON MajorId = Did JOIN COURSE ON Did = CourseId, ENROLLMENT"
        );

        assertEquals(selectStatement1, selectStatement2);
    }

    @Test
    public void parseWithJoinSpecFailNoOnKeyword() {
        assertThrows(ParsingException.class, () -> parse(
                "SELECT DName, SName FROM STUDENT JOIN DEPT MajorId = Did WHERE GradYear = 2020"));
    }

    @Test
    public void parseWithJoinSpecFailNoJoinPredicate() {
        assertThrows(ParsingException.class, () -> parse(
                "SELECT DName, SName FROM STUDENT JOIN DEPT ON WHERE GradYear = 2020"));
    }

    @Test
    public void parseUnions() {
        String query = "SELECT a, b FROM c UNION ALL SELECT c, d FROM e";
        String expected = "SELECT a, b FROM c UNION ALL SELECT c, d FROM e;";
        assertEquals(expected, parse(query).toString());
    }

    @Test
    public void parseUnionsFailMissingSecondSelect() {
        assertThrows(ParsingException.class, () -> parse("SELECT a, b FROM c UNION ALL"));
    }

    @Test
    public void parseUnionsFailIncompleteSecondSelect() {
        assertThrows(ParsingException.class, () -> parse("SELECT a, b FROM c UNION ALL SELECT a FROM"));
    }

    @Test
    public void parseUnionsFailMissingFirstSelect() {
        assertThrows(ParsingException.class, () -> parse("UNION ALL SELECT a, b FROM c"));
    }

    @Test
    public void parseSelectWildcard() {
        String query = "SELECT *, b, * FROM c";
        String expected = "SELECT *, b, * FROM c;";
        assertEquals(expected, parse(query).toString());
    }

    @Test
    public void parseSelectWildcardAs() {
        String query = "SELECT * AS a FROM c";
        assertThrows(ParsingException.class, () -> parse(query).toString());
    }

    @Test
    public void parseMultipleJoinSpecs() {
        String query = "SELECT a FROM b JOIN c ON bid = cid, d JOIN e ON did = eid";
        String expected = "SELECT a FROM b, c, d, e WHERE bid = cid AND did = eid;";
        assertEquals(expected, parse(query).toString());
    }

    @Test
    public void parseSelectWithoutWhere() {
        String query = "SELECT a FROM b";
        String expected = "SELECT a FROM b;";
        assertEquals(expected, parse(query).toString());
    }

    @Test
    public void parseMultipleUnions() {
        String query = "SELECT a FROM b UNION ALL SELECT c FROM d UNION ALL SELECT e FROM f";
        String expected = "SELECT a FROM b UNION ALL SELECT c FROM d UNION ALL SELECT e FROM f;";
        assertEquals(expected, parse(query).toString());
    }

    @Test
    public void parseSuccessConstantSelect() {
        String query = "SELECT 1, 2, 3, 4";
        String expected = "SELECT 1, 2, 3, 4;";
        assertEquals(expected, parse(query).toString());
    }

    @Test
    public void parseFailMissingSelectKeyword() {
        assertThrows(ParsingException.class, () -> parse("a, b FROM c"));
    }

    @Test
    public void parseFailDanglingCommaInSelect() {
        assertThrows(ParsingException.class, () -> parse("SELECT a, FROM b"));
    }

    @Test
    public void parseFailDanglingCommaInFrom() {
        assertThrows(ParsingException.class, () -> parse("SELECT a FROM b, "));
    }

    @Test
    public void parseFailEmptyWhereClause() {
        assertThrows(ParsingException.class, () -> parse("SELECT a FROM b WHERE"));
    }

    @Test
    public void parseFailDoubleUnionKeywords() {
        assertThrows(ParsingException.class, () -> parse("SELECT a FROM b UNION ALL UNION ALL SELECT c FROM d"));
    }

    @Test
    public void parseFailAsKeywordWithoutExpression() {
        assertThrows(ParsingException.class, () -> parse("SELECT AS f1 FROM b"));
    }

    @Test
    public void parseSelectWithPredicateProjection() {
        String query = "SELECT a > 5 AND b = 3 AS p1 FROM t";
        String expected = "SELECT a > 5 AND b = 3 AS p1 FROM t;";
        assertEquals(expected, parse(query).toString());
    }
}
