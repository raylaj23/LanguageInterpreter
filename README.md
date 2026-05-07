# Language Interpreter

A small interpreter for a custom programming language, written in **Java 21**.
It reads a program from standard input, executes it, and prints the final values
of all global variables to standard output, one per line, in
declaration order.

## Solution overview

The interpreter is a classic three-stage tree-walking pipeline. Each stage has a
single responsibility and feeds the next:

```
source string
	Lexer       (com.interpreter.lexer)     characters to tokens
	Parser      (com.interpreter.parser)    tokens to AST (Stmt / Expr)
	Interpreter (com.interpreter.runtime)   AST to side effects on globals
	Main prints the global environment
```

### Lexer
Hand-written single-pass scanner. Walks the source one char at a time, emits
`Token`s with line/column info. Handles whitespace, `#` line comments, numeric
literals (int and float), identifiers, keywords (`if`, `then`, `else`, `while`,
`do`, `fun`, `return`, `true`, `false`), and all punctuation/operators. Lex
errors (e.g. stray `@`) are reported with line numbers via `LexError`.

### Parser
Recursive-descent parser with a method per grammar non-terminal. Operator
precedence is encoded by the call chain: lower-precedence operators are parsed
first and recurse into tighter ones for their operands. The output is a
**sealed-interface AST** (`Expr`, `Stmt`) made of immutable `record` nodes — the
interpreter switch-statements over them with compile-time exhaustiveness
checking. Parse errors carry line numbers and the offending token.

### Interpreter
Tree-walking evaluator. Two scopes via `Environment`: a global scope and a
per-call local scope for functions. `return` unwinds a call via an internal
`ReturnSignal` exception (cheaper than threading a return flag through every
recursive eval). Recursion is capped at `MAX_CALL_DEPTH = 1000` so infinite
recursion surfaces as a clean runtime error instead of a `StackOverflowError`.

### Project layout
```
src/main/java/com/interpreter/
	Main.java          entry point: stdin to run to stdout
	lexer/             Token, TokenType, Lexer
	parser/            Expr, Stmt, Parser
	runtime/           Value, Environment, Interpreter
	error/             InterpreterException + LexError / ParseError / RuntimeError
src/test/java/com/interpreter/
	EndToEndTest.java  JUnit 5 tests of the full pipeline
```

## Supported features (the basic samples)

The six required sample programs all run end-to-end:

1. arithmetic with parens — `x = 2; y = (x + 2) * 2`
2. `if x > 10 then y = 100 else y = 0`
3. `while ... do` with a comma-separated compound body
4. function definition + call — `fun add(a, b) { return a + b }`
5. recursive factorial
6. iterative factorial (while + if + return inside a function)

The base language supports:

- variable assignment and reads
- `if / then / else`
- `while / do`
- `fun NAME(params) { ... }` with `return`
- arithmetic `+ - * /`
- comparisons `== != < > <= >=`
- parenthesized expressions
- `#` line comments

## What I added (beyond the samples)

These were added on top of the spec. Each one is implemented at the layer where
it naturally fits, with no impact on the core grammar.

| Feature | Where it's implemented | How |
|---|---|---|
| **Float literals** (`3.14`) | Lexer + AST + Interpreter | Lexer recognises `.` in numeric literals and tags the token's literal as `Double`; parser produces `Expr.FloatLit`; interpreter has a separate `Value.FloatVal` runtime type. |
| **Numeric promotion** (mixed int/float) | `evalBinary` in Interpreter | If either operand is float, the whole op promotes to float (`Math.pow` for `**`, IEEE arithmetic for `+ - * / %`). |
| **Modulo `%`** | Parser (`parseMultiplication`) + Interpreter | Same precedence as `* /`; integer modulo is bounds-checked (modulo by zero → runtime error). |
| **Exponentiation `**`** (right-associative) | Parser (`parsePower`) + Interpreter | Right-associative by recursing through `parseUnary` on the exponent. Integer power uses overflow-checked exponentiation-by-squaring; negative integer exponent → runtime error. |
| **Compound assignments** `+= -= *= /= %= **=` | `parseAssignOrExpr` | Desugared at parse time: `x += e` becomes the AST for `x = x + e`. No runtime support needed. |
| **Postfix `++` / `--`** | `parseAssignOrExpr` | Desugared the same way: `x++` → `x = x + 1`. |
| **Logical `&& \|\| !`** with short-circuit | Parser + Interpreter | A dedicated `Expr.Logical` AST node (separate from `Expr.Binary`) so the interpreter only evaluates the right operand when needed. |
| **Overflow-safe integer arithmetic** | Interpreter | Uses `Math.addExact / subtractExact / multiplyExact / negateExact` so overflow becomes a clean runtime error rather than a silent wrap. |
| **Recursion-depth cap** | Interpreter | `MAX_CALL_DEPTH = 1000`; infinite recursion (e.g. `fun loop(n) { return loop(n+1) }`) surfaces as a runtime error instead of crashing the JVM. |
| **`#` line comments** | Lexer | Skipped in the scanner — no token emitted, no parser changes. |
| **Comma- and newline-separated block bodies** | Parser (`parseBlockBody`) | Inside `{ ... }` either separator works, so functions can be one-liners (`{ a, b, return c }`) or multi-line. |
| **Strict typing in conditions** | Interpreter (`asBool`) | `if 1 then ...` is a runtime error — no implicit truthiness, so type mistakes are loud. |

## How to run

The project ships with a Gradle wrapper, so use `./gradlew` or `gradlew.bat` (Windows).

## How to test

The test suite (`src/test/java/com/interpreter/EndToEndTest.java`) is a JUnit 5
end-to-end test that runs source through `Lexer → Parser → Interpreter` and
asserts on the final globals. It covers:

- all six required sample programs
- output formatting (declaration order, function names hidden, locals don't leak)
- every error path (undefined var / function, wrong arity, division by zero,
  infinite recursion, non-boolean condition, lex/parse errors)
- misc edge cases (blank input, comments, operator precedence, unary minus)

Run all tests:

```sh
./gradlew test
```

## Author

[raylaj23] — Computer Engineering Student.
