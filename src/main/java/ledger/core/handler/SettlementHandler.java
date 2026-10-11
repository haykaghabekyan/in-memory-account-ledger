package ledger.core.handler;

import java.util.Optional;
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

    public SettlementHandler(Ledger ledger) {
        this.ledger = ledger;
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
        String problem = problemWith(settlement);
        if (problem != null) {
            ledger.recordError(new ProcessingError(day, settlement.id(), settlement.accountId(),
                    "Settlement rejected: " + problem));
            return;
        }
        ledger.post(new Entry(settlement.id(), settlement.accountId(), settlement.amount().negate(),
                EntryType.SETTLEMENT, settlement.valueDay(), settlement.bookedDay(), day, settlement.id(), null,
                "settles " + settlement.authId()));
    }

    /** Why the settlement cannot be applied, or null if it can. */
    private String problemWith(Settlement settlement) {
        String authId = settlement.authId();
        Optional<AuthorizationDecision> decision = ledger.decision(authId);
        if (decision.isEmpty()) {
            return "no authorization " + authId;
        }
        if (!decision.get().accountId().equals(settlement.accountId())) {
            return authId + " belongs to account " + decision.get().accountId();
        }
        if (decision.get().decision() == Decision.DECLINED) {
            return authId + " was declined";
        }
        if (ledger.isSettled(authId)) {
            return authId + " is already settled";
        }
        return null;
    }
}
