# Ambiguities

Every ambiguity found in the spec and how it was resolved.

### Balance as of when?

A day's closing balance depends on when it is evaluated (Day 2 is 250.00 at end of
Day 4, −370.00 at end of Day 5, 250.00 at end of Day 6).
Resolution: balances are queried by value date ≤ X and booked date ≤ Y.

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
Resolution: no. An authorization is decided once, with what is booked at that
moment, and stays final. The same applies to Auth-B's decline after E9.

### Settlements without a known authorization

The spec says such settlements are rejected, but not what "rejected" leaves
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
owed by the bank, so accruing daily and paying at the end of the cycle (Day 6)
carries little risk for the customer.
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
