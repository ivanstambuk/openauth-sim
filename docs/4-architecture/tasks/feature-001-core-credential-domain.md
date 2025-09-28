# Feature 001 – Core Credential Domain Tasks

_Status: In progress_
_Last updated: 2025-09-27_

## Execution Notes
- Follow the specification in `docs/4-architecture/specs/feature-001-core-credential-domain.md`.
- Maintain discrete Java packages per protocol (`io.openauth.sim.core.credentials.{ocra|fido2|eudiw|emvcap}`) and roll out one protocol at a time.
- Start with OCRA; keep other protocol tasks pending until their dedicated plans are approved.
- Keep each task to ≤10 minutes of focused work and commit after completing one task cluster.
- Tests precede implementations; mark tasks as complete (`[x]`) as they ship.
- Use this checklist with the analysis gate before coding.

## Phase 0 – Setup
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T001 | Ensure `core` module module-info and build files accommodate new credential domain packages. | FR-001–FR-008, NFR-001 | No |
| T002 | Add package structure placeholders (`io.openauth.sim.core.credentials.*`) with javadoc stubs. | FR-001–FR-005 | No |

## Phase 1 – OCRA Specification Tests
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T003 | [x] Capture the minimum OCRA metadata set (suite string, secret material requirements, optional counter/PIN handling) and document it under spec clarifications alongside global name/custom metadata rules. | FR-002, FR-010 | No |
| T004 | [x] Create OCRA unit test skeletons covering valid/invalid payload samples, counter/time drifts, and hash suite mismatches (landed disabled per spec clarification). | FR-002, NFR-004 | No |
| T005 | [x] Add property-based tests for OCRA secret material encoding/decoding utilities (committed disabled pending helper implementation). | FR-006, NFR-004 | No |
| T006 | [x] Define ArchUnit rules ensuring only the OCRA package accesses its internals from outside `core` (committed disabled pending descriptors). | FR-007, NFR-004 | No |

## Phase 2 – OCRA Core Implementation
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T007 | [x] Implement OCRA credential descriptors inside `io.openauth.sim.core.credentials.ocra`. | FR-002, NFR-003 | No |
| T008 | [x] Implement OCRA validation/factory utilities with descriptive error messaging; re-enable only the unit tests from T004 once green, keep T005/T006 disabled. | FR-002, FR-006, NFR-005 | No |
| T009 | [x] Extend secret material helpers to normalise raw/hex/Base64 inputs for OCRA secrets. | FR-006, NFR-002 | No |
| T010 | [x] Wire OCRA entries into the credential registry with capability metadata. | FR-007, NFR-002 | No |

### T007 – Descriptor Implementation Checklist
- [x] Create descriptor-focused tests (JUnit) defining expected suite parsing output, secret material handling, and metadata exposure; ensure they fail prior to implementation.
- [x] Add `io.openauth.sim.core.credentials.ocra` package with descriptor record(s) and suite parsing value objects that materialise the RFC 6287 components.
- [x] Implement descriptor factory/builders that accept raw inputs, invoke shared normalisation (pre-`SecretMaterial`), and produce immutable descriptors with validation hooks for missing/invalid data.
- [x] Update documentation touchpoints (spec clarifications already updated, feature plan checklist) and note readiness to re-enable Phase 1 tests in subsequent tasks.
- [x] Run `./gradlew spotlessApply check`, record outcome in the feature plan, and perform self-review before committing.

### T008 – Validation & Factory Checklist
- [x] Introduce `OcraCredentialFactory` to wrap descriptor creation and dynamic validations (challenge, session info, timestamp).
- [x] Harden `OcraCredentialDescriptorFactory` diagnostics for name, secret, counter, PIN, and drift inputs.
- [x] Replace placeholder assertions in `OcraCredentialFactoryTest` and re-enable the suite while keeping property/ArchUnit tests disabled.
- [x] Run `./gradlew spotlessApply check` (2025-09-27T23:16Z) – PASS.

### T009 – Secret Material Helpers Checklist
- [x] Added `OcraSecretMaterialSupport` to normalise shared secrets across RAW/HEX/Base64 inputs with descriptive diagnostics.
- [x] Updated descriptor and credential factories to accept canonicalised `SecretMaterial` instances and expose encoding metadata to callers.
- [x] Reworked `OcraSecretMaterialPropertyTest` to run active property-based checks for hex canonicalisation, base64 round-trips, and invalid input rejection.
- [x] Ran `./gradlew spotlessApply check` (2025-09-27T23:34Z) – PASS.

### T010 – Registry Capability Checklist
- [x] Introduced `CredentialCapability` model and `CredentialRegistry` with default OCRA registration.
- [x] Captured OCRA capability metadata (required/optional attributes, supported crypto functions, RFC reference).
- [x] Added `CredentialRegistryTest` to validate capability exposure and factory lookup.
- [x] Ran `./gradlew spotlessApply check` (2025-09-27T23:40Z) – PASS.

## Phase 3 – Integration & Observability
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T011 | Connect OCRA credentials to persistence serialization interfaces (no persistence implementation yet). | FR-006–FR-008 | No |
| T012 | Implement schema-versioned persistence envelope helpers and migration pipeline tests for OCRA records. | FR-006–FR-008, NFR-004 | No |
| T013 | Emit structured validation failure events/log markers for OCRA flows without secret leakage. | FR-008, NFR-005 | No |

### T011 – Persistence Serialization Bridge Checklist
- [x] Define versioned credential record and adapter interfaces covering schema version, credential type, and payload contract without binding to MapDB.
- [x] Add failing tests for OCRA descriptor → record serialization and record → descriptor hydration, including counter, PIN hash, timestamp drift, and metadata propagation.
- [x] Implement the OCRA adapter leveraging existing `SecretMaterial` utilities while preserving immutable metadata handling.
- [x] Capture adapter behaviour and follow-ups back in the feature plan/tasks after the build passes.

### T012 – MapDB Envelope Integration Checklist
- [x] Add persistence tests covering schema-v1 and simulated legacy records, ensuring upgrade hooks materialise OCRA descriptors and invalidate caches appropriately.
- [x] Introduce migration helper APIs translating legacy envelopes to the latest schema, including safe defaults for unsupported versions.
- [x] Update `MapDbCredentialStore` (and related persistence utilities) to read/write `VersionedCredentialRecord` instances via registered adapters.
- [x] Document migration behaviour and any remaining protocol gaps across spec, plan, and knowledge map.

### T013 – Validation Telemetry Checklist
- [x] Capture expected structured log events in tests (e.g., via LogCaptor) ensuring messages redact secret material while surfacing credential name, suite, and validation failure reason.
- [x] Emit telemetry from `OcraCredentialFactory` and descriptor validation paths using a dedicated logger/marker for observability pipelines.
- [x] Ensure logging obeys rate and level guidance (debug/info) and document the contract in spec/plan with follow-ups for other protocols.
- [x] Verify `./gradlew spotlessApply check` passes and record command output in the feature plan after committing.

## Phase 4 – Documentation & Cleanup
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T014 | Update `docs/1-concepts` with OCRA capability matrix and glossary. | FR-002 | No |
| T015 | Refresh `docs/4-architecture/knowledge-map.md` to reflect OCRA package relationships; note pending protocols. | FR-007 | No |
| T016 | Update roadmap and feature plan status; capture lessons learned/self-review notes. | All | No |

### T014 – Documentation Sync Checklist
- [x] Add an OCRA capability matrix and glossary entries to `docs/1-concepts/README.md`, referencing FR-002/FR-008.
- [x] Document the structured telemetry contract (event name, fields, redaction rules) and link to implementation modules.
- [x] Ensure cross-references back to the feature specification and update any TODOs for upcoming protocols.
- [x] Confirm `./gradlew spotlessApply check` passes after documentation updates.

### T015 – Knowledge Map Refresh Checklist
- [x] Update `docs/4-architecture/knowledge-map.md` with new telemetry relationships and documentation touchpoints.
- [x] Note pending protocol packages explicitly in the map to signal future work.
- [x] Ensure references stay aligned with the updated `docs/1-concepts` material.

### T016 – Roadmap & Self-Review Checklist
- [x] Refresh Workstream 1 status and notes in `docs/4-architecture/roadmap.md`.
- [x] Capture lessons learned/self-review items in the feature plan and ensure open follow-ups remain accurate.
- [x] Confirm action items list mirrors post-telemetry state and highlight upcoming increments.

## Phase 5 – RFC 6287 Test Vectors
| ID | Task | Related Requirements | Parallel? |
|----|------|----------------------|-----------|
| T017 | Catalogue the official RFC 6287 reference vectors and document their provenance within the spec/plan before adding fixtures. | FR-002, NFR-004 | No |
| T018 | Add failing-first OCRA response tests that exercise the RFC vectors (counter/time/challenge suites) and wire them to the execution helper once available. | FR-002, FR-006, NFR-004 | No |

### T017 – Vector Catalogue & Documentation Checklist
- [ ] Identify the authoritative RFC 6287 appendix containing sample credentials, suite definitions, challenge inputs, and expected OTP outputs.
- [ ] Record vector details and licensing notes in the feature specification (`## Clarifications`) and feature plan, ensuring attribution is clear.
- [ ] Introduce fixtures or helper methods to load the vectors while keeping shared secrets redacted outside test scope.
- [ ] Update open follow-ups/analysis gate notes to reflect the new compliance coverage requirement.

### T018 – RFC Vector Test Harness Checklist
- [ ] Add parameterised tests that feed the RFC counter-, time-, and challenge-based vectors through the OCRA descriptor + response calculation path, initially asserting TODO/expected failures until the engine is available.
- [ ] Ensure tests verify both successful OTP outputs and error handling (e.g., drift tolerance) per the RFC examples.
- [ ] Guard telemetry/log output so secrets remain redacted, adding assertions where necessary.
- [ ] Run `./gradlew spotlessApply check` once the harness and implementation pass, recording timing and outcomes in the feature plan.

## Phase 6 – Future Protocol Packages (Pending Separate Plans)
| Protocol | Notes |
|----------|-------|
| FIDO2/WebAuthn | Await dedicated clarification on minimum metadata, lifecycle expectations, and package rollout tasks. |
| EUDI Wallet suites | Depends on broader clarifications already logged; schedule after OCRA delivery. |
| EMV/CAP | Pending tailored plan mirroring the OCRA approach. |

## Open Follow-ups
- Populate task outcomes and timestamps upon completion.
- Attach Gradle command outputs and analysis gate results to the feature plan when tasks close.
