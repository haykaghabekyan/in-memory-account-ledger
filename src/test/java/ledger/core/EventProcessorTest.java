package ledger.core;

import static ledger.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ledger.core.handler.CreditHandler;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.domain.ProcessingError;
import ledger.event.Credit;
import ledger.event.Debit;
import ledger.event.Reversal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventProcessorTest {

    private Ledger ledger;
    private EventProcessor processor;

    @BeforeEach
    void setUp() {
        ledger = newLedger();
        processor = EventProcessor.withStandardHandlers(ledger);
    }

    @Test
    void creditsAndDebitsAreSignedEntries() {
        processor.process(E1, 1);
        processor.process(E2, 1);

        assertEquals(aed("250.00"), ledger.balance(ACC_001, 1, 1));
        assertEquals(aed("-950.00"), ledger.entries().get(1).amount());
    }

    @Test
    void backDatedDebitChangesThePastOnlyFromTheDayItIsPosted() {
        processor.process(E1, 1);
        processor.process(E2, 1);
        processor.process(E7, 5);

        assertEquals(aed("250.00"), ledger.balance(ACC_001, 2, 4));
        assertEquals(aed("-370.00"), ledger.balance(ACC_001, 2, 5));
    }

    @Test
    void reversalCancelsTheOriginalOnItsValueDay() {
        processor.process(E1, 1);
        processor.process(E2, 1);
        processor.process(E7, 5);
        processor.process(E9, 6);

        Entry reversal = ledger.entries().get(3);
        assertEquals(EntryType.REVERSAL, reversal.type());
        assertEquals(aed("620.00"), reversal.amount());
        assertEquals(2, reversal.valueDay());
        assertEquals("E7", reversal.reversesEntryId());
        assertEquals(aed("-370.00"), ledger.balance(ACC_001, 2, 5));
        assertEquals(aed("250.00"), ledger.balance(ACC_001, 2, 6));
    }

    @Test
    void instalmentsSplitEquallyWithTheLastTakingTheRemainder() {
        processor.process(E10, 6);

        List<Money> amounts = ledger.entries().stream().map(Entry::amount).toList();
        assertEquals(List.of(bhd("3.333"), bhd("3.333"), bhd("3.334")), amounts);
        assertEquals(List.of("E10-1", "E10-2", "E10-3"), ledger.entries().stream().map(Entry::entryId).toList());
        assertEquals(bhd("10.000"), ledger.balance(ACC_002, 5, 6));
    }

    @Test
    void lateBookingIsUnknownBeforeTheDayItIsPosted() {
        processor.process(E10, 6);

        Entry first = ledger.entries().get(0);
        assertEquals(5, first.bookedDay());
        assertEquals(6, first.postedDay());
        assertEquals(5, first.valueDay());
        assertEquals(bhd("0.000"), ledger.balance(ACC_002, 5, 5));
        assertEquals(bhd("10.000"), ledger.balance(ACC_002, 5, 6));
    }

    @Test
    void reversingAnUnknownEventPostsNothing() {
        processor.process(new Reversal("R1", 6, ACC_001, "E99"), 6);

        assertTrue(ledger.entries().isEmpty());
        assertEquals("Reversal rejected: unknown event E99", onlyError().message());
    }

    @Test
    void anEventCanOnlyBeReversedOnce() {
        processor.process(E7, 5);
        processor.process(E9, 6);
        processor.process(new Reversal("R2", 6, ACC_001, "E7"), 6);

        assertEquals(2, ledger.entries().size());
        assertEquals("Reversal rejected: E7 is already reversed", onlyError().message());
    }

    @Test
    void identicalDuplicateIsIgnoredAndReported() {
        processor.process(E7, 5);
        processor.process(E7, 5);

        assertEquals(1, ledger.entries().size());
        assertEquals(2, ledger.eventLog().size());
        assertEquals("Duplicate of E7, ignored", onlyError().message());
    }

    @Test
    void reusedIdWithDifferentDetailsIsRejected() {
        processor.process(E7, 5);
        processor.process(new Debit("E7", 5, ACC_001, aed("700.00"), 2), 5);

        assertEquals(aed("-620.00"), ledger.balance(ACC_001, 6, 5));
        assertEquals("E7 already processed with different details, rejected", onlyError().message());
    }

    @Test
    void invalidInputThrowsBeforeAnythingIsRecorded() {
        Credit wrongCurrency = new Credit("X1", 1, ACC_001, bhd("1.000"), 1);
        Credit unknownAccount = new Credit("X2", 1, "ACC-999", aed("1.00"), 1);

        assertThrows(IllegalArgumentException.class, () -> processor.process(wrongCurrency, 1));
        assertThrows(IllegalArgumentException.class, () -> processor.process(unknownAccount, 1));
        assertTrue(ledger.eventLog().isEmpty());
    }

    @Test
    void refusesToStartWithoutAHandlerForEveryEventType() {
        List<EventHandler<?>> onlyCredits = List.of(new CreditHandler(ledger));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new EventProcessor(ledger, onlyCredits));
        assertEquals("No handler for Authorization, Debit, Reversal, Settlement", error.getMessage());
    }

    private ProcessingError onlyError() {
        assertEquals(1, ledger.errors().size());
        return ledger.errors().get(0);
    }
}
