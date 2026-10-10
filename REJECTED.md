# Rejected

## Refused acceptance criteria

### Criterion 2: E7 causes exactly one overdraft fee, on Day 2 (refused)

E7 is value-dated Day 2, so it lowers every closing balance from Day 2 onward,
not only Day 2. After E7 (before any fee): Day 2 −370.00, Day 3 +30.00,
Day 4 −155.00, Day 5 −155.00. Three days close negative, so E7 causes three
fees (Days 2, 4, 5). Including earlier fees in later balances (Day 3 becomes
+5.00) gives the same three fees, so the count does not depend on that choice.

### Criterion 6: After E9, all balances and fees return to their pre-E7 values (refused)

With our fee-refund rule, the current ledger balances do return (Days 2–6:
250.00, 650.00, 465.00, 465.00, 465.00), and the three fees caused by E7 net
to zero. But "all … return" is not true:
- The ledger keeps E7, E9, three fees and three fee reversals. Append-only
  means the history never returns, only the balances.
- Auth-B was declined because of E7. The decision is final, so E9 cannot
  restore the hold Auth-B would otherwise have had.
- Balances as known at end of Day 5 (e.g. Day 5 at −230.00) stay as they were.
- Fees return only because we chose to refund them. The spec does not say
  so. If fees were final once charged, Day 6 would close at 390.00.
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
capitalized total: there is never a remainder to discard. It also loses money.
Rounding each day on its own does not add up: ACC-001's exact daily accruals
(0.100, 0.100, 0.260, 0.186, 0.186, 0.186) total 1.018, while rounding each day
gives 1.03. Instead, each day's rounding difference is carried into the next
day, so the rounded accruals (0.10, 0.10, 0.26, 0.19, 0.18, 0.19) sum to 1.02,
which is the exact total correctly rounded, and that is what is capitalized.
See AMBIGUITIES.md: "Making rounded daily accruals add up"

## Assessed and accepted criteria

Listed so it is clear every criterion was checked, not only the refused ones.

### Criterion 1: Day 2 balance at end of Day 5 is −370.00 (accepted)

1,200.00 − 950.00 − 620.00 = −370.00. E7 is booked on Day 5, E9 only on Day 6,
so at end of Day 5 the reversal is not yet known.
See AMBIGUITIES.md: "Balance as of when?"

### Criterion 3: The Day 4 settlement of Auth-A must be accepted (accepted)

Auth-A was approved on Day 2 (available 250.00 ≥ 200.00), so the bank has
committed to it. The settlement of 185.00 is posted as a debit and the full
200.00 hold is released (15.00 freed). With hindsight, E7 makes Day 2 negative,
but authorization decisions are not revisited.
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
