package com.luka.lbdb.records;

/// Represents everything that can describe an arbitrary field.
public record FieldInfo(DatabaseType type, int runtimeLength, boolean nullable) { }