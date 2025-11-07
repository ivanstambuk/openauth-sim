# Feature Plan 040 – EUDIW OpenID4VP Simulator

_Linked specification:_ `docs/4-architecture/specs/feature-040-eudiw-openid4vp-simulator.md`  
_Status:_ Ready  
_Last updated:_ 2025-11-06

## Vision & Success Criteria
- Demonstrate HAIP-aligned remote OpenID4VP exchanges end to end (request → wallet response → validation) without external wallets.
- Ship deterministic fixtures covering SD-JWT VC and ISO/IEC 18013-5 mdoc PID payloads, plus Trusted Authorities filtering.
- Provide REST, CLI, and operator UI surfaces with telemetry, traces, and documentation updates so operators can explore the simulator.
- Maintain Specification-Driven Development discipline: tests first, ≤30 minute increments, spotless/Gradle checks green after each step.

## Scope Alignment
- **In scope:** Verifier request builder, deterministic wallet responder, DCQL/Trusted Authorities evaluation, encryption path, fixture loaders, telemetry wiring, facade integrations (REST/CLI/UI).
- **Out of scope:** Same-device/DC-API flows, issuance (OpenID4VCI), persistent wallet state, dynamic trust federation resolution beyond the `aki`/`etsi_tl`/`openid_federation` filters delivered in this feature.

## Dependencies & Interfaces
- Extends `core` with DCQL, SD-JWT, and DeviceResponse helpers; requires optional JOSE/COSE libraries pending owner approval.
- Application services (`application.eudi.openid4vp`) orchestrate requests/responses and emit telemetry via `TelemetryContracts`.
- REST/CLI/UI facades consume the application service and share fixture loaders located under `docs/test-vectors/eudiw/openid4vp/`.
- Documentation touchpoints: roadmap, knowledge map, how-to guides, telemetry catalogue.

## Increment Breakdown (≤30 minutes; tests first)
0. **I0 – Trusted list ingestion foundation (F-040-18/25)**  
   - Add fixture metadata for ETSI Trust List entries and OpenID Federation identifiers; create loader utilities to parse local TL snapshots.  
   - Stage failing tests ensuring DCQL presets referencing `etsi_tl`/`openid_federation` resolve correctly.  
   - Commands: `./gradlew --no-daemon :core:test`.  

1. **I1 – Spec refinement & citation sync**  
   - Integrate GPT-5 Pro research (complete).  
   - Action: none (baseline done) – recorded for traceability.

2. **I2 – Fixture scaffolding & smoke tests (F-040-18/19, N-040-01)** – _Completed 2025-11-06_  
   - Added synthetic PID fixtures (SD-JWT + mdoc) and deterministic seed files.  
   - Introduced `PidFixtureSmokeTest` alongside existing trusted authority coverage to assert fixture presence and PID namespace coverage.  
   - Command: `./gradlew --no-daemon :core:test`.

3. **I3 – Verifier request builder (F-040-01/02/03/04/05/14)** – _Tests staged 2025-11-06; implementation pending_  
   - Added failing `OpenId4VpAuthorizationRequestServiceTest` cases covering DCQL enforcement, deterministic seed reuse, and telemetry expectations.  
   - Implement builder, JAR toggle, ASCII QR renderer, telemetry events `oid4vp.request.*`.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.

4. **I4 – Wallet simulator foundations (F-040-07/08/09/10/13)**  
   - Stage tests verifying VP Token shape, SD-JWT disclosure hashing, KB-JWT generation.  
   - Implement SD-JWT wallet path with deterministic salts/keys (loading synthetic issuer/holder keys), stub DeviceResponse loader, and wire inline credential inputs (preset vs manual + sample selector) to the generator; expose stored-mode seeding utilities.  
   - 2025-11-06: SD-JWT wallet service implemented per Option A (recompute disclosure hashes); telemetry + trace hashes now return from `OpenId4VpWalletSimulationService`. `:application:test` passes; full `spotlessApply check` still fails on pre-existing Feature 039 checkstyle path lookup.  
   - Commands: `./gradlew --no-daemon :core:test :application:test`, `./gradlew --no-daemon spotlessApply check`.

5. **I5 – mdoc DeviceResponse path (F-040-09/10/17)**  
   - Add failing tests for DeviceResponse verification and Claims Path Pointer mapping.  
   - Implement DeviceResponse adapter (using fixture CBOR and synthetic issuer certs) and hydrate inline DeviceResponse uploads alongside presets and sample vector selection; include HAIP encryption toggle scaffolding and stored-mode seeding data.  
   - Commands: `./gradlew --no-daemon :core:test :application:test`.

6. **I6 – Trusted Authorities + error handling (F-040-11/12/21)**  
   - Stage tests for Authority Key Identifier (`aki`) matching positives/negatives and OID4VP error mapping.  
   - Implement Trusted Authority evaluator, integrate with validation pipeline, produce problem-details responses.  
   - Commands: `./gradlew --no-daemon :application:test :core:test`, `./gradlew --no-daemon spotlessApply check`.

7. **I7 – Encryption enforcement (F-040-04/20, N-040-03)**  
   - Add failing tests for `direct_post.jwt` round-trip using fixture keys.  
   - Implement JWE encryption/decryption path (behind HAIP flag) and latency telemetry.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.

## Multi-Step Change Plan – Full Feature 040 Execution

The owner confirmed (2025-11-06) that the next delivery must cover the entire Feature 040 scope end to end. The change will ship as a sequence of ≤30 minute increments, each starting with red tests and finishing with `./gradlew spotlessApply check` green. Every step updates specs/plan/tasks/knowledge map as needed and records telemetry/trace adjustments.

1. **S1 – Authorization + SD-JWT wallet refactor (F-040-01/02/03/07/08/10/14)**  
   - Stage failing tests that capture QR rendering, request URIs, masked nonce/state traces, TelemetryContracts calls, inline-only wallet flows, Trusted Authority metadata propagation, and seed determinism.  
   - Refactor `OpenId4VpAuthorizationRequestService`/`OpenId4VpWalletSimulationService` into TelemetryContracts-aware services with typed collaborators (seed sequence, preset repository, Trusted Authority resolver, hash utility). Inline SD-JWT flows no longer require presets, and disclosure hashes are always recomputed per Option A.  
   - Commands: `./gradlew --no-daemon :application:test`, followed by `./gradlew spotlessApply check`.
   - _Updates 2025-11-06:_ T4023 completed: authorization service now emits request URI + QR telemetry, masks nonce/state with verbose trace copies, recomputes DCQL hashes, and supports inline signed requests without presets. `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestServiceTest"` and `./gradlew --no-daemon spotlessApply check` both succeeded (spotless run ≈6 min due to existing Feature 039 suites).
   - _Updates 2025-11-07:_ T4024/T4025 delivered SD-JWT wallet refactor tests + implementation covering inline-only submissions, Trusted Authority mismatch handling, disclosure hash recomputation, telemetry contract propagation, and preset-miss errors. `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationServiceTest"` (red-to-green) and the full `./gradlew --no-daemon spotlessApply check` (≈7m36s) both passed.

2. **S2 – mdoc wallet path (F-040-09/10/17/23)**  
   - Add failing tests around DeviceResponse parsing (CBOR diagnostics), Claims Path Pointer evaluation, inline uploads, and sample selector hydration.  
   - Implement `MdocWalletSimulationService` sharing Trusted Authority results, verifying COSE signatures, and exposing latency/trace metrics.  
   - Commands: `./gradlew --no-daemon :core:test :application:test`, `./gradlew spotlessApply check`.

3. **S3 – Trusted Authorities & error handling (F-040-11/12/21)**  
   - Red tests: Authority Key Identifier positive/negative matches, missing Trusted Authority policies, problem-details mapping for HAIP violations.  
   - Implement a reusable evaluator (DCQL policy lookup + friendly labels), wire into authorization/wallet/validation services, and surface telemetry fields (`trustedAuthorityMatch`, `akiPolicySource`).  
   - Commands: `./gradlew --no-daemon :application:test :core:test`, `./gradlew spotlessApply check`.

4. **S4 – Encryption enforcement (F-040-04/20/20a, N-040-03)**  
   - Tests assert `direct_post.jwt` JWE creation, decryption, error reporting, and HAIP toggle behaviour.  
   - Implement JOSE helpers (pending dependency approval already granted for Feature 040), plug latency metrics into telemetry, and extend traces with encryption verdicts.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew spotlessApply check`.

5. **S5 – Validation mode services (F-040-22/23/26)**  
   - Stage failing tests for stored vs inline VP Token selection, DCQL preview rendering, multi-presentation traces, success/error telemetry, and problem-details surfaces.  
   - Implement validation service that reuses wallet evaluators, Trusted Authority checker, and encryption verifier; ensure verbose trace payloads align with the spec’s matrix.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew spotlessApply check`.

6. **S6 – REST & CLI surfaces (F-040-15/16/30)**  
   - Red tests: MockMvc endpoints for request/wallet/validate/seed, OpenAPI snapshot additions, Picocli command integration, verbose flag parity.  
   - Implement controllers/DTOs/assemblers, update OpenAPI snapshots (`OPENAPI_SNAPSHOT_WRITE=true`), add CLI commands with output parity, and refresh telemetry catalog references.  
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test :cli:test`, `./gradlew spotlessApply check`.

7. **S7 – Operator UI integration (F-040-17/21/24/25/29/30)**  
   - Red Selenium/JS tests for Evaluate vs Replay tabs, Trusted Authority labels, DCQL read-only preview, sample selector autofill, trace dock usage, result-card VP Token rendering, and deep-link param hydration.  
   - Implement Thymeleaf templates, JS controllers, and CSS updates; ensure trace dock receives EUDIW payloads without local verbose toggles.  
   - Commands: `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`, `./gradlew spotlessApply check`.

8. **S8 – Fixture ingestion toggles, docs, and verification (F-040-18/19/25/31, N-040-04)**  
   - Red tests for synthetic vs conformance fixture selection, provenance metadata capture, and telemetry outputs.  
   - Implement ingestion toggles + stored presentation seeding endpoints, refresh roadmap/knowledge map/how-to guides, run analysis gate + full Gradle quality gate, and record drift report closure.  
   - Commands: `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test`, `./gradlew spotlessApply check` followed by `./gradlew qualityGate` if required by governance.

Each step references/updates the corresponding tasks in `docs/4-architecture/tasks/feature-040-eudiw-openid4vp-simulator.md`. Steps S1–S8 must finish with docs synced (spec, plan, tasks, knowledge map, roadmap) plus telemetry catalog updates where applicable.

8. **I8 – Validate mode scaffolding (F-040-22)**  
   - Stage failing tests for paste/upload VP Token validation via application services (inline JSON vs stored presets, positive + error cases). Verify DCQL preview renders read-only JSON under selector.  
   - Implement validation entry point reusing verification pipeline, emit `oid4vp.response.validated`/`failed` events, support inline/stored selectors, sample dropdown, and ensure error mapping aligns with F-040-12.  
   - Commands: `./gradlew --no-daemon :application:test :core:test`, `./gradlew --no-daemon spotlessApply check`.

9. **I9 – REST & CLI integrations (F-040-15/16)**  
   - Stage failing MockMvc and Picocli tests covering request creation, wallet simulate, validate flows, verbose toggles, and problem-details errors.  
   - Implement controllers, DTOs, CLI commands, regenerate the OpenAPI snapshot, and keep REST/CLI payloads aligned with the spec JSON samples.  
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test :cli:test`.

9a. **I9a – Operator UI layout & baseline banner (F-040-17/21/24/25/29)**  
   - Introduce failing JS + Selenium tests asserting two-column layout, baseline banner visibility when profile ≠ HAIP, DCQL preview read-only behaviour, and sample selector autofill.  
   - Implement Evaluate/Replay panels to honour the shared layout, render the banner, surface Trusted Authority labels, and keep forms synced with stored presets.  
   - Commands: `./gradlew --no-daemon :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`.

9b. **I9b – Trace dock & multi-presentation parity (F-040-22/22a/26/30)**  
   - Add failing UI and application tests verifying global trace dock integration, absence of per-panel verbose toggles, and multi-presentation collapsible sections with copy controls and matching trace keys.  
   - Implement trace payload wiring, result/trace field separation, and copy/download controls; confirm telemetry/trace content matches the specification matrix.  
   - Commands: `./gradlew --no-daemon :application:test :ui:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`.

10. **I10 – Fixture ingestion toggle (F-040-18, N-040-04)**  
   - Add tests covering synthetic vs conformance fixture selection, provenance metadata capture, and telemetry redaction.  
   - Implement loader abstraction, configuration toggles, and documentation updates.  
   - Commands: `./gradlew --no-daemon :core:test :application:test`, `./gradlew --no-daemon spotlessApply check`.

11. **I11 – Documentation & close-out (All requirements)**  
    - Update roadmap, knowledge map, how-to guides, telemetry catalogue, and task checklist.  
    - Run full pipeline: `./gradlew --no-daemon :application:test :cli:test :core:test :rest-api:test :ui:test spotlessApply check`.  
    - Confirm tasks closed, analysis gate completed, and follow-ups logged.

## Risks & Mitigations
- **Specification drift**: keep references current; re-run analysis gate after each major clarification.  
- **Crypto dependency footprint**: evaluate JOSE/COSE libraries; if owner approval denied, document reduced functionality and fallback strategy.  
- **Fixture authenticity**: clearly label synthetic vs conformance vectors; maintain provenance metadata to prevent confusion.  
- **Telemetry privacy**: review event payloads for PII; add regression tests ensuring redaction toggles work.

## Quality & Tooling Gates
- Run `./gradlew --no-daemon spotbugsMain spotbugsTest` after changes to enforce the Feature 015 dead-state detectors (`URF`, `UWF`, `UUF`, `NP`) across modules touched during the increment.  
- Each increment concludes with `./gradlew --no-daemon spotlessApply check`; targeted module test commands remain listed with their tasks above.  
- Capture telemetry payload diffs via existing snapshot tests and document any deviations before implementation continues.

## Analysis Gate (2025-11-06)
- ✅ Specification completeness – Overview, goals, requirements F-040-01…F-040-31 documented; clarifications up to 2025-11-01 captured; Operator UI ASCII mock-ups included.  
- ✅ Open questions review – `docs/4-architecture/open-questions.md` has no entries for Feature 040.  
- ✅ Plan alignment – Plan links to the Feature 040 spec/tasks and mirrors scope/dependencies noted in the specification.  
- ✅ Tasks coverage – T3999–T4021 map to the functional requirements (IDs referenced per task), stage tests before implementation, and keep increments ≤30 minutes.  
- ✅ Constitution compliance – Workflow preserves spec-first, clarification gate, test-first cadence, and straight-line increments via dedicated helpers.  
- ✅ Tooling readiness – Commands (`./gradlew --no-daemon :core:test`, `spotlessApply check`, `spotbugsMain spotbugsTest`, etc.) documented; telemetry snapshot notes retained.  
- Outcome: proceed to implementation once initial failing tests are staged (starting with T3999/T4001).

## Implementation Drift Gate (Pending)
- To be completed after all tasks finish and the build passes; capture drift findings, coverage confirmation, and lessons learned.

## Exit Criteria
- REST/CLI/UI demonstrate remote OpenID4VP exchange for SD-JWT VC and mdoc fixtures under deterministic seeds.
- Encryption path validated and telemetered; Trusted Authorities filtering operational with documented follow-ups for future enhancements.
- Synthetic vs conformance fixture toggles functional with provenance tracking.
- Roadmap, knowledge map, and supporting docs updated; all tests (`spotlessApply check`, module suites) passing.
