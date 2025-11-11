# Feature Plan 039 – EMV/CAP Simulation Services

| Field | Value |
|-------|-------|
| Status | In review |
| Last updated | 2025-11-10 |
| Linked specification | `docs/4-architecture/features/039/spec.md` |
| Linked tasks | `docs/4-architecture/features/039/tasks.md` |

## Vision & Success Criteria
- Deliver deterministic EMV/CAP OTP generation **and replay validation** (Identify, Respond, Sign) across core, application, REST, CLI, and operator console facades with consistent telemetry and optional verbose traces.
- Provide stored credential workflows (MapDB persistence + seeding) so presets can drive CLI/REST/UI evaluations and replay checks.
- Seed regression coverage with transcripted calculator vectors and document how to extend fixtures, including mismatch scenarios for replay regression.
- Maintain telemetry redaction rules and align new contracts with existing REST/OpenAPI conventions and CLI/UI ergonomics.
- Ensure EMV verbose traces redact master keys via SHA-256 digests while leaving session keys visible so troubleshooting matches issuer expectations without leaking long-term secrets.

## Scope Alignment
- **In scope:** Core EMV/CAP derivation utilities, application services + telemetry, REST endpoints + OpenAPI docs, CLI parity, operator console integration, MapDB persistence with seeding, verbose trace schema, fixture scaffolding, replay validation flows, and supporting documentation.
- **Out of scope:** Hardware token emulation, APDU reader workflows, and physical device integrations.
- Operator UI verbose traces must route through the shared `VerboseTraceConsole` component so copy/download controls and Selenium helpers remain consistent with HOTP/TOTP/OCRA/FIDO2 panels.

## Dependencies & Interfaces
- Requires shared cryptographic helpers (3DES, MAC) already present in `core`.
- Reuses telemetry infrastructure under `application.telemetry.TelemetryContracts`.
- REST controller integrates with Spring Boot configuration and existing verbose trace toggles.
- Test fixtures will live in `docs/test-vectors/emv-cap/` and be loaded by new test utilities.

## Implementation Drift Gate
- **Checkpoint date:** 2025-11-09 (T3952).
- **Traceability review (R1–R10):** Confirmed every specification branch is represented by executable coverage. `TraceSchemaAssertions`, `EmvCapEvaluationApplicationServiceTest`, `EmvCapReplayApplicationServiceTest`, `EmvCapEvaluationEndpointTest`, `EmvCapReplayEndpointTest`, `EmvCapReplayServiceTest`, and the Picocli suites (`EmvCliTest`, `EmvCliEvaluateStoredTest`, `EmvCliReplayTest`) lock the core/application/REST/CLI behaviours required by R1–R4 & R7–R9, including stored vs. inline parity, preview-window math, telemetry redaction, and includeTrace toggles. Node + Selenium harnesses (`rest-api/src/test/javascript/emv/console.test.js`, `EmvCapOperatorUiSeleniumTest`) enforce the UI layout rules in R5/R10 (mode selector placement, fieldset isolation, Replay CTA spacing, and verbose-trace dock alignment).
- **Verbose trace schema (R2.4):** `EmvCapTraceProvenanceSchema`, `EmvCliTraceAssertions`, and the new provenance fixture assert all six provenance sections plus digest redaction semantics. Documented the dual-fixture requirement: keep `docs/test-vectors/emv-cap/trace-provenance-example.json` and `rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json` in sync so application components, REST OpenAPI snapshots, Node tests, and Selenium stubs all consume the same contract until Feature 039 introduces an automated sync task.
- **Quality evidence:** Reran `./gradlew --no-daemon spotlessApply check` after the documentation updates to prove the repo stays green atop the T3949 full-gate run (see `_current-session.md` for the full command log captured earlier today).
- **Documentation sync:** Updated the roadmap, operator UI how-to guide, Feature 039 tasks checklist, and `_current-session.md` with the drift-gate outcome plus the fixture duplication guardrail so downstream teams inherit the operating notes.
- **Outcome:** No drift identified; Feature 039 stands ready for owner acceptance while Feature 040 can resume at T4018 now that the trace provenance schema, docs, and fixtures align across every facade.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S39-01 | I0–I19, T3932–T3935 | Deterministic evaluate flows across core/application/REST/CLI/UI. |
| S39-02 | I38–I40, T3931 | Operator UI inline presets stay editable when loading vectors. |
| S39-03 | I37, T3933–T3934 | Stored workflows hide secrets, rely on digests, and hydrate server-side. |
| S39-04 | I19, seeding increments | MapDB seeding + credential directory parity. |
| S39-05 | I18, I19A | Replay journeys highlight match deltas + mismatch diagnostics. |
| S39-06 | I18, UI JS tasks | Preview offset controls + validation errors across facades. |
| S39-07 | I41–I45 | Console layout parity (field grouping, spacing, CTA alignment). |
| S39-08 | I50/I50a | Verbose trace schema + provenance rendering. |
| S39-09 | I34–I35, sample-vector tasks | Sample selector spacing + styling parity. |
| S39-10 | REST/CLI doc tasks | Contract/docs parity, includeTrace handling, telemetry redaction guidance. |

## Increment – I46 Preview-offset helper copy cleanup (completed 2025-11-08)
- Removed the redundant helper sentence that trailed the "Preview window offsets" heading on EMV/CAP Evaluate and Replay forms so the control mirrors the HOTP/TOTP/OCRA layout.
- Verified no console JS or Selenium assertions relied on the helper copy during the aggregate `./gradlew --no-daemon spotlessApply check` (600 s timeout) run; no targeted reruns were required beyond the pipeline.

## Increment – I47 Evaluate-mode helper text removal (completed 2025-11-08)
- Removed the informational hint that reiterated inline vs. stored behavior (“Inline evaluation uses the parameters entered above…”), dropped the `data-testid="emv-stored-empty"` hook, and cleaned up console JS logic that previously rotated the copy.
- Updated Node + Selenium suites to assert the helper text remains absent, then executed the aggregate `./gradlew --no-daemon spotlessApply check` run (600 s timeout) to cover both harnesses.

## Increment – I48 Replay CTA spacing parity (completed 2025-11-08)
- Captured requirement R5.8 in the specification to lock the Replay action bar to the shared `stack-offset-top-lg` helper so its CTA mirrors the Evaluate panel and other protocol tabs.
- Extended the Node console template tests with dedicated assertions for both Evaluate and Replay action bars plus added a Selenium expectation that the replay form’s `.emv-action-bar` includes the spacing class, ensuring regressions fail before reaching operators.
- Updated `panel.html` so both action bars include the helper, reran `node --test rest-api/src/test/javascript/emv/console.test.js` and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`; deferred the aggregate `./gradlew --no-daemon spotlessApply check` to the operator per session guidance.

## Increment – I49 Input helper copy removal (completed 2025-11-08)
- Removed the Identify-mode helper copy plus the session-derivation hints from the Evaluate and Replay forms so those panels rely solely on legends, labels, and inline placeholders per the new directive.
- Deleted the corresponding customer-hint wiring in `console.js`, updated Node + Selenium suites to stop asserting the removed text, and ensured accessibility attributes (`aria-describedby`/`aria-labelledby`) no longer reference absent nodes.
- Commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.

## Increment – I50 Verbose trace provenance expansion (completed 2025-11-09)
- **Scope recap:** Deliver the normative `trace.provenance` schema end-to-end (core → application → REST/CLI/UI), update console assets so the six sections render verbatim, and keep OpenAPI/Picocli snapshots in sync.
- **Spec alignment (2025-11-09):** R2.4 now includes the normative JSON schema for the `trace.provenance` object (`protocolContext`, `keyDerivation`, `cdolBreakdown`, `iadDecoding`, `macTranscript`, `decimalizationOverlay`). Future work must treat that schema as authoritative—no ad-hoc fields.
- **I50a – Red test sweep:** Stage failing assertions across `EmvCapEvaluationApplicationServiceTest`, `EmvCapReplayApplicationServiceTest`, `EmvCapEvaluationEndpointTest`, `EmvCapReplayEndpointTest`, `EmvCliEvaluateTest`, `EmvCliReplayTest`, `rest-api/src/test/javascript/emv/console.test.js`, and `EmvCapOperatorUiSeleniumTest` to demand the exact schema (including empty-array handling). Tests must also lock the `trace.provenance` example from the spec via fixture snapshots. Commands: targeted module tests (`./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Verbose*"`, etc.) + Node/Selenium runs (expected red until I50b).
- **I50b – Implementation & OpenAPI:** Extend domain value objects and DTOs so both evaluate and replay responses populate the new schema in one increment (no phased surface rollout). Regenerate OpenAPI snapshots, Picocli JSON baselines, and operator console fixtures. Rerun `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test`, `node --test rest-api/src/test/javascript/emv/console.test.js`, Selenium (600 s timeout), and the aggregate `./gradlew --no-daemon spotlessApply check`.
- Documentation/how-to updates remain deferred to the follow-up documentation increment once the schema stabilises.

### I50b – Work Breakdown (T3949)
1. **Schema audit & planning sync (≤30 min).** Re-read R2.4 plus `EmvCapTraceProvenanceSchema` to confirm every required field, document mappings per component, and expand this plan + `tasks/feature-039-*.md` with the step-by-step checklist. No code changes; capture command expectations for later steps.
2. **Core/application provenance builder (≤90 min).** Extend the verbose trace assembly so evaluation/replay services emit the six provenance sections populated from existing derivation data (protocol context, key derivation, CDOL breakdown, IAD decoding, MAC transcript, decimalization overlay). Update `EmvCapEvaluationApplicationServiceTest` / `EmvCapReplayApplicationServiceTest` first so they fail until the builder populates the schema. Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"` plus `./gradlew --no-daemon :core:test` if helper logic migrates downward.
3. **REST/CLI propagation (≤90 min).** Wire provenance objects into REST DTOs, OpenAPI serializers, Picocli JSON output, and stored-mode inline fallback while keeping sanitized fields consistent. This requires:
   - Extending `EmvCapEvaluationResponse`/`EmvCapReplayResponse`, their `ResponseJson` serializers, and controller assemblers so `trace.provenance` is forwarded untouched from `TraceAssembler`.
   - Updating CLI response records plus the Picocli JSON/text printers (`EmvCliEvaluateCommand`, `EmvCliEvaluateStoredCommand`, `EmvCliReplayCommand`) so both inline and stored flows emit provenance in the serialized trace. Text mode remains unchanged; JSON parity is the blocker.
   - Refreshing MockMvc + CLI JSON snapshots first (expect red), then adjusting DTOs/commands until schema assertions and identify-baseline fixture comparisons pass again.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"` and `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*Emv*Trace*"`.
4. **Operator UI wiring (≤60 min).** Ensure verbose traces returned to the browser include provenance, update the Node harness fixture expectations, and refresh Selenium assertions for the six headings rendered by `VerboseTraceConsole`. Concretely: refresh the shared fetch stub/fixture in `rest-api/src/test/javascript/emv/console.test.js`, make sure `console.js` forwards the provenance object to the trace dock without stripping fields, and extend `EmvCapOperatorUiSeleniumTest` to assert each section title is visible inside the verbose trace drawer in both evaluate and replay contexts. Commands: `node --test rest-api/src/test/javascript/emv/console.test.js` and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.
5. **Snapshot refresh & full verification (≤90 min).** After surfaces agree, regenerate OpenAPI/CLI/UI snapshots (`OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliContractSnapshotTest"`, rerun `node --test` fixture writers if needed), then execute `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` (≥600 s timeout). Document outcomes plus any follow-ups in the roadmap, tasks checklist, and `_current-session.md`.

- **Status (2025-11-09):** T3949b delivered the core/application provenance builder (`TraceAssembler`) so evaluation/replay traces now emit protocol context, key derivation, CDOL breakdowns, IAD decoding, MAC transcript metadata, and decimalization overlays. Schema assertions were updated accordingly and `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"` now passes with the new payloads.
- **Status (2025-11-09, T3949c):** REST + CLI replay mismatch specs now require `trace.expectedOtp` plus the provenance overlay. `EmvCapReplayEndpointTest.inlineReplayMismatchIncludesTraceToggle` and `EmvCliReplayTest.inlineReplayReturnsMismatch` both assert that `expectedOtp` is populated, numeric, and matches `provenance.decimalizationOverlay.otp`. Targeted suites were re-run via `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*Trace*"` and `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*Emv*Trace*"` (both green).

## Increment – I50a Verbose trace provenance red tests (in progress 2025-11-09)
- Added the canonical fixture (`docs/test-vectors/emv-cap/trace-provenance-example.json`) plus the reusable `EmvCapTraceProvenanceSchema` helper in `core/` so every suite can load the schema and compute missing-field paths without duplicating parsing logic.
- Application suites now import `TraceSchemaAssertions` to assert that evaluation/replay traces satisfy the schema; current implementation omits `provenance` so the new assertions fail as expected until I50b lands.
- REST MockMvc tests convert response traces to `Map<String,Object>`, run the schema validator, and—for `identify-baseline`—compare the entire trace payload to the published fixture. Replay endpoint tests perform the same checks and confirm future `expectedOtp` parity.
- CLI JSON tests (inline + stored) and the inline replay mismatch test parse the CLI output, run the schema validator, and (for the new identify-baseline JSON test) require a byte-for-byte match against the fixture. Shared helpers under `cli/src/test/java/io/openauth/sim/cli/EmvCliTraceAssertions.java` keep the assertions DRY.
- Node + Selenium suites ensure the operator console expects the richer provenance payload: the JS harness now feeds the shared fixture into the fetch stub, captures payloads forwarded to `VerboseTraceConsole.handleResponse`, and asserts the six provenance sections exist. Selenium coverage looks for the new section headings within the verbose trace drawer so the UI fails until it renders the schema.
- Next step (I50b) is to implement provenance wiring across core/application/REST/CLI/UI and refresh OpenAPI snapshots; keep the fixture + helper authoritative for future schema adjustments.

### I50b – Completion Notes (2025-11-09)
- Mirrored the canonical trace fixture into `rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json` so Gradle’s JS/Selenium harnesses can resolve the schema locally, and introduced the shared `EmvCapTraceProvenanceSchema`/assertion helpers to keep core/application/REST/CLI suites aligned on the same provenance contract.
- Updated `EmvCapEvaluationService`/`EmvCapReplayService` to leave verbose traces off unless callers opt in (stored requests remain verbose by default) so UI/API clients can submit `"includeTrace": false` explicitly.
- Refreshed the operator console assets plus Node/Selenium harnesses to render the six provenance sections, regenerated the OpenAPI snapshot (`OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`), and reran the full verification stack as discrete invocations: `./gradlew --no-daemon --console=plain :application:test`, `:cli:test`, `:rest-api:test`, `:ui:test`, `pmdMain pmdTest`, and `spotlessApply check`.

## Increment Breakdown (≤30 minutes each)
1. **I1 – Fixture scaffolding & failing tests**  
   - Publish `docs/test-vectors/emv-cap/{identify,respond,sign}-baseline.json` seeded from emvlab.org outputs (Identify + Respond + Sign).  
   - Introduce a placeholder `EmvCapSimulationVectorsTest` that verifies fixture presence and fails pending domain wiring (keeps build red until I2/I3 complete).  
   - Commands: `./gradlew --no-daemon :core:test`.

2. **I2 – Core EMV/CAP implementation**  
   - Implement session key derivation, CAP mode validation, Generate AC computation, and IPB masking to satisfy I1 tests.  
   - Add parameter validation tests (invalid hex, mismatched mask length).  
   - Commands: `./gradlew --no-daemon :core:test`, `./gradlew --no-daemon spotlessApply check`.

3. **I3 – Application service & telemetry**  
   - Introduce `EmvCapEvaluationApplicationService`, request/response records, verbose trace assembly, and sanitized telemetry frames.  
   - Extend application-level tests covering mode switching, ATC substitution, and trace toggling.  
   - **Status (2025-11-01):** Complete – service emits sanitized `emv.cap.*` telemetry with mask-length analytics, optional trace payloads mirror core overlays, and application tests assert Identify/Respond/Sign flows plus validation failures.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.

4. **I4 – REST endpoint & OpenAPI**  
   - Create controller, request DTOs, response payloads, and validation mapping for `POST /api/v1/emv/cap/evaluate`.  
   - Add MockMvc tests for each mode (with/without transaction data) plus validation error cases.  
   - Regenerate OpenAPI snapshot and confirm snapshot tests pass.  
   - **Status (2025-11-01):** Complete – REST controller/service translate requests into `EmvCapEvaluationApplicationService`, surface sanitized telemetry/trace payloads, and expose validation failure handling; MockMvc suite covers Identify/Respond/Sign, includeTrace toggle, and missing field validation. Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapEvaluationEndpointTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test`.

5. **I5 – Documentation & telemetry verification (REST scope)**  
   - Update `docs/2-how-to` with a draft EMV/CAP REST guide, refresh knowledge map/roadmap references, and verify telemetry sanitisation via `./gradlew --no-daemon :application:test :rest-api:test`.  
   - Prepare follow-up backlog notes for CLI/UI features (see below).  
   - **Status (2025-11-01):** Complete – new EMV/CAP REST how-to added, roadmap/knowledge map/task list synced, `_current-session.md` updated, and application/rest Gradle suites rerun to confirm telemetry sanitisation while `spotlessApply check` enforces formatting.  
   - Commands: `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :application:test :rest-api:test`.

6. **I6 – CLI evaluate command**  
   - Implement `emv cap evaluate` Picocli command with mode-driven validation, text output, JSON parity flag, and includeTrace toggle.  
   - Wire telemetry adapters (`cli-emv-cap-*`) and add unit tests covering success/error/trace flows.  
   - **Status (2025-11-01):** Complete – command now streams sanitized telemetry with CLI-prefixed IDs, supports text + JSON outputs (trace optional), and the new `EmvCliTest` suite exercises happy paths, overrides, JSON parity, and validation failures.  
   - Commands: `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon spotlessApply check`.

7. **I7 – Persistence & seeding**  
   - Introduce EMV/CAP credential persistence using `CredentialStore`, load fixtures, and expose application-level seeding helpers.  
   - Add REST `POST /api/v1/emv/cap/credentials/seed` endpoint and CLI `emv cap seed` command; ensure idempotent responses and sanitized telemetry.  
   - **Status (2025-11-01):** Complete – persistence adapter + seeding service emit sanitized stored credentials, REST seeding endpoint and CLI command ship with idempotency coverage, and telemetry logging spans application/REST/CLI.  
   - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapSeedApplicationServiceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.EmvCapCredentialSeedingEndpointTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliTest"`.

8. **I8 – Operator console integration**  
   - ✅ EMV/CAP console tab now live with stored credential presets, inline inputs, and result/trace panels wired to REST/CLI seeding while honouring the global verbose toggle.  
   - ✅ New UI script + Selenium coverage (`EmvCapOperatorUiSeleniumTest`) validate Identify/Respond/Sign flows, preset auto-fill, and global trace toggle behaviour.  
   - ⚠️ Follow-up: surface verbose traces automatically when present (shared copy/download controls) and finalize documentation sweep ahead of I9.  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.

9. **I8a – Result layout alignment**  
   - Refactor EMV/CAP operator panel to use the shared two-column layout: form + preview offsets on the left, evaluation result card on the right mirroring HOTP/TOTP/OCRA/FIDO2.  
   - Limit the result card to the OTP preview table and status badge; move mask length, masked digits, ATC, branch factor, height, and Generate AC diagnostics into the verbose trace payload.  
   - Update Selenium/JS unit tests to assert the new DOM structure and trace contents.  
  - **Status (2025-11-02):** Complete – layout now matches shared pattern, CAP preview renders the ATC/Δ/OTP row, trace grid holds relocated diagnostics (mask length, masked digits count, ATC, branch, height, ICC template), and Selenium coverage asserts the relocated metrics.  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.

10. **I8b – Verbose trace console parity (new)**  
    - Make the EMV/CAP verbose trace panel reuse the shared console behaviour: show the panel when a trace exists, hide it when not, and expose the standard copy interaction.  
   - Ensure the `includeTrace` flag continues to flow through REST/CLI, and add UI/Selenium assertions that the trace displays when the global toggle is enabled.  
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.

11. **I9 – Documentation & final verification (full-scope)**  
   - Refresh how-to guides (REST, CLI, operator UI), knowledge map, roadmap, and OpenAPI snapshots; capture persistence guidance.  
   - Run full Gradle quality gate (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).  
   - Update `_current-session.md` and close outstanding follow-ups.  

12. **I10 – Replay scaffolding & red tests (pending)**  
   - Update regression fixtures with replay/mismatch cases and stage failing unit/integration tests across application, REST, CLI, and UI layers to capture stored/inline replay expectations.  
   - Add Selenium placeholders for replay tab interactions (disabled until implementation).  
   - Commands to stage red coverage: `./gradlew --no-daemon :core:test :application:test :rest-api:test :cli:test :ui:test` (targeted classes noted in tasks).  

13. **I11 – Application replay service (complete 2025-11-02)**  
   - Implement `EmvCapReplayApplicationService`, request/response records, telemetry adapters, and verbose trace assembly while driving red tests from I10 to green.  
   - Ensure mismatch handling, preview-window overrides, and stored credential lookups mirror evaluation semantics.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.  
   - **Status (2025-11-02):** Complete – application orchestrator now emits sanitized telemetry (`emv.cap.replay.*`), scans preview windows, propagates verbose traces on demand, and passes `EmvCapReplayApplicationServiceTest`.  

14. **I12 – REST replay endpoint (complete 2025-11-02)**  
   - Introduce `POST /api/v1/emv/cap/replay`, DTOs, validation, telemetry wiring, and MockMvc coverage for stored/inline success, mismatch, and validation failures.  
   - Refresh OpenAPI snapshots and regenerate documentation.  
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest"`.  
   - **Status (2025-11-02):** Complete – new controller/service expose `POST /api/v1/emv/cap/replay` with stored/inline handling, verbose trace payloads feed the shared console (`operation=emv.cap.replay.*`), metadata surfaces sanitized preview-window details, MockMvc coverage passes, and OpenAPI snapshots updated.  

15. **I13 – CLI replay command (complete 2025-11-02)**  
   - Add `emv cap replay` Picocli command with stored/inline paths, preview-window overrides, JSON/text parity, and telemetry emission.  
   - Extend CLI tests to cover success, mismatch, validation, includeTrace toggling, and OpenAPI parity for output payloads.  
   - Commands: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest"`, `./gradlew --no-daemon spotlessApply check`.  
   - **Status (2025-11-02):** Complete – CLI now delegates replay requests to `EmvCapReplayApplicationService`, renders REST-parity JSON/text responses (including metadata and optional traces), emits `cli-emv-cap-replay-*` telemetry IDs, and satisfies `EmvCliReplayTest`; full `spotlessApply check` stays red on operator UI replay Selenium timeouts until T3914.  

16. **I14 – Operator UI replay integration (complete 2025-11-02)**  
   - Activate Replay tab, wire stored preset dropdown, inline overrides, OTP input, and updated result/trace panels that respond to the global verbose toggle.  
   - Expand JS + Selenium suites to exercise stored/inline success, mismatch, validation errors, and trace suppression.  
   - Commands: `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.  
   - **Status (2025-11-02):** Complete – operator console now exposes a replay tab that reuses shared credential caches, mirrors REST metadata, honours the global verbose toggle, and drives Selenium coverage for stored match + inline mismatch flows; `spotlessApply check` still fails at aggregated Jacoco branch coverage (0.68 < 0.70) pending final documentation sweep (I15).

17. **I15 – Replay documentation & final verification (2025-11-02)**  
   - Updated REST/CLI/operator UI how-to guides with replay instructions, refreshed roadmap/knowledge map/session snapshot, and recorded telemetry behaviour.  
   - Added targeted EMV/CAP replay and TOTP evaluation tests to lift Jacoco branch coverage from 0.68 to 0.7000, then ran the full Gradle quality gate to verify the build.  
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.totp.TotpEvaluationServiceTest"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.  

18. **I16 – Verbose trace key redaction (complete 2025-11-02)**  
   - Application/REST/CLI/operator UI layers now emit `sha256:<digest>` strings for master keys while preserving session keys; fixtures, CLI output expectations, Selenium assertions, and documentation were updated accordingly.  
   - Regression suites across application, REST, CLI, and UI cover digest redaction, stored vs inline flows, and replay parity, and the full `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` pipeline passes.

19. **I17 – Console verbose toggle harmonization (complete 2025-11-02)**  
   - Removed EMV-specific include-trace checkboxes so evaluate/replay flows follow the global verbose toggle semantics; JavaScript routing now sets `includeTrace` from the shared control and Selenium asserts the toggle wiring.  
   - Targeted runs `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` and `./gradlew --no-daemon :ui:test` verify UI behaviour, and aggregated Jacoco coverage holds at 2161/3087 (≈0.7000) after rerunning `./gradlew --no-daemon spotlessApply check`.

19a. **I17a – Remove UI transaction override inputs (complete 2025-11-02)**  
   - Evaluate and Replay panels dropped resolved/override terminal payload controls, consolidating diagnostics in verbose traces while REST/CLI retain override payloads; JS caches and Selenium flows adjusted to confirm traces expose `iccPayloadResolved`.  
   - Post-change runs `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, and `./gradlew --no-daemon spotlessApply check` remain green.

20. **I18 – Operator UI stored credential evaluate parity (complete 2025-11-02)**  
   - Added stored credential selector, empty-state messaging, preset submission CTA, and inline override handling to the Evaluate panel with shared credential caching; Selenium coverage now exercises stored and inline submissions plus verbose toggle behaviour.  
   - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` and `./gradlew --no-daemon :ui:test` execute green alongside the aggregated `spotlessApply check` pipeline.

21. **I19 – REST evaluate stored credential endpoint (complete 2025-11-02)**  
   - REST controller/service resolve `credentialId` presets via `CredentialStore`, merge inline overrides, log telemetry with credential metadata, and expose OpenAPI examples for stored requests; MockMvc tests assert success, override precedence, validation errors, and trace suppression.  
   - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapEvaluationEndpointTest"` plus `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` validate the contract updates.

22. **I20 – CLI stored evaluate command (complete 2025-11-02)**  
   - Delivered `emv cap evaluate-stored` Picocli command with inline override flags, JSON/text parity, sanitized telemetry, and includeTrace toggling; CLI harness tests assert stored match behaviour, override handling, telemetry redaction, and trace suppression.  
   - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliEvaluateStoredTest"` and `./gradlew --no-daemon spotlessApply check` now pass alongside the broader replay suite runs.

23. **I21 – Operator UI evaluate mode toggle alignment (complete 2025-11-03)**  
   - Reintroduced the "Choose evaluation mode" radio selector (stored credential vs. inline parameters) on the EMV Evaluate panel to match the HOTP/TOTP/OCRA convention and persisted selection hints across reloads.  
   - Collapsed to a single Evaluate CTA whose label/state tracks the active mode, wiring JavaScript to dispatch stored evaluations when presets are active and fall back to inline payloads when overrides exist, including updated hint messaging.  
   - Refreshed Node unit tests plus Selenium coverage for stored/inline evaluation flows; commands executed: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.

24. **I22 – Preview window controls & API alignment (completed 2025-11-04)**  
   - Added preview window inputs (backward/forward) to the EMV Evaluate form, mirroring HOTP/TOTP/OCRA semantics and defaulting to `0/0` so stored presets remain selectable.  
   - Threaded `previewWindowBackward/Forward` through application request records, REST DTOs, CLI commands, and OpenAPI contracts; regenerated snapshots and refreshed regression fixtures.  
   - Implemented application-level preview generation (extra `OtpPreview` entries), REST response serialization, CLI preview-table output, and operator UI rendering (including stored-mode Selenium coverage for non-zero offsets).  
   - Executed `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `./gradlew --no-daemon :ui:test`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, and the full `./gradlew --no-daemon spotlessApply check` quality gate.

25. **I23 – Verbose trace diagnostic parity (completed 2025-11-04)**  
   - Expanded EMV verbose traces with ATC, branch factor, height, mask length, and preview window offsets so application, REST, CLI, and operator UI diagnostics align with telemetry and preview controls.  
   - Updated application trace record, REST DTOs/payloads, CLI renderers (text/JSON), OpenAPI snapshots, JS console adapters, Selenium assertions, and replay verbose metadata to exercise the new fields.  
   - Commands executed: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.

26. **I24 – Evaluate selector placement parity (completed 2025-11-04)**  
   - Reordered the operator console EMV/CAP Evaluate panel so the "Choose evaluation mode" selector renders immediately beneath the panel heading, with stored credential presets and inline parameter inputs following it for cross-protocol parity.  
   - Adjusted Selenium coverage to assert document order guarantees (mode selector precedes preset controls) while preserving stored/inline submission behaviour.  
   - Validation commands: `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.

27. **I25 – Replay selector ordering & helper copy (completed 2025-11-04)**  
   - Realigned the EMV/CAP Replay mode toggle so Inline parameters renders before Stored credential, defaulting the selector to inline for parity with other protocols.  
   - Added concise helper copy for both options (`Manual replay with full CAP derivation inputs.` / `Replay a seeded preset without advancing ATC.`) and verified they remain single-line hints.  
   - Extended Selenium coverage to assert option order, default selection, and helper text, then ran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` (pass), `./gradlew --no-daemon :ui:test` (pass), and `./gradlew --no-daemon spotlessApply check` (pass).

28. **I26 – Sample vector terminology parity (completed 2025-11-04)**  
   - Renamed the Evaluate and Replay dropdown labels to “Load a sample vector” with a “Select a sample” default option for cross-protocol consistency while keeping EMV-specific hints.  
   - Updated stored-mode hints to describe canonical parameter loading and ATC preservation without exceeding a single line.  
   - Refreshed Selenium guards and reran targeted REST/UI suites plus `spotlessApply check`.

29. **I27 – Sample vector spacing parity (completed 2025-11-04)**  
   - Applied the shared `stack-offset-top-lg` spacing helper to the Evaluate and Replay sample vector containers so the EMV panel’s vertical rhythm matches HOTP/TOTP/FIDO2 layouts.  
   - Added Selenium assertions guarding the helper on both Evaluate and Replay preset blocks, then reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check` (both green).

30. **I28 – Evaluate sample vector spacing refinement (completed 2025-11-04)**  
   - Collapsed the Evaluate sample vector block so preset selection and seed controls mirror the Replay layout with no extra vertical gap by relocating the seed actions inside the preset field group and introducing inline spacing styles.  
   - Extended Selenium assertions to require the inline helper and reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` plus `./gradlew --no-daemon spotlessApply check` (both green).

31. **I29 – Sample vector styling parity (completed 2025-11-04)**  
   - Extended Selenium coverage to assert the Evaluate and Replay sample vector dropdowns use the shared inline preset container, shared dark surface styling, and reside with seed actions/hints.  
   - Refactored the EMV operator template to reuse inline preset markup for both selectors, including new `data-testid` hooks, and ensured the dropdown appearance matches the existing HOTP/TOTP/FIDO2 inline preset background.  
   - Commands executed: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.

32. **I30 – Stored credential secret sanitisation (completed 2025-11-05)**  
   - Added failing REST (`EmvCapCredentialDirectoryControllerTest`), JS unit, and Selenium coverage forcing digest + length metadata for stored presets, then removed raw master key/CDOL1/IPB/ICC template/issuer application data fields from directory summaries, cleared stored UI inputs, reused the new length metadata for masks, and ensured stored evaluate/replay requests fetch secrets exclusively on the server while inline fallback requires explicit operator input.  
   - Updated OpenAPI snapshots and replay payload builders so stored submissions omit sensitive fields, validated UI regressions, and confirmed the sanitisation suite with the full Gradle quality pipeline.
   - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapCredentialDirectoryControllerTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.

33. **Implementation drift gate & acceptance review (completed 2025-11-05)**  
   - Validated that Feature 039 spec/plan/tasks align with the sanitised implementation, mapped each high-impact requirement to concrete code/tests, and confirmed no undocumented work shipped.  
   - Logged lessons learned for future sanitisation sweeps (share digest/length placeholder pattern) and captured the drift report below.

## Previous Increment – I37 Stored mode label hiding (completed 2025-11-06)
- Restored full-container toggling in `console.js` so stored mode applies `hidden`/`aria-hidden="true"` to the CDOL1, issuer bitmap, ICC template, issuer application data field groups, and the mask wrappers themselves. Switching back to inline mode removes those attributes and re-renders labels and helper copy.
- Extended `.emv-stored-mode` rules in `console.css` to hide the same containers to guard against stale markup, keeping inline mode unaffected.
- Refreshed Node and Selenium expectations so stored preset flows assert the absence of sensitive rows while inline mode verifies all inputs remain editable. Validation commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon spotlessApply check --console=plain`.

## Previous Increment – I38 Customer input inline grouping (completed 2025-11-06)
- Updated the operator console templates to group the mode radios with Challenge/Reference/Amount inputs beneath a single “Input from customer” legend for Evaluate and Replay panels, and tightened CSS spacing.
- Taught `console.js` to apply mode-driven enablement (Identify clears and disables all inputs, Respond enables only Challenge, Sign enables Reference/Amount while keeping Challenge masked), while keeping serialized payloads aligned and hint text mode-aware.
- Extended JS console tests to cover the new grouping/aria semantics and adjusted Selenium coverage—inline Sign replay asserted via TODO pending T3936 inline preset hydration; legacy scenario temporarily disabled to avoid false negatives.
- Verification commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.

## Previous Increment – I39 Inline preset full hydration (completed 2025-11-08)
- Hardened the Node-based console harness so it waits for credential summaries before dispatching preset change events; this mirrors the real UI flow and guarantees inline Evaluate/Replay hydration receives the sensitive defaults (master key, CDOL1, IPB, ICC template, issuer application data, and Sign-mode customer inputs) before assertions run.
- Verified the Sign replay Selenium scenario remains green by re-running `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, then exercised the full verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test` (initial run exposed the long-standing HtmlUnit FIDO2 preset-label flake; rerunning the focused FIDO2 class followed by the `./gradlew --no-daemon spotlessApply check` pipeline produced a green `:rest-api:test`)
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`
- Result: inline Evaluate/Replay preset hydration is stable, Sign replay Selenium coverage is back on, and T3937/T3938 can now proceed.

## Previous Increment – I45 Input-from-customer row layout (completed 2025-11-08)
- Added R5.7 to the specification and refreshed this plan/tasks to capture the condensed layout requirement.
- Reworked the Evaluate + Replay templates so each mode renders as its own row with the radio + label left-aligned and its related inputs (Respond + Challenge, Sign + Reference/Amount) on the same horizontal track while Identify shows a placeholder (still one shared set of inputs with the existing `data-field` hooks).
- Introduced `.emv-customer-row`, `.emv-customer-fields`, and refreshed `.emv-customer-grid` styling so the radios/labels remain vertically aligned with consistent spacing while stored-mode masking continues to leave the inputs visible (just disabled per mode).
- Extended the Node console tests to assert the single-set structure and updated the Selenium suite to confirm there is exactly one challenge/reference/amount group per panel plus Sign-mode enablement coverage.
- Verification: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.

## Previous Increment – I40 Card transaction grouping (completed 2025-11-08)
- Added a dedicated “Transaction” fieldset beneath Card configuration for Evaluate and Replay panels, stacking the ICC payload template and Issuer Application Data inputs while surfacing the mandated helper copy (`"xxxx" is replaced by the ATC before the ICC payload template is evaluated.`).
- Confirmed the existing `.emv-transaction-block` styling keeps the new group aligned with adjacent fieldsets and relied on the sensitive-field toggles to keep ICC template/IAD containers hidden in stored mode while leaving the helper hint visible.
- Refreshed the Node console tests to assert ICC template containers/masks honour stored-mode hiding, and expanded the EMV Selenium suite to verify the new legend, helper text, and textarea presence for both Evaluate and Replay flows.
- Verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

## Previous Increment – I41 Session key derivation grouping (completed 2025-11-08)
- Wrapped the ICC master key, ATC, branch factor, height, and IV inputs for both Evaluate and Replay inside a shared “Session key derivation” fieldset, added helper copy + test IDs, and aligned copy to call out every derivation parameter explicitly.
- Introduced `.emv-session-block` styling (borders, padding, legend typography) so the new group matches the card and transaction fieldsets across light/dark themes.
- Extended the Node-based console harness with per-field containers plus new assertions proving stored mode continues to hide only the secret inputs while ATC/branch/height/IV remain visible in Evaluate and Replay forms.
- Expanded `EmvCapOperatorUiSeleniumTest` to verify the new legend/hints, confirm non-secret derivation fields stay visible in stored mode, and ensure the helper copy matches the specification.
- Verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

## Previous Increment – I42 Branch factor & height row alignment (completed 2025-11-08)
- Added Selenium assertions to ensure the Evaluate and Replay forms expose a dedicated wrapper row that contains both Branch factor and Height inputs.
- Wrapped the two inputs inside `.emv-session-pair-row` containers with new `data-testid` hooks and introduced CSS to keep the row full width while stored mode still hides only secret-bearing fields.
- Verification commands:
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check` (first attempt hit the 300 s timeout; reran with a longer timeout and it passed)

## Previous Increment – I43 Session key master key/ATC width (completed 2025-11-08)
- Wrapped the ICC master key textarea + secret mask and the ATC input inside a new `.emv-session-master-row`/`.emv-session-master-column` container for Evaluate and Replay templates so both fields stretch edge-to-edge like the branch/height pair while stored mode still hides only the master key field group.
- Added shared `.emv-session-row` (grid-column span) and `.emv-session-master-row`/`.emv-session-master-column` CSS helpers so the new row matches the existing session block styling across responsive breakpoints.
- Extended Selenium coverage with `assertMasterAtcRow` to confirm the row structure and ensure both master key and ATC inputs live inside the dedicated wrapper; added JS unit assertions so stored mode continues to hide only the master key container while ATC remains interactive.
- Verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

## Previous Increment – I44 Card configuration isolation (completed 2025-11-08)
- Split the Evaluate and Replay templates so `.emv-card-block` now contains only CDOL1/IPB textareas while `.emv-transaction-block` and `.emv-customer-block` render as sibling fieldsets; added XPath-based Selenium assertions to guarantee the transaction block is a following sibling and that card borders never wrap customer inputs.
- Added a panel-template regression test to the Node console suite that reads `panel.html` directly and fails if the card fieldsets ever include the transaction or customer sections again, satisfying spec requirement R5.6 without introducing a DOM parser dependency.
- Expanded the Selenium coverage to assert the new hierarchy for both evaluate and replay flows (card block free of nested fieldsets, transaction legend/hints intact) while keeping the existing stored-mode masking checks green.
- Verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

## Previous Increment – I45 Single-line session key inputs (completed 2025-11-08)
- Converted the ICC master key control from a textarea to a single-line text input so the master key and ATC sit on the same row without vertical scrollbars, matching R5’s inline requirement.
- Ensured the `.secret-mask` wrapper still hides only the master key column in stored mode while the ATC column remains interactive; refreshed CSS helpers so the new markup keeps the existing layout widths.
- Updated console JavaScript fixtures, Node unit tests, and Selenium assertions so they look for an `<input>` element (not a textarea) for `#emvMasterKey` while preserving validation rules.
- Verification commands:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

## Upcoming Increment – I46 Single-line IV, IPB, and issuer application inputs
- Swap the Evaluate form’s Initialization Vector, Issuer Proprietary Bitmap, and Issuer Application Data controls from `<textarea>` elements to single-line `<input>` controls (CDOL1 + ICC template stay textareas) while keeping stored-mode masking scoped to the sensitive columns.
- Apply the same single-line treatment to the Replay panel (`#emvReplayIssuerBitmap`, `#emvReplayIssuerApplicationData`) so both panels remain aligned with spec R5.5/R5.6.
- Update console JavaScript fixtures, DOM helpers, Node unit tests, and Selenium coverage to expect the new markup and assert the fields stay editable in inline mode yet hidden in stored mode. Ensure the replay console harness now selects `input` nodes for the issuer bitmap/application fields when wiring sensitive-field masks.
- Rerun the target verification commands:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

## Previous Increment – I34 Inline sample vector mode persistence (completed 2025-11-05)
- Added JS unit coverage (`node --test rest-api/src/test/javascript/emv/console.test.js`) and Selenium assertions to prove inline mode stays active after preset selection while masks/seed actions remain hidden.
- Updated `console.js` to drop the automatic `stored` switch, refreshed inline evaluation hints, and kept stored-mode behaviour intact when explicitly selected.
- Validation commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.

## Previous Increment – I35 Inline preset hydration (completed 2025-11-05)
- Updated `console.js` to attach the selected preset `credentialId` to inline submissions so masked secrets fall back to MapDB while preserving editable fields.
- Added Node tests asserting fallback payloads (blank secrets, explicit overrides) and Selenium coverage that evaluates presets in inline mode without triggering stored mode.
- Validation commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.

## Implementation Drift Gate Report (2025-11-05)
- **Reviewers:** Codex (GPT-5)  
- **Preconditions:** All Feature 039 tasks (`docs/4-architecture/features/039/tasks.md`) marked complete; latest `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` succeeded at 2025-11-05T20:40Z (see command log).  
- **Spec alignment:**
  - R5.4 (`docs/4-architecture/features/039/spec.md`) → operator console masks now render digest + length metadata while stored inputs stay non-interactive; implemented in `rest-api/src/main/resources/static/ui/emv/console.js` (mask builders and `clear*SensitiveInputs`) with coverage in `rest-api/src/test/java/io/openauth/sim/rest/ui/EmvCapOperatorUiSeleniumTest.java` (stored-mode assertions) and `rest-api/src/test/javascript/emv/console.test.js` (mask formatting unit test).
  - R6.3–R6.4 → credential directory sanitisation and server-side hydration provided by `rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapCredentialDirectoryController.java`; verified via `rest-api/src/test/java/io/openauth/sim/rest/emv/cap/EmvCapCredentialDirectoryControllerTest.java` digest/length checks and stored replay submissions.
  - R7.2/R8.1 → stored replay requests now omit master key/CDOL1/IPB/etc. when `credentialId` is supplied, relying on application hydration; validated through Selenium replay flow and JS unit coverage simulating stored submissions (ensuring inline fallback still transmits overrides when edited).
- **Divergences:** None. No over-delivery detected; sanitisation pattern matches spec clarifications dated 2025-11-05. Open questions log remains empty.
- **Coverage:** Success and validation branches covered by existing suites plus new digest/length assertions across REST, JS, and Selenium. Failure paths (inline override fallback) exercised by console unit tests and replay Selenium scenario. No missing coverage identified.
- **Follow-ups:** None for Feature 039. Recommendation: reuse the digest/length placeholder helper pattern for Feature 026’s stored credential sanitisation.
- **Artifacts synced:** Feature plan/tasks updated, knowledge map entry appended, `_current-session.md` refreshed with I30 completion notes.

Outcome: Feature 039 meets the Implementation Drift Gate. Proceed to acceptance sign-off once stakeholders review this report.

## Previous Increment – I28 Evaluate sample vector spacing refinement (completed 2025-11-04)
- Collapsed the Evaluate sample vector block so preset selection and seed controls mirror the Replay layout, introduced inline spacing styles, and reran targeted Selenium/UI checks alongside `spotlessApply check`.

## Previous Increment – I27 Sample vector spacing parity (completed 2025-11-04)
- Applied the shared `stack-offset-top-lg` spacing helper to the Evaluate and Replay sample vector containers so the EMV panel’s vertical rhythm matches HOTP/TOTP/FIDO2 layouts.  
- Added Selenium assertions guarding the helper on both Evaluate and Replay preset blocks, then reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check` (both green).

## Previous Increment – T3917 Console verbose toggle harmonization (completed 2025-11-02)
- Removed the EMV panel-specific `includeTrace` checkbox so evaluations and replays rely on the global verbose toggle shared across protocols.
- Updated `panel.html`, EMV console JavaScript helpers, and REST wiring to derive trace flags from the shared toggle while keeping CLI/REST contracts unchanged.
- Refreshed Selenium assertions to require the absence of EMV-specific toggles and verify verbose traces submit/withhold diagnostics through the global control.
- Added stored replay coverage for omitted/false `includeTrace` requests, lifting Jacoco to 2161/3087 branches (≈0.7000) and restoring a green `./gradlew --no-daemon spotlessApply check` run.

## Previous Increment – T3916 Verbose trace key redaction (completed 2025-11-02)
- Application trace model now emits `masterKeySha256` digests (`sha256:<hex>`) while preserving session key visibility; CLI/REST payloads and operator UI traces surface the digest, and replay traces wrap the shared `VerboseTracePayload` with a hashed master key field.
- Targeted suites executed: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :ui:test`. Selenium EMV suite (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`) remained red pending T3917 because the panel-specific include-trace checkboxes still existed.
- OpenAPI snapshots regenerated to capture the new `masterKeySha256` attribute on verbose traces; CLI/REST docs and tasks updated to record the change.

## Assumptions & Risks
- **Assumptions:**
  - Transcript fixtures (`docs/test-vectors/emv-cap/*.json`) remain authoritative and MapDB seeding stays in sync with their schema.
  - Shared telemetry/trace infrastructure (TelemetryContracts, VerboseTraceConsole) remains available to all modules.
  - Operator console tooling (Node harness + Selenium) continues to run in CI/local environments.
- **Risks / Mitigations:**
  - **Limited test vectors:** Coordinate with the owner for new samples; enforce fixture-driven tests so additional vectors drop in quickly.
  - **Secret leakage in traces:** Redact sensitive fields in telemetry frames and guard verbose traces behind request toggles; extend SHA-256 hashing to EMV master keys while leaving session keys visible only in verbose traces.
  - **UI toggle drift:** Removing EMV-specific controls must preserve `includeTrace` behaviour—add targeted UI/Selenium coverage so global toggle changes propagate to evaluation and replay requests.
  - **Crypto implementation errors:** Use independent derivation verification (calculator outputs) and add property-based tests for mask application and MAC derivation.

## Quality & Tooling Gates
- Default gate: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.
- UI harness: `node --test rest-api/src/test/javascript/emv/console.test.js` plus `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.
- CLI/REST OpenAPI parity: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.
- Persistence hygiene: run `./gradlew --no-daemon :infra-persistence:test` whenever seeding logic changes.

## Analysis Gate
- **Reviewed:** 2025-11-02 – Replay expansion review (spec/plan/tasks updated; implementation gated behind new red tests)
- **Checklist status:**
  - Specification completeness – Feature spec reflects latest clarifications, requirements, and operator UI ASCII mock-ups (PASS).
  - Open questions – Log remains empty; all prior clarifications captured under `## Clarifications` (PASS).
- Plan alignment – Plan references the correct spec/tasks and mirrors expanded scope through increments I1–I20 (PASS).
  - Tasks coverage – Task list maps to each requirement, sequences tests before implementation (red tests precede code in I10), and keeps increments ≤30 minutes (PASS).
  - Constitution compliance – Planned work adheres to spec-first, clarification, test-first, and documentation sync principles with small, straight-line increments (PASS).
  - Tooling readiness – Required Gradle commands documented per increment; SpotBugs/quality gates noted in tasks (PASS).
- **Follow-ups:** Initiate T3910 once red tests are drafted; do not begin implementation until replay scaffolding is in place.

## Exit Criteria
- Full Gradle quality gate passes including CLI/UI modules (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).  
- REST + CLI + UI facades expose Identify/Respond/Sign evaluation **and replay** flows with consistent OTP, telemetry, preview-window, and trace outputs.  
- MapDB persistence stores EMV/CAP credentials with deterministic seeding and preset support across all facades.  
- OpenAPI snapshots, CLI help, operator UI docs, roadmap, and knowledge map reflect the expanded scope.  
- Fixture repository contains baseline vectors plus stored credential metadata, with guidance for adding new samples.

## Follow-up Backlog (post Feature 039)
1. **Fixture expansion (future):** Capture additional hardware transcripts beyond the six shipped vectors when new calculator inputs surface and fold them into regression tests.  
2. **Advanced diagnostics (optional):** Investigate tiered verbose trace controls or additional telemetry analytics once EMV/CAP parity ships.  
3. **Hardware exploration (optional):** Revisit APDU/card-emulation scope if future requirements demand it.
