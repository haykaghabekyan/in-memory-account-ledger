package ledger.event;

import java.util.Objects;
import ledger.domain.Money;

/** Settles an earlier authorization for the actual amount, which may differ from the hold. */
public record Settlement(String id, int bookedDay, String accountId, String authId, Money amount, int valueDay)
        implements Event {

    public Settlement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(authId, "authId");
        Event.requirePositive(amount);
    }
}
