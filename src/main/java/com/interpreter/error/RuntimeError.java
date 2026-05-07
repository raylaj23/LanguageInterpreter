package com.interpreter.error;

//handles errors raised during program execution
public final class RuntimeError extends InterpreterException {
    public RuntimeError(String message, int line) {
        super("Runtime", message, line);
    }
}
