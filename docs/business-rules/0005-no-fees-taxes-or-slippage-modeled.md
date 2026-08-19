# 0005. No fees, taxes, or slippage modeled

## Status

Accepted

## Context

Every trade could, in principle, model a transaction fee, capital-gains tax, and slippage
between the requested and executed price the way a real brokerage does
(`SimulationService.buy()`/`sell()`/`applyCashMovement()`). Taxes in particular vary enormously
by country, asset type, and holding period, and the project isn't scoped to one country or
currency (see [0006](0006-usd-brl-only-base-currencies.md)) — accurately modeling them for
every jurisdiction a user might simulate would require tax-rate data this project has no
reliable source for, and a large amount of jurisdiction-specific logic, for comparatively
little payoff at this stage.

## Decision

We will execute every trade at exactly `quantity × price × exchange rate`, with no fee, no
tax, and no slippage; deposits, withdrawals, and dividends are never taxed either.

## Consequences

The simulator stays scoped to "what would my portfolio be worth" without needing tax-rate data
that doesn't reliably exist across the countries/currencies in scope, which would have been a
large amount of implementation effort. The trade-off: every simulated return is systematically
more optimistic than a real brokerage account would produce, since none of the friction of
real investing (commissions, spreads, capital-gains tax) is represented.
