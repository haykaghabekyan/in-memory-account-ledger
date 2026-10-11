package ledger.domain;

/** What caused a ledger entry. */
public enum EntryType {
    CREDIT,
    DEBIT,
    SETTLEMENT,
    REVERSAL,
    FEE,
    FEE_REVERSAL,
    INTEREST
}
