# 0001. Average-cost-basis accounting, not FIFO/LIFO lot tracking

## Status

Accepted

## Context

Positions need a cost basis so the simulator can report percentage gain/loss per position and
in aggregate (`SimulationService.listPositions()`). No taxable event is modeled (see
[0005](0005-no-fees-taxes-or-slippage-modeled.md)), so there's no tax-driven reason to control
which specific lot's gain gets realized on a sale, the way FIFO/LIFO lot selection exists for
in real brokerages. The realistic alternative was to track individual purchase lots (quantity
+ price + date per lot) and choose which lot(s) a sale draws down from.

## Decision

We will track a single weighted-average cost basis per position (`Position.costBasis`). A buy
adds `quantity × price` to the position's cost basis; a sell (`SimulationService.sell()`) and a
split-driven forced fractional sale (`SimulationService.advanceMonth()`) remove cost basis in
proportion to the quantity sold, rather than selecting specific lots.

## Consequences

The simulator only needs to persist one running total per position instead of a full lot
history, which was much simpler to implement and reason about for this first version. The
trade-off is a genuine loss of information: the simulator cannot report which specific
purchase a sale drew down, or let a user compare "what if I'd sold my oldest shares first" the
way a real brokerage's lot selection would — the per-position gain the simulator reports is
always an average-cost figure, never a lot-specific one.
