package ledger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import ledger.core.Authorizations;
import ledger.core.EndOfDayClose;
import ledger.core.EventProcessor;
import ledger.core.Ledger;
import ledger.core.handler.AuthorizationHandler;
import ledger.core.handler.CreditHandler;
import ledger.core.handler.DebitHandler;
import ledger.core.handler.ReversalHandler;
import ledger.core.handler.SettlementHandler;
import ledger.domain.Account;
import ledger.domain.Currency;
import ledger.domain.Money;

/**
 * The composition root: the one place where the ledger's parts are created and connected.
 * Adding an event type means adding its handler here; nothing else is rewired.
 */
public record LedgerSetup(Ledger ledger, Authorizations authorizations, EventProcessor processor,
        EndOfDayClose close) {

    /** The overdraft fee per currency. BHD has none: a negative BHD account gets an error instead. */
    public static final Map<Currency, Money> OVERDRAFT_FEES = Map.of(Currency.AED, Money.of("25.00", Currency.AED));

    public static LedgerSetup create(Collection<Account> accounts) {
        Ledger ledger = new Ledger(accounts);
        Authorizations authorizations = new Authorizations(ledger);
        EventProcessor processor = new EventProcessor(ledger, List.of(
                new CreditHandler(ledger),
                new DebitHandler(ledger),
                new AuthorizationHandler(ledger, authorizations),
                new SettlementHandler(ledger, authorizations),
                new ReversalHandler(ledger)));
        EndOfDayClose close = new EndOfDayClose(ledger, OVERDRAFT_FEES);
        return new LedgerSetup(ledger, authorizations, processor, close);
    }
}
