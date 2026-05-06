package com.interpreter.error;

/**
 * Base class for all interpreter errors. Carries the source line so error messages
 * can point the user at the offending construct.
 *
 * <p>Subclasses correspond to the three pipeline stages: lexing, parsing, runtime.
 * They are unchecked so they can propagate freely through the recursive descent
 * parser and tree-walking interpreter without polluting every method signature.
 */
public abstract class InterpreterException extends RuntimeException {
    private final int line;

    protected InterpreterException(String stage, String message, int line) {
        super("[line " + line + "] " + stage + " error: " + message);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
