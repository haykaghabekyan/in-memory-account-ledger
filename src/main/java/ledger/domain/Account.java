package ledger.domain;

import java.util.Objects;

/** An account and the single currency all its entries are in. Every account opens at zero. */
public record Account(String id, Currency currency) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
    }
}
