# 0004. Third-party market-data fetching as a separate Python/FastAPI microservice

## Status

Accepted

## Context

Third-party market/exchange/inflation data (Yahoo Finance, BCB, FRED) needed to be fetched and
normalized somewhere. Python has stronger library affinity for this than Java — notably
`yfinance` for Yahoo Finance, which was chosen as the primary market-data source because it's
free and rich in data, even though scraping-based access like this sits in a legal gray zone that
would rule it out for a commercial product; that's acceptable here since this is a non-profit
portfolio project.

Separately, there was a desire to decouple the simulation engine (Java) from whichever specific
data provider is behind it, so that adding or swapping a provider (e.g. Alpha Vantage) later
doesn't require touching the Java side at all.

## Decision

We will fetch and normalize third-party market/exchange/inflation data in a separate Python/
FastAPI microservice (`data-service`), rather than doing it directly inside the Java backend.

## Consequences

The simulation engine is decoupled from any specific data provider: a new or replacement source
only needs to produce data in the format the existing DTOs expect, without any change to the
Java side. It also lets the project use Python where it's the better fit (library ecosystem for
this kind of data) without forcing the rest of the backend into Python too.

The trade-off: fetching data now goes through an extra network hop and a second service to run,
secure (the API key between backend and data-service), and maintain, which is slower and more
operationally involved than fetching directly inside the Java backend would have been — a cost
accepted in exchange for not having to touch the simulation engine every time the data source
changes.
