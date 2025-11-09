# Feature 001 – Core Credential Domain Specification

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Overview
Design an OCRA-focused credential domain inside the `core` module that normalises RFC 6287 credential descriptors, shared secret material, and evaluation helpers for downstream facades. The scope of Feature 001 is limited to OCRA; future protocol packages (FIDO2, EUDI wallets, EMV/CAP, generic credentials) will be delivered through separate specifications once prioritised. The domain must provide deterministic validation and transformation logic so CLI, REST, and UI surfaces consume OCRA data without duplicating cryptographic rules.


## Goals
- Model RFC 6287-compliant OCRA credential descriptors, validation rules, and shared-secret normalization inside the `core` module.
- Provide deterministic evaluation helpers plus persistence-friendly envelopes so downstream facades can consume OCRA credentials without duplicating crypto logic.

## Non-Goals
- Does not introduce HOTP/TOTP/FIDO2/EMV credential models.
- Does not wire CLI/REST/UI flows; those land in later features.

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S01-01 | Immutable OCRA credential descriptors capture suite metadata, optional counter/PIN fields, and reject malformed payloads with descriptive validation errors. |
| S01-02 | Shared secret normalization converts RAW/HEX/Base64 inputs into a canonical representation without leaking material in errors or telemetry. |
| S01-03 | Credential registry exposes capability metadata and factories so downstream modules query supported suites and required fields deterministically. |
| S01-04 | Versioned persistence envelopes round-trip descriptors, upgrading legacy records through documented migration hooks without altering caller APIs. |
| S01-05 | `OcraResponseCalculator` reproduces RFC 6287 OTPs (including S064/S128/S256/S512 session variants) for inline helpers and downstream facades. |


## Objectives & Success Criteria
- Provide immutable OCRA credential descriptors with clearly defined required and optional fields.
- Canonicalise shared secret encodings (raw bytes, hexadecimal, Base64) ahead of descriptor construction.
- Reject invalid OCRA payloads via validation rules with actionable error messages and redacted telemetry.
- Supply serialization/deserialization helpers for persistence and caching layers using versioned envelopes.
- Deliver an execution helper that produces RFC 6287-compliant OTP responses and expose results to core consumers.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FR-001 | Represent OCRA credentials with immutable descriptors capturing name, suite definition, shared secret, and optional counter/PIN metadata. | Constructing a descriptor with valid inputs succeeds; missing or malformed inputs produce descriptive validation failures. |
| FR-002 | Normalise shared secret encodings (RAW/HEX/Base64) into a unified `SecretMaterial` representation. | Property-based tests confirm round-trips for valid encodings and reject malformed inputs with actionable errors. |
| FR-003 | Expose credential factories and registry metadata describing required attributes, optional fields, and supported hash algorithms for OCRA. | Registry lookups return the correct capability metadata and factory references for OCRA credentials. |
| FR-004 | Support persistence round-trips using versioned credential envelopes with upgrade hooks for legacy records. | Stored OCRA credentials reload without data loss; loading a legacy schema triggers the upgrade pipeline and preserves semantics. |
| FR-005 | Provide an `OcraResponseCalculator` that evaluates RFC 6287 suites, including session-aware variants (S064/S128/S256/S512). | Compliance tests covering RFC 6287 Appendix C and IETF draft vectors pass using the calculator outputs. |
| FR-006 | Emit structured validation telemetry that redacts shared secrets while signalling failure stages for downstream observers. | Telemetry capture tests confirm events include expected metadata and exclude sensitive payloads. |
| FR-007 | Surface deterministic error diagnostics suitable for CLI/REST responses without leaking secret material. | Validation exceptions redact secret inputs and map to user-facing error identifiers. |
| FR-008 | Ensure every OCRA credential exposes a globally unique `name` plus an extensible metadata map returned on lookup but ignored by crypto flows. | Creating credentials without a unique name or with malformed metadata fails fast with descriptive errors. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| NFR-001 | JVM compatibility | Java 17; no preview features. |
| NFR-002 | Performance | Constructing or validating a credential executes in ≤1 ms on a modern laptop (99th percentile, single-threaded). |
| NFR-003 | Memory profile | Credential objects remain lightweight (≤16 KB per instance) to support caching thousands in memory. |
| NFR-004 | Testability | Unit and property-based tests cover factories, validation, and serialization; ArchUnit enforces module boundaries. |
| NFR-005 | Observability | Validation and transformation steps expose structured logs/events without secrets. |

## User Stories
- **Operator imports credential**: As an operator, I can import a credential definition via CLI passing names, metadata, and secret material so the core registry stores it for later simulations.
- **REST automation**: As an integration test harness, I can call the REST API to look up credential capabilities and ensure the emulator supports required protocols.
- **Performance testing**: As a load tester, I can script REST-based credential evaluations to warm caches without relying on UI flows.

## Edge Cases & Failure Modes
- Reject credential payloads missing required attributes or containing unsupported encodings.
- Handle duplicate credential names by surfacing deterministic conflict errors.
- Prevent serialization of secret material when persistence is configured to redact or encrypt secrets.
- Guard against unsupported protocol identifiers by directing users to the generic credential type with warnings.

## Observability & Telemetry
- Emit debug-level logs when credential validation fails, referencing field names not values.
- Provide metrics hooks for credential creation/validation success and failure counts to integrate with future observability modules.

## Dependencies & Integrations
- Persistence layer (MapDB facade) will rely on serialization formats defined here.
- Facade modules must treat the core registry as read/write API and respect immutability contracts.
- Future cryptographic helpers may depend on third-party libraries pending user approval.

## Out of Scope
- Persistence implementation specifics (handled in separate workstream).
- REST, CLI, UI wiring beyond verifying they can consume the core API.
- Protocol-specific network operations (e.g., actual WebAuthn ceremony simulation).

## Clarifications
- 2025-09-27 – Persistence topology: Use a single shared MapDB store with a core-managed shared in-memory cache that all facades consume.
- 2025-09-27 – EUDI wallet coverage: Support ISO/IEC 18013-5 mDL, ISO/IEC 23220-2 mdoc payloads, SD-JWT + W3C VC 2.0 formats, and lifecycle flows for OpenID4VCI issuance plus OpenID4VP/ISO/IEC 18013-7 presentations so both registration and authentication scenarios are simulatable.
- 2025-09-27 – Protocol packaging cadence: Maintain one package per protocol (`io.openauth.sim.core.credentials.{ocra|fido2|eudiw|emvcap}`), deliver them as separate increments, and begin detailed design/implementation with OCRA while parking the other protocols until their dedicated plans are prepared.
- 2025-09-27 – Cryptography extension point: Credential classes remain data/validation focused; all protocol cryptographic operations delegate to pluggable strategy interfaces (option 1: co-locate crypto in domain – rejected; option 2: pluggable strategies – accepted).
- 2025-09-27 – Persistence evolution: Persist credentials using a versioned envelope with per-record `schemaVersion`, plus an upgrade pipeline that transforms stored documents into the current model during load (option 1: per-record versioned envelope – accepted; option 2: versioned collections – rejected; option 3: singleton registry flag – rejected).
- 2025-09-27 – OCRA credential metadata: Minimum persisted fields are `name` (globally unique), `ocraSuite`, `sharedSecretKey`, optional `counterValue` when the suite specifies `C`, and optional `pinHash` when the suite specifies `P{hash}`; challenge, session, and timestamp inputs remain per-transaction values guided by the suite definition. Custom credential metadata is supported via an arbitrary key/value map that is returned on query operations but excluded from cryptographic material.
- 2025-09-27 – OCRA test cadence: Commit Phase 1 test skeletons as disabled JUnit 5 cases capturing failing expectations; document the manual failure proof while keeping `./gradlew spotlessApply check` passing until the OCRA implementation lands.
- 2025-09-27 – OCRA property tests: Phase 1/T005 delivers disabled property-based tests that exercise secret encoding/decoding scenarios; they remain annotated with `@Disabled` until the codec implementation (T009) is ready, at which point the annotation is removed and the assertions replace placeholder `fail(...)` calls.
- 2025-09-27 – OCRA ArchUnit rules: Introduce ArchUnit tests under `core` that assert only designated entrypoints interact with `io.openauth.sim.core.credentials.ocra` internals. Suite re-enabled 2025-09-28 now that the package structure and public API are in place.
- 2025-09-27 – OCRA descriptor parsing: Descriptor creation parses the RFC 6287 suite into structured components (hash algorithm, response length, data inputs, drift) so subsequent factories reuse the normalised view instead of reparsing.
- 2025-09-27 – OCRA descriptor secret handling: Descriptors store secret material as the shared `SecretMaterial` type immediately; input normalisation happens ahead of descriptor construction to keep downstream code consistent.
- 2025-09-27 – OCRA secret normalisation (T009): Shared secret inputs accept RAW, HEX, or Base64 encodings via helper utilities that canonicalise to `SecretMaterial` while surfacing descriptive validation errors for malformed input; property-based tests (T005) now execute against these helpers.
- 2025-09-27 – Credential registry (T010): Introduced a core `CredentialRegistry` exposing `CredentialCapability` metadata and wiring the OCRA factory so facades can enumerate required attributes, optional fields, and supported hash functions for the protocol.
- 2025-09-27 – OCRA Phase 1 test strategy for T008: Re-enable only the previously disabled unit tests from T004 once validation/factory utilities are ready; keep the property-based suite (T005) and ArchUnit guards (T006) disabled until the corresponding functionality lands in T009/T010.
- 2025-09-28 – Persistence serialization bridge (T011): Define `VersionedCredentialRecord` schema version 1 and per-protocol adapters starting with OCRA, storing suite data under `ocra.*` keys and custom metadata under the `ocra.metadata.*` namespace ahead of MapDB integration.
- 2025-09-28 – MapDB integration (T012): `MapDbCredentialStore` now persists `VersionedCredentialRecord` envelopes (schema v1), upgrades legacy schema-0 OCRA entries by namespacing attributes, and surface migration errors when no upgrade path exists; utility mapper converts between persisted envelopes and in-memory `Credential` aggregates.
- 2025-09-28 – Validation telemetry (T013): Emit structured debug-level events named `ocra.validation.failure` with fields (`credentialName`, `suite`, `failureCode`, `messageId`) where `failureCode` denotes the failing stage (`CREATE_DESCRIPTOR`, `VALIDATE_CHALLENGE`, etc.) and payloads redact secrets; reuse this contract across facades for consistent observability.
- 2025-09-28 – RFC 6287 vectors (T017–T018): Appendix C of RFC 6287 (Simplified BSD license) publishes the Standard, Challenge/Response, Mutual Challenge/Response, and Plain Signature OCRA suites with sample secrets (`31323334…` / `3132333435363738393031323334353637383930…`), counter/time/session inputs, and expected OTP outputs; we materialised these as test-only fixtures while placeholder tests asserted `UnsupportedOperationException` until the execution helper became available. citeturn1search5
- 2025-09-28 – OCRA execution helper stub: `OcraResponseCalculator` exists as the eventual entry point for RFC 6287 evaluation and currently throws `UnsupportedOperationException`; placeholder tests enforce the TODO to swap in real OTP checks once the helper is implemented.
- 2025-09-28 – OCRA response evaluation contract (T019): The execution helper MUST implement RFC 6287 Section 5 and Appendix A semantics by hashing the ASCII suite name, `0x00` delimiter, and enabled data inputs in the canonical order `C | question | password | session | timestamp`, encoding counters and timestamps as 8-byte big-endian values, challenge strings as UTF-8 rendered to uppercase hex, session inputs as uppercase hex padded to the declared `Snnn` byte length (default 64 when omitted), and PIN hashes with their declared digest; apply HOTP dynamic truncation using the suite’s declared digit length. citeturn1search0turn1search1turn1search5

- 2025-09-28 – OCRA session coverage: Follow the IETF OCRA Internet-Draft test-vector guidance, which lists typical session lengths S064, S128, S256, and S512 and ships a reference generator; use it to derive additional fixtures (e.g., S128/S256) beyond RFC 6287 defaults for compliance tests. citeturn0search0turn0search5
- 2025-09-28 – OCRA extended session vectors (T021): Ran the draft generator logic with the standard 32-byte demo key (`3132333435363738393031323334353637383930313233343536373839303132`) and alphanumeric challenge `SESSION01`, deriving session payloads by repeating the published S064 pattern to 64/128/256/512-byte lengths; the resulting OTPs (`17477202`, `18468077`, `77715695`, `05806151`) are captured as test fixtures for S064/S128/S256/S512 suites. citeturn0search0turn0search5
- 2025-09-28 – OCRA session compliance (T022): Extended `OcraRfc6287ComplianceTest` to assert each S064/S128/S256/S512 vector produces its published OTP and that parsed suite metadata exposes the expected session byte lengths without leaking session payloads in validation errors. citeturn0search0turn0search5
- 2025-09-28 – Session-aware helper rollout: Begin wiring the session-enabled execution helpers into the CLI facade first, providing operators a deterministic way to validate S-length inputs before expanding to REST/UI surfaces (Option A selected over REST/UI/shared helper alternatives). citeturn0search0turn0search5
- 2025-09-28 – CLI session helper (T024): Added the `maintenance ocra` command accepting suite/key/challenge/session inputs, routing them through `OcraResponseCalculator`, printing redaction-friendly `suite=`/`otp=` output, and reusing the generator-derived S064/S128/S256/S512 vectors for regression coverage. citeturn0search0turn0search5
- 2025-09-28 – Session helper rollout (next step): REST facade work moved to Feature 003, which tracks the synchronous evaluation endpoint reusing the CLI-tested calculator (Option A). citeturn0search0turn0search5

## References
- `docs/4-architecture/features/001/plan.md`
- `docs/4-architecture/features/001/tasks.md`
- `docs/5-operations/analysis-gate-checklist.md`
