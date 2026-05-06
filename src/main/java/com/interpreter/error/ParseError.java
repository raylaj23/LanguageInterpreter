package com.interpreter.error;

public final class ParseError extends InterpreterException {
    public ParseError(String message, int line) {
        super("Parse", message, line);
    }
}
