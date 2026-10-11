# In-Memory Account Ledger

[![Build and test](https://github.com/haykaghabekyan/in-memory-account-ledger/actions/workflows/ci.yml/badge.svg)](https://github.com/haykaghabekyan/in-memory-account-ledger/actions/workflows/ci.yml)

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

### Continuous integration

GitHub Actions runs on JDK 17 for every push and pull request ([workflow](.github/workflows/ci.yml)). It builds and tests the project (`mvn -B verify`), then runs the app (`mvn -B -q exec:java`), so the app's output can be read in the run's log without cloning the repository. The badge at the top shows the latest result.

## Code structure

Domain types live in `src/main/java/ledger/domain`:

| Type | What it is |
|---|---|
| `Currency` | AED (2 dp) and BHD (3 dp); the precision lives with the currency |
| `Money` | an exact signed amount in one currency, always at that currency's precision; refuses to mix currencies |
| `Account` | an account id and its currency |
| `Entry`, `EntryType` | one immutable ledger line: signed amount, value day, booked day, and the event that caused it (none for fees and interest) |
| `AuthorizationDecision`, `Decision` | the final approve/decline decision for an authorization |
| `ProcessingError` | something refused or not possible, kept for the report |
| `InterestAccrual` | a memo record of one day's rounded interest; not a ledger entry |

Input events live in `src/main/java/ledger/event`. `Event` is a sealed interface (`id`, `bookedDay`, `accountId`) implemented by one record per event type: `Credit`, `Debit`, `Authorization`, `Settlement`, `Reversal`. Sealing means the compiler knows the complete list of event types. Event amounts are positive as given in the stream; the sign is applied when an entry is posted. A `Reversal` carries only the id of the event it reverses, because its amount and value day come from the original entry.

Design choices in these types:

- **Signed amounts.** Credits are positive and debits negative, so a balance is a plain sum with no direction logic.
- **A `Money` type instead of bare `BigDecimal`.** Precision and same-currency checks happen in one place, and all rounding goes through `Money.rounded`.
- **Days are plain integers** (1–6). The window is fixed, so a wrapper type would add code without preventing a real mistake.
- **Records are immutable and nothing has a status field.** Only facts that never change are stored: entries and final authorization decisions.

## How to read the output

_TBD_

## Documents

- [NUMBERS.md](NUMBERS.md): every constant and why that value
- [AMBIGUITIES.md](AMBIGUITIES.md): ambiguities found and how they were resolved
- [REJECTED.md](REJECTED.md): assessment of every acceptance criterion (refused and accepted), plus approaches abandoned mid-build
- [WORKLOG.md](WORKLOG.md): timestamped work log
