# 0003. No future dates in a simulation

## Status

Accepted

## Context

A simulation's start month, current month, and inflation-deflation target month all need a
temporal ceiling (`SimulationService.create()`/`advanceMonth()`,
`InflationDeflationService.deflate()`). Real-world price, dividend, and inflation data simply
doesn't exist for months beyond the real current calendar month — there's no way to fabricate
it.

## Decision

We will reject a simulation's start month, current month, and any inflation-deflation target
month whenever it falls after the real-world current month
(`FutureSimulationStartMonthException`, `FutureSimulationCurrentMonthException`,
`FutureDeflationTargetException`).

## Consequences

The simulator never has to reconcile fabricated data with real data arriving later, and
behavior stays simple and predictable at the boundary. The trade-off is that a simulation only
ever exists in the past: once it reaches the real current month, it can only advance again as
real time (and real market data) passes — there's no speculative "keep simulating into the
future with the last known price" mode, unlike the truncate-to-latest-cached behavior used
elsewhere for stale-but-existing data (see
[0008](0008-out-of-range-month-lookup-asymmetry.md)). In practice this is a minor constraint,
since a simulation has a large span of already-elapsed history to run through before it ever
catches up to the present.
