package ledger;

import ledger.replay.DailyReport;
import ledger.replay.EventStream;
import ledger.replay.Replay;

/** Replays the E1 to E10 event stream and prints the report for Days 1 to 6. */
public class Main {

    public static void main(String[] args) {
        LedgerSetup setup = LedgerSetup.create(EventStream.ACCOUNTS);
        System.out.println("In-memory account ledger: replay of E1 to E10 in the listed order");
        new Replay(setup, new DailyReport(setup, System.out)).run(EventStream.ARRIVALS, EventStream.LAST_DAY);
    }
}
