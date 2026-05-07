package com.interpreter;

//handles program entry point: reads source from stdin, runs the pipeline, prints globals
import com.interpreter.error.InterpreterException;
import com.interpreter.lexer.Lexer;
import com.interpreter.lexer.Token;
import com.interpreter.parser.Parser;
import com.interpreter.parser.Stmt;
import com.interpreter.runtime.Interpreter;
import com.interpreter.runtime.Value;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entry point.
 * Errors (lex / parse / runtime) are written to standard error and the
 * process exits with status 1.
 */
public final class Main {

    public static void main(String[] args) {
        String source = readAll(System.in);
        int status = new Main().run(source, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    /**
     * Pure entry point used by tests. Returns the process exit code so callers
     * can assert on success/failure without intercepting {@link System#exit}.
     */
    public int run(String source, java.io.PrintStream out, java.io.PrintStream err) {
        try {
            List<Token> tokens = new Lexer(source).tokenize();
            List<Stmt> program = new Parser(tokens).parseProgram();
            Map<String, Value> globals = new Interpreter().run(program);
            for (Map.Entry<String, Value> e : globals.entrySet()) {
                out.println(e.getKey() + ": " + e.getValue());
            }
            return 0;
        } catch (InterpreterException e) {
            err.println(e.getMessage());
            return 1;
        }
    }

    private static String readAll(java.io.InputStream in) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            System.err.println("Failed to read input: " + e.getMessage());
            System.exit(1);
            return "";
        }
    }
}
