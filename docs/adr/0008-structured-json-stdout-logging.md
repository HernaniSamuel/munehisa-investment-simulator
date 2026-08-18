# 0008. Structured JSON access/error logging to stdout only

## Status

Accepted

## Context

Logging was added with the upcoming load-testing work in mind, which put a priority on
machine-parseable access and error logs that automated tooling could consume directly. At the
time, no hosting platform had been chosen yet, and this was the first time the maintainer added
structured logging to a project of their own — the intent was to keep it simple rather than
build toward a specific log-shipping destination that didn't exist yet.

## Decision

We will emit structured JSON logs (one JSON object per line, for both access and error logging)
to stdout only, in both the backend and the data-service, with no file-based storage and no
external log aggregator.

## Consequences

JSON output is immediately usable by automated tooling — it fed directly into the load-testing
work — and stdout-only logging fits container conventions (`docker logs`) without any extra
setup or infrastructure.

The trade-off: logs aren't persisted beyond a container's lifetime and aren't aggregated
anywhere, so there's no searchable log history once a container restarts or is replaced, and no
way yet to correlate logs across the two services' containers beyond the shared `requestId`.
That gap was accepted because no hosting platform — and therefore no concrete log-shipping
target — had been chosen at the time.
