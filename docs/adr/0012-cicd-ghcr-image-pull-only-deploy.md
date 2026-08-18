# 0012. CI/CD via GitHub Actions to GHCR, with the VM only pulling pre-built images

## Status

Accepted

## Context

The project's workflow already runs heavily through GitHub (issues, projects, Actions, Pages),
so publishing built images to GitHub Container Registry was the path of least resistance, rather
than introducing a separate registry like Docker Hub. It's also free for this use.

Separately, the Hetzner VM (`CX22`: 2 vCPU / 4 GB RAM, see
[0011](0011-hetzner-hosting-self-hosted-postgres.md)) has limited resources for comfortably
building images — but even setting that aside, a centralized, predictable build in CI was
preferred over building on the VM: it keeps image builds in one place, and makes it easy to
switch to a different hosting VM later, since any host only needs to `pull` the already-built
image rather than have build tooling and do the build itself.

## Decision

We will build Docker images for the backend and data-service in GitHub Actions and push them to
GHCR (tagged `latest` and the commit SHA); the production VM only ever pulls pre-built images —
it never builds locally.

## Consequences

Builds are centralized, predictable, and portable: free to run, low-friction given the project's
existing GitHub-centric workflow, and moving to a different host later only requires that host to
be able to `pull` from GHCR, not to build anything itself.

The trade-off: this makes the whole build-and-deploy pipeline dependent on GitHub's availability
— a GitHub Actions or GHCR outage blocks the ability to build or deploy a new version, with no
fallback build path. It also adds a layer of infrastructure and coupling to a single third-party
platform for something that could, in principle, have been built directly on the host.
