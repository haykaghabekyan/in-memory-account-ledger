package ledger.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ledger.domain.Account;
import ledger.domain.AuthorizationDecision;
import ledger.domain.Decision;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.domain.ProcessingError;
import ledger.event.Event;
import ledger.event.Settlement;

/**
 * The in-memory store: accounts and four append-only lists (entries, the event log, errors,
 * authorization decisions).
 * Nothing is ever changed or removed, and the lists are handed out only as read-only copies.
 * It holds no business rules beyond that; those live in the event handlers.
 */
public class Ledger {

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<ReceivedEvent> eventLog = new ArrayList<>();
    private final List<ProcessingError> errors = new ArrayList<>();
    private final List<AuthorizationDecision> decisions = new ArrayList<>();

    public Ledger(Collection<Account> accounts) {
        for (Account account : accounts) {
            this.accounts.put(account.id(), account);
        }
    }

    public List<Account> accounts() {
        return List.copyOf(accounts.values());
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

    public void recordDecision(AuthorizationDecision decision) {
        decisions.add(decision);
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

    /** The decision on an authorization, if one was made. */
    public Optional<AuthorizationDecision> decision(String authId) {
        return decisions.stream().filter(decision -> decision.authId().equals(authId)).findFirst();
    }

    /** Whether a settlement entry has been posted for this authorization. */
    public boolean isSettled(String authId) {
        return entries.stream()
                .filter(entry -> entry.type() == EntryType.SETTLEMENT)
                .map(entry -> event(entry.sourceEventId()).orElseThrow())
                .anyMatch(event -> event instanceof Settlement settlement && settlement.authId().equals(authId));
    }

    /** The total of approved authorizations on the account that are not settled yet. */
    public Money activeHolds(String accountId) {
        Money total = Money.zero(account(accountId).currency());
        for (AuthorizationDecision decision : decisions) {
            if (decision.accountId().equals(accountId)
                    && decision.decision() == Decision.APPROVED
                    && !isSettled(decision.authId())) {
                total = total.plus(decision.amount());
            }
        }
        return total;
    }

    /** Ledger balance minus active holds, with everything processed so far up to the given day. */
    public Money available(String accountId, int day) {
        return balance(accountId, day, day).minus(activeHolds(accountId));
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

    public List<AuthorizationDecision> decisions() {
        return List.copyOf(decisions);
    }
}
