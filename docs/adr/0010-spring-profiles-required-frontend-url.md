# 0010. Shared/dev/prod Spring profiles, with FRONTEND_URL required in prod

## Status

Accepted

## Context

Most of the backend's configuration lived only in `application-dev.properties`, with no working
configuration for a `prod` profile. Ahead of actually deploying the backend, this needed fixing:
in particular, `app.frontend-url` silently defaulting to `localhost` in production would mean
CORS breaking against the real frontend, and verification/password-reset emails shipping links
that point nowhere useful — a precaution taken while planning the deploy, before it had actually
gone live.

Separately, the Actuator endpoints added for containerization (`/actuator/*`) needed a
production posture. Only `/actuator/health` had any real demand — it's polled by an external
uptime monitor (UptimeRobot) on a 5-minute interval — so there was no reason for the other
management endpoints (`/env`, `/beans`, etc.) to be reachable in production.

## Decision

We will split Spring configuration into what's shared (`application.properties`) versus what
differs between environments (`application-dev.properties` / `application-prod.properties`),
with the prod profile requiring `FRONTEND_URL` to be set explicitly (startup fails if it's
missing, rather than defaulting to `localhost`), and exposing only `/actuator/health` in
production.

## Consequences

Production can't silently start with a broken frontend URL — a missing `FRONTEND_URL` fails
startup immediately instead of shipping broken email links or a CORS misconfiguration. Only the
one Actuator endpoint actually used (health, polled by the uptime monitor) is reachable in
production, rather than the full management surface.

The trade-off: production startup is now strict — forgetting to set `FRONTEND_URL` at deploy
time takes the whole app down rather than degrading gracefully, so that step has to be
remembered and documented. Any future need for another Actuator endpoint in production (e.g.
metrics) requires deliberately widening the exposure again rather than it already being
available.
