package ledger.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import ledger.core.handler.AuthorizationHandler;
import ledger.core.handler.CreditHandler;
import ledger.core.handler.DebitHandler;
import ledger.core.handler.ReversalHandler;
import ledger.core.handler.SettlementHandler;
import ledger.domain.Account;
import ledger.domain.ProcessingError;
import ledger.event.Event;

/**
 * Runs every event through the same steps: validate, record in the event log, check for a
 * duplicate id, then hand it to the handler for its type.
 */
public class EventProcessor {

    private final Ledger ledger;
    private final Map<Class<?>, EventHandler<?>> handlers = new HashMap<>();

    public EventProcessor(Ledger ledger, List<EventHandler<?>> handlers) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        for (EventHandler<?> handler : handlers) {
            if (this.handlers.put(handler.eventType(), handler) != null) {
                throw new IllegalArgumentException("Two handlers for " + handler.eventType().getSimpleName());
            }
        }
        requireHandlerForEveryEventType();
    }

    /** A processor with the standard handler for every event type. */
    public static EventProcessor withStandardHandlers(Ledger ledger) {
        return new EventProcessor(ledger, List.of(
                new CreditHandler(ledger),
                new DebitHandler(ledger),
                new AuthorizationHandler(),
                new SettlementHandler(),
                new ReversalHandler(ledger)));
    }

    /**
     * Processes one event on the given day. The day is passed in by the caller (the replay);
     * nothing in the ledger has a clock of its own.
     */
    public void process(Event event, int day) {
        Objects.requireNonNull(event, "event");
        EventHandler<?> handler = handlers.get(event.getClass());
        Account account = ledger.account(event.accountId());
        validate(handler, event, account);

        Optional<Event> earlier = ledger.event(event.id());
        ledger.recordEvent(new ReceivedEvent(day, event));
        if (earlier.isPresent()) {
            String message = earlier.get().equals(event)
                    ? "Duplicate of " + event.id() + ", ignored"
                    : event.id() + " already processed with different details, rejected";
            ledger.recordError(new ProcessingError(day, event.id(), event.accountId(), message));
            return;
        }
        handle(handler, event, day);
    }

    private static <E extends Event> void validate(EventHandler<E> handler, Event event, Account account) {
        handler.validate(handler.eventType().cast(event), account);
    }

    private static <E extends Event> void handle(EventHandler<E> handler, Event event, int day) {
        handler.handle(handler.eventType().cast(event), day);
    }

    /** The map loses the compiler's exhaustiveness check on the sealed Event, so check at startup. */
    private void requireHandlerForEveryEventType() {
        Set<String> missing = Arrays.stream(Event.class.getPermittedSubclasses())
                .filter(type -> !handlers.containsKey(type))
                .map(Class::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("No handler for " + String.join(", ", missing));
        }
    }
}
