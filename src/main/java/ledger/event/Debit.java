package ledger.event;

import java.util.Objects;
import ledger.domain.Money;

/** Money out of the account. Never balance-checked: it records money that has already moved. */
public record Debit(String id, int bookedDay, String accountId, Money amount, int valueDay) implements Event {

    public Debit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Event.requirePositive(amount);
    }
}
