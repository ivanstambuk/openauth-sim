# Feature 002 Tasks – TOTP Simulator & Tooling

_Status:_ Complete  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-002-01 – Stage failing core TOTP generator/validator tests (SHA-1/256/512, digits, steps, drift) (S-002-01, FR-002-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-002-02 – Implement TOTP domain logic + fixture loader (S-002-01, FR-002-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-002-03 – Add failing persistence integration tests mixing HOTP/OCRA/TOTP descriptors (S-002-01, FR-002-02).  
  _Verification:_ `./gradlew --no-daemon :infra-persistence:test --tests "*TotpIntegrationTest"`
- [x] T-002-04 – Implement persistence descriptors/defaults; rerun targeted tests (S-002-01, FR-002-02).  
  _Verification:_ `./gradlew --no-daemon :infra-persistence:test`
- [x] T-002-05 – Stage failing application telemetry tests covering stored/inline evaluation/replay (S-002-02, FR-002-03).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*TotpEvaluationApplicationServiceTest"`
- [x] T-002-06 – Implement application services/telemetry adapters (S-002-02, FR-002-03).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-002-07 – Stage failing CLI tests for import/list/evaluate/replay (drift/timestamp overrides) (S-002-02, FR-002-04).  
  _Verification:_ `./gradlew --no-daemon :cli:test --tests "*TotpCliTest"`
- [x] T-002-08 – Implement CLI commands, rerun suite (S-002-02, FR-002-04).  
  _Verification:_ `./gradlew --no-daemon :cli:test`
- [x] T-002-09 – Stage failing REST MockMvc/OpenAPI tests for evaluation/replay (S-002-03, FR-002-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Totp*EndpointTest"`
- [x] T-002-10 – Implement REST controllers/services, regenerate OpenAPI snapshots (S-002-03, FR-002-05).  
  _Verification:_ `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
- [x] T-002-11 – Stage failing Selenium coverage for TOTP stored/inline evaluate panels (S-002-04, FR-002-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUiSeleniumTest"`
- [x] T-002-12 – Implement TOTP stored/inline evaluate UI, including inline default (S-002-04, FR-002-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-002-13 – Stage failing Selenium coverage for TOTP replay (stored/inline) including auto-applied samples (S-002-04, FR-002-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUiSeleniumTest*Replay*"`
- [x] T-002-14 – Implement TOTP replay UI updates (auto-fill, remove legacy sample button) (S-002-04, FR-002-06).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-002-15 – Stage failing Selenium coverage for timestamp toggles (“Use current Unix seconds”, “Reset to now”) (S-002-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*TotpOperatorUiSeleniumTest*TimestampControls"`
- [x] T-002-16 – Implement timestamp toggle UI, quantised reset helpers (S-002-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`
- [x] T-002-17 – Publish `docs/totp_validation_vectors.json`, add shared loader/tests across modules (S-002-05, FR-002-07).  
  _Verification:_ `./gradlew --no-daemon :core:test :cli:test :rest-api:test`
- [x] T-002-18 – Update operator/CLI/REST how-to guides, roadmap, knowledge map; rerun spotless (S-002-05, FR-002-07).  
  _Verification:_ `./gradlew --no-daemon spotlessApply check`

- [x] T-002-19 – Design TOTP Native Java API seam (FR-014-01/02, S-014-02).  
  _Intent:_ Designate `io.openauth.sim.application.totp.TotpEvaluationApplicationService` (and its `EvaluationCommand` / `EvaluationResult` types) as the TOTP Native Java API seam, document it in the Feature 002 Interface & Contract catalogue, and link it explicitly to Feature 014 and ADR-0007.  
  _Verification:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-002-20 – Author `docs/2-how-to/use-totp-from-java.md` and tests (FR-014-03/04, S-014-01/02).  
  _Intent:_ Add a TOTP `*-from-java` how-to guide and tests that treat `TotpEvaluationApplicationService` (with `EvaluationCommand` / `EvaluationResult`) as façade seams for stored and inline flows, mirroring the OCRA and HOTP Java guides.  
  _Verification:_  
  - `./gradlew --no-daemon :core:test :application:test`  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-10-18 – `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- TOTP issuance/enrollment deferred to future feature.
