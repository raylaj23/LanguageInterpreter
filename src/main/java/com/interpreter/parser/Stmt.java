package com.interpreter.parser;

import java.util.List;

// AST node hierarchy for statements
public sealed interface Stmt {
    int line();

    record Assign(String name, Expr value, int line) implements Stmt {}
    record If(Expr condition, Stmt thenBranch, Stmt elseBranch, int line) implements Stmt {}
    record While(Expr condition, List<Stmt> body, int line) implements Stmt {}
    record FunDef(String name, List<String> params, List<Stmt> body, int line) implements Stmt {}
    record Return(Expr value, int line) implements Stmt {}
    record ExprStmt(Expr expr, int line) implements Stmt {}
}
