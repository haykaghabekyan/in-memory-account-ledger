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
in [AMBIGUITIES.md](AMBIGUITIES.md#rounding-mode). Every rounding in the code uses
one constant, `Money.ROUNDING`, so the mode is defined in a single place.

## Overdraft fee: AED 25.00

Given by the spec. It is a flat amount per account per negative day, not a
percentage, so it does not depend on how negative the balance is (Day 2 at
−370.00 and Day 5 at −205.00 are charged the same). Half of it would simply be
a different price; nothing in the ledger depends on the value, only on it being
positive.

Fees are configured per currency (`LedgerSetup.OVERDRAFT_FEES`: AED 25.00
only). A negative account in a currency with no fee gets an error instead of a
fee, which is how BHD is handled (see
[AMBIGUITIES.md](AMBIGUITIES.md#overdraft-fee-for-a-non-aed-account)). No BHD
amount is invented, and none is derived from an exchange rate.

## Daily interest rate: 0.04% (0.0004)

Given by the spec, per day, on the closing ledger balance, positive balances
only. It is stored as the exact decimal `0.0004` (`LedgerSetup.DAILY_INTEREST_RATE`,
built from the string "0.0004", never a binary floating-point number), so
465.00 × 0.0004 is exactly 0.186. Like the fee, the value is a price: half of
it (0.02%) would halve every accrual, and no logic depends on the value, only
on it being positive. The same rate is applied to AED and BHD because the spec
gives one rate (see
[AMBIGUITIES.md](AMBIGUITIES.md#overdraft-fee-for-a-non-aed-account)).
Per year it is roughly 14.6% simple (0.04% × 365), which is high for a deposit
rate, but the window is six days and the spec fixes it.
