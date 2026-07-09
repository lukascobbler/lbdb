package com.luka.lbdb.planning.plannerTests.runTests;

import com.luka.lbdb.planning.PlanTestUtils;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DeleteStatementRunTests {
    @Test
    public void testDeletionSuccessfulNoPredicate() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testDataThreeTablesTransaction = PlanTestUtils.initializeThreeFullTables(tmpDir);

        String deleteQueryNoPredicate = "DELETE FROM table1;";

        int totalRowsAffected = PlanTestUtils.executeUpdate(testDataThreeTablesTransaction, deleteQueryNoPredicate);

        assertEquals(250, totalRowsAffected);

        String selectQueryTable = "SELECT * FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testDataThreeTablesTransaction, selectQueryTable);

        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            for (int i = 0; i < 250; i++) {
                assertFalse(s.next());
            }
        }
    }

    @Test
    public void testDeletionSuccessfulPredicate() throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testDataThreeTablesTransaction = PlanTestUtils.initializeThreeFullTables(tmpDir);

        String deleteQueryNoPredicate = "DELETE FROM table1 WHERE samebool = true;";

        int totalRowsAffected = PlanTestUtils.executeUpdate(testDataThreeTablesTransaction, deleteQueryNoPredicate);

        assertEquals(50, totalRowsAffected);

        String selectQueryTable = "SELECT * FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testDataThreeTablesTransaction, selectQueryTable);

        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            for (int i = 0; i < 200; i++) {
                assertTrue(s.next());
                assertFalse(s.getValue("samebool").asBoolean());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testDeleteRollback(boolean undoOnlyRecovery) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();

        LBDBSettings settings = new LBDBSettings();
        settings.UNDO_ONLY_RECOVERY = undoOnlyRecovery;

        var testDataThreeTablesTransaction = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);
        testDataThreeTablesTransaction.tx().commit();

        var testDataThreeTablesDeletionTransaction = PlanTestUtils.newTransaction(testDataThreeTablesTransaction);

        String deleteQueryNoPredicate = "DELETE FROM table1;";

        int totalRowsAffected =
                PlanTestUtils.executeUpdate(testDataThreeTablesDeletionTransaction, deleteQueryNoPredicate);

        assertEquals(250, totalRowsAffected);

        String selectQueryTable = "SELECT * FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testDataThreeTablesDeletionTransaction, selectQueryTable);

        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            for (int i = 0; i < 250; i++) {
                assertFalse(s.next());
            }
        }

        testDataThreeTablesDeletionTransaction.tx().rollback();

        var testDataThreeTablesAfterRollbackTransaction =
                PlanTestUtils.newTransaction(testDataThreeTablesDeletionTransaction);

        Plan<Scan> queryPlanAfterRollback =
                PlanTestUtils.createQueryPlan(testDataThreeTablesAfterRollbackTransaction, selectQueryTable);

        try (Scan s = queryPlanAfterRollback.open()) {
            s.beforeFirst();
            for (int i = 0; i < 250; i++) {
                assertTrue(s.next());
                assertEquals(i + 50, s.getValue("sameint").asInt());
            }
            assertFalse(s.next());
        }
    }
}
