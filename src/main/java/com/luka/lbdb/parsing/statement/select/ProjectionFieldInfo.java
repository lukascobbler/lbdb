package com.luka.lbdb.parsing.statement.select;

import com.luka.lbdb.querying.virtualEntities.Evaluatable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// A field that should be sent to the user (a projected field) can be a virtual field,
/// or a real field directly from the table on the disk. Both of those types of fields
/// need to be processed in the same way and this is the record that describes one
/// projected field.
public record ProjectionFieldInfo(String name, Evaluatable evaluatable) {
    @Override
    public @NotNull String toString() {
        String projectString = evaluatable.toString();
        if (projectString.equals(name)) {
            return projectString;
        }
        return projectString + " AS " + name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, evaluatable);
    }
}
