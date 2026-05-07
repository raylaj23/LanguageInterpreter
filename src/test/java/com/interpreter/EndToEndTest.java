package com.interpreter;

//handles end-to-end tests running source through the full lex/parse/interpret pipeline
import com.interpreter.lexer.Lexer;
import com.interpreter.lexer.Token;
import com.interpreter.parser.Parser;
import com.interpreter.parser.Stmt;
import com.interpreter.runtime.Interpreter;
import com.interpreter.runtime.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests covering the six sample programs from the spec, plus
 * a handful of error and edge cases.
 */
final class EndToEndTest {

    private static Map<String, Value> run(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        List<Stmt> program = new Parser(tokens).parseProgram();
        return new Interpreter().run(program);
    }

    private static String formatted(String source) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Value> e : run(source).entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    // the six sample programs

    @Test @DisplayName("sample 1: arithmetic")
    void sampleArithmetic() {
        assertEquals(
            "x: 2\ny: 8\n",
            formatted("""
                x = 2
                y = (x + 2) * 2
                """));
    }

    @Test @DisplayName("sample 2: if/then/else")
    void sampleIfElse() {
        assertEquals(
            "x: 20\ny: 100\n",
            formatted("""
                x = 20
                if x > 10 then y = 100 else y = 0
                """));
    }

    @Test @DisplayName("sample 3: while loop with comma-compound body")
    void sampleWhileLoop() {
        assertEquals(
            "x: 3\ny: 11\n",
            formatted("""
                x = 0
                y = 0
                while x < 3 do if x == 1 then y = 10 else y = y + 1, x = x + 1
                """));
    }

    @Test @DisplayName("sample 4: simple function call")
    void sampleFunction() {
        assertEquals("four: 4\n", formatted("""
            fun add(a, b) { return a + b }
            four = add( 2, 2)
            """));
    }

    @Test @DisplayName("sample 5: recursive factorial")
    void sampleRecursiveFactorial() {
        assertEquals("a: 120\n", formatted("""
            fun fact_rec(n) { if n <= 0 then return 1 else return n*fact_rec(n-1) }
            a = fact_rec(5)
            """));
    }

    @Test @DisplayName("sample 6: iterative factorial")
    void sampleIterativeFactorial() {
        assertEquals("b: 120\n", formatted("""
            fun fact_iter(n) { r = 1, while true do if n == 0 then return r else r = r * n, n = n - 1 }
            b = fact_iter(5)
            """));
    }

    // Output formatting

    @Test @DisplayName("globals print in declaration order, function names omitted")
    void declarationOrderAndFunctionsHidden() {
        String out = formatted("""
            fun id(x) { return x }
            z = 1
            a = 2
            m = id(7)
            """);
        // declaration order: z, a, m -- and `id` (the function) does not appear.
        assertEquals("z: 1\na: 2\nm: 7\n", out);
    }

    @Test @DisplayName("function locals do not leak into globals")
    void functionLocalsHidden() {
        String out = formatted("""
            fun f(x) { y = x + 1, return y }
            r = f(10)
            """);
        assertEquals("r: 11\n", out);
    }

    // Error handling

    @Test @DisplayName("undefined variable -> runtime error mentioning the name")
    void undefinedVariable() {
        var ex = assertThrows(com.interpreter.error.RuntimeError.class,
            () -> run("y = x + 1\n"));
        assertTrue(ex.getMessage().contains("Undefined variable 'x'"), ex.getMessage());
    }

    @Test @DisplayName("division by zero -> runtime error")
    void divisionByZero() {
        var ex = assertThrows(com.interpreter.error.RuntimeError.class,
            () -> run("x = 1 / 0\n"));
        assertTrue(ex.getMessage().contains("Division by zero"), ex.getMessage());
    }

    @Test @DisplayName("undefined function -> runtime error")
    void undefinedFunction() {
        var ex = assertThrows(com.interpreter.error.RuntimeError.class,
            () -> run("x = nope(1)\n"));
        assertTrue(ex.getMessage().contains("Undefined function 'nope'"), ex.getMessage());
    }

    @Test @DisplayName("wrong arity -> runtime error")
    void arityMismatch() {
        var ex = assertThrows(com.interpreter.error.RuntimeError.class,
            () -> run("""
                fun add(a, b) { return a + b }
                x = add(1)
                """));
        assertTrue(ex.getMessage().contains("expects 2"), ex.getMessage());
    }

    @Test @DisplayName("infinite recursion -> bounded by call-depth limit")
    void infiniteRecursion() {
        var ex = assertThrows(com.interpreter.error.RuntimeError.class,
            () -> run("""
                fun loop(n) { return loop(n + 1) }
                x = loop(0)
                """));
        assertTrue(ex.getMessage().contains("call depth"), ex.getMessage());
    }

    @Test @DisplayName("non-boolean in condition -> runtime error")
    void nonBooleanCondition() {
        var ex = assertThrows(com.interpreter.error.RuntimeError.class,
            () -> run("if 1 then x = 1 else x = 2\n"));
        assertTrue(ex.getMessage().contains("boolean"), ex.getMessage());
    }

    @Test @DisplayName("syntax error -> parse error mentioning the location")
    void syntaxError() {
        var ex = assertThrows(com.interpreter.error.ParseError.class,
            () -> run("x = \n"));
        assertTrue(ex.getMessage().contains("line"), ex.getMessage());
    }

    @Test @DisplayName("unexpected character -> lex error")
    void lexError() {
        var ex = assertThrows(com.interpreter.error.LexError.class,
            () -> run("x = 1 @ 2\n"));
        assertTrue(ex.getMessage().contains("@"), ex.getMessage());
    }

    // Misc

    @Test @DisplayName("blank input runs and produces no output")
    void blankInput() {
        assertEquals("", formatted(""));
        assertEquals("", formatted("\n\n\n"));
    }

    @Test @DisplayName("line comments with '#' are ignored")
    void lineComments() {
        assertEquals("x: 3\n", formatted("""
            # set x
            x = 1 + 2  # trailing comment
            """));
    }

    @Test @DisplayName("operator precedence: * binds tighter than +")
    void operatorPrecedence() {
        assertEquals("a: 14\n", formatted("a = 2 + 3 * 4\n"));
    }

    @Test @DisplayName("unary minus")
    void unaryMinus() {
        assertEquals("a: -5\nb: 3\n", formatted("a = -5\nb = -(-3)\n"));
    }
}
