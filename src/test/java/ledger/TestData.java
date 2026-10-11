package ledger;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;

import java.util.List;
import ledger.core.Ledger;
import ledger.domain.Account;
import ledger.domain.Money;
import ledger.event.Authorization;
import ledger.event.Credit;
import ledger.event.Debit;
import ledger.event.Reversal;
import ledger.event.Settlement;

/** Shared test fixtures: the two accounts and the E1–E10 event stream from the spec. */
public final class TestData {

    public static final String ACC_001 = "ACC-001";
    public static final String ACC_002 = "ACC-002";

    public static final Credit E1 = new Credit("E1", 1, ACC_001, aed("1200.00"), 1);
    public static final Debit E2 = new Debit("E2", 1, ACC_001, aed("950.00"), 1);
    public static final Authorization E3 = new Authorization("E3", 2, ACC_001, "Auth-A", aed("200.00"), 2);
    public static final Credit E4 = new Credit("E4", 3, ACC_001, aed("400.00"), 3);
    public static final Settlement E5 = new Settlement("E5", 4, ACC_001, "Auth-A", aed("185.00"), 4);
    public static final Settlement E6 = new Settlement("E6", 4, ACC_001, "Auth-Z", aed("180.00"), 4);
    public static final Debit E7 = new Debit("E7", 5, ACC_001, aed("620.00"), 2);
    public static final Authorization E8 = new Authorization("E8", 5, ACC_001, "Auth-B", aed("90.00"), 5);
    public static final Reversal E9 = new Reversal("E9", 6, ACC_001, "E7");
    public static final Credit E10 = new Credit("E10", 5, ACC_002, bhd("10.000"), 5, 3);

    private TestData() {
    }

    /** A ledger with ACC-001 (AED) and ACC-002 (BHD), both at zero. */
    public static Ledger newLedger() {
        return new Ledger(List.of(new Account(ACC_001, AED), new Account(ACC_002, BHD)));
    }

    public static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    public static Money bhd(String amount) {
        return Money.of(amount, BHD);
    }
}
