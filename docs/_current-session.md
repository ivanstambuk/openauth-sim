# Current Session Snapshot

> Keep this file up to date across all active chats. Treat it as the single source of truth for in-progress workstreams so every hand-off is instant. Replace the bracketed text and prune sections you do not need.

- Date: 2025-11-15
- Primary branch: `main`
- Other active branches: none

## Governance & Tooling

- Governance log (2025-11-15, current session): `git config core.hooksPath` → `githooks` (session reset guard; hooks path confirmed for this Codex CLI run).

## Active workstreams

### Feature 005 – EMV/CAP Simulation Services (In review)

- Planning log (2025-11-13): Documented EMV provenance mapping (T-005-24a) in `docs/4-architecture/features/005/plan.md`; planning-only update.
- Planning log (2025-11-13): Cleaned up Feature 005 spec/plan/tasks to remove renumbering notes, refreshed timestamps, and kept the status at In review to reflect the active verification state; documentation-only change.
- Planning log (2025-11-14): Added Feature 005 tasks T-005-67–T-005-72 for increments I8b/I9/I10, linked them from the plan, and recorded the upcoming documentation/verification scope; documentation-only change.
- Planning log (2025-11-15): Updated `docs/4-architecture/features/005/plan.md` to remove Clarifications-specific checklist text and route future questions through spec sections, `docs/4-architecture/open-questions.md`, and ADRs as needed; documentation-only change.
- Verification log (2025-11-14a): `node --test rest-api/src/test/javascript/emv/console.test.js` (FAILED – new inline replay verbose-trace assertion expects `includeTrace=true`, currently `undefined`; captured for T-005-67 red coverage).
- Verification log (2025-11-14d): `node --test rest-api/src/test/javascript/emv/console.test.js` (PASS – inline replay payload now copies `includeTrace=true`, clearing the red coverage logged in 2025-11-14a).
- Verification log (2025-11-14b): `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` (PASS – Selenium suite now includes inline replay verbose-trace coverage).
- Verification log (2025-11-14c): `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"` (PASS – REST trace contract suite rerun after staging the new tests).
- Verification log (2025-11-14e): `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"` (PASS – reran after wiring includeTrace to confirm REST replay traces stay green).
- Verification log (2025-11-14f): `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*Trace*"` (PASS – CLI replay trace assertions still succeed with the shared-console updates).
- Verification log (2025-11-14g): `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"` (PASS – application trace payloads remain green with the new includeTrace flow).
- Verification log (2025-11-14h): `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` (PASS – Selenium replay verbose-trace assertions hold with the UI fix).
- Verification log (2025-11-14i): `./gradlew --no-daemon :ui:test` (PASS – UI module stayed green after the EMV console edit).

### Feature 006 – EUDIW OpenID4VP Simulator (In progress)

- Planning log (2025-11-13): Cleaned up Feature 006 spec/plan/tasks to remove renumbering notes, set their statuses to In progress (instead of Ready for implementation) with updated timestamps, deleted `docs/4-architecture/features/006/legacy/040/`, and aligned the roadmap entry with the In-progress status; documentation-only change.
- Planning log (2025-11-15): Migrated the Feature 006 spec off the Clarifications section by folding the HAIP/baseline scope, UI behaviours, telemetry guidance, and fixture provenance into the Overview/UI/Telemetry/Fixtures sections and standardising FR Source cells to “Spec.” for the UI/fixture requirements; documentation-only change.

## Next suggested actions

> Keep this section synced as workstreams progress. When an item is completed, update it to point at the next actionable task for that workstream (or remove the subsection if the stream is idle).

### Workstream – EMV/CAP console & traces

- Next suggested action: Complete EMV/CAP documentation refresh and portfolio sync for the verbose trace/console work by driving T-005-69 – Documentation refresh (S39-10) to done (update EMV/CAP how-to guides, refresh knowledge map + roadmap entry #5, and fold I8b/I9 clarifications into the spec/plan/tasks).

### Workstream – EUDIW OpenID4VP simulator

- Next suggested action: Deliver live Trusted Authority ingestion by implementing T-006-23 – Live Trusted Authority ingestion (S-040-11), including `trust/snapshots/*.json` refresh, provenance telemetry, and conformance dataset exposure through `presentations/seed` plus the accompanying CLI/Operator UI copy and telemetry snapshot updates.

## Completed Clarifications governance batch (historical summary)

- Planning log (2025-11-15): Logged and resolved a Clarifications scope question (Option A selected for completing A1/A2/T1/T2, leaving ADR work optional) and reflected the outcome in `AGENTS.md`, the templates, and the open-questions log; documentation-only updates.
- Historical note: Detailed verification logs for the Clarifications governance batch (Features 001–004 and 009–013, plus docs/runbook/AGENTS updates) have been pruned from this snapshot for readability. Consult git history and the per-feature specs/plans/tasks for the full drift-gate and verification record.
