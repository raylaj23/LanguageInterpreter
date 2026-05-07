package com.interpreter.parser;

import com.interpreter.error.ParseError;
import com.interpreter.lexer.Token;
import com.interpreter.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

// recursive-descent parsing of tokens into an AST of statements
public final class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // parse the entire token stream into a list of top-level stmts
    public List<Stmt> parseProgram() {
        List<Stmt> stmts = new ArrayList<>();
        skipNewlines();
        while (!isAtEnd()) {
            stmts.add(parseStmt());
            if (isAtEnd()) break;
            // top-level statements must be newline separated
            // commas are reserved for separating stmts.
            if (!check(TokenType.NEWLINE)) {
                throw error("Expected newline between top-level statements");
            }
            skipNewlines();
        }
        return stmts;
    }

    // statements
    private Stmt parseStmt() {
        if (check(TokenType.FUN))
            return parseFunDef();
        if (check(TokenType.IF))
            return parseIf();
        if (check(TokenType.WHILE))
            return parseWhile();
        if (check(TokenType.RETURN))
            return parseReturn();
        return parseAssignOrExpr();
    }

    // fun name (...) { ... }
    private Stmt parseFunDef() {
        Token funTok = consume(TokenType.FUN, "Expected 'fun'");
        Token name = consume(TokenType.IDENT, "Expected function name after 'fun'");
        consume(TokenType.LPAREN, "Expected '(' after function name");

        // parameter list - is it possible to be empty
        List<String> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(consume(TokenType.IDENT, "Expected parameter name").lexeme());
            while (match(TokenType.COMMA)) {
                params.add(consume(TokenType.IDENT, "Expected parameter name after ','").lexeme());
            }
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters");
        consume(TokenType.LBRACE, "Expected '{' to start function body");

        // function body uses block rules
        List<Stmt> body = parseBlockBody();

        consume(TokenType.RBRACE, "Expected '}' to close function body");
        return new Stmt.FunDef(name.lexeme(), params, body, funTok.line());
    }

    // if cond then stmt else stmt
    private Stmt parseIf() {
        Token ifTok = consume(TokenType.IF, "Expected 'if'");
        Expr cond = parseExpr();
        consume(TokenType.THEN, "Expected 'then' after if condition");
        Stmt thenBranch = parseStmt();
        Stmt elseBranch = null;
        // else is optional
        if (match(TokenType.ELSE)) {
            elseBranch = parseStmt();
        }
        return new Stmt.If(cond, thenBranch, elseBranch, ifTok.line());
    }

    // while cont do body
    private Stmt parseWhile() {
        Token whileTok = consume(TokenType.WHILE, "Expected 'while'");
        Expr cond = parseExpr();
        consume(TokenType.DO, "Expected 'do' after while condition");
        List<Stmt> body = parseWhileBody();
        return new Stmt.While(cond, body, whileTok.line());
    }

    // return expr
    private Stmt parseReturn() {
        Token retTok = consume(TokenType.RETURN, "Expected 'return'");
        Expr value = parseExpr();
        return new Stmt.Return(value, retTok.line());
    }

    // we look two tokens ahead to decide
    // if we see IDENT followed by an assignment-like operator, it's an assignment
    // else we fall through and parse the whole thing as an expression statement
    private Stmt parseAssignOrExpr() {
        // ++ --
        if (check(TokenType.IDENT) && checkNextAny(TokenType.PLUSPLUS, TokenType.MINUSMINUS)) {
            Token name = consume(TokenType.IDENT, "Expected identifier");
            Token op = advance();
            String binOp = (op.type() == TokenType.PLUSPLUS) ? "+" : "-";
            Expr lhs = new Expr.Variable(name.lexeme(), name.line());
            Expr one = new Expr.NumberLit(1L, op.line());
            Expr value = new Expr.Binary(lhs, binOp, one, op.line());
            return new Stmt.Assign(name.lexeme(), value, name.line());
        }
        // = or += -= *= /= %= **=
        if (check(TokenType.IDENT) && checkNextAny(
                TokenType.ASSIGN,
                TokenType.PLUS_ASSIGN, TokenType.MINUS_ASSIGN,
                TokenType.STAR_ASSIGN, TokenType.SLASH_ASSIGN,
                TokenType.PERCENT_ASSIGN, TokenType.STARSTAR_ASSIGN)) {
            Token name = consume(TokenType.IDENT, "Expected identifier");
            Token op = advance();
            Expr value = parseExpr();
            // compound assignment - rewrite it into basic assigment
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
        // expression statement
        Token tok = peek();
        return new Stmt.ExprStmt(parseExpr(), tok.line());
    }

    // while body - comma separated, ends at NEWLINE / RBRACE / EOF
    private List<Stmt> parseWhileBody() {
        List<Stmt> stmts = new ArrayList<>();
        if (atWhileBodyEnd()) return stmts;
        stmts.add(parseStmt());
        while (match(TokenType.COMMA)) {
            // trailing comma before terminator is allowed
            if (atWhileBodyEnd()) break;
            stmts.add(parseStmt());
        }
        return stmts;
    }

    private boolean atWhileBodyEnd() {
        return isAtEnd() || check(TokenType.NEWLINE) || check(TokenType.RBRACE);
    }

    // { ... } block - commas and newlines both separate stmts
    private List<Stmt> parseBlockBody() {
        List<Stmt> stmts = new ArrayList<>();
        skipNewlines();
        if (check(TokenType.RBRACE) || isAtEnd()) return stmts;
        stmts.add(parseStmt());
        while (true) {
            // eat any run of commas/newlines between stmts
            boolean sawSeparator = false;
            while (match(TokenType.COMMA) || match(TokenType.NEWLINE)) {
                sawSeparator = true;
            }
            if (!sawSeparator) break;
            // trailing separator before '}'
            if (check(TokenType.RBRACE) || isAtEnd()) break;
            stmts.add(parseStmt());
        }
        return stmts;
    }

    // expressions (precedence climbing)
    private Expr parseExpr() {
        return parseLogicOr();
    }

    // || - lowest precedence, short-circuits
    private Expr parseLogicOr() {
        Expr left = parseLogicAnd();
        while (check(TokenType.OR)) {
            Token op = advance();
            Expr right = parseLogicAnd();
            left = new Expr.Logical(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    // && - short-circuits
    private Expr parseLogicAnd() {
        Expr left = parseComparison();
        while (check(TokenType.AND)) {
            Token op = advance();
            Expr right = parseComparison();
            left = new Expr.Logical(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    // == != < > <= >=
    private Expr parseComparison() {
        Expr left = parseAddition();
        while (checkAny(TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.GT, TokenType.LE, TokenType.GE)) {
            Token op = advance();
            Expr right = parseAddition();
            left = new Expr.Binary(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    // + -
    private Expr parseAddition() {
        Expr left = parseMultiplication();
        while (checkAny(TokenType.PLUS, TokenType.MINUS)) {
            Token op = advance();
            Expr right = parseMultiplication();
            left = new Expr.Binary(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    // * / %
    private Expr parseMultiplication() {
        Expr left = parseUnary();
        while (checkAny(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token op = advance();
            Expr right = parseUnary();
            left = new Expr.Binary(left, op.lexeme(), right, op.line());
        }
        return left;
    }

    // unary - !
    private Expr parseUnary() {
        if (checkAny(TokenType.MINUS, TokenType.BANG)) {
            Token op = advance();
            Expr operand = parseUnary();
            return new Expr.Unary(op.lexeme(), operand, op.line());
        }
        return parsePower();
    }

    // ** - right-associative
    private Expr parsePower() {
        Expr base = parsePrimary();
        if (check(TokenType.STARSTAR)) {
            Token op = advance();
            Expr exp = parseUnary();
            return new Expr.Binary(base, op.lexeme(), exp, op.line());
        }
        return base;
    }

    // primary - literals, (expr), var, call
    private Expr parsePrimary() {
        Token tok = peek();
        if (match(TokenType.NUMBER)) {
            // int vs float literal
            if (tok.literal() instanceof Double d) {
                return new Expr.FloatLit(d, tok.line());
            }
            return new Expr.NumberLit((Long) tok.literal(), tok.line());
        }
        if (match(TokenType.TRUE))  return new Expr.BoolLit(true,  tok.line());
        if (match(TokenType.FALSE)) return new Expr.BoolLit(false, tok.line());
        // ( expr ) - just grouping
        if (match(TokenType.LPAREN)) {
            Expr expr = parseExpr();
            consume(TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }
        if (check(TokenType.IDENT)) {
            Token name = advance();
            // IDENT '(' is a call, otherwise it's a variable
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

    // helpers

    // is current token this type?
    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    // is the next token this type? (one-token lookahead)
    private boolean checkNext(TokenType type) {
        if (current + 1 >= tokens.size()) return false;
        return tokens.get(current + 1).type() == type;
    }

    // next token any of these?
    private boolean checkNextAny(TokenType... types) {
        if (current + 1 >= tokens.size()) return false;
        TokenType t = tokens.get(current + 1).type();
        for (TokenType type : types) if (t == type) return true;
        return false;
    }

    // current token any of these?
    private boolean checkAny(TokenType... types) {
        for (TokenType t : types) if (check(t)) return true;
        return false;
    }

    // consume if it matches
    private boolean match(TokenType type) {
        if (check(type)) { advance(); return true; }
        return false;
    }

    // take current token and step forward
    private Token advance() {
        Token tok = tokens.get(current);
        if (!isAtEnd()) current++;
        return tok;
    }

    private Token peek() {
        return tokens.get(current);
    }

    // consume or throw
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

    // build a ParseError tagged with the current token's line
    private ParseError error(String msg) {
        Token tok = peek();
        String got = tok.type() == TokenType.NEWLINE ? "newline"
                   : tok.type() == TokenType.EOF     ? "end of input"
                   : "'" + tok.lexeme() + "'";
        return new ParseError(msg + " (got " + got + ")", tok.line());
    }
}
