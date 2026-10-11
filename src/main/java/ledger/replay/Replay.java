package ledger.replay;

import java.util.List;
import ledger.LedgerSetup;
import ledger.replay.EventStream.Arrival;

/**
 * Feeds arrivals to the ledger in the given order. When the next arrival is on a later day, or
 * the stream ends, each finished day is closed and reported. Interest is capitalized after the
 * last day's close, before that day is reported.
 */
public class Replay {

    private final LedgerSetup setup;
    private final DailyReport report;

    public Replay(LedgerSetup setup, DailyReport report) {
        this.setup = setup;
        this.report = report;
    }

    public void run(List<Arrival> arrivals, int lastDay) {
        int day = 1;
        for (Arrival arrival : arrivals) {
            if (arrival.day() < day || arrival.day() > lastDay) {
                throw new IllegalArgumentException("Arrival on Day " + arrival.day() + " is outside Days " + day
                        + " to " + lastDay);
            }
            for (; day < arrival.day(); day++) {
                endOfDay(day, lastDay);
            }
            setup.processor().process(arrival.event(), arrival.day());
        }
        for (; day <= lastDay; day++) {
            endOfDay(day, lastDay);
        }
    }

    private void endOfDay(int day, int lastDay) {
        setup.close().close(day);
        if (day == lastDay) {
            setup.interest().capitalize(day);
        }
        report.print(day);
    }
}
