package ledger.event;

import java.util.Objects;
import ledger.domain.Money;

/**
 * Money into the account.
 *
 * @param instalments how many entries the amount is posted as (1 normally, 3 for E10)
 */
public record Credit(String id, int bookedDay, String accountId, Money amount, int valueDay, int instalments)
        implements Event {

    public Credit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Event.requirePositive(amount);
        if (instalments < 1) {
            throw new IllegalArgumentException("instalments must be at least 1: " + instalments);
        }
    }

    public Credit(String id, int bookedDay, String accountId, Money amount, int valueDay) {
        this(id, bookedDay, accountId, amount, valueDay, 1);
    }
}
