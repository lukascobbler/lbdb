package com.luka.lbdb.planning.plannerTests.checkTests;

import com.luka.lbdb.planning.PlanTestUtils;
import com.luka.lbdb.planning.exceptions.PlanValidationException;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

public class UpdateStatementCheckTests {
    @Test
    public void testSuccessfulNoPredicate() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = 5;";

        assertDoesNotThrow(
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testSuccessfulPredicate() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = 5; WHERE sameint = 1834;";

        assertDoesNotThrow(
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testSuccessfulPredicateMultiple() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET " +
                "t1_intfield1 = 5, sameint = 18, samebool = FALSE, t1_stringField3 = 'abcd'" +
                "WHERE sameint = 1834;";

        assertDoesNotThrow(
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testSetNullOnNullableField() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield3 = NULL;";

        assertDoesNotThrow(
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testExpressionWithNullOnNullableField() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield3 = NULL + 2;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testSetNullOnNonNullableField() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield2 = NULL;";

        assertThrowsExactly(PlanValidationException.class, 
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testSetValueOnNullableField() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield3 = 123;";

        assertDoesNotThrow(
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testFailOnNoExistingField() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield17 = 5;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testFailOnNoExistingFieldInPredicate() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = 5 WHERE sameintaaa = 1834;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testSuccessNullInPredicate() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = 5 WHERE t1_intfield3 IS NULL;";

        assertDoesNotThrow(
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testFailNoTable() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table5 SET t1_intfield1 = 5;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testFailWrongType() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = 'a';";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testFailWrongTypeInPredicate() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = 5 WHERE t1_boolfield2 = 123;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testConstantZeroDivision() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = 10 / 0;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testExpressionWithExistingField() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = sameint + 5;";

        assertDoesNotThrow(
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testFailOnNoExistingFieldInExpression() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = nonexistent_field + 5;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }

    @Test
    public void testFailWrongTypeFromFieldExpression() throws Exception {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeEmptyTables(tmpDir);

        String query = "UPDATE table1 SET t1_intfield1 = t1_stringField3;";

        assertThrowsExactly(PlanValidationException.class,
                () -> PlanTestUtils.checkUpdateStatement(testData, query, "executeUpdateValidated"));
    }
}
