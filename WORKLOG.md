# Worklog

Times are local (UTC+04).

## 2026-10-10

- 20:43 Started. Read the task and did a first pass over the acceptance criteria.
- 21:03 Repository set up with doc skeletons.
- 22:52 Design thinking: replayed the event stream by hand and assessed criteria 1–5 (refused 2). Recorded the related ambiguities.
- 23:45 Assessed criteria 6–8 (refused 6, 7, 8) and recorded the remaining ambiguities: fee timing and refunds, interest rounding and accruals, overdraft limit, fee currency, hold expiry, late E10. Reviewed both documents for consistency.
- 23:58 Design phase done: all criteria assessed and ambiguities resolved. Stopped for the day; will re-review the docs with a fresh mind in the morning, then choose the language and start the build.

## 2026-10-11

- 14:11 Chose Java 17 and Maven for the build. Decided to use no third-party libraries in the ledger (BigDecimal for exact amounts); JUnit 5 is the only planned dependency, for tests.
- 14:23 Added the domain types (currency, money, account, entry, authorization decision, error, interest accrual) with tests for money precision and rounding. Documented precision and rounding in NUMBERS.md.
- 14:35 Added the input event types (credit, debit, authorization, settlement, reversal) behind a sealed interface, with validation tests.
- 14:42 Added a GitHub Actions workflow that runs the tests on every push and pull request.
- 14:53 Added the ledger core: append-only entries and event log, credits (with instalments), debits, reversals, value-dated balances as known at a given day, and duplicate-event handling. Recorded duplicate events and failed reversals in AMBIGUITIES.md.
- 15:00 Split the ledger core by responsibility before adding more event types: the ledger as a store, an event processor for the shared steps, and one handler per event type (Strategy) instead of a type-check chain.
- 15:22 Added authorizations and settlements: approve or decline on available balance, holds worked out from decisions and settlement entries, Auth-Z rejected as an exception. Recorded rules for unusable settlements and reused authorization ids in AMBIGUITIES.md.
