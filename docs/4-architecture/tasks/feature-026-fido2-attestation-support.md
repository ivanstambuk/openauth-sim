# Feature 026 Tasks – FIDO2/WebAuthn Attestation Support

_Linked plan:_ `docs/4-architecture/feature-plan-026-fido2-attestation-support.md`  
_Status:_ Proposed  
_Last updated:_ 2025-10-12

☐ **T2601 – Fixture scaffolding**  
 ☐ Convert selected W3C Level 3 attestation examples and synthetic fixtures into JSON assets under `docs/`.  
 ☐ Add failing core attestation generation/verification tests for packed, FIDO-U2F, TPM, and Android Key formats.  

☐ **T2602 – Core attestation engine implementation**  
 ☐ Implement attestation helpers satisfying T2601 tests, including format-specific validation and error mapping.  
 ☐ Add failure-branch unit tests (bad signatures, unsupported formats, certificate issues).  

☐ **T2603 – Application services & telemetry**  
 ☐ Stage failing tests for `fido2.attest` / `fido2.attestReplay` telemetry emission.  
 ☐ Implement generation/replay services with sanitized telemetry and inline-only handling.  

☐ **T2604 – CLI commands**  
 ☐ Add failing Picocli tests covering attestation generation and replay (format selection, validation errors).  
 ☐ Implement CLI commands wired to the new services and update help output.  

☐ **T2605 – REST endpoints**  
 ☐ Add failing MockMvc + OpenAPI snapshot tests for `/api/v1/webauthn/attest` and `/api/v1/webauthn/attest/replay`.  
 ☐ Implement controllers/DTOs with validation, telemetry, and sanitized responses; regenerate OpenAPI artifacts.  

☐ **T2606 – Operator UI Evaluate toggle**  
 ☐ Add failing Selenium tests asserting the Assertion/Attestation toggle and attestation result rendering.  
 ☐ Implement Evaluate tab toggle, attestation form fields, and result card updates.  

☐ **T2607 – Operator UI Replay attestation support**  
 ☐ Add failing Selenium tests covering attestation replay verification (inline-only) and error messaging.  
 ☐ Implement Replay tab logic + telemetry display for attestation verification.  

☐ **T2608 – Fixture ingestion & documentation**  
 ☐ Extend fixture loaders/tests (core/app/CLI/REST) to consume the new attestation datasets.  
 ☐ Update how-to guides, knowledge map, and roadmap entry; run `./gradlew spotlessApply check` to close the feature.  
