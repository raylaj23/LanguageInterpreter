# Language Interpreter

A small interpreter for an artificial programming language, written in Java 21.
Reads a program from standard input, executes it, and prints the final values
of all top-level variables to standard output (one per line, in declaration
order).

## Build & run

The project ships with a **Gradle wrapper** (`gradlew` / `gradlew.bat`) and
the Foojay toolchain resolver, so you do **not** need to install Gradle and
you do **not** need to install Java 21 manually. The first invocation will
download both automatically (one-time, ~150 MB into your Gradle user home).

You only need:

- Any JDK on the `PATH` (8+ is enough -- Gradle itself runs on it; the
  project's own JDK 21 will be auto-provisioned by the toolchain resolver).
- An internet connection on the first run.

> **PowerShell quirk:** PowerShell does not support `<` for stdin
> redirection. Use `Get-Content` or a here-string instead. Also, run each
> command on its own line -- don't paste a multi-line block all at once.

### Step by step

#### 1. Run the test suite

PowerShell:

```powershell
.\gradlew.bat test
```

cmd.exe:

```cmd
gradlew.bat test
```

bash / WSL / macOS / Git Bash:

```sh
./gradlew test
```

Expected: `BUILD SUCCESSFUL` and 18 tests passing.

#### 2. Run the interpreter on a file

Save a program (e.g. the contents below) into `program.txt`:

```
x = 2
y = (x + 2) * 2
```

Then:

PowerShell:

```powershell
Get-Content program.txt | .\gradlew.bat -q run
```

cmd.exe:

```cmd
gradlew.bat -q run < program.txt
```

bash / WSL / macOS / Git Bash:

```sh
./gradlew -q run < program.txt
```

Expected output:

```
x: 2
y: 8
```

#### 3. Run the interpreter on an inline program

PowerShell (here-string -- type the lines exactly as shown, including the
closing `"@`):

```powershell
@"
x = 2
y = (x + 2) * 2
"@ | .\gradlew.bat -q run
```

bash / WSL / macOS / Git Bash:

```sh
printf 'x = 2\ny = (x + 2) * 2\n' | ./gradlew -q run
```

The `-q` flag silences Gradle's own logging so only the interpreter's
output is printed.

## Architecture

A classic three-stage pipeline:

```
source string
   │
   ▼ Lexer       (com.interpreter.lexer)        characters → tokens
   │
   ▼ Parser      (com.interpreter.parser)       tokens     → AST (Stmt / Expr)
   │
   ▼ Interpreter (com.interpreter.runtime)      AST        → side effects
   │
   ▼ Main prints the global environment
```

Each stage has a single responsibility and a sealed AST is used so the
interpreter can switch over node types exhaustively.

```
src/main/java/com/interpreter/
├── Main.java              # entry point: stdin → run → stdout
├── lexer/                 # Token, TokenType, Lexer
├── parser/                # Expr, Stmt, Parser (recursive descent)
├── runtime/               # Value, Environment, Interpreter
└── error/                 # InterpreterException + LexError / ParseError / RuntimeError
```

## Language summary

Two value types: 64-bit signed integers and booleans (`true` / `false`).

```
program     := stmt (NEWLINE+ stmt)*
stmt        := assignment | if | while | fun | return | expr
assignment  := IDENT '=' expr
if          := 'if' expr 'then' stmt ('else' stmt)?
while       := 'while' expr 'do' whileBody
whileBody   := stmt (',' stmt)*                    ; ends at NEWLINE / RBRACE / EOF
fun         := 'fun' IDENT '(' params? ')' '{' blockBody '}'
blockBody   := stmt ((',' | NEWLINE)+ stmt)*       ; comma OR newline separates
return      := 'return' expr

expr        := comparison
comparison  := addition (('==' | '!=' | '<' | '>' | '<=' | '>=') addition)*
addition    := multiplication (('+' | '-') multiplication)*
multiplication := unary (('*' | '/') unary)*
unary       := '-' unary | primary
primary     := NUMBER | 'true' | 'false' | IDENT | call | '(' expr ')'
call        := IDENT '(' (expr (',' expr)*)? ')'
```

Operator precedence (low → high): comparisons; `+ -`; `* /`; unary `-`; primary.

Line comments start with `#` and run to end of line.

## Ambiguities and how they were resolved

The spec is intentionally informal, so several choices had to be made.

1. **`if`/`else` branches are single statements.** In sample 3,

   ```
   while x < 3 do if x == 1 then y = 10 else y = y + 1, x = x + 1
   ```

   produces `x: 3, y: 11`. That output is only possible if `x = x + 1`
   runs every iteration -- including the iteration where `x == 1` and the
   `else` branch does *not* execute. So the comma after `y = y + 1` must
   terminate the `if`, making `x = x + 1` part of the **while** body, not
   the `else` branch.

2. **`while` body is a comma-separated compound, terminated by newline (or `}`).**
   Same example: the body is `[if-stmt, x = x + 1]`. The body greedily
   consumes everything up to the end of the logical line.

3. **Inside `{ ... }`, both commas and newlines separate statements.** The
   sample functions are written on one line with commas, but multi-line
   function bodies are also accepted.

4. **Functions are top-level only and live in their own namespace.** They
   are not first-class values (no anonymous functions, no passing functions
   as arguments). Function names therefore do not appear in the printed
   globals.

5. **Function bodies create locals, but can read globals.** Assignment in a
   function body always writes to the local frame (Python-style "local by
   default"). Reads walk up to globals, which is what makes recursion work
   (`fact_rec` is visible inside its own body because it lives in the global
   function table).

6. **Conditions must be booleans.** There is no implicit truthiness --
   `if 1 then ...` is a runtime type error. This makes mistakes loud rather
   than silent.

7. **Integers are 64-bit and overflow is reported, not wrapped.** Arithmetic
   uses `Math.addExact` / `Math.multiplyExact` etc., so overflow surfaces as
   a clean runtime error.

## Edge cases handled

| Situation                          | Behaviour                                                    |
|------------------------------------|--------------------------------------------------------------|
| Division by zero                   | Runtime error: "Division by zero" with line number           |
| Undefined variable                 | Runtime error naming the variable                            |
| Undefined function                 | Runtime error naming the function                            |
| Wrong number of arguments          | Runtime error: expected vs. actual count                     |
| Infinite recursion                 | Capped at 1000 frames; surfaces as a clean runtime error     |
| Non-boolean in `if` / `while`      | Runtime type error                                           |
| Comparing values of different types| Runtime type error                                           |
| Arithmetic overflow                | Runtime error (no silent wrap)                               |
| Function returning without `return`| Runtime error                                                |
| Empty / whitespace-only program    | No output, exit 0                                            |
| Parse error                        | Reported with line number; non-zero exit                     |
| Lex error (e.g. stray `@`)         | Reported with line number; non-zero exit                     |

All errors are written to **stderr**. Successful program output goes to
**stdout**. The process exits with status `1` on any error.

## Tests

`src/test/java/com/interpreter/EndToEndTest.java` covers:

- All six sample programs from the spec.
- Output formatting: declaration order, function names hidden, function
  locals hidden.
- Each error path listed in the table above.
- Misc.: line comments, operator precedence, unary minus, blank input.

Run with `gradle test`.

## What I would add next

These were intentionally kept out of scope:

- A `print(x)` builtin (so programs can produce output other than the final
  globals).
- Strings, arrays, first-class functions, closures.
- Block statements (`{ ... }`) usable outside function definitions, so `if`
  and `while` could take multi-statement branches without relying on the
  comma trick.
- Better error recovery in the parser (currently it stops at the first
  error -- fine for a small language, but a real IDE would want
  resynchronisation).
- Source positions on more AST nodes to make runtime error messages even
  more precise (currently they point at the start of the offending
  construct, which is usually enough but not always).
