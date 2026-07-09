package com.luka.lbdbclient;

import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;

import java.util.ArrayList;
import java.util.List;

/// Client-side table printing logic.
public class TablePrinter {
    /// Generates a string containing the schema's fields as a header,
    /// and tuples as rows.
    ///
    /// @return The generated table string, ready for printing.
    public static String print(Schema schema, List<List<Constant>> tuples) {
        if (schema == null || schema.getFields().isEmpty()) return "";

        List<String> headers = schema.getFields();
        int cols = headers.size();
        int[] maxWidths = new int[cols];

        for (int i = 0; i < cols; i++) {
            maxWidths[i] = headers.get(i).length();
        }

        List<List<String>> displayRows = new ArrayList<>(tuples.size());
        for (List<Constant> row : tuples) {
            List<String> displayRow = new ArrayList<>(cols);
            for (int i = 0; i < cols; i++) {
                Constant c = row.get(i);
                String val;

                if (c.isNull()) {
                    val = "NULL";
                } else {
                    val = c.toString().replace("'", "");
                }

                displayRow.add(val);
                maxWidths[i] = Math.max(maxWidths[i], val.length());
            }
            displayRows.add(displayRow);
        }

        StringBuilder top = new StringBuilder("┌");
        StringBuilder headSep = new StringBuilder("├");
        StringBuilder bot = new StringBuilder("└");

        for (int i = 0; i < cols; i++) {
            top.append("─".repeat(maxWidths[i] + 2));
            headSep.append("─".repeat(maxWidths[i] + 2));
            bot.append("─".repeat(maxWidths[i] + 2));

            if (i < cols - 1) {
                top.append("┬");
                headSep.append("┼");
                bot.append("┴");
            } else {
                top.append("┐\n");
                headSep.append("┤\n");
                bot.append("┘\n");
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append(top).append("│");
        for (int i = 0; i < cols; i++) {
            sb.append(String.format(" %-" + maxWidths[i] + "s │", headers.get(i)));
        }
        sb.append("\n").append(headSep);

        for (List<String> row : displayRows) {
            sb.append("│");
            for (int i = 0; i < cols; i++) {
                DatabaseType type = schema.type(headers.get(i));
                boolean isNumeric = (type == DatabaseType.INT);

                if (!isNumeric && isNumber(row.get(i))) {
                    isNumeric = true;
                }

                if (isNumeric) {
                    sb.append(String.format(" %" + maxWidths[i] + "s │", row.get(i)));
                } else {
                    sb.append(String.format(" %-" + maxWidths[i] + "s │", row.get(i)));
                }
            }
            sb.append("\n");
        }

        sb.append(bot);
        return sb.toString();
    }

    /// @return Whether a string is a number.
    private static boolean isNumber(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
