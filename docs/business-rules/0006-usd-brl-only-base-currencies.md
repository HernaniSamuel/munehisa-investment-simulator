# 0006. USD/BRL-only base currencies

## Status

Accepted

## Context

The asset and exchange-rate layers underneath a simulation are currency-agnostic — they can
fetch and convert between any pair yfinance covers. A simulation's base currency
(`CreateSimulationRequestDTO.baseCurrency()`) and the "today's money" deflation feature
(`InflationCurrency`, see [0007](0007-todays-money-deposit-withdrawal-deflation.md)) are
restricted to two ISO codes regardless. Inflation data is only available (BCB for BRL, FRED for
USD) for these two currencies, and the project's initial audience is Brazilian users investing
in both local and international (USD-denominated) markets.

## Decision

We will restrict a simulation's base currency to `BRL` and `USD` only, even though the
underlying asset/exchange-rate data could support more.

## Consequences

The two-currency restriction matches both the available inflation data and a deliberately
small initial scope — Real because the target audience is Brazilian, Dollar because it's the
de facto global reference currency for investing. The trade-off is that a simulation can't be
run in any other currency today, even for a user who'd want to. Widening this later is expected
to be straightforward for the asset/exchange-rate layers themselves (already
currency-agnostic) but is gated on having a trustworthy inflation-data source for whichever
currency is added next, which is why it wasn't done now rather than being a technical blocker.
