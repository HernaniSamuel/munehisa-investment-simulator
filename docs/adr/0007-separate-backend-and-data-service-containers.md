# 0007. Separate containers for the backend and data-service, orchestrated via Docker Compose

## Status

Accepted

## Context

The Java backend and the Python data-service both needed to be containerized ahead of choosing a
hosting platform. A single image running both runtimes together was considered and rejected: it
works against Docker's core premise of isolating each service's environment, even though it
would be operationally simpler than running and orchestrating two separate containers.

## Decision

We will run the backend and data-service as separate containers, one per service, orchestrated
together via Docker Compose, rather than a single image running both runtimes.

## Consequences

Each service can be built, changed, and redeployed independently, without risk of affecting the
other — a failure or change confined to one container stays confined to it, and the two can be
scaled, restarted, or updated on their own schedules.

The trade-off: more to build and operate — two images instead of one, and two containers to keep
running and networked together (via Compose, and later on the hosting VM) instead of a single
unit. That operational overhead was accepted for the sake of the isolation and independence
between services.
