package ledger.domain;

import java.util.Objects;

/**
 * One immutable line in the append-only ledger. Corrections are new entries, never edits.
 *
 * @param amount        signed: positive adds to the balance, negative takes from it
 * @param valueDay      the day the entry counts towards in balances
 * @param bookedDay     the day the entry was posted
 * @param sourceEventId the event that caused it (e.g. "E7"), or null for system entries (fees, interest)
 * @param description   human-readable detail for the report, e.g. "fee for Day 2"
 */
public record Entry(
        String entryId,
        String accountId,
        Money amount,
        EntryType type,
        int valueDay,
        int bookedDay,
        String sourceEventId,
        String description) {

    public Entry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
    }

    public boolean isSystemGenerated() {
        return sourceEventId == null;
    }
}
