package com.luka.lbdb.records.schema;

import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.FieldInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// `Schema` objects hold information about a table's field names, lengths
/// and types. Does not limit the number of records per field.
public class Schema {
    protected final List<String> fields = new ArrayList<>();
    protected final Map<String, FieldInfo> info = new HashMap<>();

    /// Generic field adder, can accept any SQL type (can be dangerous) along with
    /// the runtime length of that type. Allows duplicate types and assumes that every duplicate
    /// type will have the same metadata so it always returns the first one.
    public void addField(String fieldName, DatabaseType type, int runtimeLength, boolean isNullable) {
        fields.add(fieldName);

        if (!info.containsKey(fieldName)) {
            info.put(fieldName, new FieldInfo(type, runtimeLength, isNullable));
        }
    }

    /// Add an integer field.
    public void addIntField(String fieldName, boolean isNullable) {
        addField(fieldName, DatabaseType.INT, DatabaseType.INT.length, isNullable);
    }

    /// Add a string field with the maximum runtime length (VARCHAR type).
    public void addStringField(String fieldName, int runtimeLength, boolean isNullable) {
        addField(fieldName, DatabaseType.VARCHAR, runtimeLength, isNullable);
    }

    /// Add a boolean field.
    public void addBooleanField(String fieldName, boolean isNullable) {
        addField(fieldName, DatabaseType.BOOLEAN, DatabaseType.BOOLEAN.length, isNullable);
    }

    /// Add a field described by its name from some other schema.
    public void add(String fieldName, Schema otherSchema) {
        DatabaseType type = otherSchema.type(fieldName);
        int runtimeLength = otherSchema.runtimeLength(fieldName);
        boolean isNullable = otherSchema.isNullable(fieldName);
        addField(fieldName, type, runtimeLength, isNullable);
    }

    /// Add all fields from other schema to this schema.
    public void addAll(Schema otherSchema) {
        for (String fieldName : otherSchema.getFields()) {
            add(fieldName, otherSchema);
        }
    }

    /// @return The list of field names from this schema.
    public List<String> getFields() {
        return fields;
    }

    /// @return Whether this schema contains a field described by its name.
    public boolean hasField(String fieldName) {
        return fields.contains(fieldName);
    }

    /// @return The type of the field described by its name from the SQL constants.
    public DatabaseType type(String fieldName) {
        return info.get(fieldName).type();
    }

    /// @return The runtime length of the field described by its name.
    public int runtimeLength(String fieldName) {
        return info.get(fieldName).runtimeLength();
    }

    /// @return Whether the field is nullable.
    public boolean isNullable(String fieldName) {
        return info.get(fieldName).nullable();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Schema schema = (Schema) o;
        return getFields().equals(schema.getFields()) && info.equals(schema.info);
    }
}