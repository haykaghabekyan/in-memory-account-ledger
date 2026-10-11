package ledger.event;

import java.util.Objects;

/**
 * Reverses an earlier event's entry. It has no amount or value day of its own: both come from
 * the original entry, so the correction lands on the same day as what it corrects.
 */
public record Reversal(String id, int bookedDay, String accountId, String reversedEventId) implements Event {

    public Reversal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(reversedEventId, "reversedEventId");
    }
}
