# Contributing

This document formalizes the conventions already in use in this repository: branch naming, commit
message format, PR/review flow, and merge strategy. It exists so these stay consistent as the
project grows into new modules and languages.

This is currently a solo project, so there's no separate code of conduct or community guidelines
document — just the workflow conventions below.

## Branch naming

```
<type>/<issue-number>-<slug>
```

- `type` — see [Commit message format](#commit-message-format) for the allowed types.
- `issue-number` — the GitHub issue this branch resolves.
- `slug` — a short, kebab-case description.

Examples from this repo's history:

```
feat/16-user-settings-screen
feat/15-delete-account-endpoint
chore/8-setup-ci-pipeline
docs/5-openapi-readme
test/6-auth-module-testing
refactor/2-auth-module-review
```

No PR has been merged from a `fix/*` branch yet — `fix` commits so far have landed as review-round
fixes inside a `feat`/`chore`/`test` branch rather than as their own standalone branch. `fix/*` is
still a valid branch type for a change that's purely a bug fix from the start.

## Commit message format

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): description
```

- `type` — one of:
  - `feat` — new functionality
  - `fix` — bug fix
  - `docs` — documentation only
  - `test` — adding or correcting tests
  - `refactor` — code change that neither fixes a bug nor adds a feature
  - `chore` — tooling, dependencies, config, other maintenance
  - `ci` — CI/CD pipeline changes
- `scope` — optional, names the affected module or area (e.g. `auth`, `user`, `frontend`, `ui`,
  `ci`). Omit it when the change doesn't belong to a single scope (e.g. a repo-wide `docs:` change).
- `description` — imperative mood, lowercase, no trailing period.

Examples:

```
feat(user): allow authenticated users to change their own names
fix(security): return 401 instead of default 403 for unauthenticated requests
chore(ci): add GitHub Actions pipeline with staged build/unit/integration tests
```

### Commit body

The subject line alone rarely carries enough context. Add a body explaining the **why** behind the
change (motivation, root cause, trade-offs) and, for anything non-trivial, a bullet list of what
changed. Skip the body only for genuinely self-explanatory commits (typo fixes, trivial config
tweaks).

```
fix(user): stop wrong delete-account password from logging the user out

request()'s global-unauthorized handler treats any 401/403 on a
token-bearing request as a rejected session and triggers a client-side
logout. POST /user/delete reuses 401 for wrong password, so submitting
the wrong password in the delete-account modal was indistinguishable
from an expired token: it logged the user out and bounced them to
/login instead of showing the inline error inside the still-open modal.

Adds a per-request skipUnauthorizedHandling option, set on
userApi.deleteAccount, so a 401 there still rejects with an ApiError
the caller can catch, without firing the global logout side effect.
```

## Issue template

New issues should use the template at
[.github/ISSUE_TEMPLATE.md](.github/ISSUE_TEMPLATE.md), which covers summary, scope, and
acceptance criteria.

## Pull requests

Every PR must use the template at
[.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) (filled in automatically when
you open a PR on GitHub).

A PR should:

- Link the issue it closes (`Closes #<number>`).
- Stay scoped to that issue — no unrelated changes mixed in.
- Pass CI before being merged.

## Review comment conventions

When reviewing a PR, prefix comments to signal how they should be treated:

- `blocking:` — must be addressed before merge.
- `nit:` — minor, non-blocking suggestion (style, naming, small cleanup).
- `question:` — asking for clarification, not necessarily requesting a change.

Comments with no prefix are treated as `blocking:` by default.

## Merge strategy

PRs are integrated with a **merge commit**, not squash or rebase — individual commit history is
preserved so each commit's intent (per the format above) stays visible on `main`.

The merge commit subject follows GitHub's default format, and gets the same kind of explanatory
body as regular commits (see [Commit body](#commit-body)) — a summary of what the PR adds, notable
decisions, and anything addressed across review rounds:

```
Merge pull request #<number> from <branch>

<summary of what the PR does>

- <notable change or decision>
- <notable change or decision>

Reviewed in-branch across N follow-up rounds:
- <what review turned up and how it was addressed>

Closes #<issue-number>
```

## Branch cleanup

Delete the feature branch after it's merged (GitHub's "Delete branch" button on the PR, or
`git push origin --delete <branch>` locally).
