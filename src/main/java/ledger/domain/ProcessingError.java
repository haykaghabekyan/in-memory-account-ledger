package ledger.domain;

import java.util.Objects;

/**
 * Something the ledger refused or could not do, kept for the report, e.g. a settlement with no
 * known authorization, or a negative BHD account with no overdraft fee defined.
 *
 * @param eventId the event that caused it, or null when raised by the end-of-day close
 */
public record ProcessingError(int day, String eventId, String accountId, String message) {

    public ProcessingError {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(message, "message");
    }
}
