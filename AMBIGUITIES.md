# Ambiguities

Every ambiguity found in the spec and how it was resolved.

### Balance as of when?

A day's closing balance depends on when it is evaluated (before fees, Day 2 is
250.00 at end of Day 4, −370.00 at end of Day 5, 250.00 at end of Day 6).
Resolution: a balance is queried for a value date X as known at the end of
Day Y: entries with value date ≤ X, counting only what was posted up to and
including Day Y's close (events processed that day, plus the fees and fee
reversals the close itself posts). For E1–E9 this is the same as booked date
≤ Y. E10 is the exception: it is booked on Day 5 but arrives after Day 5 has
closed, so it first appears in the Day 6 view (see "E10 arrives after Day 5
has closed").

### How is a day's fee decided when fees change later balances?

A fee lowers every later day's balance, which can create or remove later fees.
Resolution: days are evaluated oldest first. A day's fee is decided on its
closing balance including earlier days' fees but excluding its own fee
(otherwise a fee could justify itself).

### Settlement amount differs from the hold

The spec does not say what happens when a settlement is not equal to its hold.
Resolution: the settlement is debited for its actual amount and the whole hold
is released. A settlement lower than the hold is normal (Auth-A: 185.00 against
200.00). A settlement higher than the hold does not occur in the stream and is
not handled specially.

### Are authorization decisions revisited?

A back-dated entry can show that an authorization would have been declined had
it been known at the time (E7 makes Auth-A's Day 2 available balance negative).
Resolution: no. An authorization is decided once, with what has been processed
at that moment, and stays final. The same applies to Auth-B's decline after E9.

### Settlements without a known authorization

Criterion 4 says such settlements are rejected, but not what "rejected" leaves
behind. In real card networks, settlements without a matching authorization
can be legitimate (offline transactions, so-called force posts), and network
rules may require the bank to honour them.
Resolution: within this model the settlement is rejected and no money moves,
but the event is recorded as an exception for audit rather than discarded.
A force-post policy is out of scope.

### When are fees assessed?

The spec says fees are assessed once per day, but not when a fee for a past day
(made negative by a back-dated entry) is charged.
Resolution: an end-of-day close runs daily. It re-evaluates every day up to the
current day (as known at that moment), oldest first, and posts or reverses fees.
This matches criterion 1 ("evaluated at end of Day 5 and before any fee is
assessed").
Reasoning: a fee is owed by the customer, and the longer the bank waits to
collect, the more likely the money is gone, a risk that adds up across many
accounts. Posting daily keeps the bank's view of what each customer owes
current, shows the fee to the customer promptly, and lowers the available
balance used for the next authorization. Interest is the opposite case: it is
owed by the bank, so earning it per day but paying it at the end of the cycle
(Day 6) carries little risk for the customer.
Assessing fees only once at the end of the window was rejected: it is not
"once per day", and E9 would hide E7's fees entirely.

### "Value date equal to the day assessed"

This could mean the day whose balance was negative (Day 2) or the day the check
ran (Day 5).
Resolution: the day whose balance was negative. Otherwise a Day 2 fee would
only count from Day 5 and the Day 2–4 balances would never show it.

### What happens to a fee when its day stops being negative?

After E9, Days 2, 4 and 5 are no longer negative, but their fees are already
posted. The spec does not say whether they stay.
Resolution: they are refunded. Like the fees themselves, a fee reversal is a
system-generated entry (not an input event): booked on the day the change is
discovered, value-dated to the fee's day, and referencing the fee it cancels.
Reasoning: E9 is value-dated to Day 2, which treats E7 as an error that never
should have happened, so the fees it caused should not stand either. If a day
turns negative again later, a new fee is charged. An account has at most one
active (not reversed) fee per day.
Because the close only brings each day's fee in line with its balance, running
it twice for the same day changes nothing: the second run finds every fee
already in place.

### Three equal instalments of BHD 10.000

10.000 / 3 cannot be split into three equal amounts at 3 decimal places.
Resolution: as equal as the precision allows, with the total preserved. The
last instalment takes the rounding difference (3.333, 3.333, 3.334). All three
have the same value date, so the order does not affect balances or interest.

### Making rounded daily accruals add up

The spec requires the rounded daily accruals to sum exactly to the capitalized
total, but not how. Rounding each day independently drifts: ACC-001's exact
accruals total 1.018, but rounding each day gives 1.03.
Resolution: carry forward the rounding difference. Each day, the exact accrual
plus the previous day's carry is rounded to the account's precision, and the
difference becomes the next day's carry:

| Day | Exact | + carry | Rounded | New carry |
|---|---|---|---|---|
| 1 | 0.100 | 0.100 | 0.10 | 0.000 |
| 2 | 0.100 | 0.100 | 0.10 | 0.000 |
| 3 | 0.260 | 0.260 | 0.26 | 0.000 |
| 4 | 0.186 | 0.186 | 0.19 | −0.004 |
| 5 | 0.186 | 0.182 | 0.18 | +0.002 |
| 6 | 0.186 | 0.188 | 0.19 | −0.002 |

The rounded accruals sum to 1.02, the exact total (1.018) correctly rounded,
and 1.02 is capitalized. The final carry (−0.002) is less than half a minor
unit and cannot be paid in AED; it is the unavoidable rounding of the total,
not a discarded remainder.
The exact accruals and the carry are intermediate calculation values and are
never stored. Every stored amount (ledger entries and the rounded daily
accruals) is at the account's precision, as the spec requires.
The alternative, capitalizing the sum of independently rounded accruals (1.03),
also "sums exactly", but it drifts from the exact amount (here it overpays by
0.012), and the drift adds up across many accounts.

### Are daily interest accruals ledger entries?

The spec says accruals "capitalize as a single credit at end of Day 6", but not
whether each daily accrual is posted to the ledger before that.
Resolution: no. Daily accruals are memo records that do not change the ledger
balance. Only the capitalization at end of Day 6 is a ledger entry. The
accruals are calculated from the closing balances as known at the end of
Day 6, so back-dated entries (E7, E9) are already reflected and no accrual
corrections are needed.
Consequence: interest does not compound within the window. If accruals were
posted daily, each day's interest would also earn interest, and every
back-dated event would need accrual reversals as well as fee reversals.

### Is the interest credit part of Day 6's closing balance?

Day 6's accrual is calculated on Day 6's closing balance, and the capitalized
credit is posted at end of Day 6, so each seems to depend on the other.
Resolution: Day 6's accrual is calculated on the balance before the credit
(like a fee being decided excluding itself). The credit is then posted with
value date Day 6, so the reported Day 6 closing balance includes it
(ACC-001: 465.00 + 1.02 = 466.02; ACC-002: 10.000 + 0.008 = 10.008).

### Rounding mode

The spec gives the precision (AED 2 dp, BHD 3 dp) but not how to round.
Resolution: round half up (e.g. 0.185 → 0.19). It is the simplest rule to
explain, and with the interest carry-forward any bias from the mode does not
accumulate. No amount in this stream lands exactly on a half (e.g. 0.186,
3.3333…), so the choice does not change any output here.

### Is there an overdraft limit? Are debits checked?

The spec only checks authorizations (available balance must stay ≥ 0). It sets
no overdraft limit and says nothing about checking other events.
Resolution: debits, settlements, reversals and fees are always posted, even if
they make the balance negative. A debit or settlement records money that has
already moved (and a back-dated one like E7 describes the past), so it cannot
be declined. There is no overdraft limit, so in this model a balance can go
negative without bound. A real account would have an agreed limit.

### Overdraft fee for a non-AED account

The fee is defined as AED 25.00, "per account", but ACC-002 is in BHD. A fee is
debited from the account itself, so it must be in that account's currency. The
spec gives no BHD fee and no exchange rate.
Resolution: the fee applies only to AED accounts. If a BHD account closes a day
negative, no fee is charged and an error is reported ("no overdraft fee defined
for BHD"). The rule is non-negotiable, so it is not being skipped: it cannot be
applied to a BHD account without a number the spec does not give, and the error
makes that visible instead of hiding it. Inventing a BHD amount, or converting
at a hard-coded rate, would put a number in the ledger that the spec does not
support. This never triggers in the stream: ACC-002 never goes negative.
Every close re-evaluates every past day, so the error is reported once per
account and day, not again at each later close.
Related: in practice interest rates also differ by currency. The spec gives one
rate (0.04% per day) for both, and it is applied to both as given.

### How long does a hold last?

The spec does not say when an unsettled hold ends. Auth-B "is never settled
inside the window".
Resolution: holds have no expiry in this model; a hold ends only when it is
settled. In practice banks release unused holds automatically after a period
that depends on the card network and merchant type. No expiry period is chosen,
because the stream never needs one: Auth-A is settled, and Auth-B is declined,
so it never has a hold.

### E10 arrives after Day 5 has closed

Events are replayed in the listed order. E10 is booked on Day 5 but listed after
E9 (Day 6), so by the time it arrives, Day 5 has already been closed.
Resolution: E10 is accepted as a late booking with its stated booked date and
value date (Day 5). It is reported in the Day 6 output, where it was actually
processed, marked as booked on Day 5. The Day 6 close re-evaluates Day 5 for
ACC-002, so E10 counts towards Day 5's balance and interest (BHD 0.004 for
Day 5). Fees are unaffected because ACC-002 stays positive.

### Duplicate events

The spec does not say what happens if the same event arrives twice. The stream
has none, but in practice retries and redelivered messages are common, and
processing a debit twice would take the money twice.
Resolution: the event id is the idempotency key. An event whose id was already
processed posts nothing:
- same id and same details: a retry; ignored and reported ("Duplicate of E7,
  ignored"), so the outcome is the same as processing it once;
- same id, different details: a conflict, not a retry; rejected and reported
  ("E7 already processed with different details, rejected").
Every arrival, duplicates included, is still kept in the event log for audit.
A real API would usually take a separate client-supplied idempotency key; here
the event id already identifies the business event.

### Reversals that cannot be applied

The spec has one reversal (E9 reverses E7) and does not say what happens to a
reversal that cannot be applied.
Resolution: the reversal is rejected (no entry) and reported as an error when
the original event is unknown, belongs to a different account, posted no
entries (e.g. an authorization), or is already reversed. The last case stops a
reversal from being applied twice. A reversal cancels every entry the original
posted, each on that entry's own value date.

### Settlements that do not match a usable authorization

Criterion 4 covers a settlement with no authorization (Auth-Z). The spec says
nothing about a settlement whose authorization exists but cannot be settled.
Resolution: the settlement is rejected (no entry) and reported as an error when
the authorization was declined (the merchant was told no, so nothing was
committed), is already settled (a second settlement would charge twice), or
belongs to a different account. Several partial settlements against one
authorization (e.g. split shipments) are out of scope.
Consequence: if a settlement is later reversed, its hold does not come back.
The authorization stays settled; the reversal only returns the money.

### Reused authorization ids

The spec does not say what happens if a second authorization arrives with an
authorization id that already exists (under a different event id).
Resolution: it is rejected and reported, and no decision is recorded.
Otherwise a later settlement could not tell which authorization it settles.
An exact repeat of the same event is already handled as a duplicate event.

### Interest details the spec leaves open

The spec fixes the rate and that accruals capitalize at the end of Day 6, but
not these details:
- Days with no interest: a memo accrual of zero is still recorded for every
  day, so the report shows all six days. A zero or negative balance accrues
  nothing, and the rounding carry passes through that day unchanged.
- An account that earned nothing gets no interest entry rather than a zero
  credit.
- Capitalization runs once per window. Running it again is refused instead of
  ignored: unlike the fee close, a second run would pay the interest twice.
