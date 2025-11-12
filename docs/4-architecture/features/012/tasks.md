# Feature 012 Tasks – Core Cryptography & Persistence

_Status:_ In migration (Batch P3)  
_Last updated:_ 2025-11-11

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-012-01 – Merge legacy persistence specs (Features 002/027/028) into the refreshed spec template.
  - _Intent:_ Capture profiles, telemetry, maintenance, encryption, unified defaults, and IDE remediation requirements.
  - _Verification commands:_ `rg "FR-012" docs/4-architecture/features/012/spec.md`, `git diff --stat docs/4-architecture/features/012/spec.md`.
  - _Notes:_ Spec now lists FR-012-01..08 + NFR-012-01..05.
- [x] T-012-02 – Update plan/tasks to reference verification commands (benchmarks, telemetry checks, maintenance CLI, doc lint) and backlog follow-ups.
  - _Intent:_ Align plan/tasks with the consolidated scope and embed the commands that must be logged per increment.
  - _Verification commands:_ `rg "P3-I" docs/4-architecture/features/012/plan.md`, `rg "T-012" docs/4-architecture/features/012/tasks.md`.
  - _Notes:_ Ensure roadmap/knowledge map sync + `_current-session.md` logging are called out explicitly.
- [x] T-012-03 – Remove `docs/4-architecture/features/012/legacy/` after reviewing the merged content; log `rm -rf …` + `ls` output in `_current-session.md`.
  - _Intent:_ Finish the legacy absorption with a documented audit trail.
  - _Verification commands:_ `rm -rf docs/4-architecture/features/012/legacy`, `ls docs/4-architecture/features/012`.
  - _Notes:_ Record the command list plus spotless run in the session log.
- [x] T-012-04 – Sync roadmap/knowledge map/architecture graph/how-to docs with the consolidated persistence story and log verification commands.
  - _Intent:_ Keep cross-cutting artefacts consistent and document the commands used (e.g., `rg credentials.db`, `spotlessApply`).
  - _Verification commands:_ `rg "credentials.db" docs` (2025-11-11), doc edits, and `./gradlew --no-daemon spotlessApply check` (captured with the governance/toolchain sweep).
  - _Notes:_ Updates + command outputs recorded in `_current-session.md` (Batch P3 Phase 2 entries).

## Verification Log
- 2025-11-11 – `rg "credentials.db" docs`
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Track persistence benchmark reruns + encryption key-management automation as future backlog items.
- Consider scripting a lint that ensures only `credentials.db` appears as the default path across docs/code.
- Legacy Coverage – T-012-L1 (Feature 002 deployment profiles + telemetry contracts). _Intent:_ Verify FR-012-01..04 and NFR-012-01/02 describe the IN_MEMORY/FILE/CONTAINER profiles, override hooks, and telemetry previously tracked in Feature 002. _Verification commands:_ `./gradlew --no-daemon :infra-persistence:test`, `rg "persistence.cache.profile" -n docs`, `./gradlew --no-daemon spotlessApply check`. _Notes:_ `_current-session.md` contains the 2025-11-11 command log for this verification.
- Legacy Coverage – T-012-L2 (Feature 027 maintenance CLI + benchmarks). _Intent:_ Ensure FR-012-05/06 and NFR-012-03 capture maintenance CLI usage, benchmarks, and documentation hooks. _Verification commands:_ `./gradlew --no-daemon :application:test --tests "*MaintenanceCli*"`, `rg "credentials.db" docs`. _Notes:_ Scenario references updated under S-012-02; see `_current-session.md`.
- Legacy Coverage – T-012-L3 (Feature 028 IDE remediation + encryption helpers). _Intent:_ Confirm FR-012-07 and NFR-012-04 cover IDE remediation, optional encryption, and governance logging drawn from Feature 028. _Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :infra-persistence:test --tests "*Encryption*"`. _Notes:_ Backlog follow-ups noted in plan P3-I4; verification logged 2025-11-11.
