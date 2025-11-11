# Feature 024 Tasks – FIDO2/WebAuthn Operator Support

_Status: Complete_  
_Last updated: 2025-11-10_

> Tasks below reflect the refreshed template; each originally stayed ≤30 minutes and staged failing tests before implementation.

## Checklist
- [x] T-024-01 – Convert W3C §16 vectors to repo fixtures; stage failing core verifier tests (S-024-01, FR-024-01).  
  _Verification:_ `./gradlew --no-daemon :core:test --tests "*WebAuthnAssertionVerifierTest"`
- [x] T-024-02 – Implement WebAuthn verifier, add failure-branch tests (S-024-01, FR-024-01).  
  _Verification:_ `./gradlew --no-daemon :core:test`
- [x] T-024-03 – Stage failing MapDB integration tests for WebAuthn descriptors (S-024-02, FR-024-02).  
  _Verification:_ `./gradlew --no-daemon :infra-persistence:test --tests "*CredentialStore*WebAuthn*"`
- [x] T-024-04 – Implement persistence wiring + curated seeding metadata (S-024-02, FR-024-02).  
  _Verification:_ `./gradlew --no-daemon :infra-persistence:test`
- [x] T-024-05 – Stage failing application service tests (stored/inline evaluation + replay, telemetry assertions) (S-024-02, FR-024-03).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*WebAuthn*ApplicationServiceTest"`
- [x] T-024-06 – Implement application services + telemetry adapters (S-024-02, FR-024-03).  
  _Verification:_ `./gradlew --no-daemon :application:test`
- [x] T-024-07 – Stage failing CLI tests for evaluate/replay commands (stored + inline + replay) (S-024-03, FR-024-04).  
  _Verification:_ `./gradlew --no-daemon :cli:test --tests "*Fido2CliTest"`
- [x] T-024-08 – Implement CLI commands/fixtures, rerun suite (S-024-03, FR-024-04).  
  _Verification:_ `./gradlew --no-daemon :cli:test`
- [x] T-024-09 – Stage failing REST MockMvc/OpenAPI tests for evaluate/replay endpoints + sample endpoints (S-024-03, FR-024-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Fido2*EndpointTest"`
- [x] T-024-10 – Implement REST controllers/services, regenerate OpenAPI snapshots (S-024-03, FR-024-04).  
  _Verification:_ `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`
- [x] T-024-11 – Stage failing Selenium coverage for FIDO2 stored/inline evaluate panels (S-024-04, FR-024-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Fido2OperatorUiSeleniumTest*Evaluate*"`
- [x] T-024-12 – Implement evaluate UI (seed button stored-only, inline defaults, layout clamp) (S-024-04, FR-024-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-024-13 – Stage failing Selenium tests for replay UI + router parity (S-024-04, FR-024-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*Fido2OperatorUiSeleniumTest*Replay*"`
- [x] T-024-14 – Implement replay UI (auto-fill samples, router shared params, status clamp) (S-024-04, FR-024-05).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`
- [x] T-024-15 – Stage failing routing/URL tests ensuring only shared `protocol/tab/mode` params remain (S-024-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test --tests "*OperatorConsoleUnificationSeleniumTest*Routing*"`
- [x] T-024-16 – Implement router/shared query param updates + legacy compatibility (S-024-04).  
  _Verification:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`
- [x] T-024-17 – Stage failing tests for JWK/PEM public-key parsing (applications + REST) (S-024-03).  
  _Verification:_ `./gradlew --no-daemon :application:test --tests "*WebAuthnPublicKeyDecoderTest"`
- [x] T-024-18 – Implement multi-format key decoder + telemetry errors (S-024-03).  
  _Verification:_ `./gradlew --no-daemon :application:test :rest-api:test`
- [x] T-024-19 – Stage failing fixture tests confirming `kty`-first ordering and JSON bundle integrity (S-024-05, FR-024-06).  
  _Verification:_ `./gradlew --no-daemon :core:test --tests "*WebAuthnJsonVectorJwkFormattingTest"`
- [x] T-024-20 – Implement serializer updates + doc references for `kty` ordering (S-024-05, FR-024-06).  
  _Verification:_ `./gradlew --no-daemon :core:test`, `./gradlew --no-daemon spotlessApply check`
- [x] T-024-21 – Update operator/CLI/REST docs, roadmap, knowledge map, and rerun full gate (S-024-05, FR-024-06/07).  
  _Verification:_ `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`

## Verification Log
- 2025-10-15 – `./gradlew --no-daemon :core:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check` (template migration verification)

## Notes / TODOs
- Future feature to cover WebAuthn registration/attestation once prioritised.
