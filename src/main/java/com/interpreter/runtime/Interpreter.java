package com.interpreter.runtime;

import com.interpreter.error.RuntimeError;
import com.interpreter.parser.Expr;
import com.interpreter.parser.Stmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// tree-walking interpreter - executes the AST and tracks globals
public final class Interpreter {

    private static final int MAX_CALL_DEPTH = 1000;

    private final Environment globals = new Environment();
    private final Map<String, Stmt.FunDef> functions = new HashMap<>();
    private int callDepth = 0;

    // run the program and return the final globals
    public Map<String, Value> run(List<Stmt> program) {
        for (Stmt s : program) {
            execute(s, globals);
        }
        return globals.values();
    }

    // statement execution
    private void execute(Stmt stmt, Environment env) {
        switch (stmt) {
            case Stmt.Assign a -> env.define(a.name(), evaluate(a.value(), env));

            case Stmt.If i -> {
                if (asBool(evaluate(i.condition(), env), i.line())) {
                    execute(i.thenBranch(), env);
                } else if (i.elseBranch() != null) {
                    execute(i.elseBranch(), env);
                }
            }

            case Stmt.While w -> {
                while (asBool(evaluate(w.condition(), env), w.line())) {
                    for (Stmt s : w.body()) {
                        execute(s, env);
                    }
                }
            }

            case Stmt.FunDef f -> {
                // top-level only, no redefinition
                if (env != globals) {
                    throw new RuntimeError("Functions can only be defined at the top level", f.line());
                }
                if (functions.containsKey(f.name())) {
                    throw new RuntimeError("Function '" + f.name() + "' is already defined", f.line());
                }
                functions.put(f.name(), f);
            }

            case Stmt.Return r -> {
                throw new ReturnSignal(evaluate(r.value(), env));
            }

            case Stmt.ExprStmt e -> evaluate(e.expr(), env);
        }
    }

    // expression evaluation
    private Value evaluate(Expr expr, Environment env) {
        return switch (expr) {
            case Expr.NumberLit n -> new Value.IntVal(n.value());
            case Expr.FloatLit  f -> new Value.FloatVal(f.value());
            case Expr.BoolLit  b  -> new Value.BoolVal(b.value());

            case Expr.Variable v -> {
                Value val = env.get(v.name());
                if (val == null) {
                    throw new RuntimeError("Undefined variable '" + v.name() + "'", v.line());
                }
                yield val;
            }

            case Expr.Unary u -> {
                Value operand = evaluate(u.operand(), env);
                if (u.op().equals("-")) {
                    // unary minus - float is plain negate, int is overflow-checked
                    if (operand instanceof Value.FloatVal fv) yield new Value.FloatVal(-fv.value());
                    long n = asInt(operand, u.line());
                    try {
                        yield new Value.IntVal(Math.negateExact(n));
                    } catch (ArithmeticException e) {
                        throw new RuntimeError("Arithmetic overflow on unary '-'", u.line());
                    }
                }
                if (u.op().equals("!")) {
                    yield new Value.BoolVal(!asBool(operand, u.line()));
                }
                throw new RuntimeError("Unknown unary operator '" + u.op() + "'", u.line());
            }

            case Expr.Binary b -> evalBinary(b, env);

            // && and || - short-circuiting
            case Expr.Logical lg -> {
                boolean lb = asBool(evaluate(lg.left(), env), lg.line());
                if (lg.op().equals("&&")) {
                    if (!lb) yield new Value.BoolVal(false);
                } else { // "||"
                    if (lb) yield new Value.BoolVal(true);
                }
                yield new Value.BoolVal(asBool(evaluate(lg.right(), env), lg.line()));
            }

            case Expr.Call c -> evalCall(c, env);
        };
    }

    private Value evalBinary(Expr.Binary b, Environment env) {
        Value l = evaluate(b.left(), env);
        Value r = evaluate(b.right(), env);
        // numeric promotion - any float operand bumps the whole op to float
        boolean fp = (l instanceof Value.FloatVal) || (r instanceof Value.FloatVal);
        return switch (b.op()) {
            case "+", "-", "*" -> fp ? floatArith(l, r, b.op(), b.line()) : intArith(l, r, b.op(), b.line());
            case "/" -> {
                if (fp) {
                    yield new Value.FloatVal(asDouble(l, b.line()) / asDouble(r, b.line()));
                }
                long li = asInt(l, b.line());
                long ri = asInt(r, b.line());
                if (ri == 0) throw new RuntimeError("Division by zero", b.line());
                if (li == Long.MIN_VALUE && ri == -1) {
                    throw new RuntimeError("Arithmetic overflow on '/'", b.line());
                }
                yield new Value.IntVal(li / ri);
            }
            case "%" -> {
                if (fp) {
                    yield new Value.FloatVal(asDouble(l, b.line()) % asDouble(r, b.line()));
                }
                long li = asInt(l, b.line());
                long ri = asInt(r, b.line());
                if (ri == 0) throw new RuntimeError("Modulo by zero", b.line());
                yield new Value.IntVal(li % ri);
            }
            // ** - float uses Math.pow, int uses overflow-checked squaring
            case "**" -> {
                if (fp) yield new Value.FloatVal(Math.pow(asDouble(l, b.line()), asDouble(r, b.line())));
                long base = asInt(l, b.line());
                long exp  = asInt(r, b.line());
                if (exp < 0) {
                    throw new RuntimeError("Negative exponent not supported for integer '**'", b.line());
                }
                long result = 1;
                try {
                    while (exp > 0) {
                        if ((exp & 1) == 1) result = Math.multiplyExact(result, base);
                        exp >>= 1;
                        if (exp > 0) base = Math.multiplyExact(base, base);
                    }
                } catch (ArithmeticException e) {
                    throw new RuntimeError("Arithmetic overflow on '**'", b.line());
                }
                yield new Value.IntVal(result);
            }
            case "==" -> new Value.BoolVal(equals(l, r, b.line()));
            case "!=" -> new Value.BoolVal(!equals(l, r, b.line()));
            case "<"  -> new Value.BoolVal(numCmp(l, r, b.line()) <  0);
            case ">"  -> new Value.BoolVal(numCmp(l, r, b.line()) >  0);
            case "<=" -> new Value.BoolVal(numCmp(l, r, b.line()) <= 0);
            case ">=" -> new Value.BoolVal(numCmp(l, r, b.line()) >= 0);
            default   -> throw new RuntimeError("Unknown operator '" + b.op() + "'", b.line());
        };
    }

    // overflow-checked int + - *
    private Value intArith(Value l, Value r, String op, int line) {
        long li = asInt(l, line);
        long ri = asInt(r, line);
        try {
            return new Value.IntVal(switch (op) {
                case "+" -> Math.addExact(li, ri);
                case "-" -> Math.subtractExact(li, ri);
                case "*" -> Math.multiplyExact(li, ri);
                default  -> throw new RuntimeError("Unknown operator '" + op + "'", line);
            });
        } catch (ArithmeticException e) {
            throw new RuntimeError("Arithmetic overflow", line);
        }
    }

    // float + - *
    private Value floatArith(Value l, Value r, String op, int line) {
        double li = asDouble(l, line);
        double ri = asDouble(r, line);
        return new Value.FloatVal(switch (op) {
            case "+" -> li + ri;
            case "-" -> li - ri;
            case "*" -> li * ri;
            default  -> throw new RuntimeError("Unknown operator '" + op + "'", line);
        });
    }

    // numeric ordering across mixed int/float operands
    private int numCmp(Value l, Value r, int line) {
        if (l instanceof Value.FloatVal || r instanceof Value.FloatVal) {
            return Double.compare(asDouble(l, line), asDouble(r, line));
        }
        return Long.compare(asInt(l, line), asInt(r, line));
    }

    private Value evalCall(Expr.Call c, Environment env) {
        Stmt.FunDef def = functions.get(c.callee());
        if (def == null) {
            throw new RuntimeError("Undefined function '" + c.callee() + "'", c.line());
        }
        if (def.params().size() != c.args().size()) {
            throw new RuntimeError(
                "Function '" + c.callee() + "' expects " + def.params().size()
                    + " argument(s), got " + c.args().size(),
                c.line());
        }

        // evaluate args in the caller's env
        Value[] argValues = new Value[c.args().size()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = evaluate(c.args().get(i), env);
        }

        // recursion guard
        if (++callDepth > MAX_CALL_DEPTH) {
            callDepth--;
            throw new RuntimeError(
                "Maximum call depth (" + MAX_CALL_DEPTH + ") exceeded -- likely infinite recursion",
                c.line());
        }
        try {
            // body sees globals (so it can call other funcs), locals stay in the local frame
            Environment local = new Environment(globals);
            for (int i = 0; i < def.params().size(); i++) {
                local.define(def.params().get(i), argValues[i]);
            }
            try {
                for (Stmt s : def.body()) {
                    execute(s, local);
                }
            } catch (ReturnSignal rs) {
                return rs.value;
            }
            throw new RuntimeError(
                "Function '" + c.callee() + "' finished without returning a value",
                c.line());
        } finally {
            callDepth--;
        }
    }

    // coercion helpers

    private long asInt(Value v, int line) {
        if (v instanceof Value.IntVal i) return i.value();
        throw new RuntimeError("Expected int, got " + v.typeName(), line);
    }

    // accept int or float, return as double
    private double asDouble(Value v, int line) {
        if (v instanceof Value.FloatVal f) return f.value();
        if (v instanceof Value.IntVal i)   return (double) i.value();
        throw new RuntimeError("Expected number, got " + v.typeName(), line);
    }

    private boolean asBool(Value v, int line) {
        if (v instanceof Value.BoolVal b) return b.value();
        throw new RuntimeError("Expected boolean (in condition), got " + v.typeName(), line);
    }

    private boolean equals(Value a, Value b, int line) {
        if (a instanceof Value.IntVal ai && b instanceof Value.IntVal bi) {
            return ai.value() == bi.value();
        }
        if (a instanceof Value.BoolVal ab && b instanceof Value.BoolVal bb) {
            return ab.value() == bb.value();
        }
        // cross-type int/float equality - compare as doubles
        if ((a instanceof Value.IntVal || a instanceof Value.FloatVal)
                && (b instanceof Value.IntVal || b instanceof Value.FloatVal)) {
            return asDouble(a, line) == asDouble(b, line);
        }
        throw new RuntimeError(
            "Cannot compare " + a.typeName() + " with " + b.typeName(),
            line);
    }

    // internal control-flow exception used to unwind a function call on `return`
    // stack trace and suppression are disabled - never surfaced to the user
    private static final class ReturnSignal extends RuntimeException {
        final Value value;
        ReturnSignal(Value value) {
            super(null, null, false, false);
            this.value = value;
        }
    }
}
