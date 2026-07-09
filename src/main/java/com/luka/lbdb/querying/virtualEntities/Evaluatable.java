package com.luka.lbdb.querying.virtualEntities;

import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;

import java.util.Map;
import java.util.Set;

/// Something that is evaluatable returns a constant upon its evaluation
/// over a scan. This interface defines methods required for describing
/// every evaluatable object.
public interface Evaluatable {
    /// @return The constant evaluation of an evaluatable over some scan.
    Constant evaluate(Scan scan);
    /// @return True if the evaluatable evaluates to a constant value,
    /// independent of any table fields.
    boolean isConstant();
    /// @return The type of this evaluatable AST for a given schema.
    DatabaseType type(Schema schema);
    /// @return The runtime length needed for the longest operand
    /// in the evaluatable AST for a given schema.
    int length(Schema schema);
    /// @return True if any of the fields in the evaluatable AST is nullable.
    boolean isNullable(Schema schema);
    /// @return All fields (field name expressions) mentioned in the whole evaluatable AST.
    Set<String> getFields();
    /// @return Whether any of the expressions in the evaluatable AST is a wildcard expression.
    boolean hasWildCard();
    /// @return A new evaluatable that has every field name fully qualified according
    /// to the aliases map.
    Evaluatable qualify(Map<String, String> aliases);
}
