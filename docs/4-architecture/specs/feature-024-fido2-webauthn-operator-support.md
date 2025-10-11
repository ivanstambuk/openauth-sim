# Feature 024 – FIDO2/WebAuthn Operator Support

_Status: In Progress_  
_Last updated: 2025-10-10_

## Overview
Deliver an end-to-end FIDO2/WebAuthn assertion verification capability across the simulator. The feature introduces a core verification engine, persistence descriptors, application services, CLI commands, REST endpoints, and operator console panels that mirror the existing HOTP and OCRA evaluate/replay experiences. Work begins by validating canonical W3C WebAuthn Level 3 §16 authentication vectors, then expands immediately to cover the synthetic JSON assertion bundle committed under `docs/webauthn_assertion_vectors.json`.

## Clarifications
- 2025-10-09 – Launch the full stack (core engine, persistence, application services, CLI, REST API, operator UI) as a single feature slice to match HOTP/OCRA parity (user selected Option A).
- 2025-10-09 – Support both stored-credential and inline assertion evaluations plus a replay-only diagnostics flow from day one (user selected Option A).
- 2025-10-09 – Begin with W3C §16 authentication vectors as the authoritative baseline, then immediately follow with coverage for the synthetic JSONL bundle so both sources are exercised in CI (user direction “start with B then immediately A”).
- 2025-10-09 – “Seed sample credentials” should load a curated subset of stored credentials—one representative entry per targeted algorithm/flag combination—rather than the entire catalog (user selected Option A).
- 2025-10-09 – “Load a sample vector” buttons on evaluate and replay panels should pull from entries in `docs/webauthn_assertion_vectors.json`, pre-populating request/response payloads (user selected Option A).
- 2025-10-09 – When surfacing sample key material in the UI, prefer JWK representations over PEM/PKCS#8 to keep output compact (user instruction).
- 2025-10-09 – The JSON vector bundle stores base64 payloads as 16-character segments so gitleaks remains green; ingestion helpers must join the segments before invoking verifiers (maintainer note).
- 2025-10-09 – W3C §16.1.6 authentication fixtures live under `docs/webauthn_w3c_vectors/packed-es256.properties` with base64url payloads for attestation and assertion data (worklog note).
- 2025-10-10 – CLI/REST/UI how-to guides live under `docs/2-how-to/use-fido2-*.md`, capturing the JSON vector workflow, JWK guidance, and curated operator preset behaviour chosen in Option A.
- 2025-10-10 – Keep the full JSON vector catalogue exposed through CLI (`--vector-id`, `vectors`) and REST sample payloads for reproducibility, while trimming operator UI presets to a curated representative subset (user selected Option A).
- 2025-10-10 – When aligning the operator UI layout with HOTP/TOTP/OCRA, maintain the JSON “Load sample vector” controls within the inline forms on both Evaluate and Replay tabs so fixture workflows remain available (user selected Option A).
- 2025-10-10 – Repurpose the Evaluate tab into an authenticator-style assertion generator: accept relying party data, challenge, and the authenticator private key, then emit a signed WebAuthn assertion; verification-only workflows live under Replay (user selected Option A).
- 2025-10-10 – Authenticator private keys entered on the Evaluate tab must support auto-detected JWK (preferred) and PEM/PKCS#8 encodings, returning clear validation errors when parsing fails; start with ES256 coverage while keeping the design extensible for the remaining algorithms.
- 2025-10-10 – CLI `fido2 evaluate` stored/inline commands transition to assertion generation outputs (Option A); verification flows continue under Replay.
- 2025-10-10 – Operator UI Evaluate result card presents a structured `PublicKeyCredential` JSON payload (with copy/download helpers) in place of the prior status-only telemetry block (Option A).
- 2025-10-10 – `Fido2OperatorSampleData` presets surface ES256 JWK private keys exclusively when pre-filling generator forms (Option A); PEM/PKCS#8 conversions remain a manual step documented in how-to guides.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FWS-001 | Implement a WebAuthn assertion verification engine that validates authenticator data, client data JSON, RP ID hash, challenge, origin, and signature according to WebAuthn Level 3 §16 and §6. | Core unit tests cover success and failure cases (mismatched RP ID hash, type/origin violations, signature failures, counter regressions). |
| FWS-002 | Persist WebAuthn credentials in MapDB schema v1 alongside existing HOTP/OCRA/TOTP entries, including algorithm metadata, credential IDs, and associated public keys. | Persistence integration tests round-trip stored FIDO2 credentials and ensure schema upgrades remain backward compatible. |
| FWS-003 | Expose application services for stored and inline assertion generation that accept relying-party metadata, authenticator state (private key, counter, UV flag), and challenges, producing signed WebAuthn assertion payloads with sanitized telemetry; replay diagnostics continue to reuse the verification engine. | Application-layer tests cover generator happy path, validation failures (missing key material, malformed challenge), and ensure Replay continues to verify generated assertions without mutating state. |
| FWS-004 | Provide CLI commands to evaluate stored and inline assertions and to trigger replay diagnostics, mirroring HOTP/OCRA command ergonomics. | Picocli integration tests validate help output, successful verification, error messaging, and replay diagnostics. |
| FWS-005 | Deliver REST endpoints: `POST /api/v1/webauthn/evaluate` (stored), `POST /api/v1/webauthn/evaluate/inline`, and `POST /api/v1/webauthn/replay`, with OpenAPI documentation and telemetry integration. | MockMvc tests assert payload schema, validation errors, telemetry emission, and replay non-mutation; OpenAPI snapshot updated. |
| FWS-006 | Update the operator console UI with FIDO2/WebAuthn evaluate and replay panels featuring stored/inline modes, “Load a sample vector” controls, and a stored-only “Seed sample credentials” button. | Selenium/system tests verify panel enablement, preset behaviour, telemetry surfaces, and query-param deep links (`protocol=fido2`). |
| FWS-007 | Validate W3C §16 authentication vectors end-to-end (core, CLI, REST, UI presets) before enabling the synthetic JSONL bundle, ensuring the generator can reproduce known assertions when supplied with the canonical private keys. | Dedicated tests ingest converted W3C vectors (hex → Base64url) and assert generation + verification success/failure as documented. |
| FWS-008 | Extend test coverage to include the JSONL bundle immediately after W3C vectors pass, ensuring every algorithm/flag combination verifies successfully. | CI suite iterates over JSONL entries, seeding/clearing MapDB as needed; failures identify specific vector IDs. |
| FWS-009 | Surface sample key material in JWK form throughout UI presets and documentation, avoiding PEM displays. | UI and doc snapshots show JWK output; tests assert absence of PEM markers. |

## Non-Functional Requirements
| ID | Requirement | Acceptance Signal |
|-----|-------------|-------------------|
| FWS-NFR-001 | Maintain existing coverage thresholds (Jacoco ≥0.90 line/branch) and mutation testing once new modules/tests land. | `./gradlew qualityGate` passes after feature completion. |
| FWS-NFR-002 | Keep SpotBugs dead-state, ArchUnit rules, and reflectionScan passing; avoid reflection in new code. | `./gradlew spotlessApply check` remains green. |
| FWS-NFR-003 | Ensure UI updates preserve accessibility (ARIA labels, keyboard navigation) for new buttons and form controls. | Selenium accessibility checks and axe-core scans pass. |
| FWS-NFR-004 | Telemetry must exclude secret material (challenges, raw IDs, signatures) while recording algorithm, origin, user verification flags, and evaluation outcome. | Telemetry tests assert redaction; log sanitiser checks stay green. |

## In Scope
- Core WebAuthn assertion parsing, flag validation, and signature verification for ES256/ES384/ES512, RS256, PS256, and Ed25519.
- Persistence schema entries for WebAuthn credentials, including metadata required for replay diagnostics.
- CLI, REST, and UI integrations with parity to HOTP/OCRA features.
- Conversion of W3C §16 vectors into repository fixtures and orchestration of JSONL vector coverage.
- Seed and preset utilities for stored credentials and inline vector loading.
- Documentation updates (how-to guides, knowledge map, operator console docs) and telemetry wiring.

## Out of Scope
- WebAuthn registration (attestation) flows and authenticator provisioning.
- CTAP2 transport emulation or authenticator hardware simulation.
- Browser automation beyond existing operator console UI tests.
- Credential issuance UX (saved for future features).

## Dependencies & Constraints
- Reuse MapDB schema v1; any additions must remain compatible with HOTP/OCRA/TOTP records.
- Telemetry routing continues through `TelemetryContracts`; no direct logger emissions.
- JSON vector file (`docs/webauthn_assertion_vectors.json`) is the canonical synthetic dataset; maintainers may append entries but must keep format stable.
- W3C §16 vectors must be stored locally (converted to Base64url) to avoid repeated external scraping.

## Test Strategy
1. Stage failing unit tests for the verification engine covering success and failure scenarios per WebAuthn §16.
2. Add failing persistence integration tests for stored credential retrieval and replay metadata.
3. Introduce failing application service tests for stored, inline, and replay flows (including telemetry assertions).
4. Create failing CLI integration tests for evaluate/replay commands.
5. Add failing REST MockMvc tests for stored/inline/replay endpoints with OpenAPI snapshot updates.
6. Extend Selenium/system tests to cover operator console activation, preset buttons, seed controls, and accessibility.
7. Convert W3C vectors into repository fixtures and add failing tests to ensure they verify end-to-end.
8. After W3C tests pass, add failing tests that iterate over the JSONL bundle to assert synthetic coverage.
9. Run `./gradlew spotlessApply check` and `./gradlew qualityGate` before closing the feature.

## Follow-up Considerations
- Future feature may introduce registration ceremonies to generate authentic WebAuthn credentials.
- Explore caching strategies for large vector bundles to keep test time manageable.
- Consider exposing analytics for verification outcomes (per algorithm/origin) once telemetry pipelines mature.
- Schedule a HOTP/TOTP preset parity feature to mirror the WebAuthn `--vector-id` ergonomics once curated fixture sets exist for those protocols (documented today as a deferred follow-up).
