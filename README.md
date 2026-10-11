# In-Memory Account Ledger

[![Build and test](https://github.com/haykaghabekyan/in-memory-account-ledger/actions/workflows/ci.yml/badge.svg)](https://github.com/haykaghabekyan/in-memory-account-ledger/actions/workflows/ci.yml)
[![Intentionally failing test](https://github.com/haykaghabekyan/in-memory-account-ledger/actions/workflows/design-limitation.yml/badge.svg)](https://github.com/haykaghabekyan/in-memory-account-ledger/actions/workflows/design-limitation.yml)

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
| Run the intentionally failing test | `mvn test -Dtest=DesignLimitationTest -DexcludedTestGroups=` |
| Run every test including it | `mvn test -DexcludedTestGroups=` |

The first run downloads JUnit and the Maven plugins, so it needs network access.

### The intentionally failing test

`src/test/java/ledger/core/DesignLimitationTest.java` fails on purpose: it states an expectation this design does not meet, with inline comments explaining what it reveals. It is tagged `design-limitation` and excluded from `mvn test` (and from the Build and test workflow) by the `excludedTestGroups` property in `pom.xml`.

The test: an account goes 10.00 overdrawn on Day 1 and the customer pays it back on Day 2. It expects one fee. The ledger charges four (Days 1 to 4, 100.00 in total), because each day's fee is decided on a balance that includes earlier days' fees, as the spec's balance definition requires, so the bank's own fee keeps the account negative and generates the next fee. The fix (decide fees excluding all fees) would not change the E1 to E10 replay, but it would depart from the spec's stated rule, so it is kept as a known limitation.

To run it locally:

1. Set `JAVA_HOME` if needed (see Prerequisites).
2. Run only this test. `-DexcludedTestGroups=` (empty value) switches the exclusion off. Without it the test stays excluded: Maven runs 0 tests and reports `BUILD SUCCESS`, which looks like a pass but is not.

   ```
   mvn test -Dtest=DesignLimitationTest -DexcludedTestGroups=
   ```

3. Expect `BUILD FAILURE` with:

   ```
   expected: <[FEE-ACC-001-D1-1]> but was: <[FEE-ACC-001-D1-1, FEE-ACC-001-D2-1, FEE-ACC-001-D3-1, FEE-ACC-001-D4-1]>
   Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
   ```

4. To run it together with all other tests: `mvn test -DexcludedTestGroups=` (60 tests, 1 failure).

To run the same check as the workflow (succeeds only if the test fails):

```
if mvn -B test -Dtest=DesignLimitationTest -DexcludedTestGroups=; then echo "UNEXPECTED: the test passed"; else echo "OK: the test failed as intended"; fi
```

### Continuous integration

Two GitHub Actions workflows run on JDK 17 for every push and pull request:

- **Build and test** ([ci.yml](.github/workflows/ci.yml)) builds and tests the project (`mvn -B verify`, which skips the intentionally failing test), then runs the app (`mvn -B -q exec:java`), so the daily report can be read in the run's log without cloning the repository.
- **Intentionally failing test** ([design-limitation.yml](.github/workflows/design-limitation.yml)) runs `DesignLimitationTest` on its own and passes only while that test still fails. Green means the design limitation is still demonstrated (the failure and its explanation are in the run's log); red means the test started passing and needs looking at. This keeps every commit's status green without hiding the failure.

The badges at the top show the latest result of each.

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
| `Ledger` | the store: accounts and five append-only lists (entries, the event log of `ReceivedEvent`s, errors, authorization decisions, interest accrual memos), handed out only as read-only copies; balance queries. No business rules. |
| `Authorizations` | questions about holds, answered from what `Ledger` stores: an authorization's decision, whether it is settled, active holds, available balance |
| `EventProcessor` | the steps every event goes through: validate, record in the event log, check for a duplicate id, then pass it to the handler for its type |
| `EventHandler<E>` | the interface for one event type's rules (Strategy pattern) |
| `EndOfDayClose` | the daily close: re-evaluates every day up to the closing day, oldest first, and charges or refunds overdraft fees so each day has an active fee exactly when its balance (excluding its own fee) is negative |
| `InterestCapitalization` | daily interest on positive closing balances as known at the end of the window, with the rounding difference carried forward day to day; records each day's accrual as a memo and posts one interest credit on the last day |
| `handler/*Handler` | one class per event type: `CreditHandler` (including instalment splits), `DebitHandler`, `ReversalHandler`, `AuthorizationHandler` (approve or decline), `SettlementHandler` (debit and release the hold) |

`process(event, day)` takes the current day from the caller; nothing in the ledger has a clock. `balance(account, valueDay, knownAtEndOfDay)` sums the entries with value day ≤ `valueDay` that were posted by the end of `knownAtEndOfDay`. `Authorizations.available(account, day)` is that balance for the current day, minus active holds. A hold is active while its approved authorization has no settlement entry; it is never stored as a status, so settling releases it without changing anything.

The replay lives in `src/main/java/ledger/replay`: `EventStream` (the accounts and E1 to E10 in the listed order, each with the day it is processed), `Replay` (processes each day's events, then runs the close, plus interest after the last close) and `DailyReport` (prints one day from what the ledger has stored). `Main` runs the replay on standard output.

All parts are created and connected in one place, the composition root `src/main/java/ledger/LedgerSetup.java`, which also holds the overdraft fee and interest rate configuration. `EventProcessor` only knows the `EventHandler` interface, so a new event type means a new handler class plus one line in `LedgerSetup`; `EventProcessor` and `Ledger` do not change. Because handlers are looked up in a map, the compiler can no longer check that every event type is handled, so `EventProcessor` checks it when it is created (against the permitted subclasses of the sealed `Event`) and refuses to start if a handler is missing.

Design choices in these types:

- **Signed amounts.** Credits are positive and debits negative, so a balance is a plain sum with no direction logic.
- **A `Money` type instead of bare `BigDecimal`.** Precision and same-currency checks happen in one place, and every rounding uses the one constant `Money.ROUNDING`.
- **Days are plain integers** (1–6). The window is fixed, so a wrapper type would add code without preventing a real mistake.
- **Records are immutable and nothing has a status field.** Only facts that never change are stored: entries and final authorization decisions. Whether an entry has been reversed is found from the entries themselves (another entry's `reversesEntryId` points at it).
- **Posted day separate from booked day.** "As known at the end of Day Y" depends on when an entry entered the ledger. For every event except E10 the two are the same; E10 is booked on Day 5 but posted on Day 6 (see [AMBIGUITIES.md](AMBIGUITIES.md#e10-arrives-after-day-5-has-closed)).
- **Two kinds of failure.** Invalid input (unknown account, wrong currency) throws before anything is recorded; `Ledger` also refuses an entry in the wrong currency, so a faulty handler cannot store one. A refusal under the ledger's rules (duplicate event, reversal that cannot be applied) posts nothing and is kept as a `ProcessingError` for the report.

## How to read the output

`mvn -q compile exec:java` replays E1 to E10 in the listed order (not sorted by booked day) and prints one block per day, Day 1 to Day 6. Each block is printed right after that day's end-of-day close, so it shows the ledger as known at the end of that day. On Day 6, interest is capitalized after the close and before the block is printed.

Each block has the same sections; an empty one prints `none`:

| Section | What it shows |
|---|---|
| Events | the events processed that day, in arrival order, with their outcome: `APPROVED`/`DECLINED` for authorizations (with the available balance after the hold), `REJECTED` when nothing was posted, `(back-dated)` when the value day is before the booked day, `late booking` when the event arrived after its booked day had closed |
| Closing ledger balance | each account's balance for that day (entries with value day up to that day, as known at its close), and the available balance (ledger minus active holds) |
| Balances by value day, as known now | the closing balance of every day so far, as known at this day's close. This is where back-dated entries show: E7 rewrites Day 2 on Day 5, and E9 rewrites it back on Day 6 |
| Fees | overdraft fees charged (`-25.00`) or refunded (`+25.00`) by that day's close, and the day each one is for |
| Interest | Day 6 only: each account's rounded daily accruals for Days 1 to 6 and the credit they capitalize into |
| Authorizations | every authorization decided so far: `ACTIVE` (hold reserved), `SETTLED` (with the settled amount) or `DECLINED` |
| Errors | what was refused that day: the event (or `close` for the end-of-day close), the account and the reason |

Day 5, for example:

```
=== Day 5 ===
Events
  E7   DEBIT          ACC-001  620.00 AED  value Day 2 (back-dated)
  E8   AUTHORIZATION  ACC-001  Auth-B 90.00 AED  DECLINED (available after hold -245.00)
Closing ledger balance
  ACC-001     -230.00 AED   available    -230.00
  ACC-002       0.000 BHD   available      0.000
Balances by value day, as known now
  ACC-001  D1 250.00 | D2 -395.00 | D3 5.00 | D4 -205.00 | D5 -230.00
  ACC-002  D1 0.000 | D2 0.000 | D3 0.000 | D4 0.000 | D5 0.000
Fees
  charged   FEE-ACC-001-D2-1         -25.00 AED  for Day 2
  charged   FEE-ACC-001-D4-1         -25.00 AED  for Day 4
  charged   FEE-ACC-001-D5-1         -25.00 AED  for Day 5
Authorizations
  Auth-A  ACC-001      200.00  SETTLED   approved Day 2, settled for 185.00 on Day 4
  Auth-B  ACC-001       90.00  DECLINED  declined Day 5
Errors          none
```

Day 5 closes at -230.00: E7 (620.00, value Day 2) was posted that day, so the Day 2 to Day 5 balances drop, and the close charges fees for Days 2, 4 and 5. Auth-B was declined because E7 had already made the balance -155.00 when it arrived.

## Documents

- [NUMBERS.md](NUMBERS.md): every constant and why that value
- [AMBIGUITIES.md](AMBIGUITIES.md): ambiguities found and how they were resolved
- [REJECTED.md](REJECTED.md): assessment of every acceptance criterion (refused and accepted), plus approaches abandoned mid-build
- [WORKLOG.md](WORKLOG.md): timestamped work log
