package ledger.domain;

import java.util.Objects;

/**
 * A memo record of one day's interest: not a ledger entry and does not change the balance.
 * Only the rounded accrual is stored; the exact value and the rounding carry are intermediate.
 */
public record InterestAccrual(String accountId, int day, Money closingBalance, Money accrual) {

    public InterestAccrual {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(closingBalance, "closingBalance");
        Objects.requireNonNull(accrual, "accrual");
    }
}
