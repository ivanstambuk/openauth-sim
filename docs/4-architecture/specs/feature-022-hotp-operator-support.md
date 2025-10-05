# Feature 022 – HOTP Operator Support

_Status: Draft_
_Last updated: 2025-10-05_

## Overview
Deliver RFC 4226 HOTP capabilities across the simulator so operators can register and validate HOTP credentials alongside the existing OCRA flows. This feature introduces a dedicated HOTP domain model, persistence wiring, telemetry events, and façade endpoints (CLI + REST) while keeping the UI scope for a later feature.

## Clarifications
- 2025-10-04 – Initial delivery must ship an end-to-end slice (core domain, application adapters, CLI commands, and REST endpoints) instead of a core-only milestone (user directive; Option B selected).
- 2025-10-04 – HOTP credentials reuse the existing MapDB credential store/schema-v1 baseline alongside OCRA descriptors; no dedicated HOTP store is created (user directive; Option A selected).
- 2025-10-04 – Telemetry coverage must match the OCRA parity level (issuance, evaluation, failure reasons) using the shared `TelemetryContracts` adapters (user directive; Option A selected).
- 2025-10-04 – First operator-facing surfaces include the CLI and REST API; UI work is deferred to a future feature (user directive; Option B selected).
- 2025-10-04 – Application layer owns HOTP counter persistence and telemetry-ready metadata so CLI/REST facades remain thin (user directive; Option A selected).
- 2025-10-05 – HOTP telemetry events adopt the `hotp.evaluate` and `hotp.issue` namespaces via `TelemetryContracts` to keep parity with future facade integrations (worklog confirmation).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| HOS-001 | Introduce HOTP credential descriptors, generator, and validator aligned with RFC 4226 (configurable digits and moving factor) exposed through the core domain/application modules. | Unit tests cover boundary cases (counter rollovers, digit lengths, secret sizes) and mutation tests exercise success/failure paths. |
| HOS-002 | Persist HOTP credentials using the shared MapDB store (`CredentialType.OATH_HOTP`) with schema-v1 metadata for counter state and issuance context. | Integration tests confirm HOTP records coexist with OCRA entries and are retrievable via the shared `CredentialStoreFactory`. |
| HOS-003 | Provide CLI commands to create/list/evaluate HOTP credentials, mirroring OCRA command UX while emitting telemetry events. | Picocli tests verify command output/exit codes; telemetry assertions capture `hotp.command.*` frames. |
| HOS-004 | Expose REST endpoints for HOTP evaluation (stored credential and inline secret modes) with OpenAPI updates and consistent telemetry. | Spring MVC tests confirm endpoint contracts; OpenAPI snapshots show HOTP sections; telemetry adapters emit `hotp.rest.*` frames and REST telemetry logs redact OTP material. |
| HOS-005 | Document HOTP usage (how-to guides, roadmap, knowledge map) and highlight CLI/REST entry points plus schema reuse. | Docs updated under `docs/2-how-to/`, roadmap milestone notes mention HOTP delivery, knowledge map links HOTP modules to shared persistence/telemetry. |

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
