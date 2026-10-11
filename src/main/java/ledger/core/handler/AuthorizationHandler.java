package ledger.core.handler;

import ledger.core.EventHandler;
import ledger.core.Ledger;
import ledger.domain.Account;
import ledger.domain.AuthorizationDecision;
import ledger.domain.Decision;
import ledger.domain.Money;
import ledger.domain.ProcessingError;
import ledger.event.Authorization;

/**
 * Approves an authorization only if the available balance (ledger minus active holds) stays at
 * or above zero after the hold. Posts no ledger entry: an approved hold reserves money without
 * moving it. The decision is final and never revisited.
 */
public class AuthorizationHandler implements EventHandler<Authorization> {

    private final Ledger ledger;

    public AuthorizationHandler(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public Class<Authorization> eventType() {
        return Authorization.class;
    }

    @Override
    public void validate(Authorization authorization, Account account) {
        account.requireCurrencyOf(authorization.amount());
    }

    @Override
    public void handle(Authorization authorization, int day) {
        if (ledger.decision(authorization.authId()).isPresent()) {
            ledger.recordError(new ProcessingError(day, authorization.id(), authorization.accountId(),
                    "Authorization rejected: " + authorization.authId() + " already exists"));
            return;
        }
        Money availableAfterHold = ledger.available(authorization.accountId(), day).minus(authorization.amount());
        Decision decision = availableAfterHold.isNegative() ? Decision.DECLINED : Decision.APPROVED;
        ledger.recordDecision(new AuthorizationDecision(authorization.authId(), authorization.accountId(),
                authorization.amount(), decision, day, availableAfterHold));
    }
}
