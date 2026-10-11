# Rejected

## Refused acceptance criteria

### Criterion 2: E7 causes exactly one overdraft fee, on Day 2 (refused)

E7 is value-dated Day 2, so it lowers every closing balance from Day 2 onward,
not only Day 2. After E7 (before any fee): Day 2 −370.00, Day 3 +30.00,
Day 4 −155.00, Day 5 −155.00. Three days close negative, so E7 causes three
fees (Days 2, 4, 5). Including earlier fees in later balances (Day 3 becomes
+5.00) gives the same three fees, so the count does not depend on that choice.

### Criterion 6: After E9, all balances and fees return to their pre-E7 values (refused)

With our fee-refund rule, the current ledger balances do return (Days 2–6,
before the Day 6 interest credit: 250.00, 650.00, 465.00, 465.00, 465.00), and
the three fees caused by E7 net to zero. But "all … return" is not true:
- The ledger keeps E7, E9, three fees and three fee reversals. Append-only
  means the history never returns, only the balances.
- Auth-B was declined because of E7. The decision is final, so E9 cannot
  restore the hold Auth-B would otherwise have had.
- Balances as known at end of Day 5 (e.g. Day 5 at −230.00) stay as they were.
- Fees return only because we chose to refund them. The spec does not say
  so. If fees were final once charged, Day 6 would close at 390.00 (before
  interest).
See AMBIGUITIES.md: "When are fees assessed?" and "What happens to a fee when
its day stops being negative?"

### Criterion 7: The three BHD instalments in E10 must each be BHD 3.334 (refused)

3 × 3.334 = 10.002, so the account would be credited 0.002 more than the
10.000 sent. Splitting must preserve the total: the first two instalments are
10.000 / 3 rounded to 3 dp (3.333 each), and the last takes the remainder,
10.000 − 6.666 = 3.334.
See AMBIGUITIES.md: "Three equal instalments of BHD 10.000"

### Criterion 8: If the rounded daily interest accruals do not sum to the capitalized total, the remainder is discarded (refused)

It contradicts the rule that the rounded daily accruals must sum exactly to the
capitalized total: there is never a remainder to discard. Discarding it would
mean the daily accrual records and the posted credit no longer reconcile, and
the difference is silently absorbed by the customer or the bank.
Rounding each day on its own does not add up: ACC-001's exact daily accruals
(0.100, 0.100, 0.260, 0.186, 0.186, 0.186) total 1.018, while rounding each day
gives 1.03. Instead, each day's rounding difference is carried into the next
day, so the rounded accruals (0.10, 0.10, 0.26, 0.19, 0.18, 0.19) sum to 1.02,
which is the exact total correctly rounded, and that is what is capitalized.
See AMBIGUITIES.md: "Making rounded daily accruals add up"

## Assessed and accepted criteria

Listed so it is clear every criterion was checked, not only the refused ones.

### Criterion 1: Day 2 balance at end of Day 5, before any fee, is −370.00 (accepted)

1,200.00 − 950.00 − 620.00 = −370.00. E7 is booked on Day 5, E9 only on Day 6,
so at end of Day 5 the reversal is not yet known.
See AMBIGUITIES.md: "Balance as of when?"

### Criterion 3: The Day 4 settlement of Auth-A must be accepted (accepted)

Auth-A was approved on Day 2 (available after the hold: 250.00 − 200.00 = 50.00
≥ 0), so the bank has committed to it. The settlement of 185.00 is posted as a
debit and the full 200.00 hold is released (15.00 freed). With hindsight, E7
makes Day 2 negative, but authorization decisions are not revisited.
See AMBIGUITIES.md: "Settlement amount differs from the hold" and
"Are authorization decisions revisited?"

### Criterion 4: A settlement referencing an unknown authorization is rejected and no funds leave (accepted, with a caveat)

E6 (Auth-Z) has no preceding authorization, so nothing was reserved and the
bank never approved the amount. The settlement is rejected: no ledger entry is
posted and Day 4 stays at 465.00. Caveat: it is not silently dropped. The event
is kept in the event log as an exception, so there is an audit record of what
arrived and why it was refused.
See AMBIGUITIES.md: "Settlements without a known authorization".

### Criterion 5: If Auth-B is approved, its hold reduces available balance but not ledger balance (accepted)

True as a rule: a hold reserves money without moving it. But in this stream
Auth-B is declined. When E8 arrives on Day 5, E7 has already made the balance
negative (−155.00; fees are only assessed at the end-of-day close), so the
condition is never met.
See AMBIGUITIES.md: "Are authorization decisions revisited?"

## Approaches abandoned mid-build

Approaches that were built (or tried in code) and then replaced, with the
reason. The final design is described in README.md.

### Event dispatch as a chain of type checks

The first ledger processed events with `if (event instanceof Credit) … else if
(event instanceof Debit) …` inside the ledger itself. It worked for credits,
debits and reversals, but every new event type meant editing that method, and
the ledger mixed storage with every event's rules. Replaced before
authorizations were added: `EventProcessor` runs the steps every event shares
(validate, log, duplicate check) and hands each event to one handler class per
type. A missing handler is caught when the processor is created.

### Wiring and hold rules inside the classes that use them

`EventProcessor` first had a factory method that created every concrete
handler, so a new event type still meant editing it, and the ledger answered
"is this hold active?" and "what is available?", which are business rules, not
storage. Replaced by a composition root (`LedgerSetup`, which also holds the
fee and interest rate configuration) and an `Authorizations` class for hold
queries. `Ledger` went back to storing records and summing balances.

### A separate index for duplicate detection

Duplicate events were first detected with a map from event id to event, kept
next to the event log. It held the same facts as the log, so the two could
drift apart. Replaced by looking the id up in the event log itself.

### Splitting an amount with two roundings

The first instalment split divided to 16 significant digits and then rounded
that to the currency's precision. Two roundings in a row can, in rare cases,
give a different result from one, and it was harder to explain. Replaced by a
single division straight to the currency's precision, half up.

### Deciding fees on the balance excluding all fees

Tried in code after the intentionally failing test was written: deciding each
day's fee on the balance without any fees removes the fee spiral (that test
passes, and the E1 to E10 replay output is unchanged). Not adopted, because the
spec's non-negotiable rule defines the balance for the fee test as all entries
with value date up to that day, which includes fees. The current rule follows
the spec, and the failing test documents its cost.
See AMBIGUITIES.md: "How is a day's fee decided when fees change later
balances?"

### A workflow that passes while the failing test fails

The intentionally failing test first ran in a workflow that inverted its
result (green while the test failed), to keep every commit's status green.
Green for a failing test was confusing, so the workflow now runs the test
plainly and fails, as it does locally. The cost is a red cross on every
commit, explained in README.md.
