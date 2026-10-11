package ledger.core;

import java.util.Objects;
import ledger.event.Event;

/** One arrival in the event log: the event exactly as received, and the day it was processed. */
public record ReceivedEvent(int day, Event event) {

    public ReceivedEvent {
        Objects.requireNonNull(event, "event");
    }
}
