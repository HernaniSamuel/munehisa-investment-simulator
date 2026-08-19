# 0015. Split-driven forced sales recorded as CASH_IN_LIEU, not SELL

## Status

Accepted

## Context

Business-rule [0004](0004-whole-share-trading-with-split-cash-out.md) established that when a
stock split leaves a fractional share, the position's quantity is floored and the fractional
remainder is force-sold for cash ("cash-in-lieu"), because the engine never fabricates or
discards a fraction of a share. Until now, `SimulationService.advanceMonth()` recorded that
forced cash-out as an ordinary `SELL` transaction, with nothing in the data distinguishing it
from a sale the user actually chose to make.

Business-rule [0012](0012-snapshot-reset-preserves-dividend-transactions.md) (snapshot reset
preserves dividend transactions) flagged this exact mis-tagging as a "known latent gap" in its
own Consequences section: `resetToSnapshot()` excludes `DIVIDEND` from its current-month delete
because a dividend is paid out by the asset itself, not a user action, but a split-driven forced
sale is the same kind of asset-originated event and had no equivalent exclusion — so reverting a
simulation to its snapshot could silently delete a real cash-in-lieu event along with genuine
user sells.

Separately, because the UI had no way to tell a real sell apart from a forced one, every `SELL`
row in the transaction history carried a hover tooltip warning that it might not represent a
user action — a caveat that applied to the large majority of `SELL` rows that were, in fact,
genuine sells.

## Decision

We will give split-driven forced sales their own transaction type, `CASH_IN_LIEU`, distinct from
`SELL`. `SimulationService.advanceMonth()` records the fractional-remainder cash-out under this
new type instead of `SELL`. `SimulationService.resetToSnapshot()` excludes `CASH_IN_LIEU` from
its current-month transaction delete, alongside the existing `DIVIDEND` exclusion, since both are
asset-originated events rather than something the user did in the current month. The database's
`ck_transactions_type` and `ck_transactions_type_shape` constraints permit `CASH_IN_LIEU` with the
same required-column shape (ticker, asset name, and quantity all required) as `BUY`/`SELL`. The
frontend renders `CASH_IN_LIEU` as a plain label, like every other transaction type, and the
blanket hover explanation is removed from `SELL` rows, since `SELL` now always means a real,
user-initiated sell.

## Consequences

Resetting to a snapshot now correctly preserves an in-month cash-in-lieu event instead of
silently deleting it alongside genuine user sells, closing the gap flagged in
[0012](0012-snapshot-reset-preserves-dividend-transactions.md). The transaction history no longer
needs a caveat on every `SELL` row, since a `SELL` is now unambiguously something the user did.

The trade-off: this only takes effect going forward. There is no way to distinguish, after the
fact, a historical `SELL` transaction that was actually a split-driven forced sale (recorded
before this change) from a genuine historical sell, so existing data is not reclassified or
backfilled — a reset-to-snapshot against a simulation with a pre-existing mis-tagged `SELL` row
from before this change can still incorrectly delete it. This risk was knowingly accepted rather
than attempting a backfill, since there is no reliable way to tell the two apart in already-stored
data.
