package com.interpreter.lexer;

//all token types the lexer can produce
public enum TokenType {
    // literals
    NUMBER, IDENT,

    // keywords
    IF, THEN, ELSE, WHILE, DO, FUN, RETURN, TRUE, FALSE,

    // operators
    PLUS, MINUS, STAR, SLASH, PERCENT, STARSTAR,
    EQ, NEQ, LT, GT, LE, GE,
    // logical operator
    AND, OR, BANG,
    ASSIGN,
    // compound assignment
    PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN, STARSTAR_ASSIGN,
    // increment and decrement
    PLUSPLUS, MINUSMINUS,

    // punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA,

    // statement separator
    NEWLINE,

    EOF
}
