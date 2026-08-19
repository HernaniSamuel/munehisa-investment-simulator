# 0002. Monthly simulation time step

## Status

Accepted

## Context

Building the simulation engine required picking a fixed time granularity for the simulation
clock (`Simulation.startMonth`/`currentMonth`, `SimulationService.advanceMonth()`) and for
every price/dividend/split series backing it. A daily or weekly resolution was a real
alternative.

## Decision

We will advance the simulation clock exactly one calendar month per step, and resolve every
asset/exchange-rate/inflation lookup to a single monthly data point; no daily or weekly
resolution exists anywhere in the engine.

## Consequences

Locking the clock to one shared period simplifies the engine substantially: shared caches for
asset/exchange-rate/inflation data can key on a single month without worrying about multiple
concurrent resolutions, and the simulation math is written and tested against one synchronized
period end-to-end. Monthly data is also lighter to store and transfer (the whole series can be
sent to the frontend without pagination) and more reliably available across assets than
daily/weekly history, which would need extra gap-handling to be trustworthy. Monthly also
matches how people actually experience money day-to-day — salaries and most dividend-like
payments land monthly or in month-multiples (quarterly, semi-annually).

The trade-off: the engine has zero time-granularity flexibility. Supporting another period
later — or a different asset class such as crypto or forex, which trade continuously and often
in fractional units (see [0004](0004-whole-share-trading-with-split-cash-out.md)) — would mean
redesigning the engine, or building a second engine side by side with this one, rather than
extending the current one.
