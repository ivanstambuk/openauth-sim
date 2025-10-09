# Feature Plan 024 – FIDO2/WebAuthn Operator Support

_Linked specification:_ `docs/4-architecture/specs/feature-024-fido2-webauthn-operator-support.md`  
_Status:_ Draft (planning)  
_Last updated:_ 2025-10-09

## Vision & Success Criteria
- Ship a parity WebAuthn assertion verification experience across core, persistence, CLI, REST, and operator UI facades, mirroring HOTP/OCRA ergonomics.
- Validate canonical W3C WebAuthn Level 3 §16 authentication vectors end-to-end, then extend coverage to the synthetic JSONL bundle to safeguard algorithm breadth (ES256/384/512, RS256, PS256, Ed25519).
- Enable operators to preload curated stored credentials and inject inline assertion vectors via UI presets, with key material surfaced as JWKs.
- Maintain green coverage/quality gates (`spotlessApply`, SpotBugs, ArchUnit, reflectionScan, Jacoco ≥0.90, PIT ≥ baseline).

## Scope Alignment
- In scope: verification engine, persistence schema entries, telemetry integration, CLI/REST/UI wiring, W3C + JSONL fixtures, seed/preset utilities, documentation.
- Out of scope: registration/attestation ceremonies, authenticator emulation, dependency upgrades, UI issuance flows.

## Dependencies & Interfaces
- Reuse existing MapDB schema v1 (Feature 002) via `CredentialStoreFactory`.
- Telemetry events must flow through `TelemetryContracts` (`application` module).
- Operator console tab already present from Feature 020; this plan activates the FIDO2/WebAuthn content and REST wiring.
- JSON bundle located at `docs/webauthn_assertion_vectors.json`; W3C vector conversions to be stored under `docs/webauthn_w3c_vectors/` (new).
- JSON bundle stores high-entropy payloads as 16-character base64url segments to satisfy gitleaks; ingest utilities must join segments before verification.

## Increment Breakdown (≤10 min each)
Each increment stages failing tests first, drives implementation to green, and runs `./gradlew spotlessApply check`.

1. **I1 – Fixture bootstrap (tests only)**  
   - Convert targeted W3C §16 authentication vectors to Base64url fixtures under `docs/webauthn_w3c_vectors/`.  
   - Add failing core tests asserting successful verification for ES256 baseline and expected failures (RP ID mismatch, bad signature).  
   - Analysis gate: update spec clarifications with vector references.

2. **I2 – Verification engine skeleton**  
   - Implement minimal parser/validator in `core/fido2` package satisfying I1 tests.  
   - Cover flag parsing, RP ID hash, `clientDataJSON` validation, signature base assembly.  
   - Add failure branch tests (UV flag requirements, type/origin mismatch).

3. **I3 – Persistence descriptors**  
   - Extend MapDB schema v1 with FIDO2 credential metadata (algorithm, credential ID, public key, flags).  
   - Add failing integration tests mixing HOTP/TOTP/OCRA/FIDO2 records; implement to green.  
   - Ensure seeds use curated subset.

4. **I4 – Application services & telemetry**  
   - Stage failing application-level tests for stored/inline evaluation + replay diagnostics.  
   - Implement services returning rich diagnostics, emitting `fido2.evaluate`/`fido2.replay` telemetry with redacted payloads.

5. **I5 – CLI façade**  
   - Add failing Picocli integration tests for evaluate/replay commands (stored + inline).  
   - Implement commands, ensure help/validation parity with HOTP/OCRA.

6. **I6 – REST endpoints + OpenAPI**  
   - Introduce failing MockMvc tests for `/api/v1/webauthn/evaluate`, `/evaluate/inline`, `/replay`.  
   - Wire controllers, request/response DTOs, telemetry, OpenAPI updates.

7. **I7 – Operator UI enablement**  
   - Extend Selenium/system tests to assert tab activation, mode switching, preset buttons, seed control, accessibility.  
   - Implement Thymeleaf/JS updates to enable forms, load vectors (inline) and seed curated stored credentials.  
   - Surface key material as JWK in UI modals/panels.

8. **I8 – JSONL coverage expansion**  
   - Add failing parameterised tests iterating over JSONL bundle entries across core/application layers.  
   - Implement ingestion utilities (parsing Base64url, verifying algorithms) ensuring deterministic seeds.  
   - Update UI preset catalogue to include JSONL-only flows where useful.

9. **I9 – Documentation & knowledge sync**  
   - Update how-to guides, roadmap, knowledge map, and protocol docs to reflect WebAuthn launch.  
   - Record telemetry contract additions, seed vector catalogue, and operator instructions.

10. **I10 – Quality gate + follow-up capture**  
    - Run `./gradlew qualityGate`.  
    - Finalise feature documentation, resolve remaining TODOs, record lessons in plan/roadmap.  
    - Prepare conventional commit and ensure push after passing checks.

## Risks & Mitigations
- **Large vector set increases test time** → run JSONL suite in targeted tests (I8) with caching helpers; consider tagging for selective execution.  
- **Signature algorithm variance** → rely on deterministic fixtures; add explicit assertions for DER decoding vs raw Ed25519.  
- **UI seed overload** → curated subset for seed button; document advanced usage for manual imports.

## Telemetry & Observability
- Add `TelemetryContracts` entries: `fido2.evaluate` and `fido2.replay`, capturing algorithm, RP ID, UV flag, outcome.  
- Ensure CLI/REST/UI call the shared adapters; include unit tests verifying redaction.

## Intent & Tooling Notes
- Session decisions (2025-10-09) captured in spec clarifications and open-questions log (resolved).  
- Planning work performed via Codex CLI; no implementation yet.  
- JSONL + W3C fixtures will be version-controlled to avoid external fetches.
