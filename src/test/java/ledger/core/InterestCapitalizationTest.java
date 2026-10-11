package ledger.core;

import static ledger.TestData.*;
import static ledger.domain.Currency.AED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import ledger.LedgerSetup;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.InterestAccrual;
import ledger.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterestCapitalizationTest {

    private static final BigDecimal RATE = LedgerSetup.DAILY_INTEREST_RATE;

    private LedgerSetup setup;
    private Ledger ledger;

    @BeforeEach
    void setUp() {
        setup = newSetup();
        ledger = setup.ledger();
    }

    @Test
    void acc001AccruesDailyWithTheRoundingCarriedForward() {
        replayStream(setup, 6);
        setup.interest().capitalize(6);

        assertEquals(List.of(aed("250.00"), aed("250.00"), aed("650.00"), aed("465.00"), aed("465.00"), aed("465.00")),
                accrualsOf(ACC_001).stream().map(InterestAccrual::closingBalance).toList());
        assertEquals(List.of(aed("0.10"), aed("0.10"), aed("0.26"), aed("0.19"), aed("0.18"), aed("0.19")),
                accrualsOf(ACC_001).stream().map(InterestAccrual::accrual).toList());
        assertEquals(aed("1.02"), interestEntry(ACC_001).amount());
        assertEquals(aed("466.02"), ledger.balance(ACC_001, 6, 6));
    }

    @Test
    void acc002AccruesAtThreeDecimalPlacesFromItsLateBooking() {
        replayStream(setup, 6);
        setup.interest().capitalize(6);

        assertEquals(List.of(bhd("0.000"), bhd("0.000"), bhd("0.000"), bhd("0.000"), bhd("0.004"), bhd("0.004")),
                accrualsOf(ACC_002).stream().map(InterestAccrual::accrual).toList());
        assertEquals(bhd("0.008"), interestEntry(ACC_002).amount());
        assertEquals(bhd("10.008"), ledger.balance(ACC_002, 6, 6));
    }

    @Test
    void creditIsValueDatedToTheLastDayAndPostedByTheSystem() {
        replayStream(setup, 6);
        setup.interest().capitalize(6);

        Entry credit = interestEntry(ACC_001);
        assertEquals("INT-ACC-001-D6", credit.entryId());
        assertEquals(6, credit.valueDay());
        assertEquals(6, credit.postedDay());
        assertNull(credit.sourceEventId());
    }

    @Test
    void carryingTheRoundingForwardGivesTheCorrectlyRoundedTotal() {
        List<Money> balances = List.of(aed("250.00"), aed("250.00"), aed("650.00"),
                aed("465.00"), aed("465.00"), aed("465.00"));

        List<Money> accruals = InterestCapitalization.accrue(balances, AED, RATE);

        // Exact total is 1.018. Rounding each day on its own would give 0.19 three times (1.03).
        assertEquals(aed("1.02"), accruals.stream().reduce(aed("0.00"), Money::plus));
    }

    @Test
    void zeroAndNegativeBalancesAccrueNothing() {
        List<Money> accruals = InterestCapitalization.accrue(List.of(aed("0.00"), aed("-500.00")), AED, RATE);

        assertEquals(List.of(aed("0.00"), aed("0.00")), accruals);
    }

    @Test
    void lastDaysAccrualDoesNotIncludeTheCreditItself() {
        replayStream(setup, 6);
        setup.interest().capitalize(6);

        InterestAccrual daySix = accrualsOf(ACC_001).get(5);
        assertEquals(aed("465.00"), daySix.closingBalance());
        assertEquals(aed("0.19"), daySix.accrual());
    }

    @Test
    void accountThatWasNeverPositiveGetsNoCredit() {
        setup.processor().process(E2, 1);
        setup.interest().capitalize(6);

        assertEquals(List.of(), ledger.entries().stream().filter(entry -> entry.type() == EntryType.INTEREST).toList());
        assertEquals(12, ledger.accruals().size());
    }

    @Test
    void capitalizingTwiceIsRefused() {
        replayStream(setup, 6);
        InterestCapitalization interest = setup.interest();
        interest.capitalize(6);

        assertThrows(IllegalStateException.class, () -> interest.capitalize(6));
        assertEquals(12, ledger.accruals().size());
    }

    private List<InterestAccrual> accrualsOf(String accountId) {
        return ledger.accruals().stream().filter(accrual -> accrual.accountId().equals(accountId)).toList();
    }

    private Entry interestEntry(String accountId) {
        return ledger.entries().stream()
                .filter(entry -> entry.type() == EntryType.INTEREST && entry.accountId().equals(accountId))
                .findFirst().orElseThrow();
    }
}
