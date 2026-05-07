package com.interpreter.lexer;

import com.interpreter.error.LexError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//converta source text into a token stream
public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
        Map.entry("if",     TokenType.IF),
        Map.entry("then",   TokenType.THEN),
        Map.entry("else",   TokenType.ELSE),
        Map.entry("while",  TokenType.WHILE),
        Map.entry("do",     TokenType.DO),
        Map.entry("fun",    TokenType.FUN),
        Map.entry("return", TokenType.RETURN),
        Map.entry("true",   TokenType.TRUE),
        Map.entry("false",  TokenType.FALSE)
    );

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;        // start offset of the current token
    private int current = 0;      // current scan offset
    private int line = 1;
    private int column = 1;       // column of current
    private int tokenColumn = 1;  // column of start

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = current;
            tokenColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case ' ', '\t', '\r' -> { /* skip insignificant whitespace */ }
            case '\n' -> {
                addToken(TokenType.NEWLINE);
                line++;
                column = 1;
            }
            case '+' -> {
                if (match('+'))      addToken(TokenType.PLUSPLUS);
                else if (match('=')) addToken(TokenType.PLUS_ASSIGN);
                else                 addToken(TokenType.PLUS);
            }
            case '-' -> {
                if (match('-'))      addToken(TokenType.MINUSMINUS);
                else if (match('=')) addToken(TokenType.MINUS_ASSIGN);
                else                 addToken(TokenType.MINUS);
            }
            case '*' -> {
                if (match('*')) {
                    addToken(match('=') ? TokenType.STARSTAR_ASSIGN : TokenType.STARSTAR);
                } else if (match('=')) {
                    addToken(TokenType.STAR_ASSIGN);
                } else {
                    addToken(TokenType.STAR);
                }
            }
            case '/' -> addToken(match('=') ? TokenType.SLASH_ASSIGN : TokenType.SLASH);
            case '%' -> addToken(match('=') ? TokenType.PERCENT_ASSIGN : TokenType.PERCENT);
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);
            case '{' -> addToken(TokenType.LBRACE);
            case '}' -> addToken(TokenType.RBRACE);
            case ',' -> addToken(TokenType.COMMA);
            case '=' -> addToken(match('=') ? TokenType.EQ : TokenType.ASSIGN);
            case '!' -> addToken(match('=') ? TokenType.NEQ : TokenType.BANG);
            case '&' -> {
                if (match('&')) addToken(TokenType.AND);
                else throw new LexError("Unexpected character '&' (did you mean '&&' ?)", line);
            }
            case '|' -> {
                if (match('|')) addToken(TokenType.OR);
                else throw new LexError("Unexpected character '|' (did you mean '||' ?)", line);
            }
            case '<' -> addToken(match('=') ? TokenType.LE : TokenType.LT);
            case '>' -> addToken(match('=') ? TokenType.GE : TokenType.GT);
            case '#' -> skipLineComment();
            default -> {
                if (Character.isDigit(c)) {
                    number();
                } else if (isIdentStart(c)) {
                    identifier();
                } else {
                    throw new LexError("Unexpected character '" + c + "'", line);
                }
            }
        }
    }

    private void skipLineComment() {
        while (!isAtEnd() && peek() != '\n') advance();
    }

    private void number() {
        while (!isAtEnd() && Character.isDigit(peek())) advance();
        String text = source.substring(start, current);
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new LexError("Number literal out of range: " + text, line);
        }
        tokens.add(new Token(TokenType.NUMBER, text, value, line, tokenColumn));
    }

    private void identifier() {
        while (!isAtEnd() && isIdentPart(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENT);
        Object literal = switch (type) {
            case TRUE  -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            default    -> null;
        };
        tokens.add(new Token(type, text, literal, line, tokenColumn));
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
        column++;
        return true;
    }

    private char advance() {
        char c = source.charAt(current++);
        column++;
        return c;
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void addToken(TokenType type) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, null, line, tokenColumn));
    }
}
