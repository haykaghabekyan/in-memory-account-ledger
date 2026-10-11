package ledger.core.handler;

import java.util.List;
import java.util.Optional;
import ledger.core.EventHandler;
import ledger.core.Ledger;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.ProcessingError;
import ledger.event.Event;
import ledger.event.Reversal;

/** Cancels every entry the original event posted, each on the original entry's value day. */
public class ReversalHandler implements EventHandler<Reversal> {

    private final Ledger ledger;

    public ReversalHandler(Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public Class<Reversal> eventType() {
        return Reversal.class;
    }

    @Override
    public void handle(Reversal reversal, int day) {
        List<Entry> originals = ledger.entriesOf(reversal.reversedEventId());
        String problem = problemWith(reversal, originals);
        if (problem != null) {
            ledger.recordError(new ProcessingError(day, reversal.id(), reversal.accountId(),
                    "Reversal rejected: " + problem));
            return;
        }
        for (int i = 0; i < originals.size(); i++) {
            Entry original = originals.get(i);
            String entryId = originals.size() == 1 ? reversal.id() : reversal.id() + "-" + (i + 1);
            ledger.post(new Entry(entryId, reversal.accountId(), original.amount().negate(), EntryType.REVERSAL,
                    original.valueDay(), reversal.bookedDay(), day, reversal.id(), original.entryId(),
                    "reverses " + original.entryId()));
        }
    }

    /** Why the reversal cannot be applied, or null if it can. */
    private String problemWith(Reversal reversal, List<Entry> originals) {
        String target = reversal.reversedEventId();
        Optional<Event> original = ledger.event(target);
        if (original.isEmpty()) {
            return "unknown event " + target;
        }
        if (!original.get().accountId().equals(reversal.accountId())) {
            return target + " belongs to account " + original.get().accountId();
        }
        if (originals.isEmpty()) {
            return target + " posted no entries";
        }
        if (originals.stream().anyMatch(ledger::isReversed)) {
            return target + " is already reversed";
        }
        return null;
    }
}
