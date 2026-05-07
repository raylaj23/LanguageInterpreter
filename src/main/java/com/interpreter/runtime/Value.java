package com.interpreter.runtime;

// runtime values: int, float, boolean
public sealed interface Value {

    record IntVal(long value) implements Value {
        @Override public String toString() { return Long.toString(value); }
    }

    // 64-bit float
    record FloatVal(double value) implements Value {
        @Override public String toString() { return Double.toString(value); }
    }

    record BoolVal(boolean value) implements Value {
        @Override public String toString() { return value ? "true" : "false"; }
    }

    // type name for error messages
    default String typeName() {
        return switch (this) {
            case IntVal   ignored -> "int";
            case FloatVal ignored -> "float";
            case BoolVal  ignored -> "boolean";
        };
    }
}
