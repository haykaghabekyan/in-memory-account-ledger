package ledger.core;

import static ledger.TestData.*;
import static ledger.domain.Currency.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import ledger.LedgerSetup;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.ProcessingError;
import ledger.event.Credit;
import ledger.event.Debit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EndOfDayCloseTest {

    private LedgerSetup setup;
    private Ledger ledger;
    private EventProcessor processor;
    private EndOfDayClose close;

    @BeforeEach
    void setUp() {
        setup = newSetup();
        ledger = setup.ledger();
        processor = setup.processor();
        close = setup.close();
    }

    @Test
    void noFeesBeforeDayFive() {
        replayStream(setup, 4);

        assertEquals(List.of(), ids(EntryType.FEE));
    }

    @Test
    void dayFiveCloseChargesFeesForDaysTwoFourAndFive() {
        replayStream(setup, 5);

        assertEquals(List.of("FEE-ACC-001-D2-1", "FEE-ACC-001-D4-1", "FEE-ACC-001-D5-1"), ids(EntryType.FEE));
        assertEquals(aed("250.00"), ledger.balance(ACC_001, 1, 5));
        assertEquals(aed("-395.00"), ledger.balance(ACC_001, 2, 5));
        assertEquals(aed("5.00"), ledger.balance(ACC_001, 3, 5));
        assertEquals(aed("-205.00"), ledger.balance(ACC_001, 4, 5));
        assertEquals(aed("-230.00"), ledger.balance(ACC_001, 5, 5));
    }

    @Test
    void feeIsValueDatedToItsDayAndBookedOnTheClosingDay() {
        replayStream(setup, 5);

        Entry fee = ledger.entries().stream().filter(entry -> entry.type() == EntryType.FEE).findFirst().orElseThrow();
        assertEquals(aed("-25.00"), fee.amount());
        assertEquals(2, fee.valueDay());
        assertEquals(5, fee.bookedDay());
        assertTrue(fee.isSystemGenerated());
    }

    @Test
    void dayStoppingBeingNegativeGetsItsFeeRefunded() {
        replayStream(setup, 6);

        assertEquals(List.of("FEE-REV-ACC-001-D2-1", "FEE-REV-ACC-001-D4-1", "FEE-REV-ACC-001-D5-1"),
                ids(EntryType.FEE_REVERSAL));
        assertEquals(aed("250.00"), ledger.balance(ACC_001, 2, 6));
        assertEquals(aed("650.00"), ledger.balance(ACC_001, 3, 6));
        assertEquals(aed("465.00"), ledger.balance(ACC_001, 4, 6));
        assertEquals(aed("465.00"), ledger.balance(ACC_001, 5, 6));
        assertEquals(aed("465.00"), ledger.balance(ACC_001, 6, 6));
    }

    @Test
    void balanceOfExactlyZeroIsNotCharged() {
        processor.process(new Credit("C1", 1, ACC_001, aed("100.00"), 1), 1);
        processor.process(new Debit("D1", 1, ACC_001, aed("100.00"), 1), 1);
        close.close(1);

        assertEquals(List.of(), ids(EntryType.FEE));
        assertEquals(aed("0.00"), ledger.balance(ACC_001, 1, 1));
    }

    @Test
    void dayNegativeAgainAfterARefundIsChargedAgain() {
        processor.process(new Debit("D1", 1, ACC_001, aed("10.00"), 1), 1);
        close.close(1); // Day 1 is -10.00: fee
        processor.process(new Credit("C1", 2, ACC_001, aed("10.00"), 1), 2);
        close.close(2); // Day 1 is 0.00 without its fee: refund. Day 2 is 0.00: no fee
        processor.process(new Debit("D2", 3, ACC_001, aed("10.00"), 1), 3);
        close.close(3); // Day 1 is -10.00 again: second fee. Days 2 and 3 are then negative too

        assertEquals(List.of("FEE-ACC-001-D1-1", "FEE-ACC-001-D1-2", "FEE-ACC-001-D2-1", "FEE-ACC-001-D3-1"),
                ids(EntryType.FEE));
        assertEquals(List.of("FEE-REV-ACC-001-D1-1"), ids(EntryType.FEE_REVERSAL));
    }

    @Test
    void negativeAccountWithNoFeeForItsCurrencyIsReportedOnce() {
        processor.process(new Debit("D1", 1, ACC_002, bhd("1.000"), 1), 1);
        close.close(1);
        close.close(2);
        close.close(3);

        assertEquals(List.of(), ids(EntryType.FEE));
        assertEquals(List.of("No overdraft fee defined for BHD (Day 1 is negative)",
                        "No overdraft fee defined for BHD (Day 2 is negative)",
                        "No overdraft fee defined for BHD (Day 3 is negative)"),
                ledger.errors().stream().map(ProcessingError::message).toList());
    }

    @Test
    void feesAreTakenFromTheConfiguredAmounts() {
        EndOfDayClose bhdFee = new EndOfDayClose(ledger, Map.of(BHD, bhd("2.500")));
        processor.process(new Debit("D1", 1, ACC_002, bhd("1.000"), 1), 1);
        bhdFee.close(1);

        assertEquals(bhd("-3.500"), ledger.balance(ACC_002, 1, 1));
    }

    @Test
    void runningTheSameCloseTwiceChangesNothing() {
        replayStream(setup, 5);
        List<Entry> before = ledger.entries();
        close.close(5);

        assertEquals(before, ledger.entries());
    }

    private List<String> ids(EntryType type) {
        return ledger.entries().stream().filter(entry -> entry.type() == type).map(Entry::entryId).toList();
    }
}
