# 0010. IPCA chained index vs. CPI level series

## Status

Accepted

## Context

FRED's CPI-U (series `CPIAUCSL`) is already published as an index level per month, so
`InflationCacheService.normalize()` stores it as-is for USD. BCB's IPCA, by contrast, is
published as a monthly rate (%), which isn't directly comparable to an index level. Both
currencies needed to end up in the same stored shape (`InflationIndex.accumulatedIndex`) so the
rest of the backend — in particular `InflationDeflationService.deflate()`'s index-ratio math —
doesn't need to know which source format a currency came from.

## Decision

We will compound BRL's monthly IPCA rate into a synthetic accumulated index, anchored at 100 on
the earliest available month (that month's own rate is not itself compounded in, since there is
no prior month for it to apply to) — the same shape USD's CPI-U already comes in natively, and
the shape that makes the deflation ratio math simplest.

## Consequences

Downstream code (deflation math, lookups) can treat every currency's inflation data
identically as an accumulated index, without a currency-specific branch anywhere outside
`normalize()`. The trade-off: BRL's index is a derived, compounded figure the project computes
itself, while USD's is taken verbatim from the source — any compounding error introduced here
would silently affect every BRL deflation calculation, in a way that can't happen on the USD
side since there's no compounding step to get wrong.
