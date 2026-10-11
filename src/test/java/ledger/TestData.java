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
import ledger.event.Reversal;
import ledger.event.Settlement;
import ledger.replay.EventStream;
import ledger.replay.EventStream.Arrival;

/** Shared test fixtures: short names for the accounts and the E1 to E10 stream in {@link EventStream}. */
public final class TestData {

    public static final String ACC_001 = "ACC-001";
    public static final String ACC_002 = "ACC-002";
    public static final List<Account> ACCOUNTS = EventStream.ACCOUNTS;

    public static final Credit E1 = EventStream.E1;
    public static final Debit E2 = EventStream.E2;
    public static final Authorization E3 = EventStream.E3;
    public static final Credit E4 = EventStream.E4;
    public static final Settlement E5 = EventStream.E5;
    public static final Settlement E6 = EventStream.E6;
    public static final Debit E7 = EventStream.E7;
    public static final Authorization E8 = EventStream.E8;
    public static final Reversal E9 = EventStream.E9;
    public static final Credit E10 = EventStream.E10;

    private TestData() {
    }

    /** A ledger with ACC-001 (AED) and ACC-002 (BHD), both at zero. */
    public static Ledger newLedger() {
        return new Ledger(ACCOUNTS);
    }

    /** The fully wired setup over ACC-001 and ACC-002. */
    public static LedgerSetup newSetup() {
        return LedgerSetup.create(ACCOUNTS);
    }

    /**
     * Replays the stream up to and including {@code lastDay}, closing each day after its events.
     * Unlike {@link ledger.replay.Replay}, it does not capitalize interest or print anything.
     */
    public static void replayStream(LedgerSetup setup, int lastDay) {
        int day = 1;
        for (Arrival arrival : EventStream.ARRIVALS) {
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

    public static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    public static Money bhd(String amount) {
        return Money.of(amount, BHD);
    }
}
