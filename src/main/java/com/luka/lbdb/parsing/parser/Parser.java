package com.luka.lbdb.parsing.parser;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.parser.parseTypes.*;
import com.luka.lbdb.parsing.statement.ExplainStatement;
import com.luka.lbdb.parsing.statement.Statement;
import com.luka.lbdb.parsing.statement.TransactionStatement;
import com.luka.lbdb.parsing.statement.transaction.TransactionAction;
import com.luka.lbdb.parsing.tokenizer.token.*;

/// The parsing system's grammar, which corresponds to the operations that
/// the virtual machine is able to execute, can be defined as this:
///
/// ```
/// <Parse>     := <ParseSelect> | <ParseInsert> | <ParseUpdate> | <ParseDelete> |
///                CREATE <ParseCreateTable> | CREATE <ParseCreateIndex> |
///                START TRANSACTION | COMMIT | ROLLBACK | EXPLAIN <Parse>;
/// ```
///
/// Each syntactic category is defined within its own class, for maintainability
/// and readability of the code. The main syntactic category (and the main entry point)
/// is the `<Parse>` category which references all other categories.
/// A parser is responsible for enforcing this grammar on provided
/// user queries and creating the parse tree. A parser can't know the semantic
/// correctness of the parse tree, and it can't enforce the lists being the same
/// size where they are used. For that, the planner is needed.
/// The concrete implementation is based on a "Recursive Descent" parsing strategy
/// with the "Pratt Parser" strategy for parsing arithmetic expressions.
public class Parser {
    private final ParserContext ctx;

    /// The topmost parser needs the query to initialize the context.
    public Parser(String query) {
        ctx = new ParserContext(query);
    }

    /// The main entry point of query parsing. Initializes every
    /// sub syntactic category per the appropriate keyword.
    ///
    /// @return A parsed statement which can be of different types.
    /// @throws ParsingException if a syntactic error was made during parsing.
    public Statement parse() {
        Statement statement = switch (ctx.lookAhead(0)) {
            case KeywordToken.SELECT -> new ParseSelect(ctx).parse();
            case KeywordToken.INSERT -> new ParseInsert(ctx).parse();
            case KeywordToken.UPDATE -> new ParseUpdate(ctx).parse();
            case KeywordToken.DELETE -> new ParseDelete(ctx).parse();
            case KeywordToken.CREATE -> {
                ctx.advance();
                yield switch (ctx.lookAhead(0)) {
                    case KeywordToken.TABLE -> new ParseCreateTable(ctx).parse();
                    case KeywordToken.INDEX -> new ParseCreateIndex(ctx).parse();
                    default -> throw new ParsingException("Invalid CREATE target: " + ctx.lookAhead(0));
                };
            }
            case KeywordToken.START -> {
                ctx.advance();
                ctx.eat(KeywordToken.TRANSACTION);
                yield new TransactionStatement(TransactionAction.START_TRANSACTION);
            }
            case KeywordToken.COMMIT -> {
                ctx.advance();
                yield new TransactionStatement(TransactionAction.COMMIT);
            }
            case KeywordToken.ROLLBACK -> {
                ctx.advance();
                yield new TransactionStatement(TransactionAction.ROLLBACK);
            }
            case KeywordToken.EXPLAIN -> {
                ctx.advance();
                yield new ExplainStatement(parse());
            }
            case EofToken() -> throw new ParsingException("Unexpected end of input");
            default -> throw new ParsingException("Expected keyword, found: " + ctx.lookAhead(0));
        };

        if (!(statement instanceof ExplainStatement)) {
            ctx.eat(SymbolToken.SEMICOLON);
        }

        return statement;
    }
}