package ledger.replay;

import static ledger.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ledger.LedgerSetup;
import ledger.core.Authorizations;
import ledger.core.Ledger;
import ledger.domain.AuthorizationDecision;
import ledger.domain.Decision;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.domain.ProcessingError;
import ledger.replay.EventStream.Arrival;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The full E1 to E10 replay against the expected results in the docs. */
class ReplayTest {

    private LedgerSetup setup;
    private Ledger ledger;
    private String output;

    @BeforeEach
    void runTheFullReplay() {
        setup = newSetup();
        ledger = setup.ledger();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        new Replay(setup, new DailyReport(setup, out)).run(EventStream.ARRIVALS, EventStream.LAST_DAY);
        output = buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void closingBalanceOfEachDayAsKnownAtItsClose() {
        assertEquals(List.of(aed("250.00"), aed("250.00"), aed("650.00"), aed("465.00"), aed("-230.00"),
                aed("466.02")), closingBalances(ACC_001));
        assertEquals(List.of(bhd("0.000"), bhd("0.000"), bhd("0.000"), bhd("0.000"), bhd("0.000"),
                bhd("10.008")), closingBalances(ACC_002));
    }

    @Test
    void dayFiveViewShowsE7AndItsFees() {
        assertEquals(aed("-395.00"), ledger.balance(ACC_001, 2, 5));
        assertEquals(aed("5.00"), ledger.balance(ACC_001, 3, 5));
        assertEquals(aed("-205.00"), ledger.balance(ACC_001, 4, 5));
    }

    @Test
    void feesForDaysTwoFourAndFiveAreChargedOnDayFiveAndRefundedOnDaySix() {
        assertEquals(List.of("FEE-ACC-001-D2-1", "FEE-ACC-001-D4-1", "FEE-ACC-001-D5-1"),
                entryIds(EntryType.FEE, 5));
        assertEquals(List.of("FEE-REV-ACC-001-D2-1", "FEE-REV-ACC-001-D4-1", "FEE-REV-ACC-001-D5-1"),
                entryIds(EntryType.FEE_REVERSAL, 6));
    }

    @Test
    void authAIsSettledAuthBIsDeclinedAndAuthZIsRejected() {
        Authorizations authorizations = setup.authorizations();
        AuthorizationDecision authA = authorizations.decision("Auth-A").orElseThrow();
        AuthorizationDecision authB = authorizations.decision("Auth-B").orElseThrow();

        assertEquals(Decision.APPROVED, authA.decision());
        assertEquals(aed("-185.00"), authorizations.settlement("Auth-A").orElseThrow().amount());
        assertEquals(Decision.DECLINED, authB.decision());
        assertEquals(aed("-245.00"), authB.availableAfterHold());
        assertEquals(List.of(new ProcessingError(4, "E6", ACC_001, "Settlement rejected: no authorization Auth-Z")),
                ledger.errors());
    }

    @Test
    void e10IsALateBookingSplitIntoThreeInstalments() {
        List<Entry> instalments = ledger.entriesOf("E10");

        assertEquals(List.of(bhd("3.333"), bhd("3.333"), bhd("3.334")),
                instalments.stream().map(Entry::amount).toList());
        assertTrue(instalments.stream().allMatch(entry -> entry.bookedDay() == 5 && entry.postedDay() == 6
                && entry.valueDay() == 5));
    }

    @Test
    void interestIsCapitalizedOnDaySix() {
        assertEquals(List.of(aed("1.02"), bhd("0.008")), ledger.entries().stream()
                .filter(entry -> entry.type() == EntryType.INTEREST).map(Entry::amount).toList());
    }

    @Test
    void reportPrintsEveryDayInOrderWithTheFinalBalances() {
        int previous = -1;
        for (int day = 1; day <= 6; day++) {
            int position = output.indexOf("=== Day " + day + " ===");
            assertTrue(position > previous, "Day " + day + " header missing or out of order");
            previous = position;
        }
        assertTrue(output.contains("ACC-001      466.02 AED"));
        assertTrue(output.contains("ACC-002      10.008 BHD"));
        assertTrue(output.contains("E6    ACC-001  Settlement rejected: no authorization Auth-Z"));
    }

    @Test
    void arrivalOnAnEarlierDayThanTheReplayHasReachedIsRefused() {
        LedgerSetup fresh = newSetup();
        Replay replay = new Replay(fresh, new DailyReport(fresh, new PrintStream(new ByteArrayOutputStream())));
        List<Arrival> backwards = List.of(new Arrival(2, E3), new Arrival(1, E1));

        assertThrows(IllegalArgumentException.class, () -> replay.run(backwards, 6));
    }

    private List<Money> closingBalances(String accountId) {
        return List.of(1, 2, 3, 4, 5, 6).stream().map(day -> ledger.balance(accountId, day, day)).toList();
    }

    private List<String> entryIds(EntryType type, int postedDay) {
        return ledger.entries().stream()
                .filter(entry -> entry.type() == type && entry.postedDay() == postedDay)
                .map(Entry::entryId).toList();
    }
}
