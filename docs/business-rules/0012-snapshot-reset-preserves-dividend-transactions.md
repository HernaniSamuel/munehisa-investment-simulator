# 0012. Snapshot reset preserves dividend transactions

## Status

Accepted

## Context

`SimulationService.resetToSnapshot()` deletes the current month's transactions when reverting a
simulation to its last snapshot, so that a user's in-month buys/sells/deposits/withdrawals are
undone along with the position/cash state. A dividend transaction in that same month, however,
doesn't come from any user action in the current month — it's paid out by the asset itself as
part of the previous `advanceMonth()` step that created the snapshot in the first place (see
[0013](0013-single-overwritten-snapshot.md)).

## Decision

We will exclude `DIVIDEND` transactions from the delete when resetting to a snapshot — every
other transaction type in the current month is removed, but dividend records are preserved.

## Consequences

A reset correctly undoes only what the user actually did in the current month, without also
erasing a real event (the dividend payment) that already happened and isn't part of what's
being reverted.

The trade-off, surfaced while establishing this record: `DIVIDEND` is the only transaction type
excluded, but a split-driven forced cash sale (see
[0004](0004-whole-share-trading-with-split-cash-out.md)) is recorded as an ordinary `SELL`
transaction with no way to distinguish it from a user-initiated sell — so a reset currently
deletes a split-driven forced sale along with genuine user sells, even though it, like a
dividend, originates from the asset rather than from anything the user did that month. This
inconsistency is a known latent gap, not something this changeset fixes (see the PR description
for the tracked follow-up).
