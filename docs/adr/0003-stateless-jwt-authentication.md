# 0003. Stateless JWT bearer-token authentication

## Status

Accepted

## Context

The frontend (React) and backend are separate, and the goal was to keep them loosely coupled —
a stateless RESTful API gives the freedom to change the frontend later without that change
touching the backend, and vice versa.

This was also the maintainer's first time writing an authentication system in Java. Rather than
design something bespoke, the intent was to lean on the pattern already established for this
kind of application and keep it as simple as the requirements allowed — complexity that isn't
cut during planning tends to compound and put the project at risk.

## Decision

We will use stateless JWT bearer-token authentication instead of session-based auth (a
server-side session store, cookies carrying a session identifier).

## Consequences

The backend stays decoupled from any particular frontend and needs no server-side session store
or its associated infrastructure (e.g. Redis) — any client that can hold a bearer token can
authenticate.

The trade-off: a JWT can't be revoked before it expires the way a server-side session can. Flows
like forced logout or password change don't retroactively invalidate a token issued before that
event, so more than one valid JWT can exist for the same user at the same time. That is a real
security exposure — a token issued before a password change stays usable against the account
until it naturally expires, even though the credential that produced it is no longer valid.
