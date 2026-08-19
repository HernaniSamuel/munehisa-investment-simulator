# 0013. Single overwritten snapshot as the simulation's one-step undo

## Status

Accepted

## Context

A user who has bought, sold, deposited, or withdrawn within the current month can want to undo
those in-month actions. The question this decision answers is a product one, not a storage one:
how far back should "undo" reach — only the current month, or any earlier point in the
simulation's history? The maintainer deliberately chose to only ever let a user undo back to
the most recent month-end, and no further — a full point-in-time restore history was a real
alternative genuinely on the table, not something that simply never came up, but it's a bigger
feature (retention policy, a way to pick which point to restore to) than what the "undo my
current-month trades" need actually calls for.

`Snapshot.simulationId` being a unique column, overwritten by `writeSnapshot()` every time
`advanceMonth()` runs (or `createSnapshot()` is called directly), is how that scope decision is
implemented, not the decision itself — a system that still only exposed one-step undo could
just as well be built on a history table that simply never surfaces older rows to the user. The
same snapshot separately serves as the frozen baseline `listPositions()` uses to compute
gain/loss, so that in-month buys/sells don't distort the reported return — a supporting
technical reason to take a snapshot at all, but not the reason its history is capped at one.

## Decision

We will expose only one step of "undo": a simulation can be reverted to its most recent
month-end, and no further back. This is implemented as a single snapshot row per simulation,
taken automatically at the end of every `advanceMonth()` step and overwritten each time, rather
than retaining a history of snapshots.

## Consequences

Limiting undo to one step is enough for its main use — walking back an in-month mistake —
without needing to design and build a full point-in-time restore feature for a need that hasn't
been validated yet. Reusing the same row as the gain-calculation baseline means no second table
was needed to get an undistorted return figure either. The trade-off: a user can only ever undo
back to the most recent month-end; there is no way to revert a simulation to an earlier month's
state once another `advanceMonth()` has overwritten the snapshot — and if a richer undo/restore
history is ever wanted later, it requires a new schema (a history table) and a new record here,
not just relaxing this one.
