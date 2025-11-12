# Feature 006 Plan – EUDIW OpenID4VP Simulator

_Linked specification:_ `docs/4-architecture/features/006/spec.md`  
_Status:_ Ready for implementation  
_Last updated:_ 2025-11-11
_Renumbering note:_ Batch P2 migrated this plan from Feature 040. The legacy copy was imported inline on 2025-11-11, so the
`docs/4-architecture/features/006/legacy/040/` directory can be removed after verification (Git history preserves it).
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

## Assumptions & Risks
- **Assumptions:**
  - Fixture catalog (synthetic PID SD-JWT + mdoc) remains available via docs/test-vectors and is kept in sync with spec DSL.
  - HAIP encryption libraries (Nimbus JOSE + COSE) are approved or stubs are provided before implementation.
  - Trusted Authority metadata (AKI snapshots) can be versioned locally without live network access.

- **Specification drift**: keep references current; re-run analysis gate after each major clarification.  
- **Crypto dependency footprint**: evaluate JOSE/COSE libraries; if owner approval denied, document reduced functionality and fallback strategy.  
- **Fixture authenticity**: clearly label synthetic vs conformance vectors; maintain provenance metadata to prevent confusion.  
- **Telemetry privacy**: review event payloads for PII; add regression tests ensuring redaction toggles work.

## Implementation Drift Gate
- **Status:** Pending – trigger when T-040-01–T-040-23 plus deferred follow-ups reach ✅ and the full Gradle stack is green.
- Evidence package:
  - Map every FR/NFR and scenario ID to code/test references inside an appendix table stored with this plan (re-use the Scenario Tracking grid as the source of truth, add code pointers).
  - Attach screenshots or JSON snippets for telemetry/trace parity (REST/CLI/UI) to demonstrate sanitized fields, hashed payloads, and Trusted Authority verdict alignment.
  - Capture coverage deltas from `jacocoTestReport` (temporary branch floor ≥0.60) and `spotbugsMain spotbugsTest` outputs.
- Required commands before sign-off: `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`, `./gradlew --no-daemon jacocoTestReport`, `./gradlew --no-daemon spotbugsMain spotbugsTest`, and Selenium/Node harness invocations referenced in the tasks checklist.
- Deliverables: update this plan with a drift gate summary (matches vs. gaps, remediation notes), link to refreshed telemetry snapshot/how-to docs, and record any unresolved divergences as open questions before closing the feature.

## Increment Map
0. **I0 – Trusted list ingestion foundation (FR-040-18/25)**  
   - Add fixture metadata for ETSI Trust List entries and OpenID Federation identifiers; create loader utilities to parse local TL snapshots.  
   - Stage failing tests ensuring DCQL presets referencing `etsi_tl`/`openid_federation` resolve correctly.  
   - Commands: `./gradlew --no-daemon :core:test`.  

1. **I1 – Spec refinement & citation sync**  
   - Integrate GPT-5 Pro research (complete).  
   - Action: none (baseline done) – recorded for traceability.

2. **I2 – Fixture scaffolding & smoke tests (FR-040-18/19, NFR-040-01)** – _Completed 2025-11-06_  
   - Added synthetic PID fixtures (SD-JWT + mdoc) and deterministic seed files.  
   - Introduced `PidFixtureSmokeTest` alongside existing trusted authority coverage to assert fixture presence and PID namespace coverage.  
   - Command: `./gradlew --no-daemon :core:test`.

3. **I3 – Verifier request builder (FR-040-01/02/03/04/05/14)** – _Completed 2025-11-06_  
   - Added failing `OpenId4VpAuthorizationRequestServiceTest` cases covering DCQL enforcement, deterministic seed reuse, and telemetry expectations.  
   - 2025-11-06: Implemented the authorization request builder (HAIP enforcement guard, QR/URI renderers, nonce/state masking) and recorded `./gradlew --no-daemon :application:test` green alongside a documented `spotlessApply` run (blocked only by the pre-existing Feature 039 checkstyle path mismatch).  
   - Implement builder, JAR toggle, ASCII QR renderer, telemetry events `oid4vp.request.*`.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.

4. **I4 – Wallet simulator foundations (FR-040-07/08/09/10/13)** – _Completed 2025-11-07_  
   - Stage tests verifying VP Token shape, SD-JWT disclosure hashing, KB-JWT generation.  
   - Implement SD-JWT wallet path with deterministic salts/keys (loading synthetic issuer/holder keys), stub DeviceResponse loader, and wire inline credential inputs (preset vs manual + sample selector) to the generator; expose stored-mode seeding utilities.  
   - 2025-11-06: SD-JWT wallet service implemented per Option A (recompute disclosure hashes); telemetry + trace hashes now return from `OpenId4VpWalletSimulationService`. `:application:test` passes; full `spotlessApply check` still fails on pre-existing Feature 039 checkstyle path lookup.  
   - 2025-11-07: Inline-only metadata, Trusted Authority mismatch handling, and telemetry propagation hardened via T4024/T4025 with `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationServiceTest"` and a 7m36s `spotlessApply check` run both green.  
   - Commands: `./gradlew --no-daemon :core:test :application:test`, `./gradlew --no-daemon spotlessApply check`.

5. **I5 – mdoc DeviceResponse path (FR-040-09/10/17)** – _Completed 2025-11-07_  
   - Add failing tests for DeviceResponse verification and Claims Path Pointer mapping.  
   - Implement DeviceResponse adapter (using fixture CBOR and synthetic issuer certs) and hydrate inline DeviceResponse uploads alongside presets and sample vector selection; include HAIP encryption toggle scaffolding and stored-mode seeding data.  
   - 2025-11-07: `MdocDeviceResponseFixtures.load` now hydrates fixture metadata + claims pointers, and `MdocWalletSimulationService` verifies DeviceResponse responses, Trusted Authority policies, and HAIP encryption hooks. Greens recorded via `./gradlew --no-daemon :core:test :application:test` followed by `./gradlew --no-daemon spotlessApply check`.
   - Commands: `./gradlew --no-daemon :core:test :application:test`.

6. **I6 – Trusted Authorities + error handling (FR-040-11/12/21)** – _Completed 2025-11-07_  
   - Stage tests for Authority Key Identifier (`aki`) matching positives/negatives and OID4VP error mapping.  
   - Implement Trusted Authority evaluator, integrate with validation pipeline, produce problem-details responses.  
   - 2025-11-07: `TrustedAuthorityEvaluator`, `Oid4vpProblemDetails`, and shared REST/CLI adapters landed, unblocking the new tests and wiring Trusted Authority decisions plus RFC 7807 `invalid_scope`/`invalid_request` payloads through authorization, wallet, and validation services.  
   - Commands: `./gradlew --no-daemon :application:test :core:test`, `./gradlew --no-daemon spotlessApply check`.

7. **I7 – Encryption enforcement (FR-040-04/20, NFR-040-03)** – _Completed 2025-11-07_  
   - Add failing tests for `direct_post.jwt` round-trip using fixture keys.  
   - Implement JWE encryption/decryption path (behind HAIP flag) and latency telemetry.  
   - 2025-11-07: `DirectPostJwtEncryptionService` now performs HAIP-compliant P-256 ECDH-ES + A128GCM, derives verifier coordinates from fixture scalars, captures latency metrics, and surfaces `invalid_request` problem-details on key/encryption failures with `:application:test` + `spotlessApply check` green.  
   - Commands: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment reference | Notes |
|-------------|--------------------|-------|
| S-040-01 | I3, S1 | HAIP authorization request builder + QR rendering. |
| S-040-02 | I3, S5 | DCQL validation + problem-detail propagation. |
| S-040-03 | I4, S2, S7 | Wallet simulator presets (SD-JWT/mdoc) and UI generate mode. |
| S-040-04 | I5, S5 | Validation services + error surfacing across facades. |
| S-040-05 | I7, S7 | Verbose toggle + telemetry parity. |
| S-040-06 | S7 | Baseline banner + profile toggles. |
| S-040-07 | S7 | Stored preset hydration and secret masking. |
| S-040-08 | S7 | Inline sample vector loader. |
| S-040-09 | I0, I10, S8 | Fixture ingestion toggles with provenance logging. |
| S-040-10 | I0, I6 | Trusted Authority metadata + telemetry redaction. |
| S-040-11 | S7 | Multi-presentation result cards + trace IDs. |

## Analysis Gate
- **Completed:** 2025-11-06 – Specification, plan, and tasks alignment checkpoint before implementation resumed.  
- ✅ Specification completeness – Overview, goals, requirements FR-040-01…FR-040-33 documented; clarifications up to 2025-11-01 captured; Operator UI ASCII mock-ups included.  
- ✅ Open questions review – `docs/4-architecture/open-questions.md` has no entries for Feature 040.  
- ✅ Plan alignment – Plan links to the Feature 040 spec/tasks and mirrors scope/dependencies noted in the specification.  
- ✅ Tasks coverage – T-040-01–T-040-22 map to the functional requirements (IDs referenced per task), stage tests before implementation, and keep increments ≤30 minutes.  
- ✅ Constitution compliance – Workflow preserves spec-first, clarification gate, test-first cadence, and straight-line increments via dedicated helpers.  
- ✅ Tooling readiness – Commands (`./gradlew --no-daemon :core:test`, `spotlessApply check`, `spotbugsMain spotbugsTest`, etc.) documented; telemetry snapshot notes retained.  
- Outcome: proceed to implementation once initial failing tests are staged (starting with T-040-01/T-040-02).

## Exit Criteria
- REST/CLI/UI demonstrate remote OpenID4VP exchange for SD-JWT VC and mdoc fixtures under deterministic seeds.
- Encryption path validated and telemetered; Trusted Authorities filtering operational with documented follow-ups for future enhancements.
- Synthetic vs conformance fixture toggles functional with provenance tracking.
- Roadmap, knowledge map, and supporting docs updated; all tests (`spotlessApply check`, module suites) passing.

## Follow-ups / Backlog
- T-040-24 – Same-device/DC-API exploration once prioritised.
- T-040-25 – OpenID4VCI issuance simulator alignment for end-to-end wallet journeys.
- T-040-26 – Trusted Authorities expansion (live TL updates, OpenID Federation resolution).
- T-040-27 – Reinstate JaCoCo branch threshold ≥0.70 after coverage stabilises.
- Quality guardrail – After any plan/task/spec change, run `./gradlew --no-daemon spotbugsMain spotbugsTest` plus `./gradlew --no-daemon spotlessApply check`; document deviations before proceeding.
- Telemetry snapshot note – Capture payload diffs via existing snapshot tests each increment so sanitisation regressions surface before implementation resumes.
