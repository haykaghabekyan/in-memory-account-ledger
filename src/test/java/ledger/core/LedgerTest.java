package ledger.core;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.event.Credit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = new Ledger(List.of(new Account("ACC-001", AED), new Account("ACC-002", BHD)));
    }

    @Test
    void accountsOpenAtZero() {
        assertEquals(Money.of("0.00", AED), ledger.balance("ACC-001", 6, 6));
        assertEquals(Money.of("0.000", BHD), ledger.balance("ACC-002", 6, 6));
    }

    @Test
    void refusesAnEntryInTheWrongCurrency() {
        Entry wrongCurrency = entry("ACC-001", Money.of("1.000", BHD));

        assertThrows(IllegalArgumentException.class, () -> ledger.post(wrongCurrency));
        assertEquals(List.of(), ledger.entries());
    }

    @Test
    void recordsCannotBeChangedFromOutside() {
        Entry entry = entry("ACC-001", Money.of("1.00", AED));
        ledger.post(entry);
        ledger.recordEvent(new ReceivedEvent(1, new Credit("E1", 1, "ACC-001", Money.of("1.00", AED), 1)));
        List<Entry> entries = ledger.entries();
        List<ReceivedEvent> log = ledger.eventLog();

        assertThrows(UnsupportedOperationException.class, () -> entries.add(entry));
        assertThrows(UnsupportedOperationException.class, log::clear);
    }

    private static Entry entry(String accountId, Money amount) {
        return new Entry("E1", accountId, amount, EntryType.CREDIT, 1, 1, 1, "E1", null, "credit");
    }
}
