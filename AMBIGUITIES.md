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
