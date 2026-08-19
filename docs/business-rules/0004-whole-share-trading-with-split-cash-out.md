# 0004. Whole-share trading with forced cash-out on split fractions

## Status

Accepted

## Context

`TradeRequestDTO.quantity()` and `Position.quantity` model share counts as whole numbers.
Fractional-share trading is a real feature some real brokerages offer, and was considered —
including because some assets (notably cryptocurrencies) aren't naturally traded in whole
units. However, supporting fractional shares would have significantly complicated computing
holdings, dividend allocation, foreign-currency conversion for assets priced in a different
currency than the simulation, and rounding throughout the engine. Separately, a stock split
(`SimulationService.advanceMonth()`) can turn a whole-share holding into a fractional one on
its own, without any user action — and that fraction has to be resolved somehow, without ever
fabricating a fractional or a whole share out of nothing.

## Decision

We will require all user buy/sell quantities to be whole integer shares. The only source of a
fractional share is a stock split; when a split produces one, the position's quantity is
floored to the nearest whole share and the fractional remainder is force-sold for cash at the
current price ("cash-in-lieu"), never rounded up to a whole share or retained as a fractional
holding.

## Consequences

Collapsing every buy/sell to whole shares simplified development and testing substantially,
and matches how the large majority of traditional equities are actually traded — the
imprecision this introduces is confined to the small slice of real-world assets
(fractional-share brokerages, cryptocurrencies) that don't naturally trade in whole units, the
same asset types the fixed monthly time step
([0002](0002-monthly-simulation-time-step.md)) is also a poor fit for.

The trade-off: flooring the split fraction and forcing a cash sale (rather than rounding up)
keeps the whole-share invariant intact everywhere else in the engine, but it takes the choice
away from the user — rounding up would fabricate a share the user never bought, something the
engine only ever does with cents of currency, never with asset ownership, so cash-in-lieu was
the only option consistent with that invariant. It mirrors a practice used by real brokerages
when a reverse split leaves a holder without enough shares for one whole unit.

Worth flagging for a future reader: the floor-and-cash-out path only runs when the price series
isn't already split-adjusted (`!lookup.pricesSplitAdjusted()` in
`SimulationService.advanceMonth()`), and the project's only live data source, yfinance, always
reports prices as already split-adjusted (`yfinance_client.py`'s `prices_split_adjusted=True`).
So today this path is dormant against real data — it's exercised only by test fixtures that set
`pricesSplitAdjusted=false`, not by any simulation running on real market data.
