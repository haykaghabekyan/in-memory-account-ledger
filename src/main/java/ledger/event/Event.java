package ledger.event;

import ledger.domain.Money;

/**
 * One input event from the stream, as received. Events are never changed after they are
 * received; processing outcomes are recorded separately. Amounts are positive as given;
 * the ledger decides the sign when it posts.
 */
public sealed interface Event permits Credit, Debit, Authorization, Settlement, Reversal {

    /** The event's id in the stream, e.g. "E7". */
    String id();

    /** The day the event was booked, as given in the stream. */
    int bookedDay();

    String accountId();

    static Money requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        return amount;
    }
}
