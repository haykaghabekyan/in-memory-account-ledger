package ledger.core;

import static ledger.domain.Currency.AED;
import static ledger.domain.Currency.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ledger.domain.Account;
import ledger.domain.AuthorizationDecision;
import ledger.domain.Decision;
import ledger.domain.EntryType;
import ledger.domain.Money;
import ledger.domain.ProcessingError;
import ledger.event.Authorization;
import ledger.event.Credit;
import ledger.event.Debit;
import ledger.event.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationAndSettlementTest {

    private static final String ACC_001 = "ACC-001";
    private static final String ACC_002 = "ACC-002";

    private static final Credit E1 = new Credit("E1", 1, ACC_001, aed("1200.00"), 1);
    private static final Debit E2 = new Debit("E2", 1, ACC_001, aed("950.00"), 1);
    private static final Authorization E3 = new Authorization("E3", 2, ACC_001, "Auth-A", aed("200.00"), 2);
    private static final Credit E4 = new Credit("E4", 3, ACC_001, aed("400.00"), 3);
    private static final Settlement E5 = new Settlement("E5", 4, ACC_001, "Auth-A", aed("185.00"), 4);
    private static final Settlement E6 = new Settlement("E6", 4, ACC_001, "Auth-Z", aed("180.00"), 4);
    private static final Debit E7 = new Debit("E7", 5, ACC_001, aed("620.00"), 2);
    private static final Authorization E8 = new Authorization("E8", 5, ACC_001, "Auth-B", aed("90.00"), 5);

    private Ledger ledger;
    private EventProcessor processor;

    @BeforeEach
    void setUp() {
        ledger = new Ledger(List.of(new Account(ACC_001, AED), new Account(ACC_002, BHD)));
        processor = EventProcessor.withStandardHandlers(ledger);
        processor.process(E1, 1);
        processor.process(E2, 1);
    }

    @Test
    void approvedHoldReducesAvailableButNotLedgerBalance() {
        processor.process(E3, 2);

        assertEquals(Decision.APPROVED, decision("Auth-A").decision());
        assertEquals(aed("50.00"), decision("Auth-A").availableAfterHold());
        assertEquals(aed("250.00"), ledger.balance(ACC_001, 2, 2));
        assertEquals(aed("50.00"), ledger.available(ACC_001, 2));
        assertTrue(ledger.entries().stream().noneMatch(entry -> entry.sourceEventId().equals("E3")));
    }

    @Test
    void holdLeavingExactlyZeroAvailableIsApproved() {
        processor.process(new Authorization("A1", 2, ACC_001, "Auth-X", aed("250.00"), 2), 2);

        assertEquals(Decision.APPROVED, decision("Auth-X").decision());
        assertEquals(aed("0.00"), ledger.available(ACC_001, 2));
    }

    @Test
    void holdTakingAvailableBelowZeroIsDeclinedAndHoldsNothing() {
        processor.process(new Authorization("A1", 2, ACC_001, "Auth-X", aed("250.01"), 2), 2);

        assertEquals(Decision.DECLINED, decision("Auth-X").decision());
        assertEquals(aed("-0.01"), decision("Auth-X").availableAfterHold());
        assertEquals(aed("250.00"), ledger.available(ACC_001, 2));
    }

    @Test
    void authBIsDeclinedBecauseE7AlreadyMadeTheBalanceNegative() {
        processor.process(E3, 2);
        processor.process(E4, 3);
        processor.process(E5, 4);
        processor.process(E7, 5);
        processor.process(E8, 5);

        assertEquals(aed("-155.00"), ledger.balance(ACC_001, 5, 5));
        assertEquals(Decision.DECLINED, decision("Auth-B").decision());
        assertEquals(aed("-245.00"), decision("Auth-B").availableAfterHold());
    }

    @Test
    void settlementDebitsTheActualAmountAndReleasesTheWholeHold() {
        processor.process(E3, 2);
        processor.process(E4, 3);
        processor.process(E5, 4);

        assertEquals(EntryType.SETTLEMENT, ledger.entries().get(3).type());
        assertEquals(aed("465.00"), ledger.balance(ACC_001, 4, 4));
        assertTrue(ledger.isSettled("Auth-A"));
        assertEquals(aed("0.00"), ledger.activeHolds(ACC_001));
        assertEquals(aed("465.00"), ledger.available(ACC_001, 4));
    }

    @Test
    void settlementWithoutAnAuthorizationIsRejectedButKeptInTheEventLog() {
        processor.process(E6, 4);

        assertEquals(2, ledger.entries().size());
        assertEquals(aed("250.00"), ledger.balance(ACC_001, 4, 4));
        assertEquals("Settlement rejected: no authorization Auth-Z", onlyError().message());
        assertEquals(E6, ledger.eventLog().get(2).event());
    }

    @Test
    void declinedAuthorizationCannotBeSettled() {
        processor.process(new Authorization("A1", 2, ACC_001, "Auth-X", aed("300.00"), 2), 2);
        processor.process(new Settlement("S1", 3, ACC_001, "Auth-X", aed("300.00"), 3), 3);

        assertEquals(2, ledger.entries().size());
        assertEquals("Settlement rejected: Auth-X was declined", onlyError().message());
    }

    @Test
    void authorizationCanOnlyBeSettledOnce() {
        processor.process(E3, 2);
        processor.process(E5, 4);
        processor.process(new Settlement("S2", 4, ACC_001, "Auth-A", aed("185.00"), 4), 4);

        assertEquals(3, ledger.entries().size());
        assertEquals("Settlement rejected: Auth-A is already settled", onlyError().message());
    }

    @Test
    void settlementMustBeOnTheAuthorizationsAccount() {
        processor.process(E3, 2);
        processor.process(new Settlement("S1", 4, ACC_002, "Auth-A", bhd("185.000"), 4), 4);

        assertFalse(ledger.isSettled("Auth-A"));
        assertEquals("Settlement rejected: Auth-A belongs to account ACC-001", onlyError().message());
    }

    @Test
    void authorizationIdCannotBeReused() {
        processor.process(E3, 2);
        processor.process(new Authorization("A2", 3, ACC_001, "Auth-A", aed("10.00"), 3), 3);

        assertEquals(1, ledger.decisions().size());
        assertEquals(aed("200.00"), ledger.activeHolds(ACC_001));
        assertEquals("Authorization rejected: Auth-A already exists", onlyError().message());
    }

    private AuthorizationDecision decision(String authId) {
        return ledger.decision(authId).orElseThrow();
    }

    private ProcessingError onlyError() {
        assertEquals(1, ledger.errors().size());
        return ledger.errors().get(0);
    }

    private static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    private static Money bhd(String amount) {
        return Money.of(amount, BHD);
    }
}
