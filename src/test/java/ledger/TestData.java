package ledger;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;

import java.util.List;
import ledger.core.Ledger;
import ledger.domain.Account;
import ledger.domain.Money;
import ledger.event.Authorization;
import ledger.event.Credit;
import ledger.event.Debit;
import ledger.event.Event;
import ledger.event.Reversal;
import ledger.event.Settlement;

/** Shared test fixtures: the two accounts and the E1–E10 event stream from the spec. */
public final class TestData {

    public static final String ACC_001 = "ACC-001";
    public static final String ACC_002 = "ACC-002";
    public static final List<Account> ACCOUNTS = List.of(new Account(ACC_001, AED), new Account(ACC_002, BHD));

    public static final Credit E1 = new Credit("E1", 1, ACC_001, aed("1200.00"), 1);
    public static final Debit E2 = new Debit("E2", 1, ACC_001, aed("950.00"), 1);
    public static final Authorization E3 = new Authorization("E3", 2, ACC_001, "Auth-A", aed("200.00"), 2);
    public static final Credit E4 = new Credit("E4", 3, ACC_001, aed("400.00"), 3);
    public static final Settlement E5 = new Settlement("E5", 4, ACC_001, "Auth-A", aed("185.00"), 4);
    public static final Settlement E6 = new Settlement("E6", 4, ACC_001, "Auth-Z", aed("180.00"), 4);
    public static final Debit E7 = new Debit("E7", 5, ACC_001, aed("620.00"), 2);
    public static final Authorization E8 = new Authorization("E8", 5, ACC_001, "Auth-B", aed("90.00"), 5);
    public static final Reversal E9 = new Reversal("E9", 6, ACC_001, "E7");
    public static final Credit E10 = new Credit("E10", 5, ACC_002, bhd("10.000"), 5, 3);

    /** The stream in the listed order, each with the day it is processed. E10 arrives on Day 6. */
    public static final List<Arrival> STREAM = List.of(
            new Arrival(1, E1), new Arrival(1, E2), new Arrival(2, E3), new Arrival(3, E4),
            new Arrival(4, E5), new Arrival(4, E6), new Arrival(5, E7), new Arrival(5, E8),
            new Arrival(6, E9), new Arrival(6, E10));

    public record Arrival(int day, Event event) {
    }

    private TestData() {
    }

    /**
     * Replays the stream up to and including {@code lastDay}, closing each day after its events.
     * Does not capitalize interest.
     */
    public static void replayStream(LedgerSetup setup, int lastDay) {
        int day = 1;
        for (Arrival arrival : STREAM) {
            if (arrival.day() > lastDay) {
                break;
            }
            for (; day < arrival.day(); day++) {
                setup.close().close(day);
            }
            setup.processor().process(arrival.event(), arrival.day());
        }
        for (; day <= lastDay; day++) {
            setup.close().close(day);
        }
    }

    /** A ledger with ACC-001 (AED) and ACC-002 (BHD), both at zero. */
    public static Ledger newLedger() {
        return new Ledger(ACCOUNTS);
    }

    /** The fully wired setup over ACC-001 and ACC-002. */
    public static LedgerSetup newSetup() {
        return LedgerSetup.create(ACCOUNTS);
    }

    public static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    public static Money bhd(String amount) {
        return Money.of(amount, BHD);
    }
}
