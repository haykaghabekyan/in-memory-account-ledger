package ledger.core;

import static ledger.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import ledger.LedgerSetup;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.event.Credit;
import ledger.event.Debit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * THE INTENTIONALLY FAILING TEST. It fails against this implementation on purpose and must not
 * be "fixed" by changing the assertion. Excluded from the default build (see README).
 */
@Tag("design-limitation")
class DesignLimitationTest {

    @Test
    void dayNegativeOnlyBecauseOfEarlierFeesIsNotCharged() {
        LedgerSetup setup = newSetup();
        Ledger ledger = setup.ledger();

        // Day 1: the customer spends 10.00 they do not have. Day 1 closes at -10.00, so one
        // overdraft fee is fair: Day 1 is now -35.00.
        setup.processor().process(new Debit("D1", 1, ACC_001, aed("10.00"), 1), 1);
        setup.close().close(1);

        // Day 2: the customer pays the 10.00 back. Their own transactions now net to 0.00.
        setup.processor().process(new Credit("C1", 2, ACC_001, aed("10.00"), 2), 2);
        setup.close().close(2);

        // Days 3 and 4: no activity at all.
        setup.close().close(3);
        setup.close().close(4);

        // WHAT THE DESIGN DOES: a day's fee is decided on its closing balance including earlier
        // days' fees (only its own fee is excluded, see AMBIGUITIES.md "How is a day's fee
        // decided when fees change later balances?"). That follows the spec's definition of
        // the closing ledger balance: all entries with value date <= that day, fees included.
        // So Day 2 closes at -25.00 because of Day 1's fee alone, and is charged. That fee
        // makes Day 3 -50.00, which is charged, and so on: 4 fees, 100.00, for one 10.00
        // overdraft that lasted a day.
        //
        // WHAT IT REVEALS: one overdraft makes the account generate fees by itself, every day,
        // with no customer activity, because the bank's own fee keeps the balance negative.
        // Fees charged on fees are the kind of practice consumer-protection rules (the UAE
        // Central Bank's fair-treatment standards among them) treat as unfair.
        //
        // THE FIX AND WHY IT IS NOT MADE: decide fees on the balance excluding all fees. This
        // test would then pass with one fee, and the E1-E10 replay would not change (Days 2, 4
        // and 5 are negative even without fees: -370.00, -155.00, -155.00). It is not made
        // because the spec's non-negotiable rule defines the balance for the fee test as "all
        // entries with value_date <= that day", which includes fees. Excluding them would
        // replace a stated rule with my own. Kept as a known limitation of following the spec.
        assertEquals(List.of("FEE-ACC-001-D1-1"), feeIds(ledger));
    }

    private static List<String> feeIds(Ledger ledger) {
        return ledger.entries().stream()
                .filter(entry -> entry.type() == EntryType.FEE)
                .map(Entry::entryId)
                .toList();
    }
}
