package ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An exact amount in one currency, always at that currency's precision (AED 2 dp, BHD 3 dp).
 * Amounts are signed: credits are positive, debits negative.
 */
public record Money(BigDecimal amount, Currency currency) {

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

    /** Rounds any exact value (half up) to the currency's precision. The only place rounding happens. */
    public static Money rounded(BigDecimal value, Currency currency) {
        return new Money(value.setScale(currency.scale(), ROUNDING), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO.setScale(currency.scale()), currency);
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
