# AI workflow

> Canonical role prompts for AI-assisted work in this repository (introduced by issue #60).
> These are the **actual prompts used** to drive each AI-assisted step — not a description
> of them. They bind the AI's behavior to the conventions already documented in
> `CONTRIBUTING.md`, so AI-assisted work follows the same standard as the project itself
> instead of ad-hoc chat instructions. The prompts are versioned and reviewed like any
> other file: changes to them go through the normal issue → PR → review flow.

## The roles

- [`issue-creator.md`](issue-creator.md) — turns an informal orientation from the
  maintainer into a well-formed issue following `.github/ISSUE_TEMPLATE.md`. Asks instead
  of assuming, and writes objectively checkable acceptance criteria.
- [`implementer.md`](implementer.md) — implements one issue on a local branch, following
  `CONTRIBUTING.md`'s conventions: real tests (happy path plus edge and error cases), docs
  kept in sync in the same changeset, and every judgment call recorded. Delivers unpushed
  commits plus a drafted PR description.
- [`reviewer.md`](reviewer.md) — verifies the changeset strictly against the issue's
  acceptance criteria, read-only, defaulting to requesting changes. Delivers a verdict,
  `blocking:` / `nit:` / `question:` comments, and — on approval — a drafted merge message.

## How the roles map onto the workflow

The project workflow from `CONTRIBUTING.md` is:
`issue → branch → implementation/docs/tests → commit → PR → review → merge`.
The roles slot into it as follows; the maintainer performs every GitHub action.

| Workflow step               | Done by                                                                    | Prompt              |
| --------------------------- | -------------------------------------------------------------------------- | ------------------- |
| issue                       | **issue creator** drafts it; maintainer opens it on GitHub                 | `issue-creator.md`  |
| branch                      | **implementer**, locally                                                   | `implementer.md`    |
| implementation / docs / tests | **implementer**, locally                                                 | `implementer.md`    |
| commit                      | **implementer**, locally — the branch stays unpushed                       | `implementer.md`    |
| PR                          | **maintainer**: inspects, runs `git push`, opens the PR with the implementer's drafted description | —  |
| review                      | **reviewer** delivers verdict, comments, and merge message as text; maintainer posts them | `reviewer.md` |
| merge                       | **maintainer**, using the drafted merge message on approval                | —                   |

When the review requests changes, the loop continues: the maintainer relays the comments to
the **implementer** (its review round), inspects and pushes the rework, then relays the
reworked branch and the implementer's comment-by-comment report to the **reviewer** (its
re-review round) — repeating until the review resolves.

## Rules shared by all three roles

1. **No GitHub access.** Every role produces local artifacts or ready-to-paste text. The
   maintainer performs all GitHub actions — opening issues, pushing, opening PRs, posting
   comments, merging — and relays information between roles in both directions.
2. **A human gate at every handoff.** Nothing is published, posted, or merged without the
   maintainer reviewing it first.
3. **The repository's files win.** Conventions live in `CONTRIBUTING.md`,
   `.github/ISSUE_TEMPLATE.md`, and `.github/PULL_REQUEST_TEMPLATE.md`; the prompts
   reference them instead of restating them. If a prompt and those files ever disagree, the
   files win, and the prompt should be fixed here.
4. **The issue is the contract.** Its acceptance criteria bind the implementer (what to
   build and prove) and the reviewer (what to verify). Ambiguity is asked about (issue
   creator), recorded as a judgment call (implementer), or grounds for requesting changes
   (reviewer) — never silently assumed.
5. **Manual and tool-agnostic.** The prompts are plain markdown usable with any AI
   assistant, invoked by hand. No automation, bots, or tool-specific agent formats.

## Invoking a role

Provide the role's file as the system prompt (or first message), then supply its input:

| Role          | Input                                                                                                   |
| ------------- | ------------------------------------------------------------------------------------------------------- |
| issue creator | the maintainer's informal description of the work                                                        |
| implementer   | the issue (number and full body) and access to the repository; for a review round, the relayed comments  |
| reviewer      | the issue, the PR description, and the branch/diff readable locally; for a re-review, the reworked branch and the implementer's report |

Each prompt file defines its role's full behavior, boundaries, and output — this README
only maps the territory.