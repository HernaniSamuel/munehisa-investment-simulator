# Business Rule Decision Records

This directory logs the project's business-rule decisions: the domain and behavioral choices
that shape how the simulation and financial logic actually behave — accounting method,
time-step granularity, rounding policy, currency scope, and the like — where a genuine
alternative existed and something specific was picked over it. Each record captures the
context, the decision, and its consequences — including the trade-offs, not just the benefits
— so the reasoning behind the domain logic stays available after the conversation that
produced it (often an interview with the maintainer, not the code itself) has scrolled out of
reach.

Records here follow the same lightweight format as [docs/adr/](../adr/README.md)'s
Architecture Decision Records. See [template.md](template.md) for the structure every record
follows.

## Business rule vs. architecture decision

A **business-rule decision** is a domain/behavioral choice: it determines what the simulation
actually does — average-cost vs. FIFO accounting, monthly vs. daily time steps, whether a
value is rounded or kept at full precision, which currencies are supported, and similar
choices where the alternative would change the simulation's behavior, not its implementation.

An **architecture decision** is about how the system is built rather than what it does —
persistence choices, service boundaries, deployment topology, testing strategy. Those stay in
[docs/adr/](../adr/README.md).

When a decision is genuinely both (e.g. it has a domain-behavior angle and a costly-to-reverse
technical angle), record it in whichever directory captures the primary force behind the
decision, and cross-reference the other one from its `Context` or `Consequences` section
rather than duplicating the record.

Not every default belongs here: a value that was simply never questioned, because no genuine
alternative was ever on the table, is an implementation detail, not a business-rule decision.

## Numbering and naming

Files are named:

```
NNNN-kebab-case-title.md
```

- `NNNN` — a sequential, zero-padded four-digit number (`0001`, `0002`, ...), assigned in the
  order records are added. This is its own sequence, independent of the ADR numbering in
  `docs/adr/` — the first business-rule record is `0001` regardless of how many ADRs exist.
  Numbers are never reused, even if a record is later superseded.
- `kebab-case-title` — a short, descriptive slug for the decision.

## Superseding, never editing

An accepted business-rule record is a historical record of what was decided and why, given
what was known at the time. Once its status is `Accepted`, its `Context`, `Decision`, and
`Consequences` are never edited to reflect a later reversal or change of mind — doing so would
erase the reasoning that made sense at the time and was genuinely believed.

Instead, when a past decision is reversed or replaced:

1. Write a new record describing the new decision, with the next sequential number.
2. Set the new record's `Status` to `Accepted`, and mention in its `Context` or `Consequences`
   which prior record it replaces.
3. Update the old record's `Status` line only, to `Superseded by business-rule-NNNN` (linking
   to the new record) — the rest of the old file stays untouched.

## When to write one

A business-rule decision gets a record in the same issue/PR that makes it, going forward — see
[CONTRIBUTING.md](../../CONTRIBUTING.md#business-rule-decisions).
