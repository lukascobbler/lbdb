package com.luka.lbdb.querying.virtualEntities;

import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.scanDefinitions.Scan;
import com.luka.lbdb.querying.virtualEntities.expression.ConstantExpression;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.querying.virtualEntities.expression.FieldNameExpression;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A predicate is the topmost structure that binds all terms,
/// which hold all expressions. It defines logical operators between
/// terms. A predicate object holds a list of terms that implicitly have an
/// `AND` between them.
public final class Predicate implements Evaluatable {
    private List<Term> terms = new ArrayList<>();

    /// Initializes a predicate with no terms, that is equivalent
    /// to a `TRUE` value that satisfies everything.
    public Predicate() {}

    /// Initializes a predicate with one term.
    public Predicate(Term term) {
        terms.add(term);
    }

    /// Initialize a predicate with multiple terms.
    public Predicate(Term... terms) {
        this.terms.addAll(List.of(terms));
    }

    /// Initialize a predicate with a list of terms.
    public Predicate(List<Term> terms) {
        this.terms = terms;
    }

    /// Adds all terms of some predicate to this one.
    public void conjoinWith(Predicate predicate) {
        terms.addAll(predicate.terms);
    }

    /// A predicate satisfies some scan if all terms that it holds
    /// satisfy that scan.
    ///
    /// @return Whether a predicate satisfies some scan.
    @Override
    public Constant evaluate(Scan scan) {
        return new BooleanConstant(terms.stream()
                .allMatch(t -> t.isSatisfied(scan)));
    }

    /// @return True if all expressions of all terms are constant.
    @Override
    public boolean isConstant() {
        return terms.stream()
                .allMatch(t -> t.getLhs().isConstant() && t.getRhs().isConstant());
    }

    /// A predicate is always of the boolean type.
    ///
    /// @return The boolean database type.
    @Override
    public DatabaseType type(Schema schema) {
        return DatabaseType.BOOLEAN;
    }

    /// Predicate's length is always 1 byte long, as booleans
    /// are always one byte.
    ///
    /// @return 1 because booleans always have a length of 1.
    @Override
    public int length(Schema schema) {
        return 1;
    }

    /// A predicate is nullable if any of the expressions in any term is nullable.
    ///
    /// @return True if any expression of any term is nullable.
    @Override
    public boolean isNullable(Schema schema) {
        return terms.stream()
                .anyMatch(t -> t.getLhs().isNullable(schema) || t.getLhs().isNullable(schema));
    }

    /// @return The set of all fields of every term's expression.
    @Override
    public Set<String> getFields() {
        return terms.stream()
                .flatMap(t -> Stream.concat(
                        t.getLhs().getFields().stream(),
                        t.getRhs().getFields().stream()
                ))
                .collect(Collectors.toSet());
    }

    /// @return True if any expression of any term has a wildcard.
    @Override
    public boolean hasWildCard() {
        return terms.stream().anyMatch(t -> t.getRhs().hasWildCard() || t.getLhs().hasWildCard());
    }

    /// @return The predicate where each expression of each term is qualified.
    @Override
    public Predicate qualify(Map<String, String> aliases) {
        Predicate qualifiedPredicate = new Predicate();

        for (Term t : terms) {
            Expression qualifiedLhs = t.getLhs().qualify(aliases);
            Expression qualifiedRhs = t.getRhs().qualify(aliases);
            Term qualifiedTerm = new Term(qualifiedLhs, t.getTermOperator(), qualifiedRhs);

            qualifiedPredicate.terms.add(qualifiedTerm);
        }

        return qualifiedPredicate;
    }

    // todo add docs once heuristic table planner is complete
    public Predicate selectSubPredicate(Schema schema) {
        Predicate result = new Predicate();

        for (Term term : terms) {
            if (term.appliesTo(schema)) {
                result.terms.add(term);
            }
        }

        if (result.terms.isEmpty()) {
            return null;
        }

        return result;
    }

    // todo add docs once heuristic table planner is complete
    public Predicate joinSubPredicate(Schema schema1, Schema schema2) {
        Predicate result = new Predicate();
        Schema newSchema = new Schema();
        newSchema.addAll(schema1);
        newSchema.addAll(schema2);

        for (Term term : terms) {
            if (!term.appliesTo(schema1) && !term.appliesTo(schema2) && term.appliesTo(newSchema)) {
                result.terms.add(term);
            }
        }

        if (result.terms.isEmpty()) {
            return null;
        }

        return result;
    }

    /// Checks for "Field1 = Constant" or "Constant = Field1" cases
    /// and if that is true, returns the constants that the requested
    /// field equates to (only for the field equalities that have
    /// the requested field).
    ///
    /// @return The stream of constants that the requested field equates to.
    /// An empty stream in any other case.
    public Stream<Constant> allEquatedConstants(String fieldName) {
        return terms.stream()
                .flatMap(t -> t.equatesWithConstant(fieldName).stream());
    }

    /// Checks for "Field1 = Field2" or "Field2 = Field1" cases
    /// and if that is true, returns the fields that the requested
    /// field equates to (only for the field equalities that have
    /// the requested field).
    ///
    /// @return The stream of fields that the requested field equates to.
    /// An empty stream in any other case.
    public Stream<String> allEquatedFields(String fieldName) {
        return terms.stream()
                .flatMap(t -> t.equatesWithFieldName(fieldName).stream());
    }

    /// Checks for "Field1 IS NULL" or "NULL IS Field1" cases
    /// and if any of them match, returns true.
    ///
    /// @return True if there is at least one NULL comparison.
    public boolean equatesWithNull(String fieldName) {
        return terms.stream().anyMatch(t ->
                t.getTermOperator() == TermOperator.IS &&
                (
                    (t.getLhs() instanceof FieldNameExpression f1 && f1.qualifiedName().equals(fieldName) &&
                    t.getRhs() instanceof ConstantExpression(Constant constant1) && constant1.isNull())
                        ||
                    (t.getRhs() instanceof FieldNameExpression f2 && f2.qualifiedName().equals(fieldName) &&
                    t.getLhs() instanceof ConstantExpression(Constant constant2) && constant2.isNull())
                )
        );
    }

    /// Checks for operations that exclude NULL values after
    /// applying them.
    ///
    /// @return True if any operation excludes null values.
    public boolean excludesNulls(String fieldName) {
        return terms.stream().anyMatch(t ->
                t.getTermOperator() != TermOperator.IS &&
                (
                    t.getLhs() instanceof FieldNameExpression f1 && f1.qualifiedName().equals(fieldName)
                        ||
                    t.getRhs() instanceof FieldNameExpression f2 && f2.qualifiedName().equals(fieldName)
                )
        );
    }

    public List<Term> getTerms() {
        return terms;
    }

    @Override
    public String toString() {
        Iterator<Term> iter = terms.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder result = new StringBuilder(iter.next().toString());
        while (iter.hasNext()) {
            result.append(" AND ").append(iter.next().toString());
        }

        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Predicate predicate = (Predicate) o;

        if (terms.size() != predicate.terms.size()) return false;

        return new HashSet<>(terms).equals(new HashSet<>(predicate.terms));
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (Term t : terms) {
            h += (t != null ? t.hashCode() : 0);
        }
        return h;
    }
}
