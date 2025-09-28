# Feature 001 – Core Credential Domain Specification

_Status: Draft_
_Last updated: 2025-09-27_

## Overview
Design a protocol-aware credential domain inside the `core` module that models credentials for FIDO2/WebAuthn, OATH/OCRA, the EU Digital Identity Wallet (ISO/IEC 18013-5 mDL, ISO/IEC 23220-2 mdoc profiles, SD-JWT and W3C Verifiable Credentials, and lifecycle protocols OpenID4VCI, OpenID4VP, ISO/IEC 18013-7), EMV/CAP, and generic use cases. Each protocol will live in its own Java package (`io.openauth.sim.core.credentials.ocra`, `...fido2`, `...eudiw`, `...emvcap`) so teams can roll out implementations independently. The domain must provide deterministic validation and transformation logic so downstream facades (CLI, REST, UI, JMeter plugin) can consume credential data without duplicating cryptographic rules.

## Objectives & Success Criteria
- Provide typed credential descriptors per protocol with clearly defined required and optional fields.
- Support secret material encodings (raw bytes, hexadecimal, Base64) and protocol-specific pre-processing hooks.
- Reject invalid credential payloads via validation rules with actionable error messages.
- Supply serialization/deserialization helpers for persistence and caching layers.
- Document the domain model in `docs/1-concepts` and keep the knowledge map in sync.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FR-001 | Represent FIDO2/WebAuthn credentials with AAGUID, credential ID, public key material, attestation/protocol metadata, and lifecycle flags. | Creating a WebAuthn credential succeeds only when these attributes are present and valid. |
| FR-002 | Model OATH/OCRA credentials with issuer, suite definition, counter/time parameters, and shared secret encodings. | Validation rejects missing suite identifiers or malformed secrets. |
| FR-003 | Support EUDI wallet credential suites (ISO/IEC 18013-5 mDL, ISO/IEC 23220-2 mdoc payloads, SD-JWT and W3C Verifiable Credentials) with document metadata, issuer attestations, data groups, and expiry controls. | EUDI credentials expose typed getters per protocol profile and validation catches missing mandatory elements. |
| FR-004 | Support EMV/CAP credential attributes such as PAN, issuer country, application PAN sequence number, and CA public keys. | EMV credential factories block construction when checksum or key sizes are invalid. |
| FR-005 | Provide a generic credential type for protocols not yet modeled while preserving secret handling contracts. | Generic credentials can be created with arbitrary metadata/secret pairs and reuse validation utilities. |
| FR-006 | Offer unified factories/builders that normalise mixed user input (hex/Base64/raw) into canonical internal representations. | Attempting to create credentials with malformed encodings results in descriptive exceptions. |
| FR-007 | Expose protocol capability introspection so facades can advertise supported credential operations. | A registry returns metadata about each credential type, including required attributes and supported crypto functions. |
| FR-008 | Produce error diagnostics suitable for CLI/REST responses without leaking secret material. | Validation errors redact secrets and point to offending fields. |
| FR-009 | Simulate EUDI wallet registration (issuance) and authentication (presentation) flows, including OpenID4VCI, OpenID4VP, and ISO/IEC 18013-7 session requirements. | Credential domain surfaces factories, state markers, and validation hooks supporting both issuance and presentation lifecycle stages. |
| FR-010 | Ensure every credential instance exposes a globally unique `name` plus an extensible key/value metadata map returned by lookup operations but ignored by crypto flows. | Creating credentials without a unique name or with malformed custom metadata must fail fast with descriptive errors. |

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
- **Performance testing**: As a load tester, I can use the JMeter plugin to warm caches by loading credential descriptors without relying on REST.

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
- 2025-09-27 – OCRA ArchUnit rules: Introduce ArchUnit tests under `core` that assert only designated entrypoints interact with `io.openauth.sim.core.credentials.ocra` internals. Keep the suite disabled until the package structure and public API are fully defined (Phase 2), then re-enable once descriptors/factories ship.
- 2025-09-27 – OCRA descriptor parsing: Descriptor creation parses the RFC 6287 suite into structured components (hash algorithm, response length, data inputs, drift) so subsequent factories reuse the normalised view instead of reparsing.
- 2025-09-27 – OCRA descriptor secret handling: Descriptors store secret material as the shared `SecretMaterial` type immediately; input normalisation happens ahead of descriptor construction to keep downstream code consistent.
- 2025-09-27 – OCRA secret normalisation (T009): Shared secret inputs accept RAW, HEX, or Base64 encodings via helper utilities that canonicalise to `SecretMaterial` while surfacing descriptive validation errors for malformed input; property-based tests (T005) now execute against these helpers.
- 2025-09-27 – Credential registry (T010): Introduced a core `CredentialRegistry` exposing `CredentialCapability` metadata and wiring the OCRA factory so facades can enumerate required attributes, optional fields, and supported hash functions for the protocol.
- 2025-09-27 – OCRA Phase 1 test strategy for T008: Re-enable only the previously disabled unit tests from T004 once validation/factory utilities are ready; keep the property-based suite (T005) and ArchUnit guards (T006) disabled until the corresponding functionality lands in T009/T010.
- 2025-09-28 – Persistence serialization bridge (T011): Define `VersionedCredentialRecord` schema version 1 and per-protocol adapters starting with OCRA, storing suite data under `ocra.*` keys and custom metadata under the `ocra.metadata.*` namespace ahead of MapDB integration.
- 2025-09-28 – MapDB integration (T012): `MapDbCredentialStore` now persists `VersionedCredentialRecord` envelopes (schema v1), upgrades legacy schema-0 OCRA entries by namespacing attributes, and surface migration errors when no upgrade path exists; utility mapper converts between persisted envelopes and in-memory `Credential` aggregates.

## References
- `docs/4-architecture/feature-plan-001-core-domain.md`
- `docs/4-architecture/tasks/feature-001-core-credential-domain.md`
- `docs/5-operations/analysis-gate-checklist.md`
