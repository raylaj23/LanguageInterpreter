package com.interpreter.runtime;

//handles runtime values: integers, floats, and booleans
public sealed interface Value {

    record IntVal(long value) implements Value {
        @Override public String toString() { return Long.toString(value); }
    }

    //handles 64-bit IEEE-754 floating-point values
    record FloatVal(double value) implements Value {
        @Override public String toString() { return Double.toString(value); }
    }

    record BoolVal(boolean value) implements Value {
        @Override public String toString() { return value ? "true" : "false"; }
    }

    /** Human-readable type name used in error messages. */
    default String typeName() {
        return switch (this) {
            case IntVal   ignored -> "int";
            case FloatVal ignored -> "float";
            case BoolVal  ignored -> "boolean";
        };
    }
}
