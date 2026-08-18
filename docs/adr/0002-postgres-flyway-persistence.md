# 0002. Postgres + Flyway persistence, with Hibernate ddl-auto fixed to validate

## Status

Accepted

## Context

The application persists financial data, where rounding behavior matters — the database needed
to support exact numeric types without floating-point rounding errors. Postgres was already
familiar to the maintainer from a prior project (their university capstone project), and is a
mature, solid choice for that requirement.

Beyond choosing the database engine, schema management needed a story too: letting Hibernate
manage the schema automatically (`update`/`create`) means a bug in an entity mapping can trigger
an unintended, silent migration that damages the database — there is no explicit, reviewable step
where that change is looked at before it happens.

## Decision

We will use Postgres as the persistence store, with hand-written SQL migrations managed by
Flyway, and Hibernate's `ddl-auto` fixed to `validate` — Hibernate never generates or applies
schema changes itself; it only checks the schema matches what the entities expect.

## Consequences

Schema changes are explicit and reviewable: every migration is a conscious, hand-written SQL
file, not something inferred and applied silently by the ORM. This also forced a database used
identically in dev and production, rather than swapping in a lighter engine like H2 for tests —
H2's type behavior diverges from Postgres's in ways that matter for financial data, so dev/prod
parity on the database itself became necessary and pushed integration testing toward
Testcontainers-backed real-Postgres tests rather than an in-memory substitute.

The trade-off: this is a heavier, more bureaucratic workflow than letting the ORM manage the
schema. Every migration is written by hand, with care taken that the SQL types being introduced
stay compatible with the types used across the other layers of the application, and the
Testcontainers-based validation this required made local development and the test suite slower
than an in-memory-database setup would have been.
