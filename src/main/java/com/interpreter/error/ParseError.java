package com.interpreter.error;

//handles errors raised during parsing
public final class ParseError extends InterpreterException {
    public ParseError(String message, int line) {
        super("Parse", message, line);
    }
}
