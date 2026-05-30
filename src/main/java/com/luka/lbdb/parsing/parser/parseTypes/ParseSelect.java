package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.statement.SelectStatement;
import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.parsing.statement.select.ProjectionFieldInfo;
import com.luka.lbdb.parsing.statement.select.SingleSelection;
import com.luka.lbdb.parsing.statement.select.TableInfo;
import com.luka.lbdb.parsing.tokenizer.token.KeywordToken;
import com.luka.lbdb.parsing.tokenizer.token.SymbolToken;
import com.luka.lbdb.querying.virtualEntities.Evaluatable;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.expression.WildcardExpression;

import java.util.*;

/// The class responsible for parsing data queries.
/// Its subgrammar is defined like this:
///
/// ```
/// <Field>                     := IdentificationToken
/// <TableName>                 := IdentificationToken[.IdentificationToken]
/// <ParseSelect>               := <ParseSingleSelection> [UNION ALL <ParseSingleSelection>]
/// <ParseSingleSelection>      := SELECT <ProjectedFields> [FROM <JoinSpecs>] [WHERE <ParsePredicate>]
/// <ProjectedFields>           := <ParseExpression> [AS <Field>] [, <ProjectedFields>]
/// <JoinSpecs>                 := <TableName> [<JoinSpec>] [, <JoinSpecs>]
/// <JoinSpec>                  := JOIN <TableName> ON <ParsePredicate>
/// ```
public class ParseSelect {
    private final ParserContext ctx;

    /// Every syntactic category requires the parse context to
    /// be initialized.
    public ParseSelect(ParserContext ctx) {
        this.ctx = ctx;
    }

    /// Parses the query select statement according to the sub-grammar
    /// defined in the class documentation.
    ///
    /// @return Parsed data required for data querying.
    public SelectStatement parse() {
        List<SingleSelection> singleSelections = new ArrayList<>();

        boolean hasMoreUnions;
        do {
            ctx.eat(KeywordToken.SELECT);
            List<ProjectionFieldInfo> fields = projectedFields();

            List<JoinSpec> joinSpecs = new ArrayList<>();
            if (ctx.eatIfMatches(KeywordToken.FROM)) {
                joinSpecs = joinSpecs();
            }

            Predicate predicate = new Predicate();
            if (ctx.eatIfMatches(KeywordToken.WHERE)) {
                predicate = new ParsePredicate(ctx).parse();
            }

            List<TableInfo> tables = new ArrayList<>();
            for (JoinSpec joinSpec : joinSpecs) {
                tables.addAll(joinSpec.tables);
                predicate.conjoinWith(joinSpec.joinPredicate);
            }

            singleSelections.add(new SingleSelection(fields, tables, predicate));

            if (ctx.eatIfMatches(KeywordToken.UNION)) {
                ctx.eat(KeywordToken.ALL);
                hasMoreUnions = true;
            } else {
                hasMoreUnions = false;
            }
        } while (hasMoreUnions);

        return new SelectStatement(singleSelections);
    }

    /// Parses a list of projection fields which can be expressions, fields,
    /// constants or any of the above. Optional renaming with `AS`.
    ///
    /// @return The list of all projection fields.
    private List<ProjectionFieldInfo> projectedFields() {
        List<ProjectionFieldInfo> projectList = new ArrayList<>();

        do {
            String newFieldName;
            Evaluatable evaluatable = new ParseEvaluatable(ctx).parse();

            if (ctx.eatIfMatches(KeywordToken.AS)) {
                if (evaluatable instanceof WildcardExpression) {
                    throw new ParsingException("The wildcard operator can't be renamed");
                }
                newFieldName = fieldName();
            } else {
                newFieldName = evaluatable.toString();
            }

            projectList.add(new ProjectionFieldInfo(newFieldName, evaluatable));
        } while (ctx.eatIfMatches(SymbolToken.COMMA));

        return projectList;
    }

    /// Parses all join operations and table names.
    ///
    /// @return The list of table names and join predicates.
    private List<JoinSpec> joinSpecs() {
        List<JoinSpec> joinSpecList = new ArrayList<>();

        boolean hasMoreJoins;
        boolean nextIsExplicitJoin = false;
        do {
            joinSpecList.add(joinSpec(nextIsExplicitJoin));

            if (ctx.eatIfMatches(SymbolToken.COMMA)) {
                hasMoreJoins = true;
                nextIsExplicitJoin = false;
            } else if (ctx.eatIfMatches(KeywordToken.JOIN)) {
                hasMoreJoins = true;
                nextIsExplicitJoin = true;
            } else {
                hasMoreJoins = false;
            }
        } while (hasMoreJoins);

        return joinSpecList;
    }

    /// Parses one join operation with one or two tables and
    /// one predicate.
    ///
    /// @return The parsed join operation result.
    private JoinSpec joinSpec(boolean nextIsExplicitJoin) {
        Predicate joinPredicate = new Predicate();

        List<TableInfo> tables = new ArrayList<>();
        tables.add(tableName());

        if (nextIsExplicitJoin) {
            ctx.eat(KeywordToken.ON);
            joinPredicate.conjoinWith(new ParsePredicate(ctx).parse());
        }

        return new JoinSpec(tables, joinPredicate);
    }

    /// @return The table name identifier string with the optional range variable name.
    private TableInfo tableName() {
        String tableName = ctx.eatIdentifier();
        Optional<String> rangeVariableName = ctx.eatIdentifierIfMatches();

        return new TableInfo(tableName, rangeVariableName);
    }

    /// @return The field name identifier string.
    private String fieldName() {
        return ctx.eatIdentifier();
    }

    /// Helper record to handle table join -> where predicate conversion.
    private record JoinSpec(List<TableInfo> tables, Predicate joinPredicate) { }
}
