package ledger.core.handler;

import ledger.core.EventHandler;
import ledger.core.Ledger;
import ledger.domain.Account;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.event.Debit;

/** Posts a debit as a negative entry. Never balance-checked: the money has already moved. */
public class DebitHandler implements EventHandler<Debit> {

    private final Ledger ledger;

    public DebitHandler(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public Class<Debit> eventType() {
        return Debit.class;
    }

    @Override
    public void validate(Debit debit, Account account) {
        account.requireCurrencyOf(debit.amount());
    }

    @Override
    public void handle(Debit debit, int day) {
        ledger.post(new Entry(debit.id(), debit.accountId(), debit.amount().negate(), EntryType.DEBIT,
                debit.valueDay(), debit.bookedDay(), day, debit.id(), null, "debit"));
    }
}
