# Feature 024 – FIDO2/WebAuthn Operator Support

_Status: Complete_  
_Last updated: 2025-10-15_

## Overview
Deliver an end-to-end FIDO2/WebAuthn assertion verification capability across the simulator. The feature introduces a core verification engine, persistence descriptors, application services, CLI commands, REST endpoints, and operator console panels that mirror the existing HOTP and OCRA evaluate/replay experiences. Work begins by validating canonical W3C WebAuthn Level 3 §16 authentication vectors, then expands immediately to cover the synthetic JSON assertion bundle committed under `docs/webauthn_assertion_vectors.json`.

## Clarifications
- 2025-10-09 – Launch the full stack (core engine, persistence, application services, CLI, REST API, operator UI) as a single feature slice to match HOTP/OCRA parity (user selected Option A).
- 2025-10-09 – Support both stored-credential and inline assertion evaluations plus a replay-only diagnostics flow from day one (user selected Option A).
- 2025-10-09 – Begin with W3C §16 authentication vectors as the authoritative baseline, then immediately follow with coverage for the synthetic JSONL bundle so both sources are exercised in CI (user direction “start with B then immediately A”).
- 2025-10-09 – “Seed sample credentials” should load a curated subset of stored credentials—one representative entry per targeted algorithm/flag combination—rather than the entire catalog (user selected Option A).
- 2025-10-11 – “Seed sample credentials” remains a stored-only affordance; hide or disable it whenever inline-only parameter panels are active to avoid confusing operators (bugfix directive).
- 2025-10-14 – Keep the FIDO2 stored-mode “Seed sample credential” control visible even when the registry already contains entries; align behaviour with HOTP/TOTP/OCRA by only hiding the control when inline mode is selected (user confirmed Option A).
- 2025-10-14 – Match the compact left-aligned styling used by HOTP/TOTP/OCRA for the seed control so FIDO2 no longer renders a full-width button (user direction).
- 2025-10-14 – When seeding FIDO2 sample credentials and no new entries are required, display the parity warning copy “Seeded 0 sample credentials. All sample credentials are already present.” to match HOTP/TOTP/OCRA behaviour (user selected Option B).
- 2025-10-14 – Align the stored Evaluate signature counter controls with inline mode: pre-fill the field with the stored credential’s counter, keep it read-only while “Use current Unix seconds” remains checked, provide the “Reset to now” helper, and allow overrides when the toggle is cleared (user selected Option A).
- 2025-10-14 – Restyle the FIDO2 replay result card to match the HOTP/TOTP/OCRA panel: retain the status badge and only display “Reason Code” and “Outcome” rows, removing telemetry lines entirely (user confirmation).
- 2025-10-14 – Normalize successful FIDO2 replay/evaluation outcomes to use the “match” reason code so parity holds across HOTP/TOTP/OCRA (user preference).
- 2025-10-09 – “Load a sample vector” buttons on evaluate and replay panels should pull from entries in `docs/webauthn_assertion_vectors.json`, pre-populating request/response payloads (user selected Option A).
- 2025-10-09 – When surfacing sample key material in the UI, prefer JWK representations over PEM/PKCS#8 to keep output compact (user instruction).
- 2025-10-11 – JSON vector bundles now keep base64 payloads as single-line strings and rely on a repository-level `.gitleaks.toml` allowlist for the fixture files; the data is synthetic test-only material, not real secrets, so the exemption does not weaken leak detection (maintainer note).
- 2025-10-11 – `key_material` entries expose a `keyPairJwk` object (renamed from `publicKeyJwk`) that carries both public and private JWK components (`d`/factor fields where applicable) so generator flows can surface algorithm-specific private material without relying on PEM fixtures (maintainer note).
- 2025-10-15 – Replay UI must display the full assertion payload fields inline (visible textareas mirroring the underlying request) so operators can inspect and edit them directly; fields stay read-only only where the protocol requires it (user selected Option A).
- 2025-10-15 – Default the “Authenticator data (Base64URL)” textarea height to three rows; typical WebAuthn `authData` blobs are <160 Base64url characters, so operators can see the entire value without excess whitespace while retaining manual resize controls (user tweak request).
- 2025-10-15 – Raise the default height of the “Client challenge (Base64URL)” textarea to three rows; challenges are usually 32–48 bytes (≈43–64 characters Base64url), so a multi-line box keeps the value visible without requiring horizontal scrolling (user tweak request).
- 2025-10-15 – Update the replay public-key label to note the accepted formats (JWK, COSE Base64URL, or PEM) so operators know the field auto-detects each representation (copy tweak).
- 2025-10-15 – Sample presets should populate the replay public-key textarea with the JWK form by default while retaining COSE/JWK/PEM manual support (user request).
- 2025-10-15 – Stored replay dropdown must load with the placeholder (“Select a stored credential”) selected, leaving the Evaluate/Replay buttons active and relying on the existing validation message if operators submit without choosing (user selected Option A).
- 2025-10-15 – Switching the stored credential refreshes both stored Evaluate and stored Replay forms (including status/result panels) so challenges, signatures, and counters always match the current selection (user selected Option A).
- 2025-10-09 – W3C §16.1 authentication fixtures now reside in `docs/webauthn_w3c_vectors.json`; each field carries both the original hex literal and a Base64url encoding so loaders no longer juggle `h'…'` parsing (worklog note).
- 2025-10-10 – CLI/REST/UI how-to guides live under `docs/2-how-to/use-fido2-*.md`, capturing the JSON vector workflow, JWK guidance, and curated operator preset behaviour chosen in Option A.
- 2025-10-10 – Keep the full JSON vector catalogue exposed through CLI (`--vector-id`, `vectors`) and REST sample payloads for reproducibility, while trimming operator UI presets to a curated representative subset (user selected Option A).
- 2025-10-10 – When aligning the operator UI layout with HOTP/TOTP/OCRA, maintain the JSON “Load sample vector” controls within the inline forms on both Evaluate and Replay tabs so fixture workflows remain available (user selected Option A).
- 2025-10-10 – Repurpose the Evaluate tab into an authenticator-style assertion generator: accept relying party data, challenge, and the authenticator private key, then emit a signed WebAuthn assertion; verification-only workflows live under Replay (user selected Option A).
- 2025-10-10 – Authenticator private keys entered on the Evaluate tab must support auto-detected JWK (preferred) and PEM/PKCS#8 encodings, returning clear validation errors when parsing fails; start with ES256 coverage while keeping the design extensible for the remaining algorithms.
- 2025-10-10 – CLI `fido2 evaluate` stored/inline commands transition to assertion generation outputs (Option A); verification flows continue under Replay.
- 2025-10-10 – Operator UI Evaluate result card presents a structured `PublicKeyCredential` JSON payload (with copy/download helpers) in place of the prior status-only telemetry block (Option A).
- 2025-10-10 – `Fido2OperatorSampleData` presets surface ES256 JWK private keys exclusively when pre-filling generator forms (Option A); PEM/PKCS#8 conversions remain a manual step documented in how-to guides.
- 2025-10-12 – When loading the console with `protocol=fido2&tab=<evaluate|replay>&mode=<inline|stored>`, always honour the query parameters on initial load/refresh even if the stored preference differs; legacy links using `fido2Mode` remain supported for backward compatibility (user selected Option A; clarified 2025-10-13).
- 2025-10-13 – Harmonise deep-link initialisation by having the router call each protocol module’s `setMode(..., { broadcast: false, force: true })` (HOTP, TOTP, OCRA, FIDO2) when a `mode` query parameter is present so stored/inline selections stick across reloads without relying on synthetic radio clicks (user confirmed shared Option A).
- 2025-10-12 – Replay APIs continue to expect Base64URL COSE public keys for now; Task T17 (Replay public-key format expansion) will extend the single `publicKey` field to auto-detect COSE, JWK, or PEM inputs (user selected Option A; implementation pending).
- 2025-10-13 – Clamp the generated assertion result panel width and provide horizontal scrolling so the evaluation column retains its layout when rendering long payloads (user selected Option A).
- 2025-10-13 – Remove the stored “Load preset challenge & key” button; selecting a stored credential auto-populates preset values, and re-selecting the placeholder option resets the form (user directive).
- 2025-10-13 – Stored Evaluate result cards no longer display the telemetry metadata line; the panel now surfaces only the generated `PublicKeyCredential` JSON while telemetry remains available in logs (user directive).
- 2025-10-13 – Harmonise operator console deep links across protocols: URLs must use `protocol=<key>`, `tab=<evaluate|replay>`, and `mode=<inline|stored>` for HOTP, TOTP, OCRA, and FIDO2 so shared bookmarks restore the correct panels (user selected Option B).
- 2025-10-13 – When an operator clicks a protocol tab, default the interface to the Evaluate tab with Inline mode selected for that protocol, overriding prior mode/tab state to provide a predictable starting point (user directive).
- 2025-10-13 – Harmonise operator console deep links across protocols: URLs must use `protocol=<key>`, `tab=<evaluate|replay>`, and `mode=<inline|stored>` for HOTP, TOTP, OCRA, and FIDO2 so shared bookmarks restore the correct panels (user selected Option B).
- 2025-10-13 – Remove the stored “Copy JSON” and “Download JSON” controls; operators can continue copying directly from the code block while inline mode keeps its copy/download helpers (user directive).
- 2025-10-14 – Stored evaluate dropdown must mirror other protocol panels: render beneath the label with stacked styling and the dark surface background instead of the default browser chrome (user directive referencing UI screenshots).
- 2025-10-14 – Stored credential dropdown entries must adopt algorithm-first labels (e.g., “EdDSA (W3C 16.1.10)”, “PS256”) and drop the “Seed … generator preset” prefix so the menu matches HOTP/TOTP/OCRA naming parity (user directive).
- 2025-10-14 – When Evaluate runs in stored mode, omit the authenticator private-key textarea entirely; generation uses the persisted key, and operators must switch to inline mode if they need to inspect or edit it. Do not add inline hints prompting the switch.
- 2025-10-14 – Stored-mode Evaluate renders the relying-party ID as a read-only value (label + static text) so operators can confirm which relying party the credential targets without mutating it; origin, challenge, counter, and UV flags remain editable overrides.
- 2025-10-12 – Sample vector renderers should list `kty` as the first property when emitting JWK key material so operators see the key type immediately; Task T18 will reorder the serialized fields across CLI/REST/UI presets (implementation pending).
- 2025-10-11 – Synthetic JSON vectors now include JWK private keys for every supported algorithm (ES256/384/512, RS256, PS256, Ed25519); generator presets across CLI/REST/UI must ingest these so operators can produce assertions without manual key entry.
- 2025-10-12 – Operator console “Load sample vector” dropdowns and seed actions default to the W3C fixture for a given algorithm when a private key exists; fall back to the synthetic bundle only when the specification omits material (e.g., PS256). CLI/REST surfaces share the same preset precedence via `WebAuthnGeneratorSamples`.
- 2025-10-12 – Core verification suites must continue to exercise both fixture sources (`docs/webauthn_w3c_vectors.json` and `docs/webauthn_assertion_vectors.json`) so regressions in either dataset are caught before UI wiring diverges.
- 2025-10-12 – Inline preset dropdown entries should suffix “(W3C Level 3)” whenever the source fixture originates from the specification, mirroring the RFC labelling approach used for HOTP/TOTP/OCRA presets.
- 2025-10-12 – Expand the W3C WebAuthn Level 3 §16.1.6 fixture set beyond `packed-es256` by converting the ES384, ES512, RS256, PS256, and Ed25519 authentication examples into `docs/webauthn_w3c_vectors/` assets so engine, CLI, REST, and UI suites have spec-authored references alongside the synthetic JSON bundle (maintainer directive).
- 2025-10-12 – W3C WebAuthn Level 3 currently omits a packed PS256 authentication fixture; retain the synthetic PS256 entries in `docs/webauthn_assertion_vectors.json` until the specification publishes an official sample (documented gap).
- 2025-10-12 – Merge the W3C packed attestation fixtures into a single JSON array (`docs/webauthn_w3c_vectors.json`) per Option A so duplicate `.properties`/`.json` pairs are eliminated (user directive).
- 2025-10-13 – W3C vectors must surface canonical Base64url values and expose per-vector private keys where provided so generator presets can bind directly to the spec data; verification loaders should consolidate on the new JSON source (user directive).
- 2025-10-13 – Normalize RS256 `private_key_p`/`private_key_q` factors into `{hex, base64Url}` objects inside `docs/webauthn_w3c_vectors.json` so the loader derives RSA private JWK parameters without relying on synthetic fallbacks.
- 2025-10-13 – Replay endpoints must auto-detect Base64URL COSE blobs, JSON JWK objects, or PEM/PKCS#8 payloads supplied via `publicKey`; invalid payloads should surface a `public_key_format_invalid` reason while preserving legacy COSE support (owner directive).
- 2025-10-11 – Inline signature counter field defaults to the current Unix seconds value, with a checked toggle labeled “Use current Unix seconds” that keeps the field read-only until operators uncheck it; a “Reset to now” helper button reapplies the latest epoch-second snapshot.
- 2025-10-12 – Evaluate and replay call-to-action buttons must surface mode-specific wording: “Generate inline assertion” / “Generate stored assertion” for Evaluate and “Replay inline assertion” / “Replay stored assertion” for Replay, mirroring HOTP/TOTP parity while preserving WebAuthn terminology (user directive).
- 2025-10-11 – FIDO2 Evaluate/Replay result cards stay hidden until after the first submission so the panels mirror HOTP/TOTP/OCRA behaviour (user selected Option A).
- 2025-10-11 – Position the “Reset to now” helper immediately to the right of the “Use current Unix seconds” label so the toggle and action read as one control cluster (layout parity directive). Maintain comfortable spacing by aligning the button to the right edge of the field and adding vertical breathing room beneath the counter input.
- 2025-10-11 – Inline/stored challenge inputs remain textareas for multi-line edits but default to a single-row height (matching credential ID fields) so the form stays compact until operators expand them.
- 2025-10-11 – Sample-loaded private keys pretty-print JWK JSON (two-space indent) while leaving manual entries untouched for readability parity across protocols.
- 2025-10-11 – Authenticator private-key inputs stack the textarea beneath the label via a dedicated field-group class with shared dark styling so the control aligns vertically across layouts.
- 2025-10-11 – Removed inline/helper hint copy beneath private-key inputs to reduce clutter now that layout clarifies the expected entry format.
- 2025-10-11 – Inline preset dropdown copy must mirror the other protocol panels: rename the control label to “Load a sample vector” and surface dropdown entries as “<algorithm> sample vector” rather than “… generator preset” (user request for cross-protocol parity).
- 2025-10-11 – Inline preset controls on Evaluate and Replay tabs must sit directly beneath the mode selector with the shared `stack-offset-top-lg` spacing token, matching HOTP/TOTP/OCRA layout (user request).
- 2025-10-11 – Inline preset dropdown must render with the placeholder selected on initial load; do not auto-apply the first sample vector until an operator makes an explicit selection (user request).
- 2025-10-11 – Inline and stored generator panels render the relying party ID and origin inputs on the same row to reduce vertical scrolling while keeping individual labels visible.
- 2025-10-11 – FIDO2 Evaluate tab must default to inline parameters mode (stored selectable afterward) so the authenticator generator aligns with HOTP/TOTP/OCRA defaults (defect report; Option A selected).
- 2025-10-11 – Inline generator parse failures must surface the `private_key_invalid` reason so CLI/REST/UI error handling stays aligned across facades (bugfix directive).
- 2025-10-11 – Inline Credential ID text areas must default to a single-line height while remaining resizable multi-line controls so long values can still expand without crowding the panel.
- 2025-10-21 – Increase the inline assertion “Authenticator private key (JWK or PEM/PKCS#8)” textarea height so bundled JWK presets render without a default vertical scrollbar; attestation inputs already meet the ergonomics target (user instruction).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| FWS-001 | Implement a WebAuthn assertion verification engine that validates authenticator data, client data JSON, RP ID hash, challenge, origin, and signature according to WebAuthn Level 3 §16 and §6. | Core unit tests cover success and failure cases (mismatched RP ID hash, type/origin violations, signature failures, counter regressions). |
| FWS-002 | Persist WebAuthn credentials in MapDB schema v1 alongside existing HOTP/OCRA/TOTP entries, including algorithm metadata, credential IDs, and associated public keys. | Persistence integration tests round-trip stored FIDO2 credentials and ensure schema upgrades remain backward compatible. |
| FWS-003 | Expose application services for stored and inline assertion generation that accept relying-party metadata, authenticator state (private key, counter, UV flag), and challenges, producing signed WebAuthn assertion payloads with sanitized telemetry; replay diagnostics continue to reuse the verification engine. | Application-layer tests cover generator happy path, validation failures (missing key material, malformed challenge), and ensure Replay continues to verify generated assertions without mutating state. |
| FWS-004 | Provide CLI commands to evaluate stored and inline assertions and to trigger replay diagnostics, mirroring HOTP/OCRA command ergonomics. | Picocli integration tests validate help output, successful verification, error messaging, and replay diagnostics. |
| FWS-005 | Deliver REST endpoints: `POST /api/v1/webauthn/evaluate` (stored), `POST /api/v1/webauthn/evaluate/inline`, and `POST /api/v1/webauthn/replay`, with OpenAPI documentation and telemetry integration. | MockMvc tests assert payload schema, validation errors, telemetry emission, and replay non-mutation; OpenAPI snapshot updated. |
| FWS-006 | Update the operator console UI with FIDO2/WebAuthn evaluate and replay panels featuring stored/inline modes, “Load a sample vector” controls, and a stored-only “Seed sample credentials” button. | Selenium/system tests verify panel enablement, preset behaviour, telemetry surfaces, query-param deep links (`protocol=fido2`), and confirm stored mode never renders the authenticator private-key field while still surfacing the relying-party ID as read-only. |
| FWS-007 | Validate W3C §16 authentication vectors end-to-end (core, CLI, REST, UI presets) before enabling the synthetic JSONL bundle, ensuring the generator can reproduce known assertions when supplied with the canonical private keys. | Dedicated tests ingest converted W3C vectors (hex → Base64url) and assert generation + verification success/failure as documented. |
| FWS-008 | Extend test coverage to include the JSONL bundle immediately after W3C vectors pass, ensuring every algorithm/flag combination verifies successfully. | CI suite iterates over JSONL entries, seeding/clearing MapDB as needed; failures identify specific vector IDs. |
| FWS-009 | Surface sample key material in JWK form throughout UI presets and documentation, avoiding PEM displays and ordering properties so `kty` appears first. | UI and doc snapshots show JWK output with `kty` leading each object; tests assert absence of PEM markers and confirm canonical ordering. |
| FWS-010 | Display curated algorithm labels on the inline preset dropdown so operators can quickly spot the signature suite in use. | Selenium coverage asserts visible option text includes the algorithm label per curated preset order. |
| FWS-011 | Align Evaluate/Replay CTA button copy with the selected mode, using inline vs stored assertion wording to match HOTP/TOTP parity. | Selenium tests toggle modes and assert the visible button text matches “Generate inline assertion” / “Generate stored assertion” and “Replay inline assertion” / “Replay stored assertion.” |
| FWS-012 | Extend replay flows (application, REST, CLI) to accept Base64URL COSE, JSON JWK, or PEM/PKCS#8 public-key inputs while emitting format-specific validation errors. | New tests exercise each format, expect successful verification for valid inputs, and assert `public_key_format_invalid` errors for malformed payloads. |

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
