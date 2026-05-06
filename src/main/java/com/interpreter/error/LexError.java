package com.interpreter.error;

public final class LexError extends InterpreterException {
    public LexError(String message, int line) {
        super("Lex", message, line);
    }
}
