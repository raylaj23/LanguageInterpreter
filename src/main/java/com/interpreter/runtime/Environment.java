package com.interpreter.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// variable scopes chained from inner to outer for name lookup
public final class Environment {

    private final Environment parent;
    private final Map<String, Value> values = new LinkedHashMap<>();

    public Environment() { this(null); }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    // define or overwrite a binding in the current scope
    public void define(String name, Value value) {
        values.put(name, value);
    }

    // look up a name in this scope, then ancestors. null if undefined.
    public Value get(String name) {
        if (values.containsKey(name)) return values.get(name);
        if (parent != null) return parent.get(name);
        return null;
    }

    // read-only view of bindings in this scope only (no ancestors)
    public Map<String, Value> values() {
        return Collections.unmodifiableMap(values);
    }
}
