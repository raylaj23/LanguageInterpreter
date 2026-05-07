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
    //handles logical operator tokens
    AND, OR, BANG,
    ASSIGN,
    //handles compound assignment tokens
    PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN, STARSTAR_ASSIGN,
    //handles increment and decrement tokens
    PLUSPLUS, MINUSMINUS,

    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA,

    // Statement separator (significant in the grammar)
    NEWLINE,

    EOF
}
