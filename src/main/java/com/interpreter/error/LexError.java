package com.interpreter.error;

//handles errors raised during lexing
public final class LexError extends InterpreterException {
    public LexError(String message, int line) {
        super("Lex", message, line);
    }
}
