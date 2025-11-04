# Feature Plan 039 – EMV/CAP Simulation Services

_Linked specification:_ `docs/4-architecture/specs/feature-039-emv-cap-simulation.md`  
_Status:_ In progress  
_Last updated:_ 2025-11-02 (verbose trace redaction planning)

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

25. **I23 – Verbose trace diagnostic parity (planned)**  
   - Enrich EMV verbose trace payloads with ATC, branch factor, height, and mask length metadata and render them in the operator verbose console; adjust REST/CLI serialization and fixtures.  
   - Update application telemetry/trace models, JSON snapshots, and Selenium assertions verifying the additional fields.  
   - Planned commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.

## Current Increment – I23 Verbose trace diagnostic parity (planned)
- Enrich verbose trace payloads (application/REST/CLI/UI) with preview metadata (ATC, branch factor, height, mask length) and render the additional fields in the shared operator verbose console. 
- Update JSON fixtures, OpenAPI contracts, CLI/REST documentation, and Selenium assertions so every facade reports the same diagnostic context. 
- Planned commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon spotlessApply check`.

## Previous Increment – T3917 Console verbose toggle harmonization (completed 2025-11-02)
- Removed the EMV panel-specific `includeTrace` checkbox so evaluations and replays rely on the global verbose toggle shared across protocols.
- Updated `panel.html`, EMV console JavaScript helpers, and REST wiring to derive trace flags from the shared toggle while keeping CLI/REST contracts unchanged.
- Refreshed Selenium assertions to require the absence of EMV-specific toggles and verify verbose traces submit/withhold diagnostics through the global control.
- Added stored replay coverage for omitted/false `includeTrace` requests, lifting Jacoco to 2161/3087 branches (≈0.7000) and restoring a green `./gradlew --no-daemon spotlessApply check` run.

## Previous Increment – T3916 Verbose trace key redaction (completed 2025-11-02)
- Application trace model now emits `masterKeySha256` digests (`sha256:<hex>`) while preserving session key visibility; CLI/REST payloads and operator UI traces surface the digest, and replay traces wrap the shared `VerboseTracePayload` with a hashed master key field.
- Targeted suites executed: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :ui:test`. Selenium EMV suite (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`) remained red pending T3917 because the panel-specific include-trace checkboxes still existed.
- OpenAPI snapshots regenerated to capture the new `masterKeySha256` attribute on verbose traces; CLI/REST docs and tasks updated to record the change.

## Risks & Mitigations
- **Limited test vectors:** Mitigate by collaborating with the user for additional samples; enforce fixture-driven tests so new vectors drop in quickly.  
- **Secret leakage in traces:** Redact sensitive fields in telemetry frames and guard verbose traces behind request toggles, extending SHA-256 hashing to EMV master keys across all facades while confirming session keys remain visible only in verbose traces.  
- **UI toggle drift:** Removing EMV-specific controls must preserve `includeTrace` behaviour—add targeted UI/Selenium coverage so global toggle changes propagate to evaluation and replay requests.
- **Crypto implementation errors:** Use independent derivation verification (double-check with calculator outputs) and add property-based tests for mask application.

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
