# Numbers

Every constant used in the implementation, why that value, and why not half of it.

## Thresholds at zero

The spec words the three zero thresholds differently, so the exact comparison matters:

| Rule | Spec wording | Comparison | At exactly 0.00 |
|---|---|---|---|
| Overdraft fee | "is negative" | balance < 0 | no fee |
| Interest | "positive balances only" | balance > 0 | no interest |
| Authorization | "at or above zero" | available after hold ≥ 0 | approved |
