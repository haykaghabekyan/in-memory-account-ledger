package ledger.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import ledger.domain.Account;
import ledger.domain.Currency;
import ledger.domain.Entry;
import ledger.domain.EntryType;
import ledger.domain.InterestAccrual;
import ledger.domain.Money;

/**
 * Daily interest on positive closing balances, capitalized as a single credit at the end of the
 * window. Run once, after the last day's close, so balances are taken as known at that moment
 * (back-dated entries and fee refunds already included).
 *
 * <p>Daily accruals are memo records, not ledger entries, so interest does not compound. Each
 * day's exact accrual plus the previous day's rounding difference (the carry) is rounded to the
 * account's precision, so the rounded accruals add up to the exact total, correctly rounded.
 */
public class InterestCapitalization {

    private final Ledger ledger;
    private final BigDecimal dailyRate;

    /** @param dailyRate e.g. 0.0004 for 0.04% per day */
    public InterestCapitalization(Ledger ledger, BigDecimal dailyRate) {
        if (dailyRate.signum() <= 0) {
            throw new IllegalArgumentException("Daily rate must be positive: " + dailyRate);
        }
        this.ledger = ledger;
        this.dailyRate = dailyRate;
    }

    /** Accrues interest for Days 1 to {@code lastDay} and posts each account's credit on {@code lastDay}. */
    public void capitalize(int lastDay) {
        if (lastDay < 1) {
            throw new IllegalArgumentException("lastDay must be at least 1: " + lastDay);
        }
        for (Account account : ledger.accounts()) {
            if (ledger.entries().stream().anyMatch(entry -> entry.entryId().equals(entryId(account, lastDay)))) {
                throw new IllegalStateException("Interest for " + account.id() + " is already capitalized");
            }
        }
        for (Account account : ledger.accounts()) {
            capitalize(account, lastDay);
        }
    }

    private void capitalize(Account account, int lastDay) {
        List<Money> balances = new ArrayList<>();
        for (int day = 1; day <= lastDay; day++) {
            balances.add(ledger.balance(account.id(), day, lastDay));
        }
        List<Money> accruals = accrue(balances, account.currency(), dailyRate);

        Money total = Money.zero(account.currency());
        for (int i = 0; i < accruals.size(); i++) {
            ledger.recordAccrual(new InterestAccrual(account.id(), i + 1, balances.get(i), accruals.get(i)));
            total = total.plus(accruals.get(i));
        }
        if (total.isPositive()) {
            ledger.post(new Entry(entryId(account, lastDay), account.id(), total, EntryType.INTEREST,
                    lastDay, lastDay, lastDay, null, null, "interest for Days 1–" + lastDay));
        }
    }

    /**
     * The rounded daily accruals for a run of closing balances. A balance that is not positive
     * accrues nothing; the carry passes through it unchanged. The exact accruals and the carry
     * are intermediate values and are never stored.
     */
    static List<Money> accrue(List<Money> closingBalances, Currency currency, BigDecimal dailyRate) {
        List<Money> accruals = new ArrayList<>();
        BigDecimal carry = BigDecimal.ZERO;
        for (Money balance : closingBalances) {
            BigDecimal exact = balance.isPositive() ? balance.amount().multiply(dailyRate) : BigDecimal.ZERO;
            BigDecimal withCarry = exact.add(carry);
            Money rounded = Money.rounded(withCarry, currency);
            carry = withCarry.subtract(rounded.amount());
            accruals.add(rounded);
        }
        return accruals;
    }

    private static String entryId(Account account, int lastDay) {
        return "INT-" + account.id() + "-D" + lastDay;
    }
}
