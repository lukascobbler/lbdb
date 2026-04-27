package com.luka.lbdb.parsing.parser.parseTypes;

import com.luka.lbdb.parsing.exceptions.ParsingException;
import com.luka.lbdb.parsing.parser.ParserContext;
import com.luka.lbdb.parsing.tokenizer.token.*;
import com.luka.lbdb.querying.virtualEntities.constant.BooleanConstant;
import com.luka.lbdb.querying.virtualEntities.constant.IntConstant;
import com.luka.lbdb.querying.virtualEntities.constant.NullConstant;
import com.luka.lbdb.querying.virtualEntities.constant.StringConstant;
import com.luka.lbdb.querying.virtualEntities.expression.*;

/// The class responsible for parsing expressions using "Pratt Parsing".
/// Its subgrammar is defined like this:
///
/// ```
/// <ConstantExpression>            := StringConstant | IntConstant | BooleanConstant | NullConstant
/// <Expression>                    := <BinaryArithmeticExpression> | <UnaryArithmeticExpression> |
///                                    <PrimaryExpression>
/// <PrimaryExpression>             := <FieldNameExpression> | <ConstantExpression> |
///                                    <WildcardExpression> | "(" <Expression> ")"
/// <BinaryArithmeticExpression>    := <Expression> ( "*" | "/" | "+" | "-" | "^" ) <Expression>
/// <UnaryArithmeticExpression>     := ( "+" | "-" ) <Expression>
/// ```
///
/// Pratt Parsing is a special type of Top-Down Operator Precedence parsing where
/// each operator has a special number attached to it - its precedence. This way,
/// for example, multiplication and division can be calculated before subtraction
/// and addition. Three main functions exist that call each other recursively.
public class ParseExpression {
    private static final int PREFIX_PRECEDENCE = 100;
    private final ParserContext ctx;

    /// Every syntactic category requires the parse context to
    /// be initialized.
    public ParseExpression(ParserContext ctx) {
        this.ctx = ctx;
    }

    /// Parses a string for as long as it has expression tokens.
    /// Treats the whole expression as one big sub-expression.
    ///
    /// @return The resulting expression from the string.
    public Expression parse() {
        return parseExpression(0);
    }

    /// The main entry point for parsing a new sub-expression.
    /// Essentially, this function iterates over operator tokens
    /// and combines their right side to the total current expression if
    /// the precedence is higher than the initial sub-expression
    /// precedence. If the precedence is lower or equal, it just returns the total
    /// current expression (the left side) without combining the right side.
    ///
    /// @return The parsed sub-expression.
    private Expression parseExpression(int precedence) {
        Expression left = parsePrefix();

        while (precedence < getPrecedence(ctx.current())) {
            Token opToken = ctx.advance();
            left = parseInfix(left, opToken);
        }

        return left;
    }

    /// The function responsible for parsing singular "things", be it constants
    /// or operations that apply to one "thing". Some examples are identifier, string
    /// and wildcard tokens, which are mapped to their respective expression
    /// variants. Other more nuanced singular tokens like unary operators require
    /// parsing of their inner value as a "right only" expression, and parentheses
    /// create totally new sub-expressions but are also pretty much unary operators.
    private Expression parsePrefix() {
        return switch (ctx.advance()) {
            case IdentifierToken(String name) -> {
                if (ctx.eatIfMatches(SymbolToken.DOT)) {
                    if (ctx.eatIfMatches(SymbolToken.STAR)) {
                        yield new WildcardExpression(name);
                    } else {
                        yield new FieldNameExpression(ctx.eatIdentifier(), name);
                    }
                }

                yield new FieldNameExpression(name);
            }
            case IntegerToken(int val) -> new ConstantExpression(new IntConstant(val));
            case StringToken(String val) -> new ConstantExpression(new StringConstant(val));
            case KeywordToken.TRUE -> new ConstantExpression(new BooleanConstant(true));
            case KeywordToken.FALSE -> new ConstantExpression(new BooleanConstant(false));
            case KeywordToken.NULL -> new ConstantExpression(NullConstant.INSTANCE);
            case SymbolToken sym -> switch (sym) {
                case MINUS -> new UnaryArithmeticExpression(ArithmeticOperator.SUB, parseExpression(PREFIX_PRECEDENCE));
                case PLUS  -> new UnaryArithmeticExpression(ArithmeticOperator.ADD, parseExpression(PREFIX_PRECEDENCE));
                case STAR  -> new WildcardExpression();
                case LEFT_PAREN -> {
                    Expression inner = parseExpression(0);
                    ctx.eat(SymbolToken.RIGHT_PAREN);
                    yield inner;
                }
                default -> throw new ParsingException("Unexpected symbol: " + sym);
            };
            default -> throw new ParsingException("Expected expression, found: " + ctx.current());
        };
    }

    /// The function actually responsible for gluing the right side of the
    /// expression to the left side. Since a right side to some binary
    /// operation is also a sub-expression (no matter how trivial it may be),
    /// it needs to be parsed first with the precedence of the operator.
    /// When the right side finishes parsing, a binary expression consisting of
    /// the left side, the operator and the right side is returned.
    ///
    /// The default is left associativity where right is glued to left, and evaluation is
    /// from left to right, but some operators like the ^ (exponent) operator
    /// require right associativity where results should be evaluated from left
    /// to right. To achieve this, precedence needs to be higher than the below
    /// category of precedences, but lower than the precedence of this right
    /// associative operator.
    ///
    /// @return The combined left and right binary expression.
    private Expression parseInfix(Expression left, Token opToken) {
        ArithmeticOperator op = switch (opToken) {
            case SymbolToken.PLUS -> ArithmeticOperator.ADD;
            case SymbolToken.MINUS -> ArithmeticOperator.SUB;
            case SymbolToken.STAR -> ArithmeticOperator.MUL;
            case SymbolToken.DIVIDE -> ArithmeticOperator.DIV;
            case SymbolToken.CARET  -> ArithmeticOperator.POWER;
            default -> throw new ParsingException("Unknown arithmetic operator: " + opToken);
        };

        int precedence = getPrecedence(opToken);
        int rightBindingPower = isRightAssociative(op) ? precedence - 1 : precedence;

        Expression right = parseExpression(rightBindingPower);

        return new BinaryArithmeticExpression(left, op, right);
    }

    /// @return The precedence of the arithmetic operator token.
    /// Returns 0 for all non-arithmetic tokens.
    private int getPrecedence(Token opToken) {
        return switch (opToken) {
            case SymbolToken.CARET -> 30;
            case SymbolToken.STAR, SymbolToken.DIVIDE -> 20;
            case SymbolToken.PLUS, SymbolToken.MINUS -> 10;
            default -> 0;
        };
    }

    /// @return Whether an operator is right-associative.
    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private boolean isRightAssociative(ArithmeticOperator operator) {
        return switch (operator) {
            case ArithmeticOperator.POWER -> true;
            default -> false;
        };
    }
}