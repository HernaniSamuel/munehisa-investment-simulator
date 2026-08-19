# 0011. Money-math precision and rounding by layer

## Status

Accepted

## Context

A monetary value passes through several layers with different precision needs: the backend's
simulation math (`SimulationService`, `InflationDeflationService`) chains many multiplications
and divisions; stock OHLC and exchange rates are ingested from yfinance; dividends can be
fractions of a cent per share once split-adjusted.

## Decision

We will run all backend simulation math at 50 significant digits (`MathContext(50)`) with no
intermediate rounding, to leave enough headroom that nothing needs truncating mid-calculation.
At ingestion, we will round stock OHLC to cents (`ROUND_HALF_UP`), because an asset can only
actually be bought or sold in cents, and rounding up rather than replicating brokerages'
internal banker's rounding (which they don't expose in their own displayed prices anyway) was
chosen for ease of manual validation. We will not round dividends or exchange rates at
ingestion: a dividend can be a fraction of a cent per share that would round away to zero and
never be counted, and an exchange rate needs full precision so that the money-in-cents rounding
happens only once, at the point it's actually spent, rather than compounding rounding error at
each stage.

## Consequences

Keeping intermediate math at 50 significant digits and only rounding money to cents where it's
actually spent (at the OHLC ingestion boundary) avoids compounding rounding error through a
long chain of multiplications and divisions. The trade-off: 50 was chosen for comfortable
headroom rather than derived from a specific worst-case calculation, so there's no proof it's
sufficient for an arbitrarily long or extreme chain of operations — it's a generous margin, not
a verified bound.
