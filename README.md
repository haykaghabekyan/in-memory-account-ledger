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
| `Entry`, `EntryType` | one immutable ledger line: signed amount, value day, booked day, posted day, the event that caused it (none for fees and interest), and the entry it reverses (if any) |
| `AuthorizationDecision`, `Decision` | the final approve/decline decision for an authorization |
| `ProcessingError` | something refused or not possible, kept for the report |
| `InterestAccrual` | a memo record of one day's rounded interest; not a ledger entry |

Input events live in `src/main/java/ledger/event`. `Event` is a sealed interface (`id`, `bookedDay`, `accountId`) implemented by one record per event type: `Credit`, `Debit`, `Authorization`, `Settlement`, `Reversal`. Sealing means the compiler knows the complete list of event types. Event amounts are positive as given in the stream; the sign is applied when an entry is posted. A `Reversal` carries only the id of the event it reverses, because its amount and value day come from the original entry.

The ledger lives in `src/main/java/ledger/core`, split by responsibility:

| Class | Responsibility |
|---|---|
| `Ledger` | the store: accounts and four append-only lists (entries, the event log of `ReceivedEvent`s, errors, authorization decisions), handed out only as read-only copies; balance, active-hold and available-balance queries. No business rules. |
| `EventProcessor` | the steps every event goes through: validate, record in the event log, check for a duplicate id, then pass it to the handler for its type |
| `EventHandler<E>` | the interface for one event type's rules (Strategy pattern) |
| `handler/*Handler` | one class per event type: `CreditHandler` (including instalment splits), `DebitHandler`, `ReversalHandler`, `AuthorizationHandler` (approve or decline), `SettlementHandler` (debit and release the hold) |

`process(event, day)` takes the current day from the caller; nothing in the ledger has a clock. `balance(account, valueDay, knownAtEndOfDay)` sums the entries with value day ≤ `valueDay` that were posted by the end of `knownAtEndOfDay`. `available(account, day)` is that balance for the current day, minus active holds. A hold is active while its approved authorization has no settlement entry; it is never stored as a status, so settling releases it without changing anything.

A new event type means a new handler class; `EventProcessor` and `Ledger` do not change. Because handlers are looked up in a map, the compiler can no longer check that every event type is handled, so `EventProcessor` checks it when it is created (against the permitted subclasses of the sealed `Event`) and refuses to start if a handler is missing.

Design choices in these types:

- **Signed amounts.** Credits are positive and debits negative, so a balance is a plain sum with no direction logic.
- **A `Money` type instead of bare `BigDecimal`.** Precision and same-currency checks happen in one place, and every rounding uses the one constant `Money.ROUNDING`.
- **Days are plain integers** (1–6). The window is fixed, so a wrapper type would add code without preventing a real mistake.
- **Records are immutable and nothing has a status field.** Only facts that never change are stored: entries and final authorization decisions. Whether an entry has been reversed is found from the entries themselves (another entry's `reversesEntryId` points at it).
- **Posted day separate from booked day.** "As known at the end of Day Y" depends on when an entry entered the ledger. For every event except E10 the two are the same; E10 is booked on Day 5 but posted on Day 6 (see [AMBIGUITIES.md](AMBIGUITIES.md#e10-arrives-after-day-5-has-closed)).
- **Two kinds of failure.** Invalid input (unknown account, wrong currency) throws before anything is recorded; `Ledger` also refuses an entry in the wrong currency, so a faulty handler cannot store one. A refusal under the ledger's rules (duplicate event, reversal that cannot be applied) posts nothing and is kept as a `ProcessingError` for the report.

## How to read the output

_TBD_

## Documents

- [NUMBERS.md](NUMBERS.md): every constant and why that value
- [AMBIGUITIES.md](AMBIGUITIES.md): ambiguities found and how they were resolved
- [REJECTED.md](REJECTED.md): assessment of every acceptance criterion (refused and accepted), plus approaches abandoned mid-build
- [WORKLOG.md](WORKLOG.md): timestamped work log
