package com.interpreter.parser;

//handles recursive-descent parsing of tokens into an AST of statements
import com.interpreter.error.ParseError;
import com.interpreter.lexer.Token;
import com.interpreter.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser. Turns a token stream into a list of top-level
 * {@link Stmt} nodes.
 *
 * <h2>Grammar (informal)</h2>
 * <pre>
 *   program     := stmt (NEWLINE+ stmt)*
 *   stmt        := assignment | if | while | fun | return | expr
 *   assignment  := IDENT ('=' | '+=' | '-=' | '*=' | '/=' | '%=' | '**=') expr
 *               |  IDENT ('++' | '--')
 *   if          := 'if' expr 'then' stmt ('else' stmt)?
 *   while       := 'while' expr 'do' whileBody
 *   whileBody   := stmt (',' stmt)*                          ; terminated by NEWLINE / RBRACE / EOF
 *   fun         := 'fun' IDENT '(' params? ')' '{' blockBody '}'
 *   blockBody   := stmt ((',' | NEWLINE)+ stmt)*             ; allows both separators inside { }
 *   return      := 'return' expr
 *
 *   expr        := logicOr
 *   logicOr     := logicAnd ('||' logicAnd)*
 *   logicAnd    := comparison ('&&' comparison)*
 *   comparison  := addition (('==' | '!=' | '<' | '>' | '<=' | '>=') addition)*
 *   addition    := multiplication (('+' | '-') multiplication)*
 *   multiplication := unary (('*' | '/' | '%') unary)*
 *   unary       := ('-' | '!') unary | power
 *   power       := primary ('**' unary)?
 *   primary     := NUMBER | 'true' | 'false' | IDENT | call | '(' expr ')'
 *   call        := IDENT '(' (expr (',' expr)*)? ')'
 * </pre>
 *
 * <h2>Key ambiguity resolutions</h2>
 * <ul>
 *   <li>The branches of an {@code if} are <em>single</em> statements -- a
 *       trailing comma after the {@code else} branch belongs to the enclosing
 *       compound, not to the {@code else}. This is what makes the third sample
 *       program ({@code while x &lt; 3 do if ... else y = y + 1, x = x + 1})
 *       produce the documented output.</li>
 *   <li>The body of a {@code while} is a comma-separated compound; it is
 *       terminated by NEWLINE (at top level) or RBRACE (inside {@code { }}).</li>
 *   <li>Inside {@code { }}, both commas and newlines separate statements --
 *       this lets function bodies be written on one line or many.</li>
 * </ul>
 */
public final class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parseProgram() {
        List<Stmt> stmts = new ArrayList<>();
        skipNewlines();
        while (!isAtEnd()) {
            stmts.add(parseStmt());
            if (isAtEnd()) break;
            if (!check(TokenType.NEWLINE)) {
                throw error("Expected newline between top-level statements");
            }
            skipNewlines();
        }
        return stmts;
    }

    // === Statements ===

    private Stmt parseStmt() {
        if (check(TokenType.FUN))    return parseFunDef();
        if (check(TokenType.IF))     return parseIf();
        if (check(TokenType.WHILE))  return parseWhile();
        if (check(TokenType.RETURN)) return parseReturn();
        return parseAssignOrExpr();
    }

    private Stmt parseFunDef() {
        Token funTok = consume(TokenType.FUN, "Expected 'fun'");
        Token name   = consume(TokenType.IDENT, "Expected function name after 'fun'");
        consume(TokenType.LPAREN, "Expected '(' after function name");

        List<String> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(consume(TokenType.IDENT, "Expected parameter name").lexeme());
            while (match(TokenType.COMMA)) {
                params.add(consume(TokenType.IDENT, "Expected parameter name after ','").lexeme());
            }
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters");
        consume(TokenType.LBRACE, "Expected '{' to start function body");

        List<Stmt> body = parseBlockBody();

        consume(TokenType.RBRACE, "Expected '}' to close function body");
        return new Stmt.FunDef(name.lexeme(), params, body, funTok.line());
    }

    private Stmt parseIf() {
        Token ifTok = consume(TokenType.IF, "Expected 'if'");
        Expr cond = parseExpr();
        consume(TokenType.THEN, "Expected 'then' after if condition");
        Stmt thenBranch = parseStmt();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = parseStmt();
        }
        return new Stmt.If(cond, thenBranch, elseBranch, ifTok.line());
    }

    private Stmt parseWhile() {
        Token whileTok = consume(TokenType.WHILE, "Expected 'while'");
        Expr cond = parseExpr();
        consume(TokenType.DO, "Expected 'do' after while condition");
        List<Stmt> body = parseWhileBody();
        return new Stmt.While(cond, body, whileTok.line());
    }

    private Stmt parseReturn() {
        Token retTok = consume(TokenType.RETURN, "Expected 'return'");
        Expr value = parseExpr();
        return new Stmt.Return(value, retTok.line());
    }

    private Stmt parseAssignOrExpr() {
        //handles postfix '++' / '--' as a statement: x++ desugars to x = x + 1
        if (check(TokenType.IDENT) && checkNextAny(TokenType.PLUSPLUS, TokenType.MINUSMINUS)) {
            Token name = consume(TokenType.IDENT, "Expected identifier");
            Token op = advance();
            String binOp = (op.type() == TokenType.PLUSPLUS) ? "+" : "-";
            Expr lhs = new Expr.Variable(name.lexeme(), name.line());
            Expr one = new Expr.NumberLit(1L, op.line());
            Expr value = new Expr.Binary(lhs, binOp, one, op.line());
            return new Stmt.Assign(name.lexeme(), value, name.line());
        }
        if (check(TokenType.IDENT) && checkNextAny(
                TokenType.ASSIGN,
                TokenType.PLUS_ASSIGN, TokenType.MINUS_ASSIGN,
                TokenType.STAR_ASSIGN, TokenType.SLASH_ASSIGN,
                TokenType.PERCENT_ASSIGN, TokenType.STARSTAR_ASSIGN)) {
            Token name = consume(TokenType.IDENT, "Expected identifier");
            Token op = advance();
            Expr value = parseExpr();
            //handles compound assignment by desugaring x op= e into x = x op e
            if (op.type() != TokenType.ASSIGN) {
                String binOp = switch (op.type()) {
                    case PLUS_ASSIGN     -> "+";
                    case MINUS_ASSIGN    -> "-";
                    case STAR_ASSIGN     -> "*";
                    case SLASH_ASSIGN    -> "/";
                    case PERCENT_ASSIGN  -> "%";
                    case STARSTAR_ASSIGN -> "**";
                    default -> throw error("Unexpected compound operator");
                };
                Expr lhs = new Expr.Variable(name.lexeme(), name.line());
                value = new Expr.Binary(lhs, binOp, value, op.line());
            }
            return new Stmt.Assign(name.lexeme(), value, name.line());
        }
        Token tok = peek();
        return new Stmt.ExprStmt(parseExpr(), tok.line());
    }

    /** Body of a {@code while ... do}: comma-separated, ends at NEWLINE / RBRACE / EOF. */
    private List<Stmt> parseWhileBody() {
        List<Stmt> stmts = new ArrayList<>();
        if (atWhileBodyEnd()) return stmts;
        stmts.add(parseStmt());
        while (match(TokenType.COMMA)) {
            if (atWhileBodyEnd()) break;
            stmts.add(parseStmt());
        }
        return stmts;
    }

    private boolean atWhileBodyEnd() {
        return isAtEnd() || check(TokenType.NEWLINE) || check(TokenType.RBRACE);
    }

    /** Body of a {@code { ... }} block: both commas and newlines separate statements. */
    private List<Stmt> parseBlockBody() {
        List<Stmt> stmts = new ArrayList<>();
        skipNewlines();
        if (check(TokenType.RBRACE) || isAtEnd()) return stmts;
        stmts.add(parseStmt());
        while (true) {
            boolean sawSeparator = false;
            while (match(TokenType.COMMA) || match(TokenType.NEWLINE)) {
                sawSeparator = true;
            }
            if (!sawSeparator) break;
            if (check(TokenType.RBRACE) || isAtEnd()) break; // trailing separator
            stmts.add(parseStmt());
        }
        return stmts;
    }

    // === Expressions (precedence climbing) ===

    private Expr parseExpr() {
        return parseLogicOr();
    }

    private Expr parseLogicOr() {
        Expr left = parseLogicAnd();
        while (check(TokenType.OR)) {
            Token op = advance();
            Expr right = parseLogicAnd();
            left = new Expr.Logical(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    private Expr parseLogicAnd() {
        Expr left = parseComparison();
        while (check(TokenType.AND)) {
            Token op = advance();
            Expr right = parseComparison();
            left = new Expr.Logical(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    private Expr parseComparison() {
        Expr left = parseAddition();
        while (checkAny(TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.GT, TokenType.LE, TokenType.GE)) {
            Token op = advance();
            Expr right = parseAddition();
            left = new Expr.Binary(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    private Expr parseAddition() {
        Expr left = parseMultiplication();
        while (checkAny(TokenType.PLUS, TokenType.MINUS)) {
            Token op = advance();
            Expr right = parseMultiplication();
            left = new Expr.Binary(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    private Expr parseMultiplication() {
        Expr left = parseUnary();
        while (checkAny(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token op = advance();
            Expr right = parseUnary();
            left = new Expr.Binary(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    private Expr parseUnary() {
        if (checkAny(TokenType.MINUS, TokenType.BANG)) {
            Token op = advance();
            Expr operand = parseUnary();
            return new Expr.Unary(op.lexeme(), operand, op.line());
        }
        return parsePower();
    }

    //handles right-associative '**' with higher precedence than unary minus on the right operand
    private Expr parsePower() {
        Expr base = parsePrimary();
        if (check(TokenType.STARSTAR)) {
            Token op = advance();
            Expr exp = parseUnary();
            return new Expr.Binary(base, op.lexeme(), exp, op.line());
        }
        return base;
    }

    private Expr parsePrimary() {
        Token tok = peek();
        if (match(TokenType.NUMBER)) {
            //handles dispatch between integer and float literal AST nodes based on the lexer's literal type
            if (tok.literal() instanceof Double d) {
                return new Expr.FloatLit(d, tok.line());
            }
            return new Expr.NumberLit((Long) tok.literal(), tok.line());
        }
        if (match(TokenType.TRUE))  return new Expr.BoolLit(true,  tok.line());
        if (match(TokenType.FALSE)) return new Expr.BoolLit(false, tok.line());
        if (match(TokenType.LPAREN)) {
            Expr expr = parseExpr();
            consume(TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }
        if (check(TokenType.IDENT)) {
            Token name = advance();
            if (match(TokenType.LPAREN)) {
                List<Expr> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (match(TokenType.COMMA)) {
                        args.add(parseExpr());
                    }
                }
                consume(TokenType.RPAREN, "Expected ')' after arguments");
                return new Expr.Call(name.lexeme(), args, name.line());
            }
            return new Expr.Variable(name.lexeme(), name.line());
        }
        throw error("Unexpected token '" + tok.lexeme() + "'");
    }

    // === Helpers ===

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    private boolean checkNext(TokenType type) {
        if (current + 1 >= tokens.size()) return false;
        return tokens.get(current + 1).type() == type;
    }

    private boolean checkNextAny(TokenType... types) {
        if (current + 1 >= tokens.size()) return false;
        TokenType t = tokens.get(current + 1).type();
        for (TokenType type : types) if (t == type) return true;
        return false;
    }

    private boolean checkAny(TokenType... types) {
        for (TokenType t : types) if (check(t)) return true;
        return false;
    }

    private boolean match(TokenType type) {
        if (check(type)) { advance(); return true; }
        return false;
    }

    private Token advance() {
        Token tok = tokens.get(current);
        if (!isAtEnd()) current++;
        return tok;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token consume(TokenType type, String msg) {
        if (check(type)) return advance();
        throw error(msg);
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private void skipNewlines() {
        while (check(TokenType.NEWLINE)) advance();
    }

    private ParseError error(String msg) {
        Token tok = peek();
        String got = tok.type() == TokenType.NEWLINE ? "newline"
                   : tok.type() == TokenType.EOF     ? "end of input"
                   : "'" + tok.lexeme() + "'";
        return new ParseError(msg + " (got " + got + ")", tok.line());
    }
}
