package com.interpreter.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A variable scope. Environments form a chain via {@link #parent}: the innermost
 * scope is searched first on lookup, then its parent, and so on up to the root
 * (global) scope.
 *
 * <p>Insertion order is preserved (via {@link LinkedHashMap}) so the program's
 * final output lists variables in declaration order, matching the sample
 * outputs.
 *
 * <p>{@link #define} always writes into the current scope; assigning a name in
 * a function body therefore creates a local rather than mutating a global. This
 * matches Python-style "local-by-default" semantics and keeps function calls
 * from leaking state into the global output.
 */
public final class Environment {

    private final Environment parent;
    private final Map<String, Value> values = new LinkedHashMap<>();

    public Environment() { this(null); }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    /** Defines or overwrites a binding in the current scope. */
    public void define(String name, Value value) {
        values.put(name, value);
    }

    /** Returns the value bound to {@code name} in this scope or any ancestor, or {@code null} if undefined. */
    public Value get(String name) {
        if (values.containsKey(name)) return values.get(name);
        if (parent != null) return parent.get(name);
        return null;
    }

    /** Read-only view of bindings in this scope only (does not include ancestors). */
    public Map<String, Value> values() {
        return Collections.unmodifiableMap(values);
    }
}
