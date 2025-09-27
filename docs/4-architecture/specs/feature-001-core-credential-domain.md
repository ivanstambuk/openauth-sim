# Feature 001 – Core Credential Domain Specification

_Status: Draft_
_Last updated: 2025-09-27_

## Overview
Design a protocol-aware credential domain inside the `core` module that models credentials for FIDO2/WebAuthn, OATH/OCRA, EUDI mobile Driving Licence (mDL), EMV/CA, and generic use cases. The domain must provide deterministic validation and transformation logic so downstream facades (CLI, REST, UI, JMeter plugin) can consume credential data without duplicating cryptographic rules.

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
| FR-003 | Support EUDI mDL credential attributes including document number, issuing authority, data groups, and expiry metadata. | mDL credentials expose typed getters and validation catches missing mandatory data groups. |
| FR-004 | Support EMV/CA credential attributes such as PAN, issuer country, application PAN sequence number, and CA public keys. | EMV credential factories block construction when checksum or key sizes are invalid. |
| FR-005 | Provide a generic credential type for protocols not yet modeled while preserving secret handling contracts. | Generic credentials can be created with arbitrary metadata/secret pairs and reuse validation utilities. |
| FR-006 | Offer unified factories/builders that normalise mixed user input (hex/Base64/raw) into canonical internal representations. | Attempting to create credentials with malformed encodings results in descriptive exceptions. |
| FR-007 | Expose protocol capability introspection so facades can advertise supported credential operations. | A registry returns metadata about each credential type, including required attributes and supported crypto functions. |
| FR-008 | Produce error diagnostics suitable for CLI/REST responses without leaking secret material. | Validation errors redact secrets and point to offending fields. |

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

## References
- `docs/4-architecture/feature-plan-001-core-domain.md`
- `docs/4-architecture/tasks/feature-001-core-credential-domain.md`
- `docs/5-operations/analysis-gate-checklist.md`
