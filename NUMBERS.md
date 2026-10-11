# Numbers

Every constant used in the implementation, why that value, and why not half of it.

## Thresholds at zero

The spec words the three zero thresholds differently, so the exact comparison matters:

| Rule | Spec wording | Comparison | At exactly 0.00 |
|---|---|---|---|
| Overdraft fee | "is negative" | balance < 0 | no fee |
| Interest | "positive balances only" | balance > 0 | no interest |
| Authorization | "at or above zero" | available after hold ≥ 0 | approved |

## Currency precision

| Currency | Decimal places | Smallest unit |
|---|---|---|
| AED | 2 | 0.01 (one fils) |
| BHD | 3 | 0.001 (one fils) |

Both values are given by the spec and match the ISO 4217 minor units of each
currency. Fewer places could not represent real amounts (BHD 3.334 at 2 dp is
3.33, losing a fils on every instalment). More places would store amounts that
cannot be paid. `Money` (`src/main/java/ledger/domain/Money.java`) rejects any
amount not at exactly its currency's precision, so an unrounded value cannot be
stored by mistake.

## Rounding mode

Half up (`RoundingMode.HALF_UP`): 0.185 → 0.19, 0.1849 → 0.18. The reasoning is
in [AMBIGUITIES.md](AMBIGUITIES.md#rounding-mode). All rounding goes through
one method, `Money.rounded`, so the mode is defined in a single place.
