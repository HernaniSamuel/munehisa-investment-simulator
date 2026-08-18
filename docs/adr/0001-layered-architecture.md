# 0001. Layered architecture (controllers / service / repository / domain / dto / infra)

## Status

Accepted

## Context

This was the maintainer's first Java project. The goal was to isolate responsibilities and keep
coupling low between them, following Java best practices researched for the occasion rather than
carried over from prior experience in the language.

A genuine alternative was considered: an event-driven architecture, which is arguably a more
natural fit for a simulation domain — features that react to what happens during a simulation
(e.g. indicators) could be added as event listeners without touching the simulator's core flow,
and the design would likely scale better as more of those reactive features are added.

## Decision

We will use a straightforward layered architecture — `controllers` / `service` / `repository` /
`domain` / `dto` / `infra` — with layers calling each other directly, rather than an
event-driven design.

## Consequences

The direct, linear flow between layers is simple to follow and reason about, which mattered for
a first project in the language. Cross-cutting or reactive features are more straightforward to
locate — everything about a request lives in the same call chain.

The trade-off accepted knowingly: an event-driven architecture would likely scale better for this
domain. Features like indicators that react to simulation events (month advance, buy/sell,
snapshot) would be more isolated and decoupled from the core simulator under an event-driven
design; with direct layers, extending that kind of reactivity means touching the core simulation
flow more directly and reasoning in terms of a linear execution path rather than emitted events.
