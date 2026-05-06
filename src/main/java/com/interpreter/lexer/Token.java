package com.interpreter.lexer;

/**
 * A single lexical token. The {@code literal} field holds a parsed value for
 * {@link TokenType#NUMBER} ({@link Long}) and {@link TokenType#TRUE} /
 * {@link TokenType#FALSE} ({@link Boolean}); it is {@code null} otherwise.
 */
public record Token(TokenType type, String lexeme, Object literal, int line, int column) {

    @Override
    public String toString() {
        if (type == TokenType.NEWLINE) return "NEWLINE";
        if (type == TokenType.EOF) return "EOF";
        return type + "(" + lexeme + ")";
    }
}
