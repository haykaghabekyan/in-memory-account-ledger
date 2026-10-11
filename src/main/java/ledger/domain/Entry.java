package ledger.domain;

import java.util.Objects;

/**
 * One immutable line in the append-only ledger. Corrections are new entries, never edits.
 *
 * @param amount          signed: positive adds to the balance, negative takes from it
 * @param valueDay        the day the entry counts towards in balances
 * @param bookedDay       the booked day stated by the event (for system entries, the day they are posted)
 * @param postedDay       the day the entry entered the ledger; decides what was known at the end of a day.
 *                        Differs from bookedDay only for a late booking (E10: booked Day 5, posted Day 6)
 * @param sourceEventId   the event that caused it (e.g. "E7"), or null for system entries (fees, interest)
 * @param reversesEntryId the entry this one cancels, or null if it is not a reversal
 * @param description     human-readable detail for the report, e.g. "fee for Day 2"
 */
public record Entry(
        String entryId,
        String accountId,
        Money amount,
        EntryType type,
        int valueDay,
        int bookedDay,
        int postedDay,
        String sourceEventId,
        String reversesEntryId,
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
