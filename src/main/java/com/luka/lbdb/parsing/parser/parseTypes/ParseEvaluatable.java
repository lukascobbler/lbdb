package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.parsing.tokenizer.token.KeywordToken;
import com.luka.lbdb.parsing.tokenizer.token.SymbolToken;
import com.luka.lbdb.querying.virtualEntities.Evaluatable;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;

import java.util.Optional;

/// The class responsible for parsing projection fields.
/// Its subgrammar is defined like this:
///
/// ```
/// <Term>              := <ParseExpression> "=" | "!=" | ">" | "<" | ">=" | "<=" | "IS" <ParseExpression>
/// <Evaluatable>       := <ParseExpression> | <Term> [AND<Predicate>]
/// ```
public class ParseEvaluatable {
    private final ParserContext ctx;
    private final ParseExpression exprParser;

    /// Every syntactic category requires the parse context to
    /// be initialized. A predicate parser will also initialize
    /// an expression parser for repeated expression parsing.
    public ParseEvaluatable(ParserContext ctx) {
        this.ctx = ctx;
        this.exprParser = new ParseExpression(ctx);
    }

    /// Parses a list of sub-predicates separated by "AND" and joins
    /// all of them into one big predicate.
    ///
    /// @return The parsed predicate.
    public Evaluatable parse() {
        Expression firstExpr = exprParser.parse();

        Optional<TermOperator> termOperatorMaybe = tryParseTermOperator();
        if (termOperatorMaybe.isEmpty()) {
            return firstExpr;
        }

        TermOperator termOperator = termOperatorMaybe.get();
        ctx.advance();
        Expression secondExpr = exprParser.parse();

        Predicate predicate = new Predicate(new Term(firstExpr, termOperator, secondExpr));

        while (ctx.eatIfMatches(KeywordToken.AND)) {
            predicate.conjoinWith(new Predicate(parseTerm()));
        }

        return predicate;
    }

    /// A sub-predicate consists of one term.
    ///
    /// @return The parsed term, with two expressions and a term
    /// operator.
    private Term parseTerm() {
        Expression lhs = exprParser.parse();
        Optional<TermOperator> op = tryParseTermOperator();
        if (op.isEmpty()) {
            throw new ParsingException("Expected comparison operator, found: " + ctx.lookAhead(0));
        }
        ctx.advance();
        Expression rhs = exprParser.parse();
        return new Term(lhs, op.get(), rhs);
    }

    /// @return A mapping from a token to a term operator if the term operator exists, else
    /// `Optional.empty()`
    private Optional<TermOperator> tryParseTermOperator() {
        return switch (ctx.lookAhead(0)) {
            case SymbolToken st -> switch (st) {
                case EQUAL -> Optional.of(TermOperator.EQUALS);
                case NOT_EQUAL -> Optional.of(TermOperator.NOT_EQUALS);
                case GREATER_THAN -> Optional.of(TermOperator.GREATER_THAN);
                case LESS_THAN -> Optional.of(TermOperator.LESS_THAN);
                case GREATER_THAN_OR_EQUAL -> Optional.of(TermOperator.GREATER_OR_EQUAL);
                case LESS_THAN_OR_EQUAL -> Optional.of(TermOperator.LESS_OR_EQUAL);
                default -> Optional.empty();
            };
            case KeywordToken.IS -> {
                if (ctx.lookAhead(1) == KeywordToken.NOT) {
                    ctx.advance();
                    yield Optional.of(TermOperator.IS_NOT);
                }

                yield Optional.of(TermOperator.IS);
            }
            default -> Optional.empty();
        };
    }
}
