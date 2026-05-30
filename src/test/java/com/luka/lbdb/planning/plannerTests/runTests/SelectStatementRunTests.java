package com.luka.lbdb.planning.plannerTests.runTests;

import com.luka.lbdb.planning.PlanTestUtils;
import com.luka.lbdb.planning.plan.Plan;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.constant.*;
import com.luka.lbdb.db.settings.QueryPlannerType;
import com.luka.lbdb.db.settings.LBDBSettings;
import com.luka.lbdb.testUtils.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Named.named;

public class SelectStatementRunTests {
    /// These tests should work across different types of planners.
    private static Stream<Arguments> settingsProvider() {
        LBDBSettings settingsBetterPlanner = new LBDBSettings();
        settingsBetterPlanner.queryPlannerType = QueryPlannerType.BETTER;

        LBDBSettings settingsHeuristicPlanner = new LBDBSettings();
        settingsHeuristicPlanner.queryPlannerType = QueryPlannerType.HEURISTIC;

        return Stream.of(
                Arguments.of(named("Better planner", settingsBetterPlanner))
                // Arguments.of(named("Heuristic planner", settingsHeuristicPlanner)) todo uncomment once heuristic planner is implemented
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simpleNoPredicateNoJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT sameInt FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(1, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("sameint"));

        int i = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                assertEquals(i + 50, s.getValue("sameint").asInt());
                i++;
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simpleManyFieldsRenamedNoPredicateNoJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < 100; i++) {
            if (i == 99) {
                sb.append("sameint AS a").append(" ");
            } else {
                sb.append("sameint AS a").append(", ");
            }
        }

        sb.append("FROM table1;");

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, sb.toString());

        assertEquals(100, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("a"));

        int i = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                assertEquals(i + 50, s.getValue("a").asInt());
                i++;
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simpleManyFieldsNotRenamedNoPredicateNoJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < 100; i++) {
            if (i == 99) {
                sb.append("sameint").append(" ");
            } else {
                sb.append("sameint").append(", ");
            }
        }

        sb.append("FROM table1;");

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, sb.toString());

        assertEquals(100, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("sameint"));

        int i = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                assertEquals(i + 50, s.getValue("sameint").asInt());
                i++;
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void complexNoPredicateNoJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String fullFieldName = "(((t1_intfield1 * table1.t1_intfield2) - sameint) + (sameint * table1.sameint))";

        String query = "SELECT t1_intfield1 * table1.t1_intfield2 - sameint + (sameint * table1.sameint) AS a, " +
                fullFieldName + " FROM table1;";

        int i = 0;
        try (Scan s = PlanTestUtils.createQueryPlan(testData, query).open()) {
            s.beforeFirst();
            while (s.next()) {
                int correct = i * (i + 1) - (i + 50) + ((i + 50) * (i + 50));
                assertEquals(correct, s.getValue("a").asInt());
                assertEquals(correct, s.getValue(fullFieldName).asInt());
                i++;
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simpleNoPredicateJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT table1.t1_intfield1, t1_intfield1, t2.sameint FROM table1, table2 t2;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(3, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("table1.t1_intfield1"));
        assertTrue(queryPlan.outputSchema().hasField("t1_intfield1"));
        assertTrue(queryPlan.outputSchema().hasField("t2.sameint"));

        int i = 0, j = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                if (j == 250) {
                    i++;
                    j = 0;
                }
                assertEquals(i, s.getValue("table1.t1_intfield1").asInt());
                assertEquals(i, s.getValue("t1_intfield1").asInt());
                assertEquals(s.getValue("t1_intfield1").asInt(),
                        s.getValue("table1.t1_intfield1").asInt());
                assertEquals(j, s.getValue("t2.sameint").asInt());
                j++;
            }
        }
    }

    /// 250^3 operations, a little slower.
    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void complexNoPredicateJoins(LBDBSettings settings) throws IOException {
        // do slower tests only with the better query planner
        assumeTrue(settings.queryPlannerType == QueryPlannerType.HEURISTIC,
                "Slow test, skipping better planner");

        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String complexExpr = "((table1.t1_intfield1 * t2.sameint) - (table3.sameint / 5))";
        String query = "SELECT " + complexExpr + " FROM table1, table2 t2, table3;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(1, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField(complexExpr));

        int i = 0, j = 0, k = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                if (k == 250) {
                    j++;
                    k = 0;
                }
                if (j == 250) {
                    i++;
                    j = 0;
                }
                int correct = i * j - k / 5;
                assertEquals(correct, s.getValue(complexExpr).asInt());
                k++;
            }
        }

        assertEquals(249*249*250, i * j * k);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simplePredicateFiltersAllJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT t3.sameint, t1.t1_intfield1 " +
                "FROM table1 t1, table2, table3 t3 " +
                "WHERE 0 = 1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(2, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("t3.sameint"));
        assertTrue(queryPlan.outputSchema().hasField("t1.t1_intfield1"));

        int i = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                i++;
            }
        }

        assertEquals(0, i);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simplePredicateJoins(LBDBSettings settings) throws IOException {
        // do slower tests only with the better query planner
        assumeTrue(settings.queryPlannerType == QueryPlannerType.HEURISTIC,
                "Slow test, skipping better planner");

        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT t1.t1_intfield1, t1.samebool, t2_stringField1, t3.sameint " +
                "FROM table1 t1, table2, table3 t3 " +
                "WHERE t1.samebool = TRUE AND t3.sameint < t1_intfield1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(4, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("t1.t1_intfield1"));
        assertTrue(queryPlan.outputSchema().hasField("t1.samebool"));
        assertTrue(queryPlan.outputSchema().hasField("t2_stringfield1"));
        assertTrue(queryPlan.outputSchema().hasField("t3.sameint"));

        int totalCount = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            for (int i = 0; i < 250; i++) {
                boolean samebool = (i < 50);
                for (int j = 0; j < 250; j++) {
                    String t2_str = "str" + j;
                    for (int k = 0; k < 250; k++) {
                        if (samebool && k < i) {
                            assertTrue(s.next());

                            assertEquals(i, s.getValue("t1.t1_intfield1").asInt());
                            assertEquals(samebool, s.getValue("t1.samebool").asBoolean());
                            assertEquals(t2_str, s.getValue("t2_stringfield1").asString());
                            assertEquals(k, s.getValue("t3.sameint").asInt());

                            totalCount++;
                        }
                    }
                }
            }
            assertFalse(s.next());
        }

        assertEquals(306250, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void wildcardNoJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT * FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(12, queryPlan.outputSchema().getFields().size());
        for (String field : testData.layouts().getFirst().getSchema().getFields()) {
            assertTrue(queryPlan.outputSchema().hasField(field));
        }

        int i = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                assertEquals(i + 50, s.getValue("sameint").asInt());
                i++;
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void wildcardJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT * FROM table1, table2;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(24, queryPlan.outputSchema().getFields().size());

        for (String field : testData.layouts().get(0).getSchema().getFields()) {
            if (field.contains("same")) {
                assertTrue(queryPlan.outputSchema().hasField("table1." + field));
                assertFalse(queryPlan.outputSchema().hasField(field));
                continue;
            }
            assertTrue(queryPlan.outputSchema().hasField(field));
        }
        for (String field : testData.layouts().get(1).getSchema().getFields()) {
            if (field.contains("same")) {
                assertTrue(queryPlan.outputSchema().hasField("table2." + field));
                assertFalse(queryPlan.outputSchema().hasField(field));
                continue;
            }
            assertTrue(queryPlan.outputSchema().hasField(field));
        }

        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            for (int i = 0; i < 250; i++) {
                for (int j = 0; j < 250; j++) {
                    assertTrue(s.next());
                    assertEquals(i + 50, s.getValue("table1.sameint").asInt());
                    assertEquals(j, s.getValue("table2.sameint").asInt());
                }
            }
            assertFalse(s.next());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void complexPredicateJoinsNullProjectAndEqPredicate(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT t1.t1_intfield3 + 5, t2.t2_intfield3, t2.t2_stringField3 " +
                "FROM table1 t1, table2 t2 " +
                "WHERE t2.t2_boolfield3 = NULL;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(3, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("(t1.t1_intfield3 + 5)"));
        assertTrue(queryPlan.outputSchema().hasField("t2.t2_intfield3"));
        assertTrue(queryPlan.outputSchema().hasField("t2.t2_stringfield3"));

        assertTrue(queryPlan.outputSchema().isNullable("(t1.t1_intfield3 + 5)"));

        int totalCount = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                totalCount++;
            }
        }

        assertEquals(0, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void complexPredicateJoinsNullProjectAndIsPredicate(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String insertNewNullQuery = "INSERT INTO table1 (" +
                "t1_intfield1, t1_intfield2, t1_intfield3, " +
                "t1_stringField1, t1_stringField2, t1_stringField3, " +
                "t1_boolField1, t1_boolField2, t1_boolField3, " +
                "sameint, samestring, samebool" +
            ") VALUES (" +
                "1, 2, 999, " +
                "'test1', 'test2', NULL, " +
                "true, TRUE, NULL, " +
                "1234, 'test', false" +
            ");";

        assertEquals(1, PlanTestUtils.executeUpdate(testData, insertNewNullQuery));

        String query = "SELECT t1.t1_intfield3 + 5, t2.t2_intfield3, t2.t2_stringField3, 3 + NULL, " +
                "3 - NULL + t1.t1_intfield1 " +
                "FROM table1 t1, table2 t2 " +
                "WHERE t2.t2_boolfield3 IS NULL AND t1.t1_intfield3 = 999;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(5, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("(t1.t1_intfield3 + 5)"));
        assertTrue(queryPlan.outputSchema().hasField("t2.t2_intfield3"));
        assertTrue(queryPlan.outputSchema().hasField("t2.t2_stringfield3"));
        assertTrue(queryPlan.outputSchema().hasField("(3 + NULL)"));
        assertTrue(queryPlan.outputSchema().hasField("((3 - NULL) + t1.t1_intfield1)"));

        assertTrue(queryPlan.outputSchema().isNullable("(t1.t1_intfield3 + 5)"));
        assertTrue(queryPlan.outputSchema().isNullable("(3 + NULL)"));
        assertTrue(queryPlan.outputSchema().isNullable("((3 - NULL) + t1.t1_intfield1)"));

        int totalCount = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                if (s.getValue("(t1.t1_intfield3 + 5)") != NullConstant.INSTANCE) {
                    assertEquals(999 + 5, s.getValue("(t1.t1_intfield3 + 5)").asInt());
                }
                assertEquals(NullConstant.INSTANCE, s.getValue("t2.t2_intfield3"));
                assertEquals(NullConstant.INSTANCE, s.getValue("t2.t2_stringfield3"));
                assertEquals(NullConstant.INSTANCE, s.getValue("(3 + NULL)"));
                assertEquals(NullConstant.INSTANCE, s.getValue("((3 - NULL) + t1.t1_intfield1)"));
                totalCount++;
            }
        }

        assertEquals(250, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simpleNoPredicateOnJoins(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT t1.t1_intfield1, t2.t2_intfield1 "+
                "FROM table1 t1 JOIN table2 t2 ON t1.sameint = t2.sameint;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(2, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("t1.t1_intfield1"));
        assertTrue(queryPlan.outputSchema().hasField("t2.t2_intfield1"));

        int totalCount = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                totalCount++;
            }
        }

        assertEquals(200, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void simpleNoPredicateUnions(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query =
                "SELECT sameint      FROM table1 " +
                    "UNION ALL " +
                "SELECT t2_intfield3 FROM table2 " +
                    "UNION ALL " +
                "SELECT sameint      FROM table3 " +
                    "UNION ALL " +
                "SELECT t2_intfield3 FROM table2 " +
                    "UNION ALL " +
                "SELECT t1_intfield2 FROM table1 " +
                    "UNION ALL " +
                "SELECT t1_intfield2 FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(1, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("sameint"));

        assertTrue(queryPlan.outputSchema().isNullable("sameint"));

        int totalCount = 0;

        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                if (totalCount < 250) {
                    assertEquals(totalCount + 50, s.getValue("sameint").asInt());
                } else if (totalCount < 500) {
                    assertEquals(NullConstant.INSTANCE, s.getValue("sameint"));
                } else if (totalCount < 750) {
                    assertEquals(totalCount - 500, s.getValue("sameint").asInt());
                } else if (totalCount < 1000){
                    assertEquals(NullConstant.INSTANCE, s.getValue("sameint"));
                } else if (totalCount < 1250) {
                    assertEquals(totalCount - 1000 + 1, s.getValue("sameint").asInt());
                } else {
                    assertEquals(totalCount - 1250 + 1, s.getValue("sameint").asInt());
                }
                totalCount++;
            }
        }

        assertEquals(1500, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void complexPredicateJoinsUnions(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query =
                "SELECT t1.t1_intfield3, t2.t2_intfield2, t2.t2_stringfield3 " +
                "FROM table1 t1, table2 t2 " +
                "WHERE t1.t1_intfield1 < 100 " +
                    "UNION ALL " +
                "SELECT t3.sameint, t2.sameint, t2.t2_stringfield1 " +
                "FROM table3 t3, table2 t2 " +
                "WHERE t3.sameint < 50 AND t2.t2_stringfield1 != 'str1';";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        int[] part1ValueCounts = new int[1000];
        int[][] part2PairCounts = new int[1000][1000];

        int totalCount = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                Constant v1 = s.getValue("t1.t1_intfield3");
                Constant v2 = s.getValue("t2.t2_intfield2");
                Constant v3 = s.getValue("t2.t2_stringfield3");

                if (v1.equals(NullConstant.INSTANCE)) {
                    assertEquals(NullConstant.INSTANCE, v3);
                    int val2 = v2.asInt();
                    assertTrue(val2 >= 1 && val2 <= 250);
                    part1ValueCounts[val2 - 1]++;
                } else {
                    int val1 = v1.asInt();
                    int val2 = v2.asInt();
                    String val3 = v3.asString();

                    assertEquals("str" + val2, val3);
                    assertTrue(val1 >= 0 && val1 < 50);
                    assertNotEquals(1, val2);

                    part2PairCounts[val1][val2]++;
                }
                totalCount++;
            }
        }

        for (int i = 0; i < 250; i++) {
            assertEquals(100, part1ValueCounts[i]);
        }

        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < 250; j++) {
                if (j == 1) continue;
                assertEquals(1, part2PairCounts[i][j]);
            }
        }

        assertEquals(37450, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void testConstantExpressionsUnionRegularExpressions1(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query =
                "SELECT t1_intfield1, t1_stringfield1, t1_boolfield1 FROM table1 " +
                        "UNION ALL " +
                        "SELECT 1000, 'a', false;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        int totalCount = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                Constant v1 = s.getValue("t1_intfield1");
                Constant v2 = s.getValue("t1_stringfield1");
                Constant v3 = s.getValue("t1_boolfield1");

                if (totalCount < 250){
                    assertEquals(new IntConstant(totalCount), v1);
                    assertEquals(new StringConstant("str" + totalCount), v2);
                    assertEquals(new BooleanConstant(true), v3);
                } else {
                    assertEquals(new IntConstant(1000), v1);
                    assertEquals(new StringConstant("a"), v2);
                    assertEquals(new BooleanConstant(false), v3);
                }
                totalCount++;
            }
        }

        assertEquals(251, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void testConstantExpressionsUnionRegularExpressions2(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query =
                "SELECT 1000, 'a', false " +
                        "UNION ALL " +
                        "SELECT t1_intfield1, t1_stringfield1, t1_boolfield1 FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        int totalCount = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                Constant v1 = s.getValue("1000");
                Constant v2 = s.getValue("'a'");
                Constant v3 = s.getValue("FALSE");

                if (totalCount == 0) {
                    assertEquals(new IntConstant(1000), v1);
                    assertEquals(new StringConstant("a"), v2);
                    assertEquals(new BooleanConstant(false), v3);
                } else {
                    assertEquals(new IntConstant(totalCount - 1), v1);
                    assertEquals(new StringConstant("str" + (totalCount - 1)), v2);
                    assertEquals(new BooleanConstant(true), v3);
                }
                totalCount++;
            }
        }

        assertEquals(251, totalCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsProvider")
    public void testPredicateAsProjectionColumn(LBDBSettings settings) throws IOException {
        Path tmpDir = TestUtils.setUpTempDirectory();
        var testData = PlanTestUtils.initializeThreeFullTables(tmpDir, settings);

        String query = "SELECT sameint > 150 AS is_large FROM table1;";

        Plan<Scan> queryPlan = PlanTestUtils.createQueryPlan(testData, query);

        assertEquals(1, queryPlan.outputSchema().getFields().size());
        assertTrue(queryPlan.outputSchema().hasField("is_large"));

        int i = 0;
        try (Scan s = queryPlan.open()) {
            s.beforeFirst();
            while (s.next()) {
                boolean expected = (i + 50) > 150;
                assertEquals(new BooleanConstant(expected), s.getValue("is_large"));
                i++;
            }
        }

        assertEquals(250, i);
    }
}
