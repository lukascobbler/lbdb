package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.statement.InsertStatement;
import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.parsing.statement.insert.AllTuplesValueInfo;
import com.luka.lbdb.parsing.tokenizer.token.KeywordToken;
import com.luka.lbdb.parsing.tokenizer.token.SymbolToken;
import com.luka.lbdb.querying.exceptions.RuntimeExecutionException;
import com.luka.lbdb.querying.virtualEntities.constant.Constant;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.planning.planner.PartialEvaluator;

import java.util.ArrayList;
import java.util.List;

/// The class responsible for parsing row insertion.
/// Its subgrammar is defined like this:
///
/// ```
/// <TableName>         := IdentificationToken
/// <FieldName>         := IdentificationToken
/// <ParseInsert>       := INSERT INTO <TableName> [(<FieldList>)] VALUES <TuplesList>
/// <FieldList>         := <FieldName> [, <FieldList>]
/// <TuplesList>        := (<ConstantList>) [, (<ConstantList>)]
/// <ConstantList>      := <Constant> [, <ConstantList>]
/// ```
///
/// For INSERT commands, putting expressions as insert values is technically
/// valid SQL, but these expressions must be constant and will be evaluated in
/// the parser instead of in the scan. If the field list is not present, it must
/// be inferred by the planner in the order of the create table statement used
/// to create this table.
public class ParseInsert {
    private final ParserContext ctx;

    /// Every syntactic category requires the parse context to
    /// be initialized.
    public ParseInsert(ParserContext ctx) {
        this.ctx = ctx;
    }

    /// Parses the insert statement according to the sub-grammar
    /// defined in the class documentation.
    ///
    /// @return Parsed data required for data insertion.
    public InsertStatement parse() {
        ctx.eat(KeywordToken.INSERT);
        ctx.eat(KeywordToken.INTO);

        String tableName = tableName();

        List<String> fields = new ArrayList<>();
        if (ctx.eatIfMatches(SymbolToken.LEFT_PAREN)) {
            fields.addAll(fieldList());
            ctx.eat(SymbolToken.RIGHT_PAREN);
        }

        ctx.eat(KeywordToken.VALUES);

        List<List<Constant>> tuples = tuplesList();

        int tupleSize = tuples.getFirst().size();

        if (tuples.stream().anyMatch(t -> t.size() != tupleSize)) {
            throw new ParsingException("Not all new values have the same length");
        }

        if (fields.size() != tupleSize && !fields.isEmpty()) {
            throw new ParsingException("Field list not same size as constant list");
        }

        return new InsertStatement(tableName, new AllTuplesValueInfo(fields, tuples, fields.isEmpty()));
    }

    private List<List<Constant>> tuplesList() {
        List<List<Constant>> tuplesList = new ArrayList<>();

        do {
            ctx.eat(SymbolToken.LEFT_PAREN);
            tuplesList.add(constantList());
            ctx.eat(SymbolToken.RIGHT_PAREN);
        } while (ctx.eatIfMatches(SymbolToken.COMMA));

        return tuplesList;
    }

    /// Parses a list of expressions and evaluates them to constants.
    ///
    /// @return A list of constants for the newly inserted row's values.
    /// @throws ParsingException if an insert statement has non-constant
    /// expressions as field values.
    private List<Constant> constantList() {
        List<Constant> constantList = new ArrayList<>();

        do {
            // Since INSERT statements can contain expressions as new values, they
            // must be calculated here instead of the planner to not complicate
            // the planner. The calculation fails if the user provides a
            // non-constant expression as the value. Constant expressions
            // can be calculated without scans if they are 100% constant.

            Expression constantExpression = new ParseExpression(ctx).parse();

            try {
                Expression foldedConstantExpression = (Expression) PartialEvaluator.evaluate(constantExpression);
                if (!foldedConstantExpression.isConstant()) {
                    throw new ParsingException("An insert statement must have constant expressions for new values");
                }

                constantList.add(foldedConstantExpression.evaluate(null));
            } catch (RuntimeExecutionException e) {
                throw new ParsingException("A new value must be a valid constant expression");
            }
        } while (ctx.eatIfMatches(SymbolToken.COMMA));

        return constantList;
    }

    /// @return The list of field names separated by commas as identifiers.
    private List<String> fieldList() {
        List<String> fieldList = new ArrayList<>();

        do {
            fieldList.add(fieldName());
        } while (ctx.eatIfMatches(SymbolToken.COMMA));

        return fieldList;
    }

    /// @return The table name identifier string.
    private String tableName() {
        return ctx.eatIdentifier();
    }

    /// @return The field name identifier string.
    private String fieldName() {
        return ctx.eatIdentifier();
    }
}
