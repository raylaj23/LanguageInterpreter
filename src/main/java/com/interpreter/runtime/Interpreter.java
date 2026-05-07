package com.interpreter.runtime;

//handles tree-walking execution of the AST and tracks global variables
import com.interpreter.error.RuntimeError;
import com.interpreter.parser.Expr;
import com.interpreter.parser.Stmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree-walking interpreter. Executes a parsed program against an in-memory
 * environment and returns the final globals.
 *
 * <h2>Semantics in brief</h2>
 * <ul>
 *   <li>Two value types: 64-bit signed integers and booleans.</li>
 *   <li>Conditions ({@code if}, {@code while}) require a boolean -- there is no
 *       implicit truthiness, which makes type errors loud and obvious.</li>
 *   <li>Functions live in their own namespace and may only be defined at the
 *       top level. They see globals (so recursion works) but assignments
 *       inside a body create locals.</li>
 *   <li>{@code return} unwinds the call via {@link ReturnSignal}, an internal
 *       exception. Cheaper than threading a "did we return?" flag through
 *       every recursive call.</li>
 *   <li>Recursion is capped at {@link #MAX_CALL_DEPTH} so an infinite recursion
 *       surfaces as a clean runtime error rather than a {@link StackOverflowError}.</li>
 * </ul>
 */
public final class Interpreter {

    private static final int MAX_CALL_DEPTH = 1000;

    private final Environment globals = new Environment();
    private final Map<String, Stmt.FunDef> functions = new HashMap<>();
    private int callDepth = 0;

    /** Runs the program and returns the final values of all top-level (global) variables. */
    public Map<String, Value> run(List<Stmt> program) {
        for (Stmt s : program) {
            execute(s, globals);
        }
        return globals.values();
    }

    // === Statement execution ===

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

    // === Expression evaluation ===

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
                    //handles unary minus on int (overflow-checked) or float (IEEE negate)
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

            //handles short-circuit evaluation for && and ||
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
        //handles numeric type promotion: if either operand is a float, do float math; else int math
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
            //handles exponentiation: float promotion uses Math.pow; pure int uses overflow-checked squaring
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

    //handles overflow-checked integer + - *
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

    //handles IEEE-754 float + - *
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

    //handles numeric ordering across mixed int/float operands
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

        // Evaluate arguments in the caller's environment.
        Value[] argValues = new Value[c.args().size()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = evaluate(c.args().get(i), env);
        }

        if (++callDepth > MAX_CALL_DEPTH) {
            callDepth--;
            throw new RuntimeError(
                "Maximum call depth (" + MAX_CALL_DEPTH + ") exceeded -- likely infinite recursion",
                c.line());
        }
        try {
            // Function body sees globals (so it can call other functions),
            // but local assignments stay in the local frame.
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

    // === Coercion helpers ===

    private long asInt(Value v, int line) {
        if (v instanceof Value.IntVal i) return i.value();
        throw new RuntimeError("Expected int, got " + v.typeName(), line);
    }

    //handles numeric coercion: accepts int or float, returns its value as a double
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
        //handles cross-type numeric equality (int vs float) by comparing as doubles
        if ((a instanceof Value.IntVal || a instanceof Value.FloatVal)
                && (b instanceof Value.IntVal || b instanceof Value.FloatVal)) {
            return asDouble(a, line) == asDouble(b, line);
        }
        throw new RuntimeError(
            "Cannot compare " + a.typeName() + " with " + b.typeName(),
            line);
    }

    /**
     * Internal control-flow exception used to unwind a function call on
     * {@code return}. Stack trace and suppression are disabled because we
     * never want this to surface to the user.
     */
    private static final class ReturnSignal extends RuntimeException {
        final Value value;
        ReturnSignal(Value value) {
            super(null, null, false, false);
            this.value = value;
        }
    }
}
