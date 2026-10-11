package ledger.replay;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import ledger.LedgerSetup;
import ledger.core.Authorizations;
import ledger.core.Ledger;
import ledger.core.ReceivedEvent;
import ledger.domain.Account;
import ledger.domain.AuthorizationDecision;
import ledger.domain.Decision;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.InterestAccrual;
import ledger.domain.Money;
import ledger.event.Authorization;
import ledger.event.Credit;
import ledger.event.Debit;
import ledger.event.Event;
import ledger.event.Reversal;
import ledger.event.Settlement;

/**
 * Prints one day of the replay from what the ledger has stored. It is printed right after the
 * day's close (and, on the last day, interest), so "now" means the end of that day.
 */
public class DailyReport {

    private final Ledger ledger;
    private final Authorizations authorizations;
    private final PrintStream out;

    public DailyReport(LedgerSetup setup, PrintStream out) {
        this.ledger = setup.ledger();
        this.authorizations = setup.authorizations();
        this.out = out;
    }

    public void print(int day) {
        out.println();
        out.println("=== Day " + day + " ===");
        section("Events", events(day));
        section("Closing ledger balance", closingBalances(day));
        section("Balances by value day, as known now", balancesByValueDay(day));
        section("Fees", fees(day));
        if (interestCapitalizedOn(day)) {
            section("Interest (daily accruals, capitalized on Day " + day + ")", interest());
        }
        section("Authorizations", authorizationStates(day));
        section("Errors", errors(day));
    }

    private void section(String title, List<String> lines) {
        if (lines.isEmpty()) {
            out.printf("%-15s none%n", title);
            return;
        }
        out.println(title);
        lines.forEach(line -> out.println("  " + line));
    }

    private List<String> events(int day) {
        List<ReceivedEvent> log = ledger.eventLog();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < log.size(); i++) {
            ReceivedEvent received = log.get(i);
            if (received.day() == day) {
                Event event = received.event();
                String type = event.getClass().getSimpleName().toUpperCase();
                lines.add(String.format("%-4s %-14s %s  %s", event.id(), type, event.accountId(),
                        isDuplicate(log, i) ? "DUPLICATE, not applied" : describe(event, day)));
            }
        }
        return lines;
    }

    private static boolean isDuplicate(List<ReceivedEvent> log, int index) {
        String id = log.get(index).event().id();
        for (int i = 0; i < index; i++) {
            if (log.get(i).event().id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private String describe(Event event, int day) {
        if (event instanceof Credit credit) {
            String text = credit.amount() + "  value Day " + credit.valueDay();
            if (credit.instalments() > 1) {
                text += " in " + credit.instalments() + " instalments (" + amountsPostedBy(credit.id()) + ")";
            }
            if (credit.bookedDay() < day) {
                text += ", booked Day " + credit.bookedDay() + ", late booking";
            }
            return text;
        }
        if (event instanceof Debit debit) {
            String text = debit.amount() + "  value Day " + debit.valueDay();
            return debit.valueDay() < debit.bookedDay() ? text + " (back-dated)" : text;
        }
        if (event instanceof Authorization authorization) {
            Optional<AuthorizationDecision> decision = authorizations.decision(authorization.authId())
                    .filter(made -> made.eventId().equals(authorization.id()));
            String text = authorization.authId() + " " + authorization.amount() + "  ";
            return decision.map(made -> text + made.decision() + " (available after hold "
                    + made.availableAfterHold().amount().toPlainString() + ")").orElse(text + "REJECTED");
        }
        if (event instanceof Settlement settlement) {
            String text = settlement.authId() + " " + settlement.amount() + "  ";
            return postedNothing(settlement.id()) ? text + "REJECTED" : text + "value Day " + settlement.valueDay();
        }
        if (event instanceof Reversal reversal) {
            String text = "reverses " + reversal.reversedEventId() + "  ";
            return postedNothing(reversal.id())
                    ? text + "REJECTED"
                    : text + "value Day " + ledger.entriesOf(reversal.id()).get(0).valueDay();
        }
        throw new IllegalArgumentException("Unknown event type: " + event.getClass().getSimpleName());
    }

    private String amountsPostedBy(String eventId) {
        return ledger.entriesOf(eventId).stream()
                .map(entry -> entry.amount().amount().toPlainString())
                .collect(Collectors.joining(", "));
    }

    private boolean postedNothing(String eventId) {
        return ledger.entriesOf(eventId).isEmpty();
    }

    private List<String> closingBalances(int day) {
        List<String> lines = new ArrayList<>();
        for (Account account : ledger.accounts()) {
            lines.add(String.format("%-8s %10s %s   available %10s", account.id(),
                    plain(ledger.balance(account.id(), day, day)), account.currency(),
                    plain(authorizations.available(account.id(), day))));
        }
        return lines;
    }

    private List<String> balancesByValueDay(int day) {
        List<String> lines = new ArrayList<>();
        for (Account account : ledger.accounts()) {
            List<String> days = new ArrayList<>();
            for (int valueDay = 1; valueDay <= day; valueDay++) {
                days.add("D" + valueDay + " " + plain(ledger.balance(account.id(), valueDay, day)));
            }
            lines.add(String.format("%-8s %s", account.id(), String.join(" | ", days)));
        }
        return lines;
    }

    private List<String> fees(int day) {
        List<String> lines = new ArrayList<>();
        for (Entry entry : ledger.entries()) {
            if (entry.postedDay() == day && (entry.type() == EntryType.FEE || entry.type() == EntryType.FEE_REVERSAL)) {
                String action = entry.type() == EntryType.FEE ? "charged" : "refunded";
                lines.add(String.format("%-9s %-22s %8s %s  for Day %d", action, entry.entryId(),
                        signed(entry.amount()), entry.amount().currency(), entry.valueDay()));
            }
        }
        return lines;
    }

    private boolean interestCapitalizedOn(int day) {
        return ledger.accruals().stream().mapToInt(InterestAccrual::day).max().orElse(0) == day;
    }

    private List<String> interest() {
        List<String> lines = new ArrayList<>();
        for (Account account : ledger.accounts()) {
            String accruals = ledger.accruals().stream()
                    .filter(accrual -> accrual.accountId().equals(account.id()))
                    .map(accrual -> plain(accrual.accrual()))
                    .collect(Collectors.joining(" | "));
            String credit = ledger.entries().stream()
                    .filter(entry -> entry.type() == EntryType.INTEREST && entry.accountId().equals(account.id()))
                    .map(entry -> "credit " + plain(entry.amount()))
                    .findFirst().orElse("no credit");
            lines.add(String.format("%-8s %s   %s", account.id(), accruals, credit));
        }
        return lines;
    }

    private List<String> authorizationStates(int day) {
        List<String> lines = new ArrayList<>();
        for (AuthorizationDecision decision : ledger.decisions()) {
            if (decision.decidedDay() > day) {
                continue;
            }
            Optional<Entry> settlement = authorizations.settlement(decision.authId())
                    .filter(entry -> entry.postedDay() <= day);
            String state;
            String detail;
            if (decision.decision() == Decision.DECLINED) {
                state = "DECLINED";
                detail = "declined Day " + decision.decidedDay();
            } else if (settlement.isPresent()) {
                state = "SETTLED";
                detail = "approved Day " + decision.decidedDay() + ", settled for "
                        + plain(settlement.get().amount().negate()) + " on Day " + settlement.get().postedDay();
            } else {
                state = "ACTIVE";
                detail = "approved Day " + decision.decidedDay() + ", hold still reserved";
            }
            lines.add(String.format("%-7s %-8s %10s  %-9s %s", decision.authId(), decision.accountId(),
                    plain(decision.amount()), state, detail));
        }
        return lines;
    }

    private List<String> errors(int day) {
        return ledger.errors().stream()
                .filter(error -> error.day() == day)
                .map(error -> String.format("%-5s %-8s %s",
                        error.eventId() == null ? "close" : error.eventId(), error.accountId(), error.message()))
                .toList();
    }

    private static String plain(Money money) {
        return money.amount().toPlainString();
    }

    private static String signed(Money money) {
        return (money.isPositive() ? "+" : "") + plain(money);
    }
}
