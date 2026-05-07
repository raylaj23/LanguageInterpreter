package com.interpreter.lexer;

//representa a single lexical token (type, lexeme, literal, position)
public record Token(TokenType type, String lexeme, Object literal, int line, int column) {

    @Override
    public String toString() {
        if (type == TokenType.NEWLINE) return "NEWLINE";
        if (type == TokenType.EOF) return "EOF";
        return type + "(" + lexeme + ")";
    }
}
