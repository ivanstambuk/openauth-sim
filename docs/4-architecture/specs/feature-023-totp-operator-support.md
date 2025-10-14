# Feature 023 – TOTP Operator Support

_Status: Complete_
_Last updated: 2025-10-13_

## Overview
Deliver RFC 6238 TOTP capabilities across the simulator so operators can validate time-based one-time passwords alongside existing HOTP and OCRA flows. This feature introduces a TOTP domain model, shared persistence descriptors, application-layer services, CLI commands, REST endpoints, and operator console evaluation/replay experiences while keeping issuance out of scope for the initial release.

## Clarifications
- 2025-10-08 – Kickoff delivers a single end-to-end slice spanning core domain, persistence, application services, CLI, REST API, and operator console UI (user directive; Option A selected).
- 2025-10-08 – Support SHA-1, SHA-256, and SHA-512 algorithms, 6- and 8-digit codes, and configurable time-step durations (30 s and 60 s minimum) at launch (user directive; Option A selected).
- 2025-10-08 – Persist TOTP credentials within the existing MapDB schema v1 alongside HOTP/OCRA entries (user directive; Option A selected).
- 2025-10-08 – Evaluation flows expose configurable ± step drift windows and optional operator-supplied timestamp overrides to simulate clock skew (user directive; Option A selected).
- 2025-10-08 – Initial scope remains evaluation/replay-only; issuance/enrollment UX is deferred to a future feature (user directive; Option B selected).
- 2025-10-08 – CLI and Java application facades must launch alongside REST/UI surfaces to match HOTP/OCRA parity (user directive).
- 2025-10-08 – TOTP evaluation telemetry emits through the shared `totp.evaluate` adapter, mirroring HOTP/OCRA telemetry patterns (worklog note).
- 2025-10-08 – Facade integration will stage failing tests for CLI and REST in parallel increments before implementing the shared wiring (user confirmed Option B).
- 2025-10-08 – Operator console documentation lives in `docs/2-how-to/use-totp-operator-ui.md`; roadmap and knowledge map now flag the TOTP panels as live (worklog note).
- 2025-10-08 – TOTP replay tab must mirror HOTP/OCRA replay semantics via a dedicated `/api/v1/totp/replay` flow that performs non-mutating diagnostics while reusing evaluation logic where practical (user directive; Option A selected).
- 2025-10-08 – Stored-mode hint copy shortens to “Validate a stored simulator credential (epoch-second timestamps).” (user selected Option A).
- 2025-10-08 – Replay actions reuse the “Verify OTP” label + primary button styling to match HOTP/OCRA consoles (user directive).
- 2025-10-08 – Operator console mode selectors present inline TOTP inputs before stored credential options to mirror HOTP/OCRA ordering (user directive; Option A selected).
- 2025-10-08 – Inline TOTP parameter controls (algorithm, digits, step seconds) render on a single row for both evaluate and replay panels (user directive).
- 2025-10-08 – Drift window inputs (backward/forward steps) display side-by-side across stored and inline evaluate/replay forms (user directive).
- 2025-10-09 – Inline TOTP evaluate and replay panels must include a "Load a sample vector" preset control mirroring HOTP/OCRA; presets populate RFC 6238 sample secrets, timestamps, OTPs, and metadata (user directive; Option A selected).
- 2025-10-09 – Stored-mode TOTP evaluations expose a `Seed sample credentials` control backed by a dedicated TOTP seeding endpoint; the button mirrors HOTP/OCRA behaviour and surfaces only within the evaluate tab’s stored mode (user directive; Option A selected).
- 2025-10-09 – TOTP stored replay flows use a stored credential dropdown (labelled “Stored credential”) instead of a free-text identifier, matching HOTP/OCRA parity across evaluate/replay panels (user directive; Option A selected).
- 2025-10-09 – Stored TOTP replay panels must include a “Load sample data” control fed by a backend sampler endpoint (`/api/v1/totp/credentials/{credentialId}/sample`) so the payload reflects canonical demo vectors (user directive; Option A selected).
- 2025-10-09 – Position the stored replay “Load sample data” control directly below the “Stored credential” selector, matching the vertical spacing used by the OCRA panel (user directive).
- 2025-10-09 – Canonical seeded TOTP credentials must satisfy algorithm-specific minimum secret lengths so `/api/v1/totp/credentials/{credentialId}/sample` succeeds for SHA-512 alongside SHA-1/SHA-256 presets (defect report; Option A selected).
- 2025-10-11 – TOTP evaluate tabs must default the mode selector to “Inline parameters” (stored option selectable afterward) to preserve parity with HOTP/OCRA/FIDO2 evaluate panels (defect report; Option A selected).
- 2025-10-11 – TOTP evaluate and replay inline preset controls must reuse the HOTP/OCRA spacing token (`stack-offset-top-lg`) so “Load a sample vector” retains matching vertical padding beneath the mode selector (user directive; Option A selected).
- 2025-10-12 – Task T2346 will publish `docs/totp_validation_vectors.json`, translating RFC 6238 Appendix B examples into the shared JSON format so all facades load canonical TOTP fixtures; implementation is pending.
- 2025-10-13 – T2346 scope confirmed: bundle includes RFC 6238 Appendix B timestamps for SHA-1/SHA-256/SHA-512 (8-digit outputs) plus curated 6-digit truncations; core/CLI/REST/UI layers must hydrate presets from the JSON loader instead of hard-coded vectors (worklog note; Option A selected).
- 2025-10-13 – Task T2346 delivered the shared `totp_validation_vectors.json` catalogue, added `TotpJsonVectorFixtures`, and rewired core/CLI/REST/UI tests and presets to consume the loader while keeping the demo preset inline-only.
- 2025-10-12 – Inline TOTP preset dropdown must expose RFC 6238 SHA-256 and SHA-512 vectors for 8-digit configurations (labelled with the RFC suffix), plus 6-digit truncations that use plain labels without additional qualifiers (user directive; Option A selected, label refinement).
- 2025-10-13 – Evaluation result cards mirror HOTP/OCRA layout by showing the submitted OTP and status badge only; telemetry identifiers and drift metadata remain available via server logs rather than UI chrome (user directive; Option A selected).
- 2025-10-13 – Stored credential seeding mirrors the inline preset catalogue (SHA-1/SHA-256/SHA-512 across 6- and 8-digit variants) so dropdown labels match the inline sample vector names (user directive; Option A selected).
- 2025-10-13 – Operator console deep links standardise on `protocol=<key>`, `tab=<evaluate|replay>`, and `mode=<inline|stored>` parameters so TOTP URLs align with HOTP/OCRA/FIDO2 conventions (user directive; Option B selected).
- 2025-10-13 – Selecting the TOTP protocol tab must always open the Evaluate tab in Inline mode, discarding prior tab/mode state to provide a consistent entry point (user directive).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| TOS-001 | Implement TOTP credential descriptors and generators complying with RFC 6238 (SHA-1/SHA-256/SHA-512, 6/8 digits, configurable step duration). | Core unit tests cover algorithm/digit combinations, rollover boundaries, and time-step conversions. |
| TOS-002 | Store TOTP credentials in the existing schema v1 MapDB store without migrations, coexisting with HOTP/OCRA records. | Persistence integration tests confirm mixed credential types round-trip via `CredentialStoreFactory` and retain version markers. |
| TOS-003 | Provide application-layer services that evaluate stored and inline TOTP submissions, exposing configurable drift windows and operator-supplied timestamps. | Application service tests verify success, invalid secret/hmac failures, and drift boundary rejection while emitting telemetry. |
| TOS-004 | Extend telemetry adapters (`TelemetryContracts`) for TOTP evaluation/replay events, mirroring naming conventions (`totp.evaluate`, `totp.replay`). | Telemetry unit tests assert emitted frames redact secrets and include algorithm/digit/window metadata. |
| TOS-005 | Deliver CLI commands to import/list TOTP credentials and evaluate stored/inline submissions with drift controls. | CLI integration tests (Picocli) demonstrate command help, successful evaluations, and failure messaging for out-of-window OTPs. |
| TOS-006 | Expose REST endpoints for stored (`POST /api/v1/totp/evaluate`) and inline (`POST /api/v1/totp/evaluate/inline`) evaluation plus replay (`POST /api/v1/totp/replay`), supporting drift and timestamp overrides. | MockMvc tests validate status codes, payload contracts, OpenAPI snapshots, and non-mutating replay behaviour. |
| TOS-007 | Update operator console UI with TOTP evaluation and replay panels (stored + inline) that integrate with REST endpoints, presets, and drift controls. | Selenium/system tests confirm panel rendering, preset behaviours, drift/timestamp inputs, and query-parameter deep links (`protocol=totp`). |
| TOS-008 | Document TOTP usage (operator how-to, roadmap, knowledge map) and align placeholder messaging with live functionality. | Documentation diffs show updated instructions; lint (`./gradlew spotlessApply check`) passes after doc updates. |
| TOS-009 | Provide a dedicated TOTP replay application/REST flow that mirrors HOTP/OCRA replay semantics, returning diagnostic metadata without mutating credential state. | Application + MockMvc tests assert telemetry, non-mutating behaviour, stored/inline replay handling, and error signalling. |
| TOS-010 | Surface stored replay "Load sample data" controls backed by `/api/v1/totp/credentials/{credentialId}/sample`, emitting `totp.sample` telemetry and populating deterministic OTP/timestamp/drift inputs. | Application tests cover sampler output, MockMvc tests exercise the new endpoint, and Selenium coverage asserts UI form population plus status messaging. |
| TOS-011 | Ensure canonical TOTP seed definitions honour algorithm-specific constraints (secret length, drift defaults) so stored sample payloads resolve for SHA-512 as well as SHA-1/SHA-256. | Regression tests (application + REST) invoke seeded SHA-512 credentials and receive deterministic sample responses without errors. |

## Non-Functional Requirements
| ID | Requirement | Acceptance Signal |
|-----|-------------|-------------------|
| TOS-NFR-001 | Maintain existing Jacoco (≥0.90 line / ≥0.90 branch) and PIT mutation thresholds after TOTP additions. | `./gradlew qualityGate` reports coverage ≥ thresholds across touched modules. |
| TOS-NFR-002 | Ensure SpotBugs dead-state detectors (Feature 015) and reflection guardrails remain green across new code. | `./gradlew spotlessApply check` and the SpotBugs/ArchUnit suites run as part of the increment. |
| TOS-NFR-003 | Keep operator UI accessible (ARIA labelling, keyboard navigation) for new controls. | Selenium accessibility assertions and axe-core scans pass on updated views. |
| TOS-NFR-004 | Telemetry events must exclude shared secrets/OTP values while capturing context (algorithm, digits, step, drift window, timestamp override flag). | Telemetry tests assert field absence/presence; log sanitiser checks remain green. |

## In Scope
- Core domain updates for TOTP key derivation, time-step computation, and drift handling.
- Shared persistence descriptors, schema adjustments, and migrations (if required) within schema v1.
- Application services, telemetry wiring, and facade adapters (CLI, REST, operator UI).
- Test-first coverage across unit, integration, MockMvc, and Selenium suites.
- Documentation updates (how-to, roadmap, knowledge map, OpenAPI).

## Out of Scope
- TOTP credential issuance/enrollment UX (CLI/REST/UI).
- Backup/export tooling beyond existing credential store interactions.
- Non-RFC 6238 extensions (e.g., custom HMAC algorithms, non-integer drifts).

## Dependencies & Constraints
- Reuse existing MapDB schema v1; any schema deltas require compatibility checks with HOTP/OCRA records.
- Telemetry must continue flowing through `TelemetryContracts` without bypasses.
- Spotless/SpotBugs configurations from Feature 022 remain authoritative; no dependency upgrades without owner approval.
- Maintain parity with HOTP/OCRA CLI command naming conventions and REST payload schema styles.

## Persistence Attributes
- Persist TOTP credentials with `CredentialType.OATH_TOTP` and normalise attribute keys under schema v1:
  - `totp.algorithm` → HMAC digest (`SHA1`, `SHA256`, `SHA512`).
  - `totp.digits` → OTP length (`6` or `8`).
  - `totp.stepSeconds` → time-step size in seconds.
  - `totp.drift.backward` / `totp.drift.forward` → permitted negative/positive step windows.
- MapDB store must default unset attributes to (`SHA1`, `6`, `30`, `1`, `1`) during save/load while preserving explicit overrides for non-default configurations.

## Test Strategy
1. Add failing unit tests for TOTP generators/validators covering algorithm/digit combinations, time-step boundaries, and drift limits.
2. Introduce failing persistence integration tests mixing TOTP, HOTP, and OCRA descriptors.
3. Extend application-layer tests to cover success, invalid secret, expired OTP, and drift-window rejection scenarios; add telemetry assertions.
4. Create failing CLI integration tests for evaluation/replay commands (stored + inline) including drift controls.
5. Add failing REST MockMvc/OpenAPI tests for evaluation and replay endpoints with timestamp override inputs.
6. Stage failing application and REST tests for the replay flow, ensuring stored/inline requests remain non-mutating and telemetry matches HOTP/OCRA patterns.
7. Stage failing Selenium/system tests for operator console TOTP replay panels and parity interactions.
8. Run `./gradlew spotlessApply check` after each increment; execute `./gradlew qualityGate` before closure.

## Follow-up Considerations
- Future feature should define issuance/enrollment flows (shared secrets, provisioning URIs, QR codes).
- Assess the need for configurable simulator clock sources (e.g., fixed offset) if operator demand extends beyond manual timestamp overrides.
