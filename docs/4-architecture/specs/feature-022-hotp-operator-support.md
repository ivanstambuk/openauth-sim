# Feature 022 – HOTP Operator Support

_Status: Draft_
_Last updated: 2025-10-06_

## Overview
Deliver RFC 4226 HOTP capabilities across the simulator so operators can register and validate HOTP credentials alongside the existing OCRA flows. This feature introduces a dedicated HOTP domain model, persistence wiring, telemetry events, façade endpoints (CLI + REST), and operator console UI evaluation flows (stored + inline) while keeping issuance out of scope.


## Clarifications
- 2025-10-06 – HOTP evaluate screen selects Inline parameters by default on initial load or refresh to mirror OCRA behaviour (user directive).
- 2025-10-04 – Initial delivery must ship an end-to-end slice (core domain, application adapters, CLI commands, and REST endpoints) instead of a core-only milestone (user directive; Option B selected).
- 2025-10-04 – HOTP credentials reuse the existing MapDB credential store/schema-v1 baseline alongside OCRA descriptors; no dedicated HOTP store is created (user directive; Option A selected).
- 2025-10-04 – Telemetry coverage must match the OCRA parity level (issuance, evaluation, failure reasons) using the shared `TelemetryContracts` adapters (user directive; Option A selected).
- 2025-10-04 – Application layer owns HOTP counter persistence and telemetry-ready metadata so CLI/REST facades remain thin (user directive; Option A selected).
- 2025-10-05 – HOTP telemetry events adopt the `hotp.evaluate` and `hotp.issue` namespaces via `TelemetryContracts` to keep parity with future facade integrations (worklog confirmation).
- 2025-10-05 – Operator UI remains evaluation-only across all credential types; HOTP UI excludes issuance until a future roadmap decision revisits the scope (user directive).
- 2025-10-05 – HOTP operator UI lives within the existing operator console tab, supporting stored credential evaluation and inline secret evaluation flows (options B + C selected).
- 2025-10-05 – HOTP operator UI reuses existing REST telemetry events; no additional UI-specific telemetry frames are required (option A selected).
- 2025-10-05 – Operator documentation will be updated to reflect HOTP UI availability and usage patterns (option A selected).
- 2025-10-05 – HOTP operator UI acquires stored credentials via `/api/v1/hotp/credentials`, using the shared console CSRF token when invoking `/api/v1/hotp/evaluate` and `/api/v1/hotp/evaluate/inline` (implementation note).
- 2025-10-05 – HOTP operator console reuses the evaluate/replay pill header with Evaluate active and a Replay tab present (currently without behaviour) to mirror OCRA styling while signalling future scope (option B confirmed).
- 2025-10-05 – HOTP replay will ship a dedicated non-mutating REST endpoint (`POST /api/v1/hotp/replay`) handling stored and inline submissions without advancing counters (option A confirmed).
- 2025-10-05 – HOTP operator replay UI will mirror the OCRA replay experience with stored and inline modes and sample data affordances (option A confirmed).
- 2025-10-06 – HOTP operator replay UI removes the advanced context toggle/fields (label, notes) so replay submissions focus solely on credential inputs (user directive).
- 2025-10-05 – HOTP replay interactions emit dedicated `hotp.replay` telemetry frames (REST and UI), keeping evaluation metrics separate (option A confirmed).
- 2025-10-05 – Operator console deep links must mirror OCRA conventions by writing `protocol=hotp` and `tab=evaluate|replay` query parameters so HOTP views restore correctly on refresh (user confirmation).
- 2025-10-05 – HOTP evaluation panels remove the stored/inline headings and hint copy so operators see the input fields immediately after selecting a mode (user directive).
- 2025-10-05 – HOTP mode selection mirrors OCRA ordering with Inline parameters listed before Stored credential for consistent operator workflows (user directive).
- 2025-10-05 – HOTP replay hints read “Select a persisted credential, and replay the OTP without advancing counters.” and “Provide HOTP parameters directly for ad-hoc verification.” to align copy with operator guidance (user directive).
- 2025-10-06 – HOTP replay stored credential selector label must read “Stored credential” to align the replay screen with the OCRA equivalent (user directive).
- 2025-10-06 – HOTP replay stored credential panel omits the heading before the selector so the label is the first element, matching the OCRA replay layout (user directive).
- 2025-10-05 – HOTP inline evaluation no longer collects an operator-provided identifier; the REST/API surface accepts only secret, algorithm, digits, counter, and OTP for inline requests (user directive).
- 2025-10-05 – HOTP Evaluate tab (stored and inline modes) must generate the OTP and display it without requiring operator input; OTP entry remains exclusive to the Replay tab (user directive; Option A selected).
- 2025-10-05 – HtmlUnit `@SuppressFBWarnings` annotation dependency is added to the REST API test configuration (via `com.github.spotbugs:spotbugs-annotations` on the test classpath) so compilation warnings are suppressed without disabling linting (user directive; Option A selected).
- 2025-10-05 – HOTP inline evaluate "Load a sample vector" control must offer multiple presets (e.g., RFC 4226 SHA-1 and an additional SHA-256 demo vector) so operators can exercise different hash digests during generation flows (user directive; Option B selected).
- 2025-10-05 – HOTP inline evaluation result panel matches the OCRA layout: headline “Evaluation result,” OTP row rendered as “OTP: <value>,” status row rendered inline with “Status” label and value, the container width and padding align with the OCRA result panel, success badges use the green style adopted from HOTP across protocols, no ancillary metadata row, and the panel positioned at the top-right of the input form (user directive).
- 2025-10-06 – HOTP inline evaluation form renders Hash algorithm, Digits, and Counter controls in a single compact row matching the OCRA policy builder layout so related inputs remain visible without consuming extra vertical space (user directive).
- 2025-10-06 – HOTP evaluate UI must retain a vertical gap between the "Stored credential" selector and the "Load a sample vector" controls so the inline mode matches the OCRA evaluate layout (user directive).
- 2025-10-06 – HOTP stored evaluation view positions the “Seed sample credentials” button above the stored credential selector to mirror the OCRA tab layout (user directive).
- 2025-10-06 – HOTP stored evaluation seeding control maintains the same vertical spacing from the evaluation mode selector as the OCRA tab so the layout remains visually consistent (user directive).

- 2025-10-06 – HOTP operator console seeding button must provision both canonical stored credentials (`ui-hotp-demo` SHA-1/6 and `ui-hotp-demo-sha256` SHA-256/8) mirroring inline presets; reseeding appends missing entries without overwriting existing records (user directive; Option B selected).
## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| HOS-001 | Introduce HOTP credential descriptors, generator, and validator aligned with RFC 4226 (configurable digits and moving factor) exposed through the core domain/application modules. | Unit tests cover boundary cases (counter rollovers, digit lengths, secret sizes) and mutation tests exercise success/failure paths. |
| HOS-002 | Persist HOTP credentials using the shared MapDB store (`CredentialType.OATH_HOTP`) with schema-v1 metadata for counter state and issuance context. | Integration tests confirm HOTP records coexist with OCRA entries and are retrievable via the shared `CredentialStoreFactory`. |
| HOS-003 | Provide CLI commands to create/list/evaluate HOTP credentials, mirroring OCRA command UX while emitting telemetry events. | Picocli tests verify command output/exit codes; telemetry assertions capture `hotp.command.*` frames. |
| HOS-004 | Expose REST endpoints for HOTP evaluation (stored credential and inline secret modes) with OpenAPI updates and consistent telemetry; inline mode omits operator-provided identifiers. | Spring MVC tests confirm endpoint contracts; OpenAPI snapshots show HOTP sections; telemetry adapters emit `hotp.rest.*` frames and REST telemetry logs redact OTP material. |
| HOS-005 | Document HOTP usage (how-to guides, roadmap, knowledge map) and highlight CLI/REST entry points plus schema reuse. | Docs updated under `docs/2-how-to/`, roadmap milestone notes mention HOTP delivery, knowledge map links HOTP modules to shared persistence/telemetry. |
| HOS-006 | Surface HOTP evaluation in the operator UI by extending the existing console with stored credential selection and inline secret evaluation flows that call the REST API. Issuance remains out of scope. | UI integration tests (e.g., Spring/Selenium) cover stored + inline evaluation; UI links reuse REST telemetry without new event types; UX reflects documentation requirements. |
| HOS-007 | Provide a HOTP operator console seeding control that appends canonical SHA-1/6 and SHA-256/8 demo credentials without overwriting existing records, and align REST/UI telemetry with the operation. | UI + REST tests confirm the button appears only in stored mode, seeds missing credentials idempotently, and telemetry/events mirror OCRA seeding behaviour. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| HOS-NFR-001 | Security | Secrets remain encrypted at rest when MapDB encryption is enabled; telemetry redacts OTP values similar to OCRA. |
| HOS-NFR-002 | Compatibility | Changes to schema-v1 metadata maintain backward compatibility with existing stores and do not require migrations. |
| HOS-NFR-003 | Quality | `./gradlew spotlessApply check` and `qualityGate` stay green; ArchUnit/SpotBugs/PMD suites cover new modules. |

## Test Strategy
- Extend core unit and property-based tests to validate HOTP generation/verification for common digit counts and counter progressions.
- Add integration tests that open a MapDB store containing both OCRA and HOTP credentials to confirm shared persistence behaviour.
- Expand CLI command tests (JUnit + Picocli) to cover new HOTP options and telemetry emission.
- Update REST API tests (Spring MockMvc) and OpenAPI snapshot assertions to cover HOTP contracts.
- Extend operator UI tests (integration/system) to cover HOTP stored + inline evaluation flows and ensure accessibility + telemetry expectations are met.
- Add coverage around the HOTP credential directory endpoint feeding the operator UI so stored credential listings remain deterministic.
- Re-run mutation, SpotBugs, and ArchUnit suites to guard against regressions in new code paths.

## Dependencies & Risks
- Introducing HOTP alongside OCRA increases credential-store surface area; ensure schema-v1 metadata remains additive to avoid migrations.
- Telemetry volume may rise; validate event naming to prevent collisions with existing dashboards.
- HOTP production-ready UI remains pending, so operator expectations must be managed via documentation.

## Out of Scope
- Adding HOTP support to the web UI or operator console (deferred to a later feature).
- Implementing TOTP or other OTP variants.
- Building migration tooling for legacy HOTP data outside the repo.

## Verification
- Core, application, CLI, and REST tests cover HOTP flows and pass in CI alongside existing suites.
- Documentation reflects HOTP availability and persistence alignment.
- Telemetry events for HOTP appear in automated tests and follow `TelemetryContracts` schemas.
- `./gradlew spotlessApply check` succeeds after HOTP code and docs land (2025-10-05 verification reports Jacoco branch coverage ≈0.9002 / line coverage ≈0.9706).

Update this specification as further clarifications emerge.
