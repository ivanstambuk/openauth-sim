# Feature 011 Tasks – Governance & Workflow Automation

_Status:_ In migration (Batch P3)  
_Last updated:_ 2025-11-11

## Checklist
- [x] T-011-01 – Merge legacy governance specs (Features 019/032) into the consolidated spec template.
  - _Intent:_ Capture hook workflows (gitlint, cache warm/retry) and Palantir formatter policy directly in `spec.md`.
  - _Verification commands:_ `rg "FR-011" docs/4-architecture/features/011/spec.md`, `git diff --stat docs/4-architecture/features/011/spec.md`.
  - _Notes:_ Spec now lists FR-011-01..08 + NFR-011-01..05.
- [x] T-011-02 – Refresh plan/tasks to reference hook guard, gitlint, Palantir, and analysis-gate verification commands.
  - _Intent:_ Keep plan/tasks synchronized with the new requirements and explicit command logs.
  - _Verification commands:_ `rg "P3-I" docs/4-architecture/features/011/plan.md`, `rg "T-011" docs/4-architecture/features/011/tasks.md`.
  - _Notes:_ Ensure checklist includes hook guard + analysis gate logging expectations.
- [x] T-011-03 – Remove `docs/4-architecture/features/011/legacy/` after reviewing the merged content; log `rm -rf …` and `ls` output in `_current-session.md`.
  - _Intent:_ Finish the legacy absorption while preserving audit logs.
  - _Verification commands:_ `rm -rf docs/4-architecture/features/011/legacy`, `ls docs/4-architecture/features/011`, append log entries.
  - _Notes:_ Mention the command list + spotless run in both logs.
- [x] T-011-04 – Update governance docs (AGENTS/runbooks/constitution/analysis gate) and log verification commands.
  - _Intent:_ Keep cross-cutting governance artefacts pointing at Feature 011 and record `git config core.hooksPath`, hook dry runs, and `spotlessApply` results.
  - _Verification commands:_ `git config core.hooksPath` (2025-11-11), isolated `./githooks/pre-commit` dry-run via a temporary index, `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
  - _Notes:_ Command outputs captured in `_current-session.md`; governance docs updated to cite Feature 011.

### Legacy Coverage Checklist
- [x] T-011-L1 – Feature 019 (governance + hook guard).
  - _Intent:_ Ensure FR-011-02/03 and NFR-011-02/04 capture hook guard logging, gitlint policy, analysis-gate logging, and session snapshot expectations migrated from Feature 019.
  - _Verification commands:_ `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit`, `./gradlew --no-daemon spotlessApply check`.
  - _Notes:_ `_current-session.md` (2025-11-11) records the guard logs + spotless reruns that close this mapping.
- [x] T-011-L2 – Feature 032 (formatter + workflow automation).
  - _Intent:_ Confirm FR-011-06/07 and NFR-011-03/04 cover the Palantir formatter pin, spotless/qualityGate enforcement, and CI parity requirements inherited from Feature 032.
  - _Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`, review `.github/workflows/*` outputs for gitlint/formatter parity.
  - _Notes:_ Plan increment P3-I2 and the session log detail these commands before the legacy directory deletion.

## Verification Log
- 2025-11-11 – `git config core.hooksPath`
- 2025-11-11 – Temporary-index `./githooks/pre-commit` dry-run
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`
- 2025-11-11 – `./gradlew --no-daemon qualityGate`

## Notes / TODOs
- Capture gitlint/markdown-lint automation backlog items in Feature 013 after Batch P3 closes.
- Record hook runtime snapshots (pre-commit, commit-msg) in `_current-session.md` during future governance increments.
