package ledger.domain;

import java.util.Objects;

/** An account and the single currency all its entries are in. Every account opens at zero. */
public record Account(String id, Currency currency) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
    }

    /** Throws if the amount is not in this account's currency. */
    public void requireCurrencyOf(Money amount) {
        if (amount.currency() != currency) {
            throw new IllegalArgumentException("Account " + id + " is in " + currency + ", not " + amount.currency());
        }
    }
}
