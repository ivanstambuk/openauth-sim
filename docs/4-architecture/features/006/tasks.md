# Feature 006 Tasks – EUDIW OpenID4VP Simulator

_Status:_ In progress  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).


## Checklist
- [x] T-006-01 – Trusted list ingestion foundation (FR-040-18/25, S-040-10).
  _Intent:_ Capture ETSI TL/OpenID Federation metadata, seed friendly labels, and prove the resolver fails red before implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test`
  _Notes:_ 2025-11-06 run added HAIP baseline snapshots, stored presentation manifests, and `TrustedAuthorityFixturesTest`/`SyntheticKeyFixturesTest` coverage to guard future ingestion refactors.

- [x] T-006-02 – Fixture scaffolding & deterministic seeds (FR-040-18/19/25/31, NFR-040-01, S-040-03, S-040-07, S-040-08, S-040-13).
  _Intent:_ Land SD-JWT VC + mdoc PID fixtures, deterministic seed files, friendly issuer labels, and smoke tests verifying availability.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test`
  _Notes:_ 2025-11-06 update introduced disclosure/KB-JWT/DeviceResponse bundles plus `PidFixtureSmokeTest` to ensure each preset stays loadable.

- [x] T-006-03 – Authorization request red tests (FR-040-01/02/03/04/05/14, S-040-01, S-040-02).
  _Intent:_ Stage failing coverage for DCQL enforcement, nonce/state determinism, response mode toggles, and telemetry expectations before touching implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  _Notes:_ Added `OpenId4VpAuthorizationRequestServiceTest` with preset override, deterministic seed, and telemetry assertions (red via `UnsupportedOperationException`).

- [x] T-006-04 – Authorization request implementation (S-040-01, S-040-02, S-040-05).
  _Intent:_ Make tests from T-006-03 green by implementing the builder, QR renderer, HAIP signed-request toggle, and telemetry.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Deterministic seed usage, DCQL override support, and telemetry dispatch now exist; `spotlessApply check` failures recorded were due to pre-existing Feature 039 OpenAPI/PMD issues, not this work.

- [x] T-006-05 – SD-JWT wallet tests (FR-040-07/08/10/13/23, S-040-03).
  _Intent:_ Add red coverage for VP Token shape, disclosure hashing, KB-JWT generation, and inline credential inputs (preset vs manual + sample selector).
  _Verification commands:_
  - `./gradlew --no-daemon :core:test :application:test`
  _Notes:_ Tests cover deterministic SD-JWT outputs and telemetry plumbing, forcing the upcoming wallet implementation.

- [x] T-006-06 – SD-JWT wallet implementation (S-040-03, S-040-04).
  _Intent:_ Satisfy T-006-05 by implementing deterministic SD-JWT + KB-JWT flows, inline credential hydration, and trace hashing.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test :application:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ `OpenId4VpWalletSimulationService` now recomputes disclosure hashes (Option A) and emits telemetry; lingering Spotless failure traced to Feature 039 path mismatches.

- [x] T-006-07 – mdoc wallet tests (FR-040-09/10/17/23, S-040-01).
  _Intent:_ Stage failing coverage for DeviceResponse parsing, Claims Path Pointer mapping, inline DeviceResponse uploads, sample selector hydration, and HAIP hooks.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test :application:test`
  _Notes:_ Added `MdocDeviceResponseFixturesTest` + `MdocWalletSimulationServiceTest`; remained red until the service existed, forcing deterministic fixture loaders.

- [x] T-006-08 – mdoc wallet implementation (S-040-02).
  _Intent:_ Make T-006-07 green by validating DeviceResponse COSE signatures, exposing inline uploads, and threading Trusted Authority/HAIP toggles through the wallet path.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test :application:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Implemented `MdocWalletSimulationService`, pointer diagnostics, and encryption guards with full module + formatting runs green.

- [x] T-006-09 – Trusted Authority red tests (FR-040-11/12/21, S-040-05, S-040-01, S-040-02).
  _Intent:_ Stage failing tests for AKI match/miss logic and RFC 7807 error propagation before building the evaluator.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  _Notes:_ `TrustedAuthorityEvaluatorTest` and `Oid4vpProblemDetailsMapperTest` capture success/failure expectations; stayed red until the evaluator shipped.

- [x] T-006-10 – Trusted Authority implementation (S-040-05, S-040-03, S-040-04).
  _Intent:_ Implement the AKI evaluator, error mappers, telemetry redaction tweaks, and wire them into authorization + wallet flows.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test :core:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Added evaluator, RFC 7807 helpers, REST `@ControllerAdvice`, and CLI formatter; telemetry now emits friendly labels and Trusted Authority verdicts.

- [x] T-006-11 – Encryption path tests (FR-040-04/20, NFR-040-03, S-040-10).
  _Intent:_ Stage failing tests for HAIP `direct_post.jwt` round-trip, latency telemetry, and invalid-request error mapping.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  _Notes:_ `DirectPostJwtEncryptionServiceTest` asserts deterministic P-256 material, latency capture, and error handling; red until the service landed.

- [x] T-006-12 – Encryption implementation (S-040-03).
  _Intent:_ Implement the HAIP JWE encoder/decoder (P-256 ECDH-ES + A128GCM), telemetry hooks, and retry behaviour tied to the enforcement flag.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Service now derives verifier coordinates from fixture scalars, surfaces latency metrics, and maps failures to `invalid_request`.

- [x] T-006-13 – Validation mode tests (FR-040-23/23, S-040-04, S-040-10).
  _Intent:_ Add failing coverage for inline/stored selectors, DCQL preview rendering, VP Token paste/upload flows, and error classification alignment.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  _Notes:_ `OpenId4VpValidationServiceTest` now asserts telemetry and Trusted Authority enforcement, forcing validation service work.

- [x] T-006-14 – Validation mode implementation (S-040-13, S-040-13).
  _Intent:_ Wire stored/inline validation, telemetry events, and shared error mappers to satisfy T-006-13.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ `OpenId4VpValidationService` now recomputes disclosures, enforces Trusted Authorities, and emits `oid4vp.response.*` telemetry with green pipelines.

- [x] T-006-15 – REST & CLI contract tests (FR-040-15/16/30, S-040-04, S-040-10, S-040-15, S-040-04, S-040-10).
  _Intent:_ Stage failing MockMvc + Picocli suites covering request, wallet, validate flows, verbose toggles, and problem-details responses.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test`
  - `./gradlew --no-daemon :cli:test`
  _Notes:_ 2025-11-08 run introduced `Oid4vpRestContractTest`/`Oid4vpCliContractTest` plus temporary `eudiw` Picocli stubs so tests compile before implementation.

- [x] T-006-16 – REST & CLI implementation (S-040-15, S-040-14, S-040-14, S-040-04, S-040-07).
  _Intent:_ Deliver controllers/DTOs, fully wired `eudiw` Picocli commands, verbose toggles, and regenerated OpenAPI snapshot per the spec JSON samples.
  _Verification commands:_
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `./gradlew --no-daemon :rest-api:test :cli:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ REST/CLI now share fixture-backed services, and contract suites plus formatting runs pass.

- [x] T-006-16a – Operator UI layout & baseline banner (FR-040-17/21/24/25/29, S-040-04, S-040-07, S-040-01, S-040-03).
  _Intent:_ Enforce the two-column Evaluate/Replay layout, render the baseline banner, show Trusted Authority labels, keep DCQL preview read-only, and verify JS/Selenium parity.
  _Verification commands:_
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`
  _Notes:_ Added `EudiwOperatorConsoleData` hydrate bundle plus Selenium assertions for baseline banner + sample selector autofill.

- [x] T-006-16b – Trace dock integration parity (FR-040-14/22/26/30, S-040-04, S-040-05).
  _Intent:_ Route verbose traces through the shared dock (no local checkbox), expose VP Token JSON in result cards only, and align trace IDs with presentation rows.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :ui:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Result cards plus Selenium coverage now point to shared trace IDs; consoles reuse the global verbose warning copy.

- [x] T-006-17 – Multi-presentation rendering (FR-040-09/22a, S-040-15).
  _Intent:_ Add integration tests and UI updates for multi-credential DCQL responses, collapsible sections, copy/download controls, and trace alignment.
  _Verification commands:_
  - `./gradlew --no-daemon :ui:test :application:test`
  _Notes:_ Result cards now include per-presentation collapsibles and trace IDs; JS + Selenium suites assert behaviour.

- [x] T-006-19 – Deep-link & flag consolidation (FR-040-31/FR-040-32, S-040-01, S-040-03).
  _Intent:_ Treat `protocol=eudiw` as the canonical alias, hydrate `tab`/`mode` on load, sync history/back-forward, and consolidate verbose trace builders across REST/CLI/UI.
  _Verification commands:_
  - `node --test rest-api/src/test/javascript/eudi/openid4vp/console.test.js`
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.eudi.openid4vp.Oid4vpRestContractTest"`
  - `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.eudi.openid4vp.Oid4vpCliContractTest"`
  _Notes:_ Selenium + Node harnesses now assert alias persistence and history handling; `spotlessApply check` remains blocked by in-flight Feature 039 fixture/PMD issues (documented in `_current-session.md`).

- [x] T-006-20 – Fixture ingestion toggle tests (FR-040-18, NFR-040-04, S-040-04, S-040-05).
  _Intent:_ Add failing tests proving synthetic vs conformance selection and provenance metadata handling before wiring ingestion.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test :application:test`
  _Notes:_ Introduced `FixtureDatasetsTest` + `OpenId4VpFixtureIngestionServiceTest` with stored-presentation fixtures; both red until T-006-21 implemented loaders.

- [x] T-006-21 – Fixture ingestion implementation (S-040-15, S-040-06).
  _Intent:_ Deliver `FixtureDatasets` loader, stored presentation catalog, ingestion service, and telemetry event `oid4vp.fixtures.ingested`.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test :application:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Added provenance files, ingestion service wiring, and green module/full-formatting runs (900 s timeout for long EMV console JS tests).

- [x] T-006-22 – Documentation & telemetry verification (All, S-040-08, S-040-10).
  _Intent:_ Refresh roadmap, knowledge map, how-to guides, telemetry snapshot, and rerun the full Gradle gate to document the feature.
  _Verification commands:_
  - `./gradlew --no-daemon :application:test :cli:test :core:test :rest-api:test :ui:test`
  - `./gradlew --no-daemon spotlessApply check`
  _Notes:_ Authored the three how-to guides plus telemetry snapshot, bumped roadmap entry #15 to “In progress,” and reran the full gate (spotless run used a 900 s timeout).

- [x] T-006-23 – Live Trusted Authority ingestion (S-040-11).
  _Intent:_ Build ETSI TL/OpenID Federation refresh that populates `trust/snapshots/*.json`, records provenance telemetry, and exposes conformance datasets through `presentations/seed`.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test --tests "*FixtureDatasets*"`
  - `./gradlew --no-daemon :application:test --tests "*OpenId4VpFixtureIngestionServiceTest*"`
  - `./gradlew --no-daemon :rest-api:test --tests "*Oid4vpRestContractTest*"`
  - `node --test rest-api/src/test/javascript/eudi/openid4vp/console.test.js`
  - `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`
  _Notes:_ Captured EU LOTL seq 373 (DE 149, SI 78) snapshots under `docs/trust/snapshots/2025-11-15/`, updated fixture provenance/trust snapshots/DCQL presets/stored presentations, wired `presentations/seed` through `OpenId4VpFixtureIngestionService`, refreshed how-to + telemetry snapshot, and reran the Gradle/Node/Selenium suites above (spotless/quality gate to follow during drift gate).

## Verification Log (Optional)
- 2025-11-08 – `./gradlew --no-daemon :application:test :cli:test :core:test :rest-api:test :ui:test`  
- 2025-11-08 – `./gradlew --no-daemon spotlessApply check` (900 s timeout due to EMV console JS tests)

## Notes / TODOs
- [ ] T-006-24 – Same-device/DC-API exploration once prioritised.
- [ ] T-006-25 – OpenID4VCI issuance simulator alignment for end-to-end wallet journeys.
- [ ] T-006-26 – Trusted Authorities expansion (live TL updates, OpenID Federation resolution enhancements).
- [ ] T-006-27 – Reinstate JaCoCo branch threshold ≥0.70 after new coverage lands (temporary drop to 0.60 on 2025-11-06).
