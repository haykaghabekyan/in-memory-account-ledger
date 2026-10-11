package ledger.replay;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.Money;
import ledger.event.Authorization;
import ledger.event.Credit;
import ledger.event.Debit;
import ledger.event.Event;
import ledger.event.Reversal;
import ledger.event.Settlement;

/** The accounts and the E1 to E10 event stream from the spec, in the listed order. */
public final class EventStream {

    public static final int LAST_DAY = 6;

    public static final List<Account> ACCOUNTS = List.of(new Account("ACC-001", AED), new Account("ACC-002", BHD));

    public static final Credit E1 = new Credit("E1", 1, "ACC-001", aed("1200.00"), 1);
    public static final Debit E2 = new Debit("E2", 1, "ACC-001", aed("950.00"), 1);
    public static final Authorization E3 = new Authorization("E3", 2, "ACC-001", "Auth-A", aed("200.00"), 2);
    public static final Credit E4 = new Credit("E4", 3, "ACC-001", aed("400.00"), 3);
    public static final Settlement E5 = new Settlement("E5", 4, "ACC-001", "Auth-A", aed("185.00"), 4);
    public static final Settlement E6 = new Settlement("E6", 4, "ACC-001", "Auth-Z", aed("180.00"), 4);
    public static final Debit E7 = new Debit("E7", 5, "ACC-001", aed("620.00"), 2);
    public static final Authorization E8 = new Authorization("E8", 5, "ACC-001", "Auth-B", aed("90.00"), 5);
    public static final Reversal E9 = new Reversal("E9", 6, "ACC-001", "E7");
    public static final Credit E10 = new Credit("E10", 5, "ACC-002", bhd("10.000"), 5, 3);

    /**
     * The stream in the listed order, each with the day it is processed. The order is not sorted
     * by booked day: E10 is booked on Day 5 but listed after E9 (Day 6), so it arrives on Day 6.
     */
    public static final List<Arrival> ARRIVALS = List.of(
            new Arrival(1, E1), new Arrival(1, E2), new Arrival(2, E3), new Arrival(3, E4),
            new Arrival(4, E5), new Arrival(4, E6), new Arrival(5, E7), new Arrival(5, E8),
            new Arrival(6, E9), new Arrival(6, E10));

    /** One event and the day it is processed. */
    public record Arrival(int day, Event event) {
    }

    private EventStream() {
    }

    private static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    private static Money bhd(String amount) {
        return Money.of(amount, BHD);
    }
}
