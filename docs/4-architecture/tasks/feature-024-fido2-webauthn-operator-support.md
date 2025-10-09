# Feature 024 Tasks – FIDO2/WebAuthn Operator Support

_Linked plan:_ `docs/4-architecture/feature-plan-024-fido2-webauthn-operator-support.md`  
_Status:_ Draft (planning)  
_Last updated:_ 2025-10-09

☐ **T1 – Stage W3C vector fixtures**  
 ☐ Convert selected W3C §16 authentication vectors to Base64url fixtures under `docs/webauthn_w3c_vectors/`.  
 ☐ Add failing core verification tests (success + RP ID mismatch + signature failure cases).  

☐ **T2 – Implement verification engine**  
 ☐ Implement minimal parser/validator satisfying T1 tests (flags, RP ID hash, origin/type, signature base).  
 ☐ Add additional failure branch tests (UV required vs optional, counter regression).  

☐ **T3 – Persistence support**  
 ☐ Add failing integration tests ensuring MapDB schema v1 stores/retrieves FIDO2 credentials with metadata.  
 ☐ Implement persistence descriptors and schema wiring, keeping HOTP/TOTP/OCRA compatibility.  

☐ **T4 – Application services & telemetry**  
 ☐ Add failing application service tests for stored/inline evaluation and replay diagnostics (telemetry assertions).  
 ☐ Implement services emitting `fido2.evaluate` / `fido2.replay` events with redacted payloads.  

☐ **T5 – CLI façade**  
 ☐ Add failing Picocli tests covering evaluate (stored/inline) and replay commands.  
 ☐ Implement CLI commands with HOTP/OCRA parity (validation, output, telemetry).  

☐ **T6 – REST endpoints**  
 ☐ Add failing MockMvc + OpenAPI snapshot tests for `/api/v1/webauthn/evaluate`, `/evaluate/inline`, `/replay`.  
 ☐ Implement controllers/DTOs, ensure telemetry + validation, update OpenAPI docs.  

☐ **T7 – Operator UI enablement**  
 ☐ Extend Selenium/system + accessibility tests for WebAuthn panels (presets, seed button, keyboard navigation).  
 ☐ Implement Thymeleaf/JS updates enabling forms, preset loading, curated seed action, JWK display.  

☐ **T8 – JSON bundle coverage**  
 ☐ Add failing parameterised tests iterating over `docs/webauthn_assertion_vectors.json` across core/application layers.  
 ☐ Implement ingestion helpers and expand presets to include JSONL flows where appropriate.  

☐ **T9 – Documentation & telemetry updates**  
 ☐ Update how-to guides, roadmap, knowledge map, and telemetry docs with WebAuthn coverage + seed info.  
 ☐ Ensure documentation reflects JWK preference and vector handling.  

☐ **T10 – Quality gate & wrap-up**  
 ☐ Run `./gradlew spotlessApply check` and `./gradlew qualityGate`; resolve issues.  
 ☐ Finalise spec/plan/task updates, capture lessons, and prepare conventional commit & push.  
