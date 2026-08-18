# 0013. Single overwritten snapshot as the simulation's one-step undo

## Status

Accepted

## Context

`Snapshot.simulationId` is a unique column — a simulation only ever has one snapshot row, which
`writeSnapshot()` overwrites every time `advanceMonth()` runs (or `createSnapshot()` is called
directly). A simulation could instead have kept a full history of month-end snapshots, letting
a user revert to any earlier point, not just the most recent one.

## Decision

We will keep exactly one snapshot per simulation, taken automatically at the end of every
`advanceMonth()` step and overwritten each time, rather than retaining a history of snapshots.
Its purpose is twofold: it's the one-step "undo" for the current month's trades, and it's the
frozen baseline `listPositions()` uses to compute gain/loss so that in-month buys/sells don't
distort the reported return.

## Consequences

A single always-current snapshot is enough to serve both roles it's used for — undoing the
current month and providing an undistorted gain baseline — without the storage and bookkeeping
a full history of restore points would require. The trade-off: a user can only ever undo back
to the most recent month-end; there is no way to revert a simulation to an earlier month's
state once another `advanceMonth()` has overwritten the snapshot.
