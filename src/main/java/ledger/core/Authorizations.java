package ledger.core;

import java.util.Optional;
import ledger.domain.AuthorizationDecision;
import ledger.domain.Decision;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.event.Settlement;

/**
 * Answers questions about authorizations and holds from what the ledger has stored. Nothing
 * here is stored itself: a hold is active while its approved authorization has no settlement
 * entry, so settling releases it without changing any record.
 */
public class Authorizations {

    private final Ledger ledger;

    public Authorizations(Ledger ledger) {
        this.ledger = ledger;
    }

    /** The decision on an authorization, if one was made. */
    public Optional<AuthorizationDecision> decision(String authId) {
        return ledger.decisions().stream().filter(decision -> decision.authId().equals(authId)).findFirst();
    }

    /** Whether a settlement entry has been posted for this authorization. */
    public boolean isSettled(String authId) {
        return ledger.entries().stream()
                .filter(entry -> entry.type() == EntryType.SETTLEMENT)
                .map(entry -> ledger.event(entry.sourceEventId()).orElseThrow())
                .anyMatch(event -> event instanceof Settlement settlement && settlement.authId().equals(authId));
    }

    /** The total of approved authorizations on the account that are not settled yet. */
    public Money activeHolds(String accountId) {
        Money total = Money.zero(ledger.account(accountId).currency());
        for (AuthorizationDecision decision : ledger.decisions()) {
            if (decision.accountId().equals(accountId)
                    && decision.decision() == Decision.APPROVED
                    && !isSettled(decision.authId())) {
                total = total.plus(decision.amount());
            }
        }
        return total;
    }

    /** Ledger balance minus active holds, with everything processed so far up to the given day. */
    public Money available(String accountId, int day) {
        return ledger.balance(accountId, day, day).minus(activeHolds(accountId));
    }
}
