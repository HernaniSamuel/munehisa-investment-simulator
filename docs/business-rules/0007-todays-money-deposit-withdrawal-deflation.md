# 0007. "Today's money" deposit/withdrawal deflation

## Status

Accepted

## Context

A user running a simulation set in the past (e.g. starting in 2002) but thinking in
present-day terms — "I want to simulate depositing what I earn today, R$600/month" — would get
a distorted simulation if that value were applied nominally to a past month, since R$600 was
worth substantially more purchasing power decades ago than it is now.
`CashMovementRequestDTO`'s optional `todaysMoney` flag exists to let a user express a deposit
or withdrawal in today's purchasing power and have the simulator convert it to the nominal
amount for the simulation's current month — useful in particular for a recurring deposit meant
to track something like a salary, where re-deriving the inflation adjustment by hand every
month would be tedious and easy to get wrong.

## Decision

We will, when a cash movement is flagged `todaysMoney`, convert the entered value by the ratio
of two accumulated inflation-index points (target month over real current month) before
applying it to the cash balance (`InflationDeflationService.deflate()`), rather than applying
the entered value nominally. The flag stays optional — a user can also enter a fixed/
period-accurate value directly, without inflation adjustment, which also keeps a withdrawal
simple when the user wants an exact, known amount out.

## Consequences

This lets a user express recurring cash movements in constant, present-day terms without
having to work out the inflation adjustment by hand for every month of the simulation — and,
as a side effect, lets them see the effect of inflation directly (e.g. depositing R$100
"today" landing as roughly R$50 in 2020). The trade-off: this feature's accuracy is entirely
dependent on the inflation-index data being available and a good proxy for purchasing power
for the target month and currency — a limitation shared with the lookup-clamping behavior in
[0008](0008-out-of-range-month-lookup-asymmetry.md).
