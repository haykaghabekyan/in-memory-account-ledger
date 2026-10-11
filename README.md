# In-Memory Account Ledger

An in-memory account ledger core. The full spec is in [TASK.md](TASK.md).

## Technology

Java 17 and Maven.

**Dependencies:** no third-party libraries in the ledger itself. Exact decimal arithmetic and rounding use `java.math.BigDecimal` from the standard library. The only dependency is JUnit 5, for tests only. A library would be added only when it solves a real problem better than plain Java can (for example, a decimal or money type if multi-currency arithmetic grew beyond `BigDecimal` with explicit scales). Each one would be recorded here with the reason.

## How to run

### Prerequisites

- JDK 17 or newer
- Maven 3.9 or newer

Check both with:

```
java -version
mvn -v
```

`mvn -v` must report Java 17 or newer. If it fails with "The JAVA_HOME environment variable is not defined correctly", point `JAVA_HOME` at your JDK. On macOS:

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

(Homebrew's `mvn` defaults `JAVA_HOME` to the `openjdk` formula, so it fails when only a versioned one such as `openjdk@17` is installed.)

### Commands

Run from the repository root.

| What | Command |
|---|---|
| Run all tests | `mvn test` |
| Run one test class | `mvn test -Dtest=ClassName` |
| Run one test method | `mvn test -Dtest=ClassName#methodName` |
| Run the app | `mvn -q compile exec:java` |

The first run downloads JUnit and the Maven plugins, so it needs network access.

## How to read the output

_TBD_

## Documents

- [NUMBERS.md](NUMBERS.md): every constant and why that value
- [AMBIGUITIES.md](AMBIGUITIES.md): ambiguities found and how they were resolved
- [REJECTED.md](REJECTED.md): assessment of every acceptance criterion (refused and accepted), plus approaches abandoned mid-build
- [WORKLOG.md](WORKLOG.md): timestamped work log
