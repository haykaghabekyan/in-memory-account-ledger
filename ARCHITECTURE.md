# Architecture & Trade-offs

The ledger is an in-memory core in plain Java 17 with no runtime dependencies. Its state is five append-only lists: entries, the event log, errors, authorization decisions and interest accrual memos. Everything else is derived from them on demand: balances (by value day, as known at a given day), active holds, available balance, whether an entry is reversed, and which fees are active. Nothing is ever updated in place, so there is no status field that could disagree with the records. This document covers what that design costs at scale and in production, and what it leaves out.

## 1. Append-only at scale

**What breaks first at 100× is processing time, not memory.** Every query is a full scan of a list:

- *Duplicate detection* scans the event log for every incoming event, so replaying n events costs O(n²). This breaks first, because it runs on every single event.
- *Hold lookups* scan all entries and resolve each settlement's event through another scan of the log, so `isSettled` is O(n²), and `activeHolds`, which calls it once per decision, is worse.
- *The end-of-day close* re-evaluates every day since Day 1 for every account, and each evaluation sums the account's entries. Its cost grows with accounts × days × entries, every day.

Memory grows linearly and would last far longer than the CPU time.

**Where state accumulates without bound:**

- all five lists, by design (append-only);
- the close's look-back, which always starts at Day 1, because a back-dated entry may arrive for any past day;
- active holds, because holds never expire;
- the event log of duplicates and rejected events, which is kept for audit.

**The cheapest structural change: close past periods.** At each end-of-day close, store a balance snapshot per account and value day, and only accept back-dated entries within a short open window (for example the current and previous business day). The close then re-evaluates only the open days, a balance becomes "snapshot plus the few entries after it", and the look-back stops growing. Hash indexes on event id, authorization id and reversed entry id would remove the quadratic lookups even more cheaply, but they leave the look-back unbounded. Closing periods fixes the growth itself, and it also turns back-dating into a governed process (section 2).

**What production needs beyond that.** The current ledger relies on two things a real system cannot assume: everything stays in one process's memory, and events arrive one at a time, in order.

- *Persistence.* The append-only design maps directly onto durable storage: the event log becomes the source of truth, written durably before an event is acknowledged, and entries, decisions and balances become projections that can be rebuilt from it. That covers both losing data (a crash loses nothing acknowledged) and scaling (projections can be rebuilt, moved and distributed).
- *Concurrency.* Most events do not race: a debit is never balance-checked, so two debits cannot break a rule by running together. The real race is the authorization check, which reads the available balance and then writes a decision. Two authorizations arriving together could both read the same balance and both be approved. The options are pessimistic locking (lock the account row for the read and the write: simple and correct, but busy accounts contend), optimistic locking (a version number on the account; a write fails if the version changed and the event is retried: cheap when collisions are rare), or avoiding the race by design. Every rule in this ledger is per account, so routing each account's events to a single writer (for example a message queue partitioned by account id) processes them in order without locks, while different accounts run in parallel. That is the approach I would take. A unique constraint on the event id would make duplicate detection hold under concurrency too, which today's list scan does not.
- *Running balances, not a cache.* Summing every entry for each balance query does not survive volume. The snapshots above become a stored running balance per account and value day, updated in the same transaction as the entry insert, so it is never stale and can always be rebuilt from the entries. A back-dated entry updates the snapshots from its value day onward, which the open window keeps short.

## 2. Value-dated entries in production

A value-dated entry changes history that other processes have already consumed. In this ledger a single back-dated debit rewrote four past closing balances, charged three fees and later refunded them, and made an already approved authorization unfunded in hindsight. In a bank, the same entry reaches further.

**Operational surface:**

- *Statements and notifications* already sent show balances that are no longer true. The customer saw one Day 2 balance and the ledger now reports another.
- *Fees and interest* already charged or accrued must be recalculated and refunded or corrected, with the customer told why.
- *Accounting periods and reports* that are already closed (end-of-day positions, the general ledger, liquidity reports) need prior-period adjustments.
- *Reconciliation* with card-scheme settlement files and correspondent accounts happens on booking dates, so value-dated corrections create breaks that operations must explain.

**Regulatory surface in a UAE-licensed bank:**

- *Consumer protection.* The Central Bank of the UAE's Consumer Protection Regulation and Standards require fees to be fair and disclosed, and statements to be accurate. Fees caused by a back-dated entry, and their refunds, must be explainable to the customer and in complaint handling. (This ledger's intentionally failing test shows a related risk: under the spec's balance rule, fees can generate further fees.)
- *Audit trail and record keeping.* Every back-dated entry needs who, when and why. This ledger keeps the what (booked, posted and value day on every entry) but has no who or why.
- *Regulatory reporting.* Returns already submitted for a period may need resubmission or a disclosed prior-period adjustment.
- *AML/CFT.* Transaction monitoring and suspicious transaction reporting (through the Financial Intelligence Unit's goAML system) depend on when money actually moved. Back-dating can disguise the timing of a movement, so it is also a fraud and insider-abuse vector.

**The control I would add before go-live: a back-dating window with dual approval.** Any entry whose value date is more than a set number of business days before its booking date, or falls in a closed period, cannot be posted by one person or one system alone. It needs a second approver (maker-checker) and a reason code, and every such entry appears on a daily exception report to operations and audit. It prevents unreviewed history changes rather than only detecting them, and it is the governance side of the closed periods from section 1.

## 3. Authorization lifecycle

Ways an authorization can end in this model other than a matching settlement, plus the one ending the model is missing:

| How it ends here | Real-world scenario | Behaviour I would mandate |
|---|---|---|
| Declined when decided | Insufficient available balance | Final. No hold. The decline reason is returned and recorded. |
| Rejected because its authorization id already exists | A network or client retry that reuses the id under a new message | Treated as a conflict: no second hold, an alert, never a silent approval. |
| Never ends: approved, never settled | An abandoned purchase; a fuel or hotel pre-authorization that is never captured | Expiry after the period card-scheme rules set for the merchant type, then automatic release of the hold. A settlement that arrives after expiry is still accepted (a force post), but is reviewed. |
| Settled for a different amount | Tips, fuel, split shipments | Partial captures up to the authorized amount, each releasing its share, until fully captured or expired. An over-capture tolerance only for merchant types whose scheme rules allow it. |
| Settled, then the settlement is reversed (the hold does not come back) | A merchant refund or a chargeback | The authorization stays closed. A refund is a new credit, not a reopened hold. |
| Approved, then shown unfunded by a back-dated debit | The bank decided on what it knew at the time | The decision stands (reversing it would break the commitment to the merchant), but it is flagged for credit-risk review. |
| Missing: an explicit authorization reversal | The customer cancels, or the merchant voids before capture (the scheme's reversal message, ISO 8583 0400/0420) | A new event type that releases the hold immediately. |

## 4. What I cut and why

| Simplification | Why it was acceptable here | Production risk it defers |
|---|---|---|
| In memory, single-threaded, events processed in order | The task excludes persistence, and the replay is sequential | Data loss on restart; races on the authorization check (section 1) |
| Every query is a full scan | Ten events; correctness and simplicity first | Quadratic processing time at volume (section 1) |
| No overdraft limit; debits, settlements and fees never checked | The spec sets no limit | Unlimited exposure on any account |
| No hold expiry, no authorization reversal, one settlement per authorization | Not needed by the stream | Customer funds locked indefinitely; failed partial captures (section 3) |
| Overdraft fee only in AED; no exchange rates; one interest rate for all currencies | The spec gives no BHD fee and one rate | A negative BHD account is never charged; mispriced interest per currency |
| Days are integers 1 to 6 | The window is fixed | No calendar, time zones, cut-off times, business days (the UAE weekend is Saturday and Sunday) or interest day-count convention |
| Fees decided on a balance that includes earlier fees | Follows the spec's balance definition | Fees charged on fees; shown by the intentionally failing test |
| Idempotency by event id only | Event ids are unique business ids in the stream | A retry sent under a new id is posted twice |
| Errors are stored, nothing alerts | The report prints them | Rejected settlements and missing fees go unnoticed in operations |
| No operator identity, approvals, statements or notifications | Out of scope for a ledger core | No audit of who changed what; customers not told about fees and refunds |
| `Ledger` is one concrete class, not separate reader and writer interfaces | One implementation and no storage layer | The first change once a real store is added |
