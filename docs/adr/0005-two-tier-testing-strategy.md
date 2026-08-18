# 0005. Two-tier testing strategy: Mockito unit tests + Testcontainers integration tests

## Status

Accepted

## Context

Testing everything through Testcontainers-backed integration tests (the real-Postgres approach
adopted for dev/prod parity, see [0002](0002-postgres-flyway-persistence.md)) would be realistic
but slow — it's wasteful to spin up a real database container just to check that a service's
logic behaves as expected. Testing everything with mocks only would be fast but less trustworthy,
especially given how much the persistence choice already leans on catching real database
behavior rather than an approximation of it.

## Decision

We will use a two-tier testing strategy: Mockito-based unit tests for the service layer, and
Testcontainers-backed integration tests for controller/repository behavior, run as separate
stages in CI (unit tests first, then integration tests).

## Consequences

Fast, cheap feedback on service-layer logic without paying for a container on every check, while
integration tests still validate real database behavior where it matters. An issue that passes
review reliably delivers what it promised — the layered, exhaustive coverage means bugs
generally surface during implementation, not after merge.

The trade-off: more tests to write and more discipline required to know what belongs at which
layer — deciding whether a given behavior is a unit-test or integration-test concern isn't
always obvious. Running two separate stages also means the CI pipeline takes several minutes
before a PR is clear to move to review, rather than a single faster pass.
