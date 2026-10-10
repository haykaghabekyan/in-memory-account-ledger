# Rejected

## Refused acceptance criteria

### Criterion 2: E7 causes exactly one overdraft fee, on Day 2 (refused)

E7 is value-dated Day 2, so it lowers every closing balance from Day 2 onward,
not only Day 2. After E7 (before any fee): Day 2 −370.00, Day 3 +30.00,
Day 4 −155.00, Day 5 −155.00. Three days close negative, so E7 causes three
fees (Days 2, 4, 5). Including earlier fees in later balances (Day 3 becomes
+5.00) gives the same three fees, so the count does not depend on that choice.

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

## Approaches abandoned mid-build
