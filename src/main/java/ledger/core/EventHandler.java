package ledger.core;

import ledger.domain.Account;
import ledger.event.Event;

/** The rules for one event type. Steps shared by every event live in {@link EventProcessor}. */
public interface EventHandler<E extends Event> {

    /** The event type this handler handles. */
    Class<E> eventType();

    /** Rejects invalid input by throwing. Called before anything is recorded. */
    default void validate(E event, Account account) {
    }

    /** Applies the event to the ledger. Called once per event id. */
    void handle(E event, int day);
}
