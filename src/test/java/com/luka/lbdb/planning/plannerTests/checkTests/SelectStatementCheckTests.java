package com.luka.lbdb.planning.plannerTests.checkTests;

import com.luka.lbdb.planning.PlanTestUtils;
import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.statement.SelectStatement;
import com.luka.lbdb.parsing.statement.select.TableInfo;
import com.luka.lbdb.planning.exceptions.*;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.expression.ArithmeticOperator;
import com.luka.lbdb.querying.virtualEntities.expression.BinaryArithmeticExpression;
import com.luka.lbdb.querying.virtualEntities.expression.ConstantExpression;
import com.luka.lbdb.querying.virtualEntities.expression.FieldNameExpression;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class SelectStatementCheckTests {
    /// Helper record to rename all fields in a schema.
    private record SchemaRename(Schema schema, String qualifier) { }

    /// Generates a schema that has qualified fieldName names from every sub-schema.
    /// Assumes no uniqueness test will fail and should be used only for checks
    /// that require a valid schema.
    ///
    /// @return The unified schema object.
    private Schema generateUnifiedQualifiedUniqueSchema(SchemaRename... schemas) {
        Schema unifiedQualifiedSchema = new Schema();

        for (SchemaRename s : schemas) {
            for (String field : s.schema.getFields()) {
                unifiedQualifiedSchema.addField(s.qualifier + "." + field,
                        s.schema.type(field), s.schema.runtimeLength(field), s.schema.isNullable(field));
            }
        }

        return unifiedQualifiedSchema;
    }

    @Test
    public void testSimpleSuccess() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameInt FROM table1;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testNoTable() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameInt FROM t;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Table not found: t", ex.getMessage());
    }

    @Test
    public void testNoTableAliasWildcard() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT a.* FROM table1;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Unknown table alias: a", ex.getMessage());
    }

    @Test
    public void testNoField() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT a FROM table1;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Field 'a' does not exist (SELECT clause)", ex.getMessage());
    }

    @Test
    public void testWildcardExpandSuccess1() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT * FROM table1;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));

        Function<Integer, String> getName = (Integer pos) -> {
            assertNotNull(selectStatement);
            return selectStatement
                    .unionizedSelections().getFirst().projectionFields().get(pos).name();
        };

        Function<Integer, String> getExpressionString = (Integer pos) -> {
            assertNotNull(selectStatement);
            return selectStatement
                    .unionizedSelections().getFirst().projectionFields().get(pos).evaluatable().toString();
        };

        assertNotNull(selectStatement);
        assertEquals(12, selectStatement.unionizedSelections().getFirst().projectionFields().size());
        assertEquals("t1_intfield1", getName.apply(0));
        assertEquals("table1.t1_intfield1", getExpressionString.apply(0));
        assertEquals("t1_intfield2", getName.apply(1));
        assertEquals("table1.t1_intfield2", getExpressionString.apply(1));
        assertEquals("t1_intfield3", getName.apply(2));
        assertEquals("table1.t1_intfield3", getExpressionString.apply(2));
        assertEquals("sameint", getName.apply(3));
        assertEquals("table1.sameint", getExpressionString.apply(3));
        assertEquals("t1_stringfield1", getName.apply(4));
        assertEquals("table1.t1_stringfield1", getExpressionString.apply(4));
        assertEquals("t1_stringfield2", getName.apply(5));
        assertEquals("table1.t1_stringfield2", getExpressionString.apply(5));
        assertEquals("t1_stringfield3", getName.apply(6));
        assertEquals("table1.t1_stringfield3", getExpressionString.apply(6));
        assertEquals("samestring", getName.apply(7));
        assertEquals("table1.samestring", getExpressionString.apply(7));
        assertEquals("t1_boolfield1", getName.apply(8));
        assertEquals("table1.t1_boolfield1", getExpressionString.apply(8));
        assertEquals("t1_boolfield2", getName.apply(9));
        assertEquals("table1.t1_boolfield2", getExpressionString.apply(9));
        assertEquals("t1_boolfield3", getName.apply(10));
        assertEquals("table1.t1_boolfield3", getExpressionString.apply(10));
        assertEquals("samebool", getName.apply(11));
        assertEquals("table1.samebool", getExpressionString.apply(11));
    }

    @Test
    public void testWildcardExpandJoinsOnlyOneTableExpanded() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT table1.* FROM table1, table2;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));

        Function<Integer, String> getName = (Integer pos) -> {
            assertNotNull(selectStatement);
            return selectStatement
                    .unionizedSelections().getFirst().projectionFields().get(pos).name();
        };

        Function<Integer, String> getExpressionString = (Integer pos) -> {
            assertNotNull(selectStatement);
            return selectStatement
                    .unionizedSelections().getFirst().projectionFields().get(pos).evaluatable().toString();
        };

        assertNotNull(selectStatement);
        assertEquals(12, selectStatement.unionizedSelections().getFirst().projectionFields().size());
        assertEquals("t1_intfield1", getName.apply(0));
        assertEquals("table1.t1_intfield1", getExpressionString.apply(0));
        assertEquals("t1_intfield2", getName.apply(1));
        assertEquals("table1.t1_intfield2", getExpressionString.apply(1));
        assertEquals("t1_intfield3", getName.apply(2));
        assertEquals("table1.t1_intfield3", getExpressionString.apply(2));
        assertEquals("sameint", getName.apply(3));
        assertEquals("table1.sameint", getExpressionString.apply(3));
        assertEquals("t1_stringfield1", getName.apply(4));
        assertEquals("table1.t1_stringfield1", getExpressionString.apply(4));
        assertEquals("t1_stringfield2", getName.apply(5));
        assertEquals("table1.t1_stringfield2", getExpressionString.apply(5));
        assertEquals("t1_stringfield3", getName.apply(6));
        assertEquals("table1.t1_stringfield3", getExpressionString.apply(6));
        assertEquals("samestring", getName.apply(7));
        assertEquals("table1.samestring", getExpressionString.apply(7));
        assertEquals("t1_boolfield1", getName.apply(8));
        assertEquals("table1.t1_boolfield1", getExpressionString.apply(8));
        assertEquals("t1_boolfield2", getName.apply(9));
        assertEquals("table1.t1_boolfield2", getExpressionString.apply(9));
        assertEquals("t1_boolfield3", getName.apply(10));
        assertEquals("table1.t1_boolfield3", getExpressionString.apply(10));
        assertEquals("samebool", getName.apply(11));
        assertEquals("table1.samebool", getExpressionString.apply(11));
    }

    @Test
    public void testWildcardExpandSuccess2() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT *, *, * FROM table1;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));

        assertNotNull(selectStatement);
        assertEquals(36, selectStatement.unionizedSelections().getFirst().projectionFields().size());
    }

    @Test
    public void testWildcardExpandJoinsAs() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT table1.* AS a FROM table1, table2;";

        assertThrowsExactly(ParsingException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testWildcardExpandJoins() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT * FROM table1, table2;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testWildcardExpandJoinsOnSelf() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT * FROM table1, table1;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Duplicate table alias name: table1", ex.getMessage());
    }

    @Test
    public void testWildcardExpandJoinsOnSelfRenamed() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT * FROM table1 t1, table1 t2;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testWildCardUsedInExpression() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 2 * * FROM table1;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Wildcard operator used in an expression.", ex.getMessage());
    }

    @Test
    public void testDuplicateNameUnusedNoError() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_intfield1 FROM table1, table2;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testDuplicateTableTableNameDuplicateFieldNameRawRenamed() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1.sameint FROM table1 t1, table1 t1;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Duplicate table alias name: t1", ex.getMessage());
    }

    @Test
    public void testUseNonExistentFieldJoins() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT x_field FROM table1, table2, table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Field 'x_field' does not exist (SELECT clause)", ex.getMessage());
    }

    @Test
    public void testDuplicateNameRaw() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameint FROM table1, table3;";

        PlanValidationException exception =
                assertThrowsExactly(PlanValidationException.class,
                        () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Ambiguous field: 'sameint' (SELECT clause)", exception.getMessage());
    }

    @Test
    public void testDuplicateNameRawRenamed() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1.sameint, table3.sameint, t1_intfield1, t1.t1_intfield2 FROM table1 t1, table3;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testDuplicateNameRawRenamedIncorrectName() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT table1.sameint FROM table1 t1, table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Field 'table1.sameint' does not exist (SELECT clause)", ex.getMessage());
    }

    @Test
    public void testDuplicateNameRawRenamedDuplicateTableName() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1.t1_intfield1 FROM table1 t1, table3 t1;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Duplicate table alias name: t1", ex.getMessage());
    }

    @Test
    public void testRawRenamedSameTableJoin() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1.t1_intfield1 FROM table1 t1, table1 t2;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testExtensionCorrect() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_intfield1 AS i1, table3.sameint AS i2, t2.samestring AS s1 " +
                "FROM table1 t1, table2 t2, table3;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testDuplicateNameExtended() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_intfield1 AS i, t2_intfield2 AS i FROM table1, table2;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testCorrectFieldTypesSimple() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_intfield1 * 5 FROM table1, table2;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));

        Schema unifiedQualifiedSchema = generateUnifiedQualifiedUniqueSchema(
                new SchemaRename(testData.layouts().get(0).getSchema(), "table1"),
                new SchemaRename(testData.layouts().get(1).getSchema(), "table2")
        );

        Function<Integer, DatabaseType> typeOf = (Integer fieldNum) ->
        {
            assertNotNull(selectStatement);
            return selectStatement
                    .unionizedSelections().getFirst().projectionFields()
                    .get(fieldNum)
                    .evaluatable().type(unifiedQualifiedSchema);
        };

        assertEquals(DatabaseType.INT, typeOf.apply(0));
    }

    @Test
    public void testCorrectFieldTypesComplexExpressions() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT " +
                "t1_intfield1 * 5 - 14 / t2_intfield2 -(+table3.sameint), " +
                "t2_stringfield1, " +
                "t1_boolfield1 " +
                "FROM table1, table2, table3;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));

        Schema unifiedQualifiedSchema = generateUnifiedQualifiedUniqueSchema(
                new SchemaRename(testData.layouts().get(0).getSchema(), "table1"),
                new SchemaRename(testData.layouts().get(1).getSchema(), "table2"),
                new SchemaRename(testData.layouts().get(2).getSchema(), "table3")
        );

        Function<Integer, DatabaseType> typeOf = (Integer fieldNum) ->
        {
            assertNotNull(selectStatement);
            return selectStatement
                    .unionizedSelections().getFirst().projectionFields()
                    .get(fieldNum)
                    .evaluatable().type(unifiedQualifiedSchema);
        };

        assertEquals(DatabaseType.INT, typeOf.apply(0));
        assertEquals(DatabaseType.VARCHAR, typeOf.apply(1));
        assertEquals(DatabaseType.BOOLEAN, typeOf.apply(2));
    }

    @Test
    public void testIncorrectFieldTypesMultipleDifferentConstantsUsedBinary() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 5 + 'a' FROM table1, table2, table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Arithmetic requires numeric types", ex.getMessage());
    }

    @Test
    public void testIncorrectFieldTypesMultipleDifferentConstantsUsedUnary() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT -'a' FROM table1, table2, table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Arithmetic requires numeric types", ex.getMessage());
    }

    @Test
    public void testIncorrectFieldTypesMultipleDifferentFieldnamesUsed1() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT " +
                "t1_intfield1 * 5 - 14 / t2_boolfield2 -(+table3.sameint) " +
                "FROM table1, table2, table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Arithmetic requires numeric types", ex.getMessage());
    }

    @Test
    public void testIncorrectFieldTypesMultipleDifferentFieldnamesUsed2() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT " +
                "t1_intfield1 * 5 - 14 / t2_boolfield2 -(+table3.sameint) " +
                "FROM table1, table2, table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Arithmetic requires numeric types", ex.getMessage());
    }

    @Test
    public void testIncorrectFieldTypesMultipleDifferentFieldnamesUsed3() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT " +
                "t1_stringfield1 * 5 - 14 / t2_intfield2 -(+table1.t1_boolfield2) " +
                "FROM table1, table2, table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Arithmetic requires numeric types", ex.getMessage());
    }

    @Test
    public void testWithPredicateSuccess() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameint FROM table1 WHERE sameint > 5;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertNotNull(selectStatement);
        assertEquals(
                new Predicate(
                        new Term(
                                new FieldNameExpression("sameint", Optional.of("table1")),
                                TermOperator.GREATER_THAN,
                                new ConstantExpression(new IntConstant(5))
                        )
                ),
                selectStatement.unionizedSelections().getFirst().predicate()
        );
    }

    @Test
    public void testWithPredicateUnknownFieldUsed() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameint FROM table1 WHERE a > 5;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Field 'a' does not exist (WHERE clause)", ex.getMessage());
    }

    @Test
    public void testWithPredicateDuplicateFieldRaw() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT table1.sameint FROM table1, table2 WHERE sameint > 5;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Ambiguous field: 'sameint' (WHERE clause)", ex.getMessage());
    }

    @Test
    public void testWithPredicateWrongTypesInPredicate() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameint FROM table1 WHERE t1_boolfield1 > 5;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Different types are compared in the 'WHERE' predicate.", ex.getMessage());
    }

    @Test
    public void testWithPredicateMultipleTablesComparisons() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_stringfield1 FROM table1, table2 t2 WHERE " +
                "t2.sameint > table1.sameint - t2_intfield1 AND " +
                "t2.t2_boolfield1 IS t1_boolfield1;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertNotNull(selectStatement);
        assertEquals(
                new Predicate(
                        new Term(
                                new FieldNameExpression("sameint", Optional.of("t2")),
                                TermOperator.GREATER_THAN,
                                new BinaryArithmeticExpression(
                                        new FieldNameExpression("sameint", Optional.of("table1")),
                                        ArithmeticOperator.SUB,
                                        new FieldNameExpression("t2_intfield1", Optional.of("t2"))
                                )
                        ),
                        new Term(
                                new FieldNameExpression("t2_boolfield1", Optional.of("t2")),
                                TermOperator.IS,
                                new FieldNameExpression("t1_boolfield1", Optional.of("table1"))
                        )
                ),
                selectStatement.unionizedSelections().getFirst().predicate()
        );
    }

    @Test
    public void testWithPredicateFieldExtendedNoMatch() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameint * 5 - t1_intfield1 AS i FROM table1 WHERE i < 5;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Field 'i' does not exist (WHERE clause)", ex.getMessage());
    }

    @Test
    public void testFieldRenamingNoTable() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT a.sameint FROM table1;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("Field 'a.sameint' does not exist (SELECT clause)", ex.getMessage());
    }

    @Test
    public void testUnionCorrect() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_stringfield1 FROM table1 UNION ALL " +
                "SELECT t2_stringfield3 FROM table2;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertNotNull(selectStatement);
        assertEquals(2, selectStatement.unionizedSelections().size());
        assertTrue(selectStatement.unionizedSelections().get(0).tables().contains(new TableInfo("table1")));
        assertTrue(selectStatement.unionizedSelections().get(1).tables().contains(new TableInfo("table2")));
    }

    @Test
    public void testUnionCorrect3Tables() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_intfield1 FROM table1 UNION ALL " +
                "SELECT t2_intfield2 FROM table2 UNION ALL " +
                "SELECT sameint FROM table3;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertNotNull(selectStatement);
        assertEquals(3, selectStatement.unionizedSelections().size());
        assertTrue(selectStatement.unionizedSelections().get(0).tables().contains(new TableInfo("table1")));
        assertTrue(selectStatement.unionizedSelections().get(1).tables().contains(new TableInfo("table2")));
        assertTrue(selectStatement.unionizedSelections().get(2).tables().contains(new TableInfo("table3")));
    }

    @Test
    public void testUnionDifferentNumberOfFieldsFail() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_intfield1, t1_intfield2 FROM table1 UNION ALL " +
                "SELECT t2_intfield2 FROM table2 UNION ALL " +
                "SELECT sameint FROM table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("UNION columns do not match.", ex.getMessage());
    }

    @Test
    public void testUnionDifferentFieldTypesFail() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT t1_intfield1 FROM table1 UNION ALL " +
                "SELECT t2_boolfield2 FROM table2 UNION ALL " +
                "SELECT sameint FROM table3;";

        PlanValidationException ex = assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertEquals("UNION columns do not match.", ex.getMessage());
    }

    @Test
    public void testConstantExpressionsSuccess() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', true, NULL;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testConstantExpressionsPredicateSuccess() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', true, NULL WHERE NULL IS NULL AND 1 = 1;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testConstantExpressionsUnionsSuccess() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', true, NULL UNION ALL SELECT 2, '3', false, NULL;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testConstantExpressionsUnionsDifferentTypes() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', 1, NULL UNION ALL SELECT 2, '3', false, NULL;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testConstantExpressionsUnionsDifferentLength() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', 1, NULL UNION ALL SELECT 1, '3', false;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testConstantExpressionsUnionsWithNonConstant() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', true, 'a' " +
                "UNION ALL SELECT t1_intfield1, t2_stringfield1, t1_boolfield1, t1_stringfield3 FROM table1, table2;";

        assertDoesNotThrow(() -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testConstantExpressionsFieldNameFail() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', true, NULL, a;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testConstantExpressionsFieldNameFailInPredicate() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT 1, '2', true, NULL WHERE a = 1;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
    }

    @Test
    public void testPredicateAsProjectionColumnSuccess() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "SELECT sameint > 5 AND t1_intfield1 = 2 AS p FROM table1;";

        SelectStatement selectStatement = assertDoesNotThrow(
                () -> PlanTestUtils.resultingCheckedSelectStatement(testData, query));
        assertNotNull(selectStatement);

        Schema unifiedQualifiedSchema = generateUnifiedQualifiedUniqueSchema(
                new SchemaRename(testData.layouts().getFirst().getSchema(), "table1")
        );

        assertEquals(DatabaseType.BOOLEAN, selectStatement
                .unionizedSelections().getFirst().projectionFields().getFirst()
                .evaluatable().type(unifiedQualifiedSchema));
    }
}
