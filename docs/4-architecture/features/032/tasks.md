# Feature 032 Tasks – Palantir Formatter Adoption

_Linked plan:_ `docs/4-architecture/features/032/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

> Checklist mirrors the staged rollout: tooling swap first, repo-wide reformat second, docs + tracker updates last.

## Checklist
- [x] T-032-01 – Governance setup (FR-032-05, S-032-05).  
  _Intent:_ Create spec/plan/tasks, update roadmap + session snapshot with the Option B decision, and document the staged rollout.  
  _Verification:_ `./gradlew --no-daemon spotlessCheck` (2025-10-19 baseline).

- [x] T-032-02 – Spotless configuration swap (FR-032-01, S-032-01).  
  _Intent:_ Pin Palantir Java Format 2.78.0 in the version catalog and switch Spotless to `palantirJavaFormat`.  
  _Verification:_ `./gradlew --no-daemon spotlessCheck` (expected Palantir diffs, 2025-10-19).

- [x] T-032-03 – Dependency lock refresh (FR-032-02, S-032-02).  
  _Intent:_ Regenerate root + module lockfiles to capture Palantir artifacts and drop google-java-format.  
  _Verification:_ `./gradlew --no-daemon --write-locks spotlessApply check` plus targeted module `compileJava` runs (2025-10-19).

- [x] T-032-04 – Managed hook update (FR-032-03, S-032-03).  
  _Intent:_ Update `githooks/pre-commit` messaging to reference Palantir 2.78.0 and dry-run the hook against staged files.  
  _Verification:_ Manual hook dry-run logged 2025-10-19 (formatter invoked via Spotless Palantir task).

- [x] T-032-05 – Documentation & IDE guidance (FR-032-05, S-032-05).  
  _Intent:_ Revise AGENTS, CONTRIBUTING, roadmap, knowledge map, and how-to guides with Palantir policy + IDE setup notes.  
  _Verification:_ `./gradlew --no-daemon spotlessCheck` (docs sweep, 2025-10-19).

- [x] T-032-06 – Stage 1 verification (FR-032-01..03, S-032-01..03).  
  _Intent:_ Re-run `spotlessCheck` and targeted compile tasks to ensure the tooling swap is stable before reformatting.  
  _Verification:_ `./gradlew --no-daemon spotlessCheck` (2025-10-19).

- [x] T-032-07 – Repository-wide reformat (FR-032-04, S-032-04).  
  _Intent:_ Execute `./gradlew --no-daemon spotlessApply` to apply Palantir formatting across all modules; spot-check sample files.  
  _Verification:_ `./gradlew --no-daemon spotlessApply` (2025-10-19).

- [x] T-032-08 – Final quality gate (FR-032-04, S-032-04).  
  _Intent:_ Run `./gradlew --no-daemon spotlessApply check` to ensure the build is green post-reformat.  
  _Verification:_ `./gradlew --no-daemon spotlessApply check` (2025-10-19).

- [x] T-032-09 – Documentation handoff & tracker updates (FR-032-05, S-032-05).  
  _Intent:_ Update roadmap, changelog, `_current-session.md`, and migration tracker with the Palantir formatter outcome; publish rebase guidance.  
  _Verification:_ Documentation diffs recorded 2025-10-19 (no additional command required).

## Verification Log
- 2025-10-19 – `./gradlew --no-daemon spotlessCheck`
- 2025-10-19 – `./gradlew --no-daemon --write-locks spotlessApply check`
- 2025-10-19 – `./gradlew --no-daemon spotlessApply`
- 2025-10-19 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Monitor future Palantir releases; open a new feature if an upgrade is desired.
