# Feature 009 Tasks – Operator Console Infrastructure

_Status:_ In progress
_Last updated:_ 2025-12-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [ ] T-009-01 – Enforce operator-console facade seams via application layer + CredentialStoreFactory (NFR-facade-seam).  
  _Intent:_ Ensure operator console (REST/UI/templates) avoids direct `io.openauth.sim.core..`/`MapDbCredentialStore`, relying on `application` services and `CredentialStoreFactory`; add ArchUnit coverage and refactor UI/REST wiring as needed.  
  _Verification:_  
  - `./gradlew --no-daemon :core-architecture-tests:test`  
  - `./gradlew --no-daemon :rest-api:test :ui:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-009-02 – Add Playwright visual snapshot harness (FR-009-11, S-009-11, NFR-009-07).  
  _Intent:_ Provide an opt-in, headless-by-default harness that drives `/ui/console` in a real browser, clicks protocol tabs, and writes screenshots to `build/ui-snapshots/**` (not committed).  
  _Verification:_  
  - `bash tools/ui-visual/run-operator-console-snapshots.sh`  
  - `ls -la build/ui-snapshots`  

- [x] T-009-03 – Document visual snapshot workflow (FR-009-11, NFR-009-07).  
  _Intent:_ Document how to install Playwright dependencies, run the local console server for snapshots, and keep outputs local-only.  
  _Verification:_  
  - `./tools/docs-verify.sh --all`  

- [x] T-009-04 – Capture interactive Evaluate/Replay result states in snapshots (FR-009-11, S-009-11, NFR-009-07).  
  _Intent:_ Extend the Playwright harness so screenshots cover not just tab navigation, but also representative inline/stored Evaluate + Replay flows that render the right-side result panels (using presets and/or seed buttons instead of manual input).  
  _Verification:_  
  - `bash tools/ui-visual/run-operator-console-snapshots.sh`  
  - `cat build/ui-snapshots/<run-id>/manifest.json`  

- [x] T-009-05 – Prune old local snapshot runs (FR-009-11, NFR-009-07).  
  _Intent:_ Keep `build/ui-snapshots/**` bounded by deleting the oldest run directories before creating a new run (default keep: 10; configurable via env var).  
  _Verification:_  
  - `bash tools/ui-visual/run-operator-console-snapshots.sh`  
  - `ls -1 build/ui-snapshots | wc -l`  

- [x] T-009-06 – Define the visual QA review loop for snapshots (FR-009-11, NFR-009-07).  
  _Intent:_ Document the capture → review → backlog → validate workflow for using snapshot runs as the artifact for AI visual review and UI drift detection.  
  _Verification:_  
  - `./tools/docs-verify.sh --all`  

- [x] T-009-07 – Generate triage artefacts and freeze time for snapshots (FR-009-11, NFR-009-07).  
  _Intent:_ Reduce visual QA latency by freezing time to a deterministic timestamp and producing local-only triage artefacts (diff ranking + montage) for each snapshot run.  
  _Verification:_  
  - `bash tools/ui-visual/run-operator-console-snapshots.sh`  
  - `cat build/ui-snapshots/<run-id>/triage/triage.json`  

## Verification Log
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (Implementation Drift Gate verification run)
- 2025-12-13 – `bash tools/ui-visual/run-operator-console-snapshots.sh` (includes `UI_VISUAL_MAX_RUNS` pruning), `./tools/docs-verify.sh --all`, `./gradlew --no-daemon spotlessApply check` (PASS; includes interactive Evaluate/Replay result states)

## Notes / TODOs
- Keep the knowledge map/roadmap aligned with Feature 009 ownership; update references when upstream features change scope.
