package ledger.domain;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void amountMustBeAtTheCurrencyPrecision() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("10.000", AED));
        assertThrows(IllegalArgumentException.class, () -> Money.of("10.00", BHD));
        assertThrows(IllegalArgumentException.class, () -> Money.of("10", AED));
    }

    @Test
    void roundsHalfUpToTheCurrencyPrecision() {
        assertEquals(Money.of("0.19", AED), Money.rounded(new BigDecimal("0.185"), AED));
        assertEquals(Money.of("0.18", AED), Money.rounded(new BigDecimal("0.1849"), AED));
        assertEquals(Money.of("3.334", BHD), Money.rounded(new BigDecimal("3.3335"), BHD));
        assertEquals(Money.of("0.008", BHD), Money.rounded(new BigDecimal("0.0080"), BHD));
    }

    @Test
    void addsAndSubtractsExactly() {
        Money balance = Money.of("1200.00", AED).minus(Money.of("950.00", AED));
        assertEquals(Money.of("250.00", AED), balance);
        assertEquals(Money.of("10.000", BHD),
                Money.of("3.333", BHD).plus(Money.of("3.333", BHD)).plus(Money.of("3.334", BHD)));
    }

    @Test
    void refusesToMixCurrencies() {
        Money dirham = Money.of("1.00", AED);
        Money dinar = Money.of("1.000", BHD);
        assertThrows(IllegalArgumentException.class, () -> dirham.plus(dinar));
    }

    @Test
    void zeroIsNeitherNegativeNorPositive() {
        Money zero = Money.zero(AED);
        assertEquals(Money.of("0.00", AED), zero);
        assertFalse(zero.isNegative());
        assertFalse(zero.isPositive());
        assertTrue(Money.of("-0.01", AED).isNegative());
        assertTrue(Money.of("0.01", AED).isPositive());
    }
}
