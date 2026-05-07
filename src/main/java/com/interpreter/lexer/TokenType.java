package com.interpreter.lexer;

//enumeration of all token kinds the lexer can produce
public enum TokenType {
    // Literals
    NUMBER, IDENT,

    // Keywords
    IF, THEN, ELSE, WHILE, DO, FUN, RETURN, TRUE, FALSE,

    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT, STARSTAR,
    EQ, NEQ, LT, GT, LE, GE,
    ASSIGN,
    //handles compound assignment tokens
    PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN, STARSTAR_ASSIGN,

    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA,

    // Statement separator (significant in the grammar)
    NEWLINE,

    EOF
}
