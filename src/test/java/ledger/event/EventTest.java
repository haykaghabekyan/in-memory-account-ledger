package ledger.event;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ledger.domain.Money;
import org.junit.jupiter.api.Test;

class EventTest {

    private static final Money ZERO = Money.zero(AED);
    private static final Money NEGATIVE = Money.of("-1.00", AED);

    @Test
    void amountsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new Credit("E1", 1, "ACC-001", ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new Debit("E2", 1, "ACC-001", NEGATIVE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Authorization("E3", 2, "ACC-001", "Auth-A", ZERO, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new Settlement("E5", 4, "ACC-001", "Auth-A", NEGATIVE, 4));
    }

    @Test
    void creditIsOneInstalmentUnlessStated() {
        assertEquals(1, new Credit("E1", 1, "ACC-001", Money.of("1200.00", AED), 1).instalments());
        assertEquals(3, new Credit("E10", 5, "ACC-002", Money.of("10.000", BHD), 5, 3).instalments());
    }

    @Test
    void creditNeedsAtLeastOneInstalment() {
        Money amount = Money.of("10.000", BHD);
        assertThrows(IllegalArgumentException.class, () -> new Credit("E10", 5, "ACC-002", amount, 5, 0));
    }
}
