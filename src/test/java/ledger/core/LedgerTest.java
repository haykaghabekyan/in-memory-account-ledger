package ledger.core;

import static ledger.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = newLedger();
    }

    @Test
    void accountsOpenAtZero() {
        assertEquals(aed("0.00"), ledger.balance(ACC_001, 6, 6));
        assertEquals(bhd("0.000"), ledger.balance(ACC_002, 6, 6));
    }

    @Test
    void refusesAnEntryInTheWrongCurrency() {
        Entry wrongCurrency = entry(ACC_001, bhd("1.000"));

        assertThrows(IllegalArgumentException.class, () -> ledger.post(wrongCurrency));
        assertEquals(List.of(), ledger.entries());
    }

    @Test
    void recordsCannotBeChangedFromOutside() {
        Entry entry = entry(ACC_001, aed("1.00"));
        ledger.post(entry);
        ledger.recordEvent(new ReceivedEvent(1, E1));
        List<Entry> entries = ledger.entries();
        List<ReceivedEvent> log = ledger.eventLog();

        assertThrows(UnsupportedOperationException.class, () -> entries.add(entry));
        assertThrows(UnsupportedOperationException.class, log::clear);
    }

    private static Entry entry(String accountId, Money amount) {
        return new Entry("E1", accountId, amount, EntryType.CREDIT, 1, 1, 1, "E1", null, "credit");
    }
}
