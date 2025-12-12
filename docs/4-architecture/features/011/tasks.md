# Feature 011 Tasks – Governance & Workflow Automation

_Status:_ Complete  
_Last updated:_ 2025-12-12

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-011-01 – Harden [AGENTS.md](AGENTS.md) for AI/agent onboarding (FR-011-01, FR-010-13).  
  _Intent:_ Refine the root [AGENTS.md](AGENTS.md) so it clearly describes module roles (core, application, cli, rest-api, ui), canonical Native Java entry points for HOTP/TOTP/OCRA/FIDO2/EMV/CAP/EUDIW, build/test commands, and guardrails for AI agents, while keeping governance policies and Feature 011 references intact.  
  _Verification commands:_  
  - `git config core.hooksPath`  
  - `./gradlew --no-daemon spotlessApply check`  

- [x] T-011-02 – Enforce semicolon-free commit messages and multi-`-m` handoff protocol (FR-011-02, FR-011-09).  
  _Intent:_ Reject commit messages containing semicolons via managed hooks, require assistants to compose multi-line commit bodies using repeated `-m` flags, and update governance docs/runbooks to state the fenced-code-block Git handoff rule.  
  _Verification commands:_  
  - `git config core.hooksPath`  
  - `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`  
  - `./githooks/commit-msg <temp-message-file>`  
  - `./gradlew --no-daemon spotlessApply check`  

- [x] T-011-03 – Add “low-ambiguity fast path” exception for mechanical questions (FR-011-01).  
  _Intent:_ Keep the open-questions workflow strict for high-/medium-impact ambiguity while allowing a fast path for purely mechanical/logistical questions (for example timeouts, which Gradle task to run, file paths), so agents can move quickly without polluting `open-questions.md`.  
  _Verification commands:_  
  - `git config core.hooksPath`  
  - `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`  
  - `./githooks/commit-msg <temp-message-file>`  
  - `./gradlew --no-daemon spotlessApply check`  

- [x] T-011-04 – Loosen session-start roadmap summary rule (FR-011-01).  
  _Intent:_ Require roadmap/feature/open-question summaries only on session reset or when the user explicitly asks for status/context, so routine “keep going” interactions skip redundant recaps.  
  _Verification commands:_  
  - `git config core.hooksPath`  
  - `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`  
  - `./githooks/commit-msg <temp-message-file>`  
  - `./gradlew --no-daemon spotlessApply check`  

- [x] T-011-05 – Document dependency lock refresh workflow (FR-011-10).  
  _Intent:_ Ensure governance docs explicitly cover `--write-locks` usage when dependencies change (especially for PMD aux-classpath), so lockfile drift is resolved immediately rather than surfacing late during full verification.  
  _Verification commands:_  
  - `git config core.hooksPath`  
  - `./gradlew --no-daemon spotlessApply check`  

## Verification Log
- 2025-12-12 – `git config core.hooksPath` (hook guard check)
- 2025-12-12 – Temporary-index [./githooks/pre-commit](./githooks/pre-commit) dry-run (no staged changes, hook skipped as expected)
- 2025-12-12 – Temporary [./githooks/commit-msg](./githooks/commit-msg) run with semicolon fixture (expected rejection)
- 2025-12-12 – `./gradlew --no-daemon spotlessApply check` (governance drift gate verification, PASS)
- 2025-12-12 – `git config core.hooksPath` (hook guard check; low-ambiguity fast path update)
- 2025-12-12 – Temporary-index [./githooks/pre-commit](./githooks/pre-commit) dry-run (no staged changes, hook skipped as expected; low-ambiguity fast path update)
- 2025-12-12 – Temporary [./githooks/commit-msg](./githooks/commit-msg) run with valid Conventional Commit message (PASS; low-ambiguity fast path update)
- 2025-12-12 – `./gradlew --no-daemon spotlessApply check` (PASS; low-ambiguity fast path update)
- 2025-12-12 – `git config core.hooksPath` (hook guard check; session-kickoff summary rule update)
- 2025-12-12 – Temporary-index [./githooks/pre-commit](./githooks/pre-commit) dry-run (no staged changes, hook skipped as expected; session-kickoff summary rule update)
- 2025-12-12 – Temporary [./githooks/commit-msg](./githooks/commit-msg) run with valid Conventional Commit message (PASS; session-kickoff summary rule update)
- 2025-12-12 – `./gradlew --no-daemon spotlessApply check` (PASS; session-kickoff summary rule update)
- 2025-11-13 – `git config core.hooksPath` (closure guard check)
- 2025-11-13 – Temporary-index [./githooks/pre-commit](./githooks/pre-commit) dry-run (no staged changes, hook skipped as expected)
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (governance drift gate verification, 10 s, 96 tasks: 2 executed, 94 up-to-date)
- 2025-11-13 – `./gradlew --no-daemon qualityGate` (governance closure verification, 16 s, 40 tasks: 1 executed, 39 up-to-date)
- 2025-11-11 – `git config core.hooksPath`
- 2025-11-11 – Temporary-index [./githooks/pre-commit](./githooks/pre-commit) dry-run
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`
- 2025-11-11 – `./gradlew --no-daemon qualityGate`

## Notes / TODOs
- Capture gitlint/markdown-lint automation backlog items in Feature 013 as the governance backlog progresses.
- Record hook runtime snapshots (pre-commit, commit-msg) in `_current-session.md` during future governance increments.
