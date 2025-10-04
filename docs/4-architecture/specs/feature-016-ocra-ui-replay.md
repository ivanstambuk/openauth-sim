# Feature 016 – OCRA UI Replay

_Status: Draft_
_Last updated: 2025-10-04_

## Overview
Extend the operator console with replay/verification capabilities so auditors can validate historical OCRA OTP submissions without leaving the UI. The feature introduces a dedicated replay screen that drives the existing REST `/api/v1/ocra/verify` endpoint, supports both stored-credential and inline-secret flows, and surfaces enhanced telemetry so replay usage is traceable alongside CLI and REST facades.

## Clarifications
- 2025-10-04 – Inline replay presets continue to auto-fill immediately when selected; no separate button is required (user preference).
- 2025-10-03 – Replay inline mode reuses the evaluation console's inline sample presets so operators can auto-fill suite/secret/context data (user selected Option A). Load-a-sample stays inline-only to match the evaluation console.
- 2025-10-03 – Selecting a replay inline preset should populate the expected OTP alongside other context fields so auditors can submit immediately (user request).
- 2025-10-03 – Replay result card should mirror the evaluation console aesthetics: highlight status with visual emphasis and present telemetry as labeled rows for readability (user request).
- 2025-10-04 – Replay result cards may omit the mode row; reason code and outcome remain sufficient for operator context (user directive).
- 2025-10-04 – Replay Selenium coverage should fall back to programmatic credential seeding when the bundled sample MapDB cannot be copied; the test must continue rather than failing the build (owner selected Option B).
- 2025-10-03 – Operator UI telemetry posts to `/ui/ocra/replay/telemetry`, feeding the shared `TelemetryContracts.ocraVerificationAdapter` with `origin=ui` and replay context (mode, outcome, fingerprint).
- 2025-10-03 – Replay REST responses will surface a `mode` attribute (stored vs inline) in metadata and telemetry events so UI instrumentation can log mode-specific outcomes (user accepted).
- 2025-10-03 – Task T1602 remains focused on inline replay Selenium coverage; stored replay navigation stays with earlier increments (user selected Option B).
- 2025-10-03 – Replay scope covers both stored-credential and inline-secret verification flows in the first increment (user selected Option A).
- 2025-10-03 – The operator UI will gain a dedicated replay route/screen within the existing `rest-api` module rather than overloading the current evaluation console (user selected Option B).
- 2025-10-03 – UI interactions will continue to call the published REST replay endpoint (`/api/v1/ocra/verify`) to preserve facade parity (user selected Option A).
- 2025-10-03 – Replay interactions will emit additional telemetry fields/events alongside the existing UI instrumentation so audit logs differentiate matches, mismatches, and context issues (user selected Option B).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| OUR-001 | Provide a replay screen accessible from the operator console that lets users choose between stored credentials and inline secrets before submitting a verification request. | UI navigation exposes a replay entry point; Selenium/system tests assert the route renders mode selectors and context inputs. |
| OUR-002 | When replaying stored credentials, populate credential choices from the REST credential listing and send the selected identifier plus context to `/api/v1/ocra/verify`. | UI fetches credential inventory, renders dropdown, and POST requests succeed in integration tests using stored fixtures. |
| OUR-003 | Inline replay mode accepts the full OCRA suite descriptor, shared secret, and OTP context, then submits the payload via `/api/v1/ocra/verify`. | Tests cover inline submissions, verifying that validation errors surface inline and successful responses reflect match status. |
| OUR-004 | Surface verification outcomes (match, strict mismatch, validation failure) with descriptive messaging and the telemetry identifier so auditors can reference logs. | UI displays response status, reason code, and telemetry ID; tests assert rendering rules. |
| OUR-005 | Emit replay-specific telemetry that extends existing UI events with mode (stored vs inline), outcome classification, and context fingerprint hashes while maintaining secret sanitisation. | Telemetry contract documentation updated; automated tests confirm emitted frames include new fields without leaking secrets. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| OUR-NFR-001 | Latency | Replay submissions initiated via the UI must complete within 200 ms P95 under local testing, matching REST contract expectations. |
| OUR-NFR-002 | Accessibility | New replay screen components meet WCAG AA color contrast and keyboard navigation parity with the existing evaluation console. |
| OUR-NFR-003 | Telemetry Consistency | Replay telemetry aligns with CLI/REST schema extensions so downstream analytics can aggregate outcomes across facades. |
| OUR-NFR-004 | Test Coverage | System tests (Selenium) and UI integration tests cover both success and failure paths for stored and inline modes before implementation completes. |

## Test Strategy
- Begin by writing or extending Selenium tests that drive stored and inline replay scenarios, asserting rendered outcomes and telemetry hooks.
- Add REST contract integration tests (mock MVC or WebTestClient) validating the UI controller forwards payloads correctly and handles error states from `/api/v1/ocra/verify`.
- Update telemetry unit tests to cover new replay-specific fields and ensure sanitisation rules still hold (hashed OTP/context fingerprints only).
- Run `./gradlew :rest-api:test :rest-api:integrationTest` (or module-specific UI tests) before implementation to confirm failures guide the coding work.

## Dependencies & Risks
- Requires the existing REST replay endpoint (Feature 009) to remain stable; schema drift would break the UI contract.
- Introducing new telemetry fields must stay compatible with the shared telemetry adapters in the `application` module; coordination with other facades may be necessary.
- UI layout changes could impact the completed evaluation console; ensure regression tests cover navigation and shared components.

## Out of Scope
- General learning/educational walk-throughs (deferred back to the OCRA learning UI workstream).
- Credential lifecycle management improvements; focus is solely on replay/verification interactions.
- Offline or batch replay tooling; the feature targets interactive operator flows.

## Verification
- Selenium/system tests demonstrate stored and inline replays succeed and surface match/mismatch outcomes.
- Telemetry fixtures and documentation confirm replay events include mode, outcome, and telemetry ID without exposing secrets.
- `./gradlew spotlessApply check` passes with all new tests and documentation updates.

Update this specification as additional clarifications emerge.
