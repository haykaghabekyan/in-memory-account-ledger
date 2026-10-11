package ledger.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ledger.domain.Account;
import ledger.domain.Entry;
import ledger.domain.Money;
import ledger.domain.ProcessingError;
import ledger.event.Event;

/**
 * The in-memory store: accounts and three append-only lists (entries, the event log, errors).
 * Nothing is ever changed or removed, and the lists are handed out only as read-only copies.
 * It holds no business rules beyond that; those live in the event handlers.
 */
public class Ledger {

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<ReceivedEvent> eventLog = new ArrayList<>();
    private final List<ProcessingError> errors = new ArrayList<>();

    public Ledger(Collection<Account> accounts) {
        for (Account account : accounts) {
            this.accounts.put(account.id(), account);
        }
    }

    public Account account(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Unknown account: " + accountId);
        }
        return account;
    }

    public void post(Entry entry) {
        account(entry.accountId()).requireCurrencyOf(entry.amount());
        entries.add(entry);
    }

    public void recordEvent(ReceivedEvent received) {
        eventLog.add(received);
    }

    public void recordError(ProcessingError error) {
        errors.add(error);
    }

    /** The first event received with this id, if any. */
    public Optional<Event> event(String eventId) {
        return eventLog.stream().map(ReceivedEvent::event).filter(event -> event.id().equals(eventId)).findFirst();
    }

    /** The entries an event posted, in posting order. */
    public List<Entry> entriesOf(String eventId) {
        return entries.stream().filter(entry -> eventId.equals(entry.sourceEventId())).toList();
    }

    /** Whether another entry already cancels this one. */
    public boolean isReversed(Entry entry) {
        return entries.stream().anyMatch(other -> entry.entryId().equals(other.reversesEntryId()));
    }

    /**
     * The closing balance of a value day, as known at the end of a given day: entries with
     * value day on or before {@code valueDay}, counting only those posted by {@code knownAtEndOfDay}.
     */
    public Money balance(String accountId, int valueDay, int knownAtEndOfDay) {
        Money balance = Money.zero(account(accountId).currency());
        for (Entry entry : entries) {
            if (entry.accountId().equals(accountId)
                    && entry.valueDay() <= valueDay
                    && entry.postedDay() <= knownAtEndOfDay) {
                balance = balance.plus(entry.amount());
            }
        }
        return balance;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public List<ReceivedEvent> eventLog() {
        return List.copyOf(eventLog);
    }

    public List<ProcessingError> errors() {
        return List.copyOf(errors);
    }
}
