# Feature 019 – Commit Message Hook Refactor

_Status: Complete_
_Last updated: 2025-10-04_

## Overview
The current pre-commit hook executes `gitlint` by reading `.git/COMMIT_EDITMSG`. This fails whenever commits are prepared outside the default Git flow (e.g., hosted editors that supply the commit message through arguments). This feature relocates commit message linting to a dedicated `commit-msg` hook so Git provides the message file path directly, keeping pre-commit focused on staged-content validation while preserving existing quality gates (gitleaks, Gradle formatting, targeted tests, and `check`).

## Clarifications
- 2025-10-04 – Commit message linting must move to a `commit-msg` hook that Git invokes with the commit message file path (Option B from the clarification gate). The pre-commit hook should no longer read `.git/COMMIT_EDITMSG`.
- 2025-10-04 – Adopt a repository `.gitlint` that enforces Conventional Commit titles, 100-character titles, and 120-character body lines.
- 2025-10-04 – When Spotless reports a stale configuration cache during pre-commit, the hook should automatically remove `.gradle/configuration-cache` and retry once (Option A from follow-up clarification).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| CMH-001 | Provide a `githooks/commit-msg` script that runs `gitlint` against the message file argument Git supplies. | Running `GIT_PARAMS=.git/COMMIT_EDITMSG githooks/commit-msg .git/COMMIT_EDITMSG` succeeds when `gitlint` passes and fails when lint rules are violated. |
| CMH-002 | Update the pre-commit hook to drop the dependency on `.git/COMMIT_EDITMSG` while retaining gitleaks, Gradle formatting, targeted tests, and `check`. | Executing `githooks/pre-commit` on staged changes no longer shells out to `gitlint` yet still runs the remaining stages. |
| CMH-003 | Document the new hook architecture so contributors know `gitlint` now fires during the `commit-msg` stage. | Contributor runbooks reference the `commit-msg` hook for message linting and no longer instruct developers to rely on `.git/COMMIT_EDITMSG`. |
| CMH-004 | Handle Spotless stale cache errors by clearing `.gradle/configuration-cache` once and rerunning the failed Gradle command inside the pre-commit hook. | Triggering the stale-cache message during a hook run removes the cache, reruns the Gradle task, and succeeds without manual intervention. |
| CMH-005 | Provide a version-controlled `.gitlint` that aligns with Conventional Commit rules (title ≤100 chars, body ≤120 chars, minimum body length 20, enforced allowed types, project forbidden words). | `gitlint --staged` uses the repo config and fails when commits break the policy; documentation references the enforced types and limits. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| CMH-NFR-001 | Tooling parity | Local development and CI must both execute `gitlint` through the `commit-msg` hook without additional configuration. |
| CMH-NFR-002 | Developer ergonomics | Hook runtimes remain within existing expectations (pre-commit <30s including Gradle tasks; commit-msg hook ≤2s with gitlint). |

## Test Strategy
- Before implementation, run the existing pre-commit hook in an environment where `.git/COMMIT_EDITMSG` is absent to reproduce the failure (optional sandbox observation).
- After implementation, manually invoke `githooks/commit-msg` with a sample message file to observe both passing and failing scenarios.
- Perform a dry-run `git commit` to confirm both pre-commit and commit-msg hooks trigger as expected.

## Dependencies & Risks
- Developers lacking `gitlint` will now encounter failures during the `commit-msg` stage; ensure guidance remains clear in runbooks.
- Hosted Git providers executing commits server-side must be configured to run repository hooks if commit linting is required in CI.

## Out of Scope
- Altering gitlint rule configuration or Gradle tasks executed by the pre-commit hook.
- Introducing additional commit message validation beyond existing gitlint rules.

## Verification
- `./gradlew spotlessApply check` passes after hook adjustments.
- Manual invocation of the new `commit-msg` hook demonstrates lint pass/fail scenarios.
- Updated documentation merged alongside hook changes.
- 2025-10-04 – `githooks/commit-msg /tmp/gitlint-pass.XSp1tL` passed while `/tmp/gitlint-fail.UaXqSh` failed as expected; `githooks/pre-commit` and `./gradlew --no-daemon spotlessApply check` both succeeded.
- 2025-10-04 – Simulated Spotless stale-cache failure via stubbed `gradlew`; auto-retry cleared `.gradle/configuration-cache` and the hook succeeded.
- 2025-10-04 – `.gitlint` config added with Conventional Commit enforcement; gitlint run passes on compliant commit message and fails on disallowed type.
