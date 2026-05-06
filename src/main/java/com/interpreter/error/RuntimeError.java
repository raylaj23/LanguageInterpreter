package com.interpreter.error;

public final class RuntimeError extends InterpreterException {
    public RuntimeError(String message, int line) {
        super("Runtime", message, line);
    }
}
