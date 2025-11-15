# Feature 005 Plan – EMV/CAP Simulation Services

_Linked specification:_ `docs/4-architecture/features/005/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/005/tasks.md`  
_Status:_ In review  
_Last updated:_ 2025-11-13

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

## Implementation Drift Gate
- **Checkpoint date:** 2025-11-09 (T3952).
- **Traceability review (R1–R10):** Confirmed every specification branch is represented by executable coverage. `TraceSchemaAssertions`, `EmvCapEvaluationApplicationServiceTest`, `EmvCapReplayApplicationServiceTest`, `EmvCapEvaluationEndpointTest`, `EmvCapReplayEndpointTest`, `EmvCapReplayServiceTest`, and the Picocli suites (`EmvCliTest`, `EmvCliEvaluateStoredTest`, `EmvCliReplayTest`) lock the core/application/REST/CLI behaviours required by R1–R4 & R7–R9, including stored vs. inline parity, preview-window math, telemetry redaction, and includeTrace toggles. Node + Selenium harnesses (`rest-api/src/test/javascript/emv/console.test.js`, `EmvCapOperatorUiSeleniumTest`) enforce the UI layout rules in R5/R10 (mode selector placement, fieldset isolation, Replay CTA spacing, and verbose-trace dock alignment).
- **Verbose trace schema (R2.4):** `EmvCapTraceProvenanceSchema`, `EmvCliTraceAssertions`, and the new provenance fixture assert all six provenance sections plus digest redaction semantics. Documented the dual-fixture requirement: keep `docs/test-vectors/emv-cap/trace-provenance-example.json` and `rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json` in sync so application components, REST OpenAPI snapshots, Node tests, and Selenium stubs all consume the same contract until a future fixture-sync automation task lands.
- **Quality evidence:** Reran `./gradlew --no-daemon spotlessApply check` after the documentation updates to prove the repo stays green atop the T3949 full-gate run (see `_current-session.md` for the full command log captured earlier today).
- **Documentation sync:** Updated the roadmap, operator UI how-to guide, Feature 005 tasks checklist, and `_current-session.md` with the drift-gate outcome plus the fixture duplication guardrail so downstream teams inherit the operating notes.
- **Outcome:** No drift identified; the EMV/CAP provenance work captured in this feature stands ready for acceptance while downstream features can resume at T4018 now that the trace provenance schema, docs, and fixtures align across every facade.
- **Report (2025-11-05):**
  - **Reviewers:** Codex (GPT-5); Preconditions: all EMV/CAP provenance tasks under Feature 005 complete with a green `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` (2025-11-05T20:40Z log).
  - **Spec alignment:** R5.4 (stored-mask sanitisation), R6.3–R6.4 (credential directory), and R7.2/R8.1 (stored replay hydration) now cite concrete code/tests: `rest-api/src/main/resources/static/ui/emv/console.js`, `EmvCapOperatorUiSeleniumTest`, `rest-api/src/test/javascript/emv/console.test.js`, `EmvCapCredentialDirectoryController{,Test}`, and replay Selenium coverage. Inline overrides + stored hydration flows remain covered in both JS and Selenium tests.
  - **Coverage:** Success, validation, and failure branches span REST, CLI, JS, and Selenium harnesses with digest/length assertions. No divergences identified; sanitisation helper pattern recommended for Feature 026. Artifacts synced across feature plan/tasks, knowledge map, and `_current-session.md`.

## Provenance Field Mapping (T-005-24a)

Spec R2.4 plus `EmvCapTraceProvenanceSchema` define a six-section provenance payload. Capturing the lineage here keeps future increments aligned without rereading `TraceAssembler` every time.

| Provenance section | Field derivation | Validation anchors |
|--------------------|------------------|--------------------|
| **Protocol Context** | `TraceAssembler.protocolContext` fuses the inbound `EmvCapMode` (driving `profile` + `mode`) with constants (`EMV_VERSION`, `AC_TYPE`, `CID_LABEL`) and `IssuerProfile` metadata (`issuerPolicyId`, `issuerPolicyNotes`). | `EmvCapEvaluationApplicationServiceTest`/`EmvCapReplayApplicationServiceTest` assert all fields; CLI/REST JSON-parity tests keep serialized output identical. |
| **Key Derivation** | Pulls master-key length/digest from `EvaluationRequest.masterKeyHex()`, masked PAN/PSN digests from `IssuerProfiles.resolve(masterKey)`, ATC/IV from the request, and session-key bytes/digest from `EmvCapResult.sessionKeyHex()`. | `EmvCapTraceProvenanceSchema.missingFields` (consumed by `EmvCliTraceAssertions` and application tests) fails fast if any field drops. |
| **CDOL Breakdown** | Schema (tag order + lengths) comes from `EvaluationRequest.cdol1Hex()`, while offsets/raw hex derive from `EmvCapResult.generateAcInput().terminalHex()`; `decodeCdolField` stamps decoded labels. | MockMvc JSON snapshots (`EmvCapEvaluationEndpointTest`, `EmvCapReplayEndpointTest`) plus CLI replay tests ensure entries/arrays render exactly. |
| **IAD Decoding** | `TraceAssembler.iadDecoding` slices `EvaluationRequest.issuerApplicationDataHex()` into `cvr`, optional `issuerActionCode`, and the derived `cdaSupported` boolean. | Application/REST suites compare values against fixtures; UI tests only assert section presence to avoid duplicating server assertions. |
| **MAC Transcript** | Uses constants for algorithm/padding/IV/block count; `EmvCapResult.generateAcResultHex()` supplies `generateAcRaw` and the CID byte that yields `cidFlags`, while placeholder block labels document B0…B10. | CLI/REST snapshots plus operator-console verbose-trace assertions keep CID decoding + block metadata in sync. |
| **Decimalization Overlay** | `buildDecimalizationSourceHex(request.atc, generateAc)` concatenates ATC + AC segments; ISO-0 decimalization and `result.bitmaskOverlay()/maskedDigitsOverlay()` produce `sourceDecimal`, `overlaySteps`, OTP, and digit count. Optional overrides flow from `IssuerProfile.decimalizationOverride()`. | Application trace tests verify the math; Node + Selenium suites ensure overlay labels render; fixture diffs catch format drift. |

Golden fixtures: keep `docs/test-vectors/emv-cap/trace-provenance-example.json` and `rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json` synchronized; `EmvCapTraceProvenanceSchema` loads the repo-root copy so every module/CLI test references the same canonical payload.

### Surface verification plan (pre-implementation)

1. **Core/Application:** Extend `EmvCapEvaluationApplicationServiceTest`/`EmvCapReplayApplicationServiceTest` plus helper coverage before wiring behaviour; target command `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*Trace*"` (add `:core:test` if schema helpers change).
2. **REST facade:** Update `EmvCapEvaluationEndpointTest`, `EmvCapReplayEndpointTest`, and regenerate OpenAPI via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` once DTOs emit provenance.
3. **CLI:** Keep `EmvCliEvaluateCommand`, `EmvCliReplayCommand`, and `EmvCliTraceAssertions` aligned; rerun `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*Emv*Trace*"` after mapper updates.
4. **Operator UI / JS:** Refresh `rest-api/src/main/resources/static/ui/emv/console.js`, `static/ui/shared/verbose-trace.js`, and `rest-api/src/test/javascript/emv/console.test.js`, then rerun Selenium via `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.
5. **Fixture & doc sync:** Update both provenance fixtures in lockstep, run `rg "trace-provenance-example" -n docs rest-api/docs` to double-check references, and log the edits in `_current-session.md` per Feature 011 governance guidance.

## Increment Map
The active increment batch (I46–I50b) focuses on UI parity and the verbose trace provenance schema. Historical increments (I1–I45, T3916/T3917, early breakdown entries) now live in the appendix under `## Follow-ups / Backlog` to keep this section concise.

### Current increments (I46–I50b)
- **I46 – Preview-offset helper copy cleanup (completed 2025-11-08)**  
  - Removed the redundant preview helper sentence from Evaluate/Replay panels so the layout matches other protocols.  
  - Confirmed no automation relied on the copy during the aggregate `./gradlew --no-daemon spotlessApply check` run (600 s timeout).
- **I47 – Evaluate-mode helper text removal (completed 2025-11-08)**  
  - Dropped inline hints about inline vs. stored behaviour, removed the `data-testid="emv-stored-empty"` hook, and simplified console JS copy rotation.  
  - Updated Node + Selenium suites to assert the helper text stays absent; reran the aggregate spotless/Gradle gate (600 s timeout).
- **I48 – Replay CTA spacing parity (completed 2025-11-08)**  
  - Captured requirement R5.8 and applied the shared `stack-offset-top-lg` helper to Replay action bars so Evaluate/Replay CTAs stay aligned.  
  - Added Node assertions + Selenium coverage for both action bars, executed `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and deferred the aggregate spotless run per session guidance.
- **I49 – Input helper copy removal (completed 2025-11-08)**  
  - Removed Identify/session-derivation helper text, cleaned up `console.js`, and ensured accessibility hooks no longer point to missing nodes.  
  - Commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
- **I50 – Verbose trace provenance expansion (completed 2025-11-09)**  
  - Documented the six-section `trace.provenance` schema (R2.4) and locked fixtures/helpers (`docs/test-vectors/emv-cap/trace-provenance-example.json`, `EmvCapTraceProvenanceSchema`).  
  - Added red tests across application, REST, CLI, Node, and Selenium harnesses to demand the schema while provenance wiring was pending.  
  - Implementation work (I50b) wired provenance through every facade, regenerated OpenAPI snapshots, refreshed console assets, and reran `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` plus Node/Selenium suites.
- **I50a – Verbose trace provenance red tests (in progress 2025-11-09)**  
  - Introduced canonical fixtures and schema helpers, added application/REST/CLI assertions that currently fail until provenance wiring lands, and updated Node/Selenium harnesses to expect the richer payloads.  
  - Next step: land I50b implementation and keep fixtures authoritative for future schema updates.
- **I50b – Completion notes (2025-11-09)**  
  - Mirrored fixtures into `rest-api/docs/test-vectors/...`, ensured verbose traces remain opt-in for inline requests, and updated console assets to render all six provenance sections.  
  - Reran `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"` plus discrete module/test/PMD/spotless runs to keep provenance changes audited.
- **I50b – Work breakdown (T3949)**  
  1. **Schema audit & planning sync (≤30 min):** Expand plan/tasks with provenance mapping + command expectations.  
  2. **Core/application provenance builder (≤90 min):** Extend verbose trace assembly so evaluation/replay services emit the schema, enforce via unit tests.  
  3. **REST DTOs & controllers (≤90 min):** Wire provenance through endpoints, refresh OpenAPI snapshot/tests.  
  4. **CLI/console integration (≤90 min):** Update Picocli JSON outputs, Node harness fixtures, and Selenium DOM assertions.  
  5. **Docs + telemetry sync (≤30 min):** Update how-to guides, telemetry catalogue, roadmap/knowledge map, and rerun spotless/quality gates.  
  6. **Validation sweep (≤30 min):** Capture commands + screenshots in `_current-session.md`, ensure Jacoco/JUnit deltas recorded.
- **Upcoming – Single-line IV/IPB/IAD inputs (I46 follow-up)**  
  - Convert Evaluate/Replay IV, Issuer Proprietary Bitmap, and Issuer Application Data fields to `<input>` controls, keep stored-mode masking scoped, and align Replay markup.  
  - Update JS fixtures, DOM helpers, Node tests, and Selenium coverage; rerun `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, and `./gradlew --no-daemon spotlessApply check`.

Historical increment notes (I1–I45, T3916/T3917, sample spacing tasks) reside under `## Follow-ups / Backlog` → `### Historical increment appendix`.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-005-01 | I0–I19, T3932–T3935 | Deterministic evaluate flows across core/application/REST/CLI/UI. |
| S-005-02 | I38–I40, T3931 | Operator UI inline presets stay editable when loading vectors. |
| S-005-03 | I37, T3933–T3934 | Stored workflows hide secrets, rely on digests, and hydrate server-side. |
| S-005-04 | I19, seeding increments | MapDB seeding + credential directory parity. |
| S-005-05 | I18, I19A | Replay journeys highlight match deltas + mismatch diagnostics. |
| S-005-06 | I18, UI JS tasks | Preview offset controls + validation errors across facades. |
| S-005-07 | I41–I45 | Console layout parity (field grouping, spacing, CTA alignment). |
| S-005-08 | I50/I50a | Verbose trace schema + provenance rendering. |
| S-005-09 | I34–I35, sample-vector tasks | Sample selector spacing + styling parity. |
| S-005-10 | REST/CLI doc tasks | Contract/docs parity, includeTrace handling, telemetry redaction guidance. |

## Analysis Gate
- **Reviewed:** 2025-11-02 – Replay expansion review (spec/plan/tasks updated; implementation gated behind new red tests)
- **Checklist status:**
  - Specification completeness – Feature spec reflects the latest requirements, resolved questions, and operator UI ASCII mock-ups directly in its main sections (PASS).
  - Open questions – Log remains empty; any future high- or medium-impact questions must be tracked in `docs/4-architecture/open-questions.md` and, once resolved, reflected in spec sections (with ADRs added for architectural decisions) (PASS).
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

## Follow-ups / Backlog
1. **Fixture expansion (future):** Capture additional hardware transcripts beyond the six shipped vectors when new calculator inputs surface and fold them into regression tests.  
2. **Advanced diagnostics (optional):** Investigate tiered verbose trace controls or additional telemetry analytics once EMV/CAP parity ships.  
3. **Hardware exploration (optional):** Revisit APDU/card-emulation scope if future requirements demand it.

### Historical increment appendix
#### Increment Breakdown (≤30 minutes each)
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
    - Task linkage: T-005-67 stages Node/Selenium coverage for the shared console toggle, and T-005-68 wires the shared console plumbing plus REST/CLI propagation.
    - **Status (2025-11-14):** Inline replay payloads now set `includeTrace` based on the shared verbose toggle, and CLI/REST/application/Selenium/Node suites plus the full Gradle gate were rerun (see T-005-68 verification log) to confirm the shared console plumbing holds.
    - **Clarification (2025-11-15):** Evaluate and Replay submissions both read the single “Enable verbose tracing” toggle. Stored and inline replay payloads must copy that toggle state into `includeTrace` so REST + CLI traces only render when operators expect them, keeping the shared `VerboseTraceConsole` hidden whenever no trace returned.
    - **2025-11-15 note:** Staged replay-mismatch fixtures (`docs/test-vectors/emv-cap/replay-mismatch.json`, FX-005-04) and red tests (T-005-71) asserting `expectedOtpHash` + `mismatchReason` telemetry fields across application/REST/CLI layers are now satisfied by the replay mismatch telemetry wiring (T-005-73); UI placeholder coverage (T-005-72) remains skipped/disabled until the banner surfaces hashed OTP guidance.

11. **I9 – Documentation & final verification (full-scope)**  
   - Refresh how-to guides (REST, CLI, operator UI), knowledge map, roadmap, and OpenAPI snapshots; capture persistence guidance.  
   - Run full Gradle quality gate (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).  
   - Update `_current-session.md` and close outstanding follow-ups.  
   - Task linkage: T-005-69 handles the documentation + roadmap/knowledge-map refresh, while T-005-70 captures the verification run and session log updates.
   - **Clarification (2025-11-15):** Documentation updates must explicitly call out the shared verbose trace console, the cross-facade `includeTrace` toggle semantics (Evaluate + Replay + CLI flags), and the provenance fixture dual-storage requirement before the final verification sweep.

12. **I10 – Replay scaffolding & red tests (complete 2025-11-15)**  
   - Updated regression fixtures with replay/mismatch cases and staged failing unit/integration tests across application, REST, CLI, and UI layers to capture stored/inline replay expectations.  
   - Added Selenium placeholders for replay tab interactions, then activated them once replay telemetry and mismatch diagnostics landed.  
   - Commands used while staging and driving red coverage to green included: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.EmvCapReplayApplicationServiceTest.storedReplayMismatchTelemetryIncludesExpectedOtpHash"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayEndpointTest.storedReplayMismatchIncludesOtpHash"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.EmvCapReplayServiceTest.metadataIncludesExpectedOtpHash"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCliReplayTest.inlineReplayMismatchReturnsMismatchStatus"`, `node --test rest-api/src/test/javascript/emv/console.test.js`, and targeted Selenium runs for `EmvCapOperatorUiSeleniumTest.replayMismatchDisplaysDiagnosticsBanner`.  
   - Task linkage: T-005-71 captured fixture updates plus backend/CLI red tests, T-005-72 staged the UI/Node/Selenium placeholders for replay interactions, and T-005-73/T-005-74 delivered the replay mismatch telemetry/metadata wiring and banner coverage required by TE-005-05.

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

#### Previous Increment – I37 Stored mode label hiding (completed 2025-11-06)
- Restored full-container toggling in `console.js` so stored mode applies `hidden`/`aria-hidden="true"` to the CDOL1, issuer bitmap, ICC template, issuer application data field groups, and the mask wrappers themselves. Switching back to inline mode removes those attributes and re-renders labels and helper copy.
- Extended `.emv-stored-mode` rules in `console.css` to hide the same containers to guard against stale markup, keeping inline mode unaffected.
- Refreshed Node and Selenium expectations so stored preset flows assert the absence of sensitive rows while inline mode verifies all inputs remain editable. Validation commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon spotlessApply check --console=plain`.

#### Previous Increment – I38 Customer input inline grouping (completed 2025-11-06)
- Updated the operator console templates to group the mode radios with Challenge/Reference/Amount inputs beneath a single “Input from customer” legend for Evaluate and Replay panels, and tightened CSS spacing.
- Taught `console.js` to apply mode-driven enablement (Identify clears and disables all inputs, Respond enables only Challenge, Sign enables Reference/Amount while keeping Challenge masked), while keeping serialized payloads aligned and hint text mode-aware.
- Extended JS console tests to cover the new grouping/aria semantics and adjusted Selenium coverage—inline Sign replay asserted via TODO pending T3936 inline preset hydration; legacy scenario temporarily disabled to avoid false negatives.
- Verification commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, and `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.

#### Previous Increment – I39 Inline preset full hydration (completed 2025-11-08)
- Hardened the Node-based console harness so it waits for credential summaries before dispatching preset change events; this mirrors the real UI flow and guarantees inline Evaluate/Replay hydration receives the sensitive defaults (master key, CDOL1, IPB, ICC template, issuer application data, and Sign-mode customer inputs) before assertions run.
- Verified the Sign replay Selenium scenario remains green by re-running `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, then exercised the full verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test` (initial run exposed the long-standing HtmlUnit FIDO2 preset-label flake; rerunning the focused FIDO2 class followed by the `./gradlew --no-daemon spotlessApply check` pipeline produced a green `:rest-api:test`)
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`
- Result: inline Evaluate/Replay preset hydration is stable, Sign replay Selenium coverage is back on, and T3937/T3938 can now proceed.

#### Previous Increment – I45 Input-from-customer row layout (completed 2025-11-08)
- Added R5.7 to the specification and refreshed this plan/tasks to capture the condensed layout requirement.
- Reworked the Evaluate + Replay templates so each mode renders as its own row with the radio + label left-aligned and its related inputs (Respond + Challenge, Sign + Reference/Amount) on the same horizontal track while Identify shows a placeholder (still one shared set of inputs with the existing `data-field` hooks).
- Introduced `.emv-customer-row`, `.emv-customer-fields`, and refreshed `.emv-customer-grid` styling so the radios/labels remain vertically aligned with consistent spacing while stored-mode masking continues to leave the inputs visible (just disabled per mode).
- Extended the Node console tests to assert the single-set structure and updated the Selenium suite to confirm there is exactly one challenge/reference/amount group per panel plus Sign-mode enablement coverage.
- Verification: `node --test rest-api/src/test/javascript/emv/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.

#### Previous Increment – I40 Card transaction grouping (completed 2025-11-08)
- Added a dedicated “Transaction” fieldset beneath Card configuration for Evaluate and Replay panels, stacking the ICC payload template and Issuer Application Data inputs while surfacing the mandated helper copy (`"xxxx" is replaced by the ATC before the ICC payload template is evaluated.`).
- Confirmed the existing `.emv-transaction-block` styling keeps the new group aligned with adjacent fieldsets and relied on the sensitive-field toggles to keep ICC template/IAD containers hidden in stored mode while leaving the helper hint visible.
- Refreshed the Node console tests to assert ICC template containers/masks honour stored-mode hiding, and expanded the EMV Selenium suite to verify the new legend, helper text, and textarea presence for both Evaluate and Replay flows.
- Verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

#### Previous Increment – I41 Session key derivation grouping (completed 2025-11-08)
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

#### Previous Increment – I42 Branch factor & height row alignment (completed 2025-11-08)
- Added Selenium assertions to ensure the Evaluate and Replay forms expose a dedicated wrapper row that contains both Branch factor and Height inputs.
- Wrapped the two inputs inside `.emv-session-pair-row` containers with new `data-testid` hooks and introduced CSS to keep the row full width while stored mode still hides only secret-bearing fields.
- Verification commands:
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check` (first attempt hit the 300 s timeout; reran with a longer timeout and it passed)

#### Previous Increment – I43 Session key master key/ATC width (completed 2025-11-08)
- Wrapped the ICC master key textarea + secret mask and the ATC input inside a new `.emv-session-master-row`/`.emv-session-master-column` container for Evaluate and Replay templates so both fields stretch edge-to-edge like the branch/height pair while stored mode still hides only the master key field group.
- Added shared `.emv-session-row` (grid-column span) and `.emv-session-master-row`/`.emv-session-master-column` CSS helpers so the new row matches the existing session block styling across responsive breakpoints.
- Extended Selenium coverage with `assertMasterAtcRow` to confirm the row structure and ensure both master key and ATC inputs live inside the dedicated wrapper; added JS unit assertions so stored mode continues to hide only the master key container while ATC remains interactive.
- Verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

#### Previous Increment – I44 Card configuration isolation (completed 2025-11-08)
- Split the Evaluate and Replay templates so `.emv-card-block` now contains only CDOL1/IPB textareas while `.emv-transaction-block` and `.emv-customer-block` render as sibling fieldsets; added XPath-based Selenium assertions to guarantee the transaction block is a following sibling and that card borders never wrap customer inputs.
- Added a panel-template regression test to the Node console suite that reads `panel.html` directly and fails if the card fieldsets ever include the transaction or customer sections again, satisfying spec requirement R5.6 without introducing a DOM parser dependency.
- Expanded the Selenium coverage to assert the new hierarchy for both evaluate and replay flows (card block free of nested fieldsets, transaction legend/hints intact) while keeping the existing stored-mode masking checks green.
- Verification matrix:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`

#### Previous Increment – I45 Single-line session key inputs (completed 2025-11-08)
- Converted the ICC master key control from a textarea to a single-line text input so the master key and ATC sit on the same row without vertical scrollbars, matching R5’s inline requirement.
- Ensured the `.secret-mask` wrapper still hides only the master key column in stored mode while the ATC column remains interactive; refreshed CSS helpers so the new markup keeps the existing layout widths.
- Updated console JavaScript fixtures, Node unit tests, and Selenium assertions so they look for an `<input>` element (not a textarea) for `#emvMasterKey` while preserving validation rules.
- Verification commands:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

#### Upcoming Increment – I46 Single-line IV, IPB, and issuer application inputs
- Swap the Evaluate form’s Initialization Vector, Issuer Proprietary Bitmap, and Issuer Application Data controls from `<textarea>` elements to single-line `<input>` controls (CDOL1 + ICC template stay textareas) while keeping stored-mode masking scoped to the sensitive columns.
- Apply the same single-line treatment to the Replay panel (`#emvReplayIssuerBitmap`, `#emvReplayIssuerApplicationData`) so both panels remain aligned with spec R5.5/R5.6.
- Update console JavaScript fixtures, DOM helpers, Node unit tests, and Selenium coverage to expect the new markup and assert the fields stay editable in inline mode yet hidden in stored mode. Ensure the replay console harness now selects `input` nodes for the issuer bitmap/application fields when wiring sensitive-field masks.
- Rerun the target verification commands:
  - `node --test rest-api/src/test/javascript/emv/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon spotlessApply check`

#### Previous Increment – I34 Inline sample vector mode persistence (completed 2025-11-05)
- Added JS unit coverage (`node --test rest-api/src/test/javascript/emv/console.test.js`) and Selenium assertions to prove inline mode stays active after preset selection while masks/seed actions remain hidden.
- Updated `console.js` to drop the automatic `stored` switch, refreshed inline evaluation hints, and kept stored-mode behaviour intact when explicitly selected.
- Validation commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.

#### Previous Increment – I35 Inline preset hydration (completed 2025-11-05)
- Updated `console.js` to attach the selected preset `credentialId` to inline submissions so masked secrets fall back to MapDB while preserving editable fields.
- Added Node tests asserting fallback payloads (blank secrets, explicit overrides) and Selenium coverage that evaluates presets in inline mode without triggering stored mode.
- Validation commands: `node --test rest-api/src/test/javascript/emv/console.test.js`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :ui:test`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon spotlessApply check`.

#### Implementation Drift Gate Report (2025-11-05)
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

#### Previous Increment – I28 Evaluate sample vector spacing refinement (completed 2025-11-04)
- Collapsed the Evaluate sample vector block so preset selection and seed controls mirror the Replay layout, introduced inline spacing styles, and reran targeted Selenium/UI checks alongside `spotlessApply check`.

#### Previous Increment – I27 Sample vector spacing parity (completed 2025-11-04)
- Applied the shared `stack-offset-top-lg` spacing helper to the Evaluate and Replay sample vector containers so the EMV panel’s vertical rhythm matches HOTP/TOTP/FIDO2 layouts.  
- Added Selenium assertions guarding the helper on both Evaluate and Replay preset blocks, then reran `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"` and `./gradlew --no-daemon spotlessApply check` (both green).

#### Previous Increment – T3917 Console verbose toggle harmonization (completed 2025-11-02)
- Removed the EMV panel-specific `includeTrace` checkbox so evaluations and replays rely on the global verbose toggle shared across protocols.
- Updated `panel.html`, EMV console JavaScript helpers, and REST wiring to derive trace flags from the shared toggle while keeping CLI/REST contracts unchanged.
- Refreshed Selenium assertions to require the absence of EMV-specific toggles and verify verbose traces submit/withhold diagnostics through the global control.
- Added stored replay coverage for omitted/false `includeTrace` requests, lifting Jacoco to 2161/3087 branches (≈0.7000) and restoring a green `./gradlew --no-daemon spotlessApply check` run.

#### Previous Increment – T3916 Verbose trace key redaction (completed 2025-11-02)
- Application trace model now emits `masterKeySha256` digests (`sha256:<hex>`) while preserving session key visibility; CLI/REST payloads and operator UI traces surface the digest, and replay traces wrap the shared `VerboseTracePayload` with a hashed master key field.
- Targeted suites executed: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.emv.cap.*"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.EmvCli*"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.emv.cap.*"`, `./gradlew --no-daemon :ui:test`. Selenium EMV suite (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`) remained red pending T3917 because the panel-specific include-trace checkboxes still existed.
- OpenAPI snapshots regenerated to capture the new `masterKeySha256` attribute on verbose traces; CLI/REST docs and tasks updated to record the change.

#### Assumptions & Risks
- **Assumptions:**
  - Transcript fixtures (`docs/test-vectors/emv-cap/*.json`) remain authoritative and MapDB seeding stays in sync with their schema.
  - Shared telemetry/trace infrastructure (TelemetryContracts, VerboseTraceConsole) remains available to all modules.
  - Operator console tooling (Node harness + Selenium) continues to run in CI/local environments.
- **Risks / Mitigations:**
  - **Limited test vectors:** Coordinate with the owner for new samples; enforce fixture-driven tests so additional vectors drop in quickly.
  - **Secret leakage in traces:** Redact sensitive fields in telemetry frames and guard verbose traces behind request toggles; extend SHA-256 hashing to EMV master keys while leaving session keys visible only in verbose traces.
  - **UI toggle drift:** Removing EMV-specific controls must preserve `includeTrace` behaviour—add targeted UI/Selenium coverage so global toggle changes propagate to evaluation and replay requests.
  - **Crypto implementation errors:** Use independent derivation verification (calculator outputs) and add property-based tests for mask application and MAC derivation.

#### Quality & Tooling Gates
- Default gate: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`.
- UI harness: `node --test rest-api/src/test/javascript/emv/console.test.js` plus `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EmvCapOperatorUiSeleniumTest"`.
- CLI/REST OpenAPI parity: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.
- Persistence hygiene: run `./gradlew --no-daemon :infra-persistence:test` whenever seeding logic changes.
