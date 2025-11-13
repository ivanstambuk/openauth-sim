# Feature 011 Tasks – Governance & Workflow Automation

_Status:_ Complete  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- _No active tasks._ Capture the next governance increment (≤90 min plan effort) with explicit FR/NFR/Scenario references before editing docs or hooks.

## Verification Log
- 2025-11-13 – `git config core.hooksPath` (closure guard check)
- 2025-11-13 – Temporary-index `./githooks/pre-commit` dry-run (no staged changes, hook skipped as expected)
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (governance drift gate verification, 10 s, 96 tasks: 2 executed, 94 up-to-date)
- 2025-11-13 – `./gradlew --no-daemon qualityGate` (governance closure verification, 16 s, 40 tasks: 1 executed, 39 up-to-date)
- 2025-11-11 – `git config core.hooksPath`
- 2025-11-11 – Temporary-index `./githooks/pre-commit` dry-run
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`
- 2025-11-11 – `./gradlew --no-daemon qualityGate`

## Notes / TODOs
- Capture gitlint/markdown-lint automation backlog items in Feature 013 as the governance backlog progresses.
- Record hook runtime snapshots (pre-commit, commit-msg) in `_current-session.md` during future governance increments.
