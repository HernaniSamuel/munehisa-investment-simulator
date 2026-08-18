# 0011. Hetzner Cloud VM hosting, with self-hosted Postgres on the same VM

## Status

Accepted

## Context

The original plan was to host on Oracle Cloud's Always Free tier. That never got past account
creation: Oracle's identity-verification step failed repeatedly without explanation, to the
point of apparently getting blocked after retrying — a practical dead end unrelated to any
technical evaluation of Oracle as a platform.

Looking for an alternative, predictable billing mattered — AWS was ruled out for its
reputation for unpredictable, surprise-heavy billing. Hetzner Cloud's flat monthly pricing, with
hard, well-defined limits (the VM stops rather than incurring overage charges if a limit is hit),
offered the predictability that was actually wanted, even though billing is in EUR rather than
being free.

Separately, a managed Postgres service wasn't used: the disk space available on the VM itself is
more than enough for this project's data, and keeping Postgres on the same machine as the
backend simplifies administering the production stack, since everything lives in one place.

## Decision

We will host on a Hetzner Cloud VM (`CX22`: 2 vCPU / 4 GB RAM, flat monthly price), with Postgres
self-hosted on the same VM rather than using a managed database service.

## Consequences

Billing is predictable and capped — a deliberate improvement over the surprise-billing risk of
alternatives like AWS — and administering the stack is simpler with the database co-located with
the backend on a single machine.

The trade-off: unlike Oracle's Always Free tier, this isn't free — hosting on Hetzner has cost
around €25 so far over a few months, a real recurring expense accepted in exchange for actually
being able to get the app live. Self-hosting Postgres also means backups and disaster recovery
for the production database are entirely the maintainer's own responsibility rather than a
managed provider's — and, at the time of this decision, no backup strategy exists yet.
