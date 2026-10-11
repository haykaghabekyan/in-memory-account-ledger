package ledger.domain;

import java.util.Objects;

/**
 * The final decision on an authorization. Decisions are never revisited, so this is stored;
 * whether an approved hold is still active is derived from the ledger, not stored here.
 *
 * @param eventId            the authorization event this decision was made for
 * @param availableAfterHold available balance (ledger minus active holds) minus this hold, at decision time
 */
public record AuthorizationDecision(
        String authId,
        String eventId,
        String accountId,
        Money amount,
        Decision decision,
        int decidedDay,
        Money availableAfterHold) {

    public AuthorizationDecision {
        Objects.requireNonNull(authId, "authId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(availableAfterHold, "availableAfterHold");
    }
}
