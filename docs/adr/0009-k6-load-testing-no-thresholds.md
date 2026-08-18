# 0009. k6 for backend load testing, with no fixed pass/fail thresholds

## Status

Accepted

## Context

The backend had no load-testing setup. Researching the space pointed to k6 as the tool most
commonly used for this kind of testing, so it was chosen to follow the established industry
convention rather than evaluating alternatives like JMeter, Locust, or Gatling in depth.

This was also the maintainer's first time working with load tests — they were added to learn how
load testing works and what it can reveal about the application's production behavior, not to
enforce a specific performance target. The application itself isn't hosted with multi-user,
production-scale traffic in mind; it's a portfolio project, not a SaaS with real capacity
requirements to defend.

## Decision

We will use k6 for load testing, targeting only the backend's own API (with the data-service and
real upstream APIs excluded from the timed run via cache warm-up), and the scripts define no
fixed pass/fail thresholds.

## Consequences

Using the industry-standard tool means better documentation, community examples, and a
transferable skill going forward. The load tests currently serve as an exploratory, informational
tool — they report throughput, latency percentiles, and error rate for inspection, without ever
failing a CI run or blocking a merge over a performance regression.

The trade-off: without thresholds, a real performance regression introduced by a change won't be
caught automatically — there's no enforced safety net, only a report someone has to read and
judge by eye. That's an accepted gap for now, since there's no real capacity target yet to set a
meaningful threshold against.
