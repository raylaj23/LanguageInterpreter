package com.interpreter.parser;

import java.util.List;

/**
 * AST node for statements. Sealed for exhaustive pattern-matching in the
 * interpreter.
 *
 * <p>Note that the bodies of {@code while} loops and function definitions are
 * lists of statements (a "compound"), while the branches of an {@code if} are
 * single statements -- this matches the comma-vs-newline rules described in
 * the README's grammar section.
 */
public sealed interface Stmt {
    int line();

    record Assign(String name, Expr value, int line) implements Stmt {}
    record If(Expr condition, Stmt thenBranch, Stmt elseBranch, int line) implements Stmt {}
    record While(Expr condition, List<Stmt> body, int line) implements Stmt {}
    record FunDef(String name, List<String> params, List<Stmt> body, int line) implements Stmt {}
    record Return(Expr value, int line) implements Stmt {}
    record ExprStmt(Expr expr, int line) implements Stmt {}
}
