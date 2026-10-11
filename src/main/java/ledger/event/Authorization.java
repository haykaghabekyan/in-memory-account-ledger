package ledger.event;

import java.util.Objects;
import ledger.domain.Money;

/** A request to hold funds. Approved or declined when processed; it never posts a ledger entry. */
public record Authorization(String id, int bookedDay, String accountId, String authId, Money amount, int valueDay)
        implements Event {

    public Authorization {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(authId, "authId");
        Event.requirePositive(amount);
    }
}
