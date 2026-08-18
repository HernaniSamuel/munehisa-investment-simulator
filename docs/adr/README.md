# Architecture Decision Records

This directory logs the architecturally significant decisions made in this project: the ones
that are costly to reverse, or that were chosen over a real alternative. Each record captures
the context, the decision, and its consequences — including the trade-offs, not just the
benefits — so the reasoning behind the codebase stays available after the conversation that
produced it has scrolled out of reach.

Records here follow Michael Nygard's lightweight ADR format. See
[template.md](template.md) for the structure every record follows.

## When to write one

An architecturally significant decision gets an ADR in the same issue/PR that makes it. As a
rule of thumb: if reversing the decision later would mean redoing real work, or if a genuine
alternative was on the table and rejected, it's architectural. Implementation-level nuances
that are already explained at the right level of detail in a code comment or a module's own
README don't need an ADR on top.

## Numbering and naming

Files are named:

```
NNNN-kebab-case-title.md
```

- `NNNN` — a sequential, zero-padded four-digit number (`0001`, `0002`, ...), assigned in the
  order records are added. Numbers are never reused, even if a record is later superseded.
- `kebab-case-title` — a short, descriptive slug for the decision.

## Superseding, never editing

An accepted ADR is a historical record of what was decided and why, given what was known at the
time. Once its status is `Accepted`, its `Context`, `Decision`, and `Consequences` are never
edited to reflect a later reversal or change of mind — doing so would erase the reasoning that
made sense at the time and was genuinely believed.

Instead, when a past decision is reversed or replaced:

1. Write a new ADR describing the new decision, with the next sequential number.
2. Set the new ADR's `Status` to `Accepted`, and mention in its `Context` or `Consequences`
   which prior ADR it replaces.
3. Update the old ADR's `Status` line only, to `Superseded by ADR-NNNN` (linking to the new
   record) — the rest of the old file stays untouched.
