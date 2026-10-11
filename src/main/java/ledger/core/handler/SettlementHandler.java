package ledger.core.handler;

import java.util.Optional;
import ledger.core.Authorizations;
import ledger.core.EventHandler;
import ledger.core.Ledger;
import ledger.domain.Account;
import ledger.domain.AuthorizationDecision;
import ledger.domain.Decision;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.ProcessingError;
import ledger.event.Settlement;

/**
 * Debits the actual settled amount, which releases the whole hold. Never balance-checked: the
 * bank already committed when it approved the authorization.
 */
public class SettlementHandler implements EventHandler<Settlement> {

    private final Ledger ledger;
    private final Authorizations authorizations;

    public SettlementHandler(Ledger ledger, Authorizations authorizations) {
        this.ledger = ledger;
        this.authorizations = authorizations;
    }

    @Override
    public Class<Settlement> eventType() {
        return Settlement.class;
    }

    @Override
    public void validate(Settlement settlement, Account account) {
        account.requireCurrencyOf(settlement.amount());
    }

    @Override
    public void handle(Settlement settlement, int day) {
        Optional<String> problem = problemWith(settlement);
        if (problem.isPresent()) {
            ledger.recordError(new ProcessingError(day, settlement.id(), settlement.accountId(),
                    "Settlement rejected: " + problem.get()));
            return;
        }
        ledger.post(new Entry(settlement.id(), settlement.accountId(), settlement.amount().negate(),
                EntryType.SETTLEMENT, settlement.valueDay(), settlement.bookedDay(), day, settlement.id(), null,
                "settles " + settlement.authId()));
    }

    /** Why the settlement cannot be applied, or empty if it can. */
    private Optional<String> problemWith(Settlement settlement) {
        String authId = settlement.authId();
        Optional<AuthorizationDecision> decision = authorizations.decision(authId);
        if (decision.isEmpty()) {
            return Optional.of("no authorization " + authId);
        }
        if (!decision.get().accountId().equals(settlement.accountId())) {
            return Optional.of(authId + " belongs to account " + decision.get().accountId());
        }
        if (decision.get().decision() == Decision.DECLINED) {
            return Optional.of(authId + " was declined");
        }
        if (authorizations.isSettled(authId)) {
            return Optional.of(authId + " is already settled");
        }
        return Optional.empty();
    }
}
