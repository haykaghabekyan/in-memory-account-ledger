package ledger.event;

import static ledger.TestData.*;
import static ledger.domain.Currency.AED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ledger.domain.Money;
import org.junit.jupiter.api.Test;

class EventTest {

    private static final Money ZERO = Money.zero(AED);
    private static final Money NEGATIVE = aed("-1.00");

    @Test
    void amountsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new Credit("E1", 1, ACC_001, ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new Debit("E2", 1, ACC_001, NEGATIVE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Authorization("E3", 2, ACC_001, "Auth-A", ZERO, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new Settlement("E5", 4, ACC_001, "Auth-A", NEGATIVE, 4));
    }

    @Test
    void creditIsOneInstalmentUnlessStated() {
        assertEquals(1, new Credit("E1", 1, ACC_001, aed("1200.00"), 1).instalments());
        assertEquals(3, new Credit("E10", 5, ACC_002, bhd("10.000"), 5, 3).instalments());
    }

    @Test
    void creditNeedsAtLeastOneInstalment() {
        Money amount = bhd("10.000");
        assertThrows(IllegalArgumentException.class, () -> new Credit("E10", 5, ACC_002, amount, 5, 0));
    }
}
