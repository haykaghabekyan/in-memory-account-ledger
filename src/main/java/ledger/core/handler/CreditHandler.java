package ledger.core.handler;

import java.util.List;
import ledger.core.EventHandler;
import ledger.core.Ledger;
import ledger.domain.Account;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.event.Credit;

/** Posts a credit as one entry, or as several when it is split into instalments (E10). */
public class CreditHandler implements EventHandler<Credit> {

    private final Ledger ledger;

    public CreditHandler(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public Class<Credit> eventType() {
        return Credit.class;
    }

    @Override
    public void validate(Credit credit, Account account) {
        account.requireCurrencyOf(credit.amount());
    }

    @Override
    public void handle(Credit credit, int day) {
        List<Money> parts = credit.amount().split(credit.instalments());
        for (int i = 0; i < parts.size(); i++) {
            boolean single = parts.size() == 1;
            String entryId = single ? credit.id() : credit.id() + "-" + (i + 1);
            String description = single ? "credit" : "credit, instalment " + (i + 1) + " of " + parts.size();
            ledger.post(new Entry(entryId, credit.accountId(), parts.get(i), EntryType.CREDIT,
                    credit.valueDay(), credit.bookedDay(), day, credit.id(), null, description));
        }
    }
}
