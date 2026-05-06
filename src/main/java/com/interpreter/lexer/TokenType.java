package com.interpreter.lexer;

public enum TokenType {
    // Literals
    NUMBER, IDENT,

    // Keywords
    IF, THEN, ELSE, WHILE, DO, FUN, RETURN, TRUE, FALSE,

    // Operators
    PLUS, MINUS, STAR, SLASH,
    EQ, NEQ, LT, GT, LE, GE,
    ASSIGN,

    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA,

    // Statement separator (significant in the grammar)
    NEWLINE,

    EOF
}
