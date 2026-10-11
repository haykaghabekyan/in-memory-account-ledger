package ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An exact amount in one currency, always at that currency's precision (AED 2 dp, BHD 3 dp).
 * Amounts are signed: credits are positive, debits negative.
 */
public record Money(BigDecimal amount, Currency currency) {

    /** The one rounding mode used everywhere amounts are rounded. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() != currency.scale()) {
            throw new IllegalArgumentException(
                    amount + " must have exactly " + currency.scale() + " decimal places for " + currency);
        }
    }

    /** An amount that is already at the currency's precision, e.g. of("1200.00", AED). */
    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    /** Rounds any exact value (half up) to the currency's precision. */
    public static Money rounded(BigDecimal value, Currency currency) {
        return new Money(value.setScale(currency.scale(), ROUNDING), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO.setScale(currency.scale()), currency);
    }

    /**
     * Splits into parts as equal as the precision allows, preserving the total: each part is
     * total / parts rounded half up to the currency's precision, and the last part takes the remainder (10.000 / 3 = 3.333, 3.333, 3.334).
     */
    public List<Money> split(int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("parts must be at least 1: " + parts);
        }
        Money share = new Money(amount.divide(BigDecimal.valueOf(parts), currency.scale(), ROUNDING), currency);
        List<Money> result = new ArrayList<>();
        Money remainder = this;
        for (int i = 1; i < parts; i++) {
            result.add(share);
            remainder = remainder.minus(share);
        }
        result.add(remainder);
        return List.copyOf(result);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrency(Money other) {
        if (other.currency != currency) {
            throw new IllegalArgumentException("Cannot combine " + currency + " with " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
