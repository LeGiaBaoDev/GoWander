# GoWander — Agent Guide

> **Audience:** every coding agent (Claude Code, Codex, others) working in this repository.
> **Read order:** [`OVERVIEW.md`](./OVERVIEW.md) → [`PIPELINE.md`](./PIPELINE.md) → this file.
> `OVERVIEW.md` defines *what* and *why*, `PIPELINE.md` defines *order and gates*, this file defines *which agent/skill does each step* and *how every change is recorded*.
> **Detailed design sources:** [`docs/design/`](./docs/design/README.md) holds the six full design documents, split into small section files. Use its index to open only the section relevant to the current task.

## 1. Non-negotiable rules

1. Follow `PIPELINE.md` §0. Never claim a status without executed evidence.
2. Work on the earliest unblocked milestone. Do not start deferred scope to get around a blocker.
3. **`develop` is the working branch; `main` only receives a merge at each major release.** One short-lived branch per milestone or task is created from `develop`, merged back into `develop` and deleted when its gate passes. Nothing is merged or committed directly to `main` between releases. The only exception was the initial repository setup (M00), which went straight to `main` before `develop` existed.
4. **Every change gets a change record in `docs/changes/` (see §4). A task without a change record is not done.**
5. No secrets, raw datasets, DB volumes, backups or OTP graph artifacts in Git.
6. **Git history belongs to the project owner. Agents never run a Git command that writes history or touches the remote:** no `git add`, `commit`, `merge`, `rebase`, `reset`, `revert`, `tag`, `push`, `pull`, `stash`, and no creating, switching or deleting branches. Agents may only run read-only Git commands (`status`, `diff`, `log`, `show`, `branch --list`, `check-ignore`). When work is ready, the agent stops and gives the owner the exact commands and a proposed commit message; the owner runs them. This holds even if an earlier message in the session approved a commit.

## 2. Standard task loop

Every task, whatever the milestone, goes through these steps in order. The names are ECC plugin agents and skills; if ECC is unavailable, do the same step manually.

| # | Step | Agent / skill | Output |
|---|---|---|---|
| 1 | Understand | `ecc:code-explorer` agent, `ecc:search-first` skill | What exists, what is reusable |
| 2 | Plan | `/ecc:plan` (`ecc:planner` agent); `ecc:architect` agent for design decisions | Files to change, steps, risks — approved by the user before coding |
| 3 | Decide | `ecc:architecture-decision-records` skill | `docs/decisions/ADR-*.md` when a significant choice is made |
| 4 | Implement test-first | `ecc:tdd-workflow` skill (`ecc:tdd-guide` agent) | Failing test → minimal code → refactor |
| 5 | Fix the build | Language build resolver (§3) | Green build, minimal diff |
| 6 | Review | Language reviewer (§3), then `ecc:silent-failure-hunter` agent | Findings fixed or explicitly accepted |
| 7 | Secure | `ecc:security-reviewer` agent / `/ecc:security-scan` | Required for auth, user input, API endpoints, secrets, SQL |
| 8 | Verify | `ecc:verification-loop` skill, `/ecc:test-coverage` | Commands run and observed results |
| 9 | Document | `ecc:doc-updater` agent / `/ecc:update-docs` | README, OpenAPI, runbooks, `PIPELINE.md` status |
| 10 | Record the change | Manual, per §4 | `docs/changes/change-DD-MM-YYYY.md` |
| 11 | Hand over for commit | `ecc:commit-reviewer` agent, `ecc:git-workflow` skill (review and draft only) | List of changed files, proposed Conventional Commits message and the exact commands. **The owner commits, merges and pushes** (rule 6) |

Steps 4–5 do not apply to research or documentation-only tasks. Steps 1, 2, 8, 10 and 11 always apply.

## 3. Milestone → branch → agents and skills

| Milestone | Branch | Skills | Agents |
|---|---|---|---|
| **M00** Repo + toolchain | `chore/repository-bootstrap` | `ecc:git-workflow` | `ecc:planner`, `ecc:doc-updater` |
| **M01** Transit data feasibility | `research/transit-data` | `ecc:deep-research`, `ecc:search-first`, `ecc:architecture-decision-records` | `ecc:architect` |
| **M02** OTP routing PoC | `poc/otp-routing` | `ecc:docker-patterns`, `ecc:documentation-lookup`, `ecc:benchmark` | `ecc:docs-lookup`, `ecc:java-build-resolver` |
| **M03** KMP + MapLibre PoC | `poc/kmp-map` | `ecc:compose-multiplatform-patterns`, `ecc:kotlin-patterns`, `ecc:kotlin-coroutines-flows`, `ecc:android-clean-architecture`, `ecc:kotlin-testing` | `ecc:kotlin-reviewer`, `ecc:kotlin-build-resolver`, `ecc:swift-build-resolver` (iOS shell) |
| **M04** Go routing service | `feat/backend-routing` | `ecc:golang-patterns`, `ecc:golang-testing`, `ecc:api-design`, `ecc:backend-patterns`, `ecc:error-handling` | `ecc:go-reviewer`, `ecc:go-build-resolver`, `ecc:tdd-guide`, `ecc:security-reviewer` |
| **M05** PostGIS + Discover | `feat/discover` | `ecc:postgres-patterns`, `ecc:database-migrations`, `ecc:golang-patterns` | `ecc:database-reviewer`, `ecc:go-reviewer` |
| **M06** End-to-end slice | `feat/core-vertical-slice` | `ecc:verification-loop`, `ecc:kotlin-testing`, `ecc:golang-testing` | `ecc:kotlin-reviewer`, `ecc:go-reviewer`, `ecc:silent-failure-hunter` |
| **M07** Admin + RBAC | `feat/admin-rbac` | `ecc:architecture-decision-records` (stack choice), `ecc:frontend-patterns`, `ecc:security-review`, `ecc:e2e-testing`, `ecc:accessibility` | `ecc:architect`, `ecc:security-reviewer`, `ecc:e2e-runner`, `ecc:a11y-architect` |
| **M08** Backup + PITR | `infra/database-backup` | `ecc:postgres-patterns`, `ecc:docker-patterns`, `ecc:deployment-patterns` | `ecc:database-reviewer`, `ecc:security-reviewer` |
| **M09** CI + observability | `infra/ci-observability` | `ecc:deployment-patterns`, `ecc:github-ops`, `ecc:security-review` | `ecc:security-reviewer`, `ecc:silent-failure-hunter` |
| **M10** Pilot gate | `release/pilot` | `ecc:production-audit`, `ecc:delivery-gate`, `/ecc:quality-gate` | `ecc:security-reviewer`, `ecc:code-reviewer` |

Notes:
- Every branch in this table except M00 starts from `develop` and merges back into `develop`. `release/pilot` is the branch that goes from `develop` into `main`; whether a milestone counts as a major release is the owner's call.
- Branch dependencies follow `PIPELINE.md` §2. A branch is created only when its milestone is unblocked and about to start, and the owner creates it (rule 6).
- The Admin web reviewer (`ecc:react-reviewer`, `ecc:vue-reviewer`, …) is chosen after the M07 stack ADR.
- `ecc:e2e-runner` drives browsers only. Mobile end-to-end checks in M06 are run on emulator/simulator/device and recorded as evidence.
- M08 restore, PITR cutover and any production mutation always need explicit human approval; no agent runs them autonomously.
- Skill and agent names come from the installed ECC plugin. If one is missing, type `/` in Claude Code to see the current names and update this table.

## 4. Change records (`docs/changes/`)

**Rule:** every change to the repository — code, config, schema, infrastructure, data or documentation — is recorded in `docs/changes/` in the same branch and commit as the change itself.

- **File name:** `change-DD-MM-YYYY.md`, using the date of the change in Asia/Ho_Chi_Minh, e.g. `change-11-11-2026.md`.
- **One file per day.** If the file for today already exists, append a new numbered entry; never rewrite earlier entries.
- **Language:** Vietnamese, with technical identifiers in English.
- **Template and field definitions:** [`docs/changes/README.md`](./docs/changes/README.md).

Each entry must answer: **what** changed, **where** (file paths), **how** (what was done), **why**, **evidence** (commands run and results), **impact/risks**, and **what comes next**. Write "chưa kiểm chứng" when something was not verified; do not invent evidence.
