package com.interpreter.parser;

import java.util.List;

/**
 * AST node for expressions. Sealed so the interpreter can exhaustively switch
 * over the variants without a default case.
 *
 * <p>Each node carries the source line on which it begins, which the runtime
 * uses when reporting errors.
 */
public sealed interface Expr {
    int line();

    record NumberLit(long value, int line) implements Expr {}
    record BoolLit(boolean value, int line) implements Expr {}
    record Variable(String name, int line) implements Expr {}
    record Unary(String op, Expr operand, int line) implements Expr {}
    record Binary(Expr left, String op, Expr right, int line) implements Expr {}
    record Call(String callee, List<Expr> args, int line) implements Expr {}
}
