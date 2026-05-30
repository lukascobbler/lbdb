package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.parsing.tokenizer.token.KeywordToken;
import com.luka.lbdb.parsing.tokenizer.token.SymbolToken;
import com.luka.lbdb.querying.virtualEntities.Predicate;
import com.luka.lbdb.querying.virtualEntities.expression.Expression;
import com.luka.lbdb.querying.virtualEntities.term.Term;
import com.luka.lbdb.querying.virtualEntities.term.TermOperator;

/// The class responsible for parsing predicates.
/// Its subgrammar is defined like this:
///
/// ```
/// <Term>              := <ParseExpression> "=" | "!=" | ">" | "<" | ">=" | "<=" | "IS" <ParseExpression>
/// <Predicate>         := <Term> [AND<Predicate>]
/// ```
public class ParsePredicate {
    private final ParserContext ctx;
    private final ParseExpression exprParser;

    /// Every syntactic category requires the parse context to
    /// be initialized. A predicate parser will also initialize
    /// an expression parser for repeated expression parsing.
    public ParsePredicate(ParserContext ctx) {
        this.ctx = ctx;
        this.exprParser = new ParseExpression(ctx);
    }

    /// Parses a list of sub-predicates separated by "AND" and joins
    /// all of them into one big predicate.
    ///
    /// @return The parsed predicate.
    public Predicate parse() {
        Predicate predicate = new Predicate(parseTerm());

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
        TermOperator op = parseTermOperator();
        Expression rhs = exprParser.parse();
        return new Term(lhs, op, rhs);
    }

    /// @return A mapping from a token to a term operator.
    /// @throws ParsingException if the matched token can't be a comparison operator.
    private TermOperator parseTermOperator() {
        return switch (ctx.advance()) {
            case SymbolToken st -> switch (st) {
                case EQUAL -> TermOperator.EQUALS;
                case NOT_EQUAL -> TermOperator.NOT_EQUALS;
                case GREATER_THAN -> TermOperator.GREATER_THAN;
                case LESS_THAN -> TermOperator.LESS_THAN;
                case GREATER_THAN_OR_EQUAL -> TermOperator.GREATER_OR_EQUAL;
                case LESS_THAN_OR_EQUAL -> TermOperator.LESS_OR_EQUAL;
                default -> throw new ParsingException("Expected comparison operator, found: " + st);
            };
            case KeywordToken.IS -> {
                if (ctx.eatIfMatches(KeywordToken.NOT)) {
                    yield TermOperator.IS_NOT;
                }

                yield TermOperator.IS;
            }
            default -> throw new ParsingException("Expected comparison operator, found: " + ctx.lookAhead(0));
        };
    }
}