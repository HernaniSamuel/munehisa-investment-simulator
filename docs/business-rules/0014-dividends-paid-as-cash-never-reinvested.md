# 0014. Dividends paid as cash, never automatically reinvested

## Status

Accepted

## Context

`SimulationService.advanceMonth()` adds a position's dividend payout directly to
`Simulation.cashBalance` (and records a `DIVIDEND` transaction). Real brokerages commonly offer
a dividend reinvestment plan (DRIP) that automatically uses a payout to buy more of the same
asset instead of holding it as cash.

## Decision

We will always pay a dividend out as cash, and never automatically reinvest it — the user
decides separately, in a later `buy()` call, whether and how to use that cash.

## Consequences

The user retains full control over what to do with a dividend payout — reinvest in the same
asset, buy something else, or hold it as cash — without the engine needing a per-position
reinvestment preference or an opinion about what "reinvest" should mean when it happens
automatically. The trade-off: simulating a classic buy-and-hold-with-DRIP strategy requires the
user to manually re-execute the reinvestment every month a dividend is paid, rather than it
happening on its own; automatic reinvestment was left out of the initial scope as a separate
feature rather than folded into this behavior.
