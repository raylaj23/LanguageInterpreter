package com.interpreter.parser;

import java.util.List;

// handles the AST node hierarchy for expressions
public sealed interface Expr {
    int line();

    record NumberLit(long value, int line) implements Expr {}
    //handles floating-point literal AST nodes
    record FloatLit(double value, int line) implements Expr {}
    record BoolLit(boolean value, int line) implements Expr {}
    record Variable(String name, int line) implements Expr {}
    record Unary(String op, Expr operand, int line) implements Expr {}
    record Binary(Expr left, String op, Expr right, int line) implements Expr {}
    //handles short-circuiting logical && and || (separate node so the interpreter doesn't pre-evaluate both sides)
    record Logical(Expr left, String op, Expr right, int line) implements Expr {}
    record Call(String callee, List<Expr> args, int line) implements Expr {}
}
