package com.interpreter.error;

//handles the base error type and carryi the source line
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
