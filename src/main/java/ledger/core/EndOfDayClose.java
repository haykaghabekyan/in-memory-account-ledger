package ledger.core;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import ledger.domain.Account;
import ledger.domain.Currency;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.domain.ProcessingError;

/**
 * The end-of-day close. Re-evaluates every day up to the closing day, oldest first, with
 * everything known at that moment, and posts or refunds overdraft fees so that each day has
 * an active fee exactly when its closing balance is negative.
 *
 * <p>Oldest first matters: a fee lowers every later day's balance, and each fee is posted
 * immediately, so later days are evaluated with earlier days' fees included. A day's own fee is
 * excluded from its balance, so a fee can never justify itself.
 *
 * <p>Running the close twice for the same day changes nothing: the second run finds every fee
 * already in place.
 */
public class EndOfDayClose {

    private final Ledger ledger;
    private final Map<Currency, Money> overdraftFees;

    /** @param overdraftFees the fee per currency; a currency with no fee gets an error instead */
    public EndOfDayClose(Ledger ledger, Map<Currency, Money> overdraftFees) {
        overdraftFees.forEach((currency, fee) -> {
            if (fee.currency() != currency || !fee.isPositive()) {
                throw new IllegalArgumentException("Invalid fee for " + currency + ": " + fee);
            }
        });
        this.ledger = ledger;
        this.overdraftFees = Map.copyOf(overdraftFees);
    }

    public void close(int closingDay) {
        for (Account account : ledger.accounts()) {
            for (int day = 1; day <= closingDay; day++) {
                evaluate(account, day, closingDay);
            }
        }
    }

    private void evaluate(Account account, int day, int closingDay) {
        Optional<Entry> activeFee = activeFee(account.id(), day);
        Money balance = ledger.balance(account.id(), day, closingDay);
        Money balanceExcludingOwnFee = activeFee.map(fee -> balance.minus(fee.amount())).orElse(balance);

        if (balanceExcludingOwnFee.isNegative() && activeFee.isEmpty()) {
            chargeFee(account, day, closingDay);
        } else if (!balanceExcludingOwnFee.isNegative() && activeFee.isPresent()) {
            refundFee(activeFee.get(), closingDay);
        }
    }

    private void chargeFee(Account account, int day, int closingDay) {
        Money fee = overdraftFees.get(account.currency());
        if (fee == null) {
            reportNoFeeDefinedOnce(account, day, closingDay);
            return;
        }
        String entryId = "FEE-" + account.id() + "-D" + day + "-" + (feesCharged(account.id(), day) + 1);
        ledger.post(new Entry(entryId, account.id(), fee.negate(), EntryType.FEE,
                day, closingDay, closingDay, null, null, "overdraft fee for Day " + day));
    }

    private void refundFee(Entry fee, int closingDay) {
        String entryId = fee.entryId().replaceFirst("^FEE-", "FEE-REV-");
        ledger.post(new Entry(entryId, fee.accountId(), fee.amount().negate(), EntryType.FEE_REVERSAL,
                fee.valueDay(), closingDay, closingDay, null, fee.entryId(),
                "refund of overdraft fee for Day " + fee.valueDay()));
    }

    /** Every close re-evaluates past days, so the same missing fee would otherwise be reported daily. */
    private void reportNoFeeDefinedOnce(Account account, int day, int closingDay) {
        String message = "No overdraft fee defined for " + account.currency() + " (Day " + day + " is negative)";
        boolean alreadyReported = ledger.errors().stream().anyMatch(error -> error.eventId() == null
                && error.accountId().equals(account.id())
                && error.message().equals(message));
        if (!alreadyReported) {
            ledger.recordError(new ProcessingError(closingDay, null, account.id(), message));
        }
    }

    /** The fee for this account and day that has not been refunded. There is at most one. */
    private Optional<Entry> activeFee(String accountId, int day) {
        return feesFor(accountId, day).filter(fee -> !ledger.isReversed(fee)).findFirst();
    }

    private long feesCharged(String accountId, int day) {
        return feesFor(accountId, day).count();
    }

    private Stream<Entry> feesFor(String accountId, int day) {
        return ledger.entries().stream().filter(entry -> entry.type() == EntryType.FEE
                && entry.accountId().equals(accountId)
                && entry.valueDay() == day);
    }
}
