package com.interpreter.runtime;

//handles runtime values: integers and booleans
/**
 * Runtime value. The language has two primitive types: 64-bit signed integers
 * and booleans. Strings, arrays, and first-class functions are intentionally
 * out of scope for this prototype.
 */
public sealed interface Value {

    record IntVal(long value) implements Value {
        @Override public String toString() { return Long.toString(value); }
    }

    record BoolVal(boolean value) implements Value {
        @Override public String toString() { return value ? "true" : "false"; }
    }

    /** Human-readable type name used in error messages. */
    default String typeName() {
        return switch (this) {
            case IntVal  ignored -> "number";
            case BoolVal ignored -> "boolean";
        };
    }
}
