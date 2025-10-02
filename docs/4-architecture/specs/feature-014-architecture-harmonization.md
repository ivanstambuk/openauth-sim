# Feature 014 – Architecture Harmonization

_Status: In Progress_
_Last updated: 2025-10-02_

## Overview
Introduce a shared application layer, unified infrastructure components, and consistent telemetry contracts so CLI, REST, and UI facades reuse the same orchestration pathways, storage bootstrapping, and validation logic. The goal is to reduce duplicate domain wiring, simplify onboarding new credential protocols, and prepare the codebase for future observability and modularity extensions.

## Clarifications
1. 2025-10-01 – User confirmed adoption of all five architecture improvements as a single feature scope (Option A).
2. 2025-10-01 – Work will target existing OCRA flows while establishing extensible seams for future protocols; no non-OCRA behaviour ships in this feature (Option B).
3. 2025-10-01 – Telemetry consolidation will retain structured logging outputs but route them through a shared contract so existing operator tooling keeps functioning (Option A).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| AH-001 | Provide a shared OCRA application/service layer module that encapsulates credential lookup, validation, and OTP execution so all facades delegate through it. | CLI/REST/UI integration tests instantiate the application layer via dependency injection or factories; direct `OcraCredentialFactory` usage disappears from facade entry points. |
| AH-002 | Centralize persistence provisioning behind a reusable `CredentialStoreFactory` with configuration hooks for database path resolution and lifecycle management. | CLI and REST modules depend on the factory; tests demonstrate swapping in-memory stores without duplicating setup helpers. |
| AH-003 | Define a common telemetry contract and adapter implementation that emits sanitized events for all facades, replacing bespoke log builders. | CLI/REST/UI emit events via the shared contract; tests assert consistent field sets and sanitization flags. |
| AH-004 | Split the monolithic `core` module into protocol-focused submodules (starting with `core-ocra`) while retaining shared primitives in a base module. | Gradle settings and module structure updated; ArchUnit rules confirm facades depend only on the new application layer or published APIs. |
| AH-005 | Expose shared DTOs and normalization helpers for inline vs stored evaluation/verification requests, reused across facades and future surfaces. | CLI/REST tests import the shared DTOs; duplicate validation logic removed; contract documented in specs/how-to guides. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| AH-NFR-001 | Maintain existing `./gradlew qualityGate` runtime within ±10% despite module splitting by configuring Gradle composite builds and avoiding redundant compilation. | Quality gate timing recorded before/after; target met. |
| AH-NFR-002 | Preserve current telemetry redaction guarantees (no secret material logged) when migrating to the shared contract. | Sanitization unit tests assert no raw secrets appear; telemetry analyzer scripts show compliance. |
| AH-NFR-003 | Ensure backwards compatibility for CLI/REST command-line flags and REST endpoints; any contract changes require explicit versioning. | Snapshot tests and OpenAPI diff confirm unchanged external surface. |

## Architecture Notes
- Introduce an `application` Gradle module hosting orchestrations (`OcraExecutionService`, `OcraVerificationService`, etc.) that wrap domain components and expose facade-friendly APIs.
- Refactor facades to compose the application services via dependency injection (Spring BEAN, Picocli factory, UI controller), reducing direct domain instantiation.
- Extend automated tests to cover error, validation-failure, and unexpected-state branches in the shared services so delegates expose deterministic outcomes and sustain coverage budgets.
- Create `infra-persistence` utilities where `CredentialStoreFactory` lives; CLI/REST share defaults and configuration resolution.
- Telemetry contract surfaced as interfaces (`TelemetryEvent`, `TelemetryEmitter`) with adapters for CLI (PrintWriter), REST (JUL/SLF4J), and UI; future adapters can publish to metrics.
- Core module split: move OCRA-specific classes into `core-ocra`; keep shared primitives in `core-shared`. Update knowledge map and ArchUnit boundaries accordingly.
- Shared DTOs (e.g., `OcraExecutionRequest`, `OcraVerificationRequest`) live alongside the application layer, ensuring single normalization path for inline/stored credential flows.

## Success Metrics
- 0% duplicate OCRA orchestration code across facades (verified via architectural tests or coverage diff).
- 100% of OCRA telemetry routed through the new contract with consistent field sets.
- Quality gate remains green with module boundaries enforced by updated ArchUnit rules.
- Aggregated Jacoco branch coverage stays ≥0.90 by exercising failure paths across shared services and facade handlers.

## Dependencies & Risks
- Requires updating Gradle configuration and dependency graphs; ensure build cache and IDE support remain stable.
- UI module currently consumes REST responses; shared DTO adoption must avoid breaking Thymeleaf templates.
- Module split may necessitate package renaming; plan migration carefully to preserve code history and test coverage.

## Out of Scope
- Implementing non-OCRA protocol support (FIDO2, EUDI, etc.).
- Replacing MapDB with alternative persistence engines.
- Introducing new telemetry sinks (e.g., OpenTelemetry exporters); adapters limited to existing logging mechanisms for now.
