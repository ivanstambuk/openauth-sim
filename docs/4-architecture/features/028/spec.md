# Feature 028 – IDE Warning Remediation

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/028/plan.md` |
| Linked tasks | `docs/4-architecture/features/028/tasks.md` |
| Roadmap entry | #28 – Quality platform / IDE hygiene |

## Overview
Resolve the IDE diagnostics captured on 2025-10-18 by tightening assertions and usages across application, core, CLI,
REST, and UI test code. The change eliminates dead assignments and unused locals without masking potential regressions,
keeping the workspace warning-free while reinforcing test intent.

## Clarifications
- 2025-10-18 – Adopt Option B for remediation: strengthen assertions/usages rather than deleting placeholders so
  diagnostics continue to guard behaviour (owner decision).
- 2025-10-19 – Move `WebAuthnAssertionResponse` into its own source file to avoid auxiliary-class warnings while keeping
  the DTO public (owner decision, Option A).
- 2025-10-19 – Promote `spotbugs-annotations` to the application module’s exported compile classpath (`compileOnlyApi`)
  so downstream builds resolve `@SuppressFBWarnings` without reintroducing SpotBugs violations (owner decision).
- 2025-10-19 – Flag REST exception `details`/`metadata` maps as `transient` to silence serialization warnings without
  changing HTTP semantics (owner directive).

## Goals
- G-028-01 – Capture the remediation scope and decisions across spec/plan/tasks, roadmap, and knowledge map.
- G-028-02 – Replace unused locals with meaningful assertions in application/core modules (TOTP constructors, WebAuthn
  verifier/replay flows) while preserving behaviour.
- G-028-03 – Convert CLI/REST/Selenium unused variables into assertions so telemetry, payloads, and UI flows stay tested.
- G-028-04 – Complete toolchain updates (SpotBugs annotations, DTO extraction, transient fields) and rerun the regression
  suite until IDE inspections report zero warnings.

## Non-Goals
- N-028-01 – Introducing new functional behaviour beyond the assertions required to justify existing variables.
- N-028-02 – Expanding lint/static analysis tooling or modifying SpotBugs/Checkstyle configuration.
- N-028-03 – Refactoring unrelated modules or consolidating Selenium flows.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-028-01 | Governance artefacts (spec/plan/tasks, roadmap, knowledge map, current-session) capture the remediation scope and IDE warning inventory. | Docs reference Feature 028 decisions and manual verification steps. | Documentation review confirms only the updated guidance remains. | Any doc retains stale warning references or missing clarifications. | No telemetry change; roadmap + knowledge map entries log the decision. | Clarifications 2025-10-18. |
| FR-028-02 | Application/core modules replace unused locals with assertions (TOTP constructors, WebAuthn verifier/replay, DTO extraction) and rerun targeted tests. | Constructors keep optional semantics; WebAuthn helpers assert decoded metadata; DTOs compile without warnings. | `:application:test` (TOTP & WebAuthn) + `:core:test` suites cover the updated assertions. | Nullability regressions, failing tests, or lingering IDE warnings in these modules. | Telemetry remains unchanged; trace helpers only emit existing fields. | Clarifications 2025-10-18/19. |
| FR-028-03 | CLI/REST/Selenium suites convert unused variables into assertions, keeping telemetry/UI flows validated. | CLI help/tests assert descriptor content; REST MockMvc/Selenium tests validate attestation payloads and selectors. | `:cli:test`, `:rest-api:test`, Selenium suites all pass without warnings. | Tests retain unused locals or telemetry expectations drift. | CLI/REST logs still reference existing telemetry fields; no new events. | G-028-03. |
| FR-028-04 | Toolchain/quality updates (SpotBugs annotations export, transient REST exception maps, DTO extraction, regression gate) remove compiler warnings across modules. | `spotbugs-annotations` exported, REST exception maps marked transient, DTO extracted, and full `spotlessApply check` passes. | Gradle runs (`:application:compileJava`, `:rest-api:compileJava`, full gate) succeed with zero warnings in IDE snapshot. | Remaining IDE warnings or build regressions. | No telemetry change; build logs note explicit overrides only when used. | Clarifications 2025-10-19. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-028-01 | IDE warning count returns to zero for the affected modules. | Maintain warning-free developer experience. | IntelliJ/IDEA inspections after changes show no diagnostics dated 2025-10-18 set. | application, core, cli, rest-api, ui modules. | Clarifications 2025-10-18. |
| NFR-028-02 | Regression suite (`:infra-persistence:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check`) remains green. | Ensure behavioural parity while tightening assertions. | Gradle logs captured in tasks checklist. | All affected modules + tools. | G-028-04. |
| NFR-028-03 | Documentation parity across roadmap/how-to/session snapshot. | Governance traceability. | Docs review ensures only the unified guidance remains. | docs/ hierarchy. | G-028-01. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-028-01 | Governance artefacts capture the remediation scope and IDE warning inventory. |
| S-028-02 | Application/core modules tighten assertions and pass targeted tests. |
| S-028-03 | CLI/REST/Selenium tests use meaningful assertions for previously unused locals. |
| S-028-04 | Toolchain/quality updates (SpotBugs annotations, DTO extraction, transient fields, spotless) complete the remediation cycle. |

## Test Strategy
- **Application:** `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.totp.*"` and WebAuthn replay suites.
- **Core:** `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationVerifierTest"` and `WebAuthnAssertionVerifierTest`.
- **CLI:** `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.TotpCliTest"`.
- **REST:** `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*"` plus compile-time checks for DTO/exceptions.
- **UI/Selenium:** `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"` (operator console attestation suites).
- **Docs/Contracts:** Lint + manual verification of roadmap/how-to entries; final `./gradlew --no-daemon spotlessApply check`.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-028-01 | `TotpEvaluationApplicationService.Command` optional `evaluationInstant` handling. | application |
| DO-028-02 | `WebAuthnAttestationVerifier` decoded `clientData` metadata used for assertions. | core, application |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-028-01 | REST `POST /api/v1/webauthn/attest/replay` | Replay endpoint verifying attestation blobs; now asserts metadata via used locals. | Tests ensure logging/telemetry unaffected. |

### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-028-01 | `totp evaluate` / `totp replay` | Command tests now assert descriptor metadata instead of holding unused locals. |

### Telemetry / Logging
| ID | Event name | Fields / Notes |
|----|-----------|----------------|
| TE-028-01 | `persistence.defaultPathSelected` / existing telemetry | No schema change; logs only emitted for explicit overrides and remain warning-free. |

## Documentation Deliverables
- Roadmap, knowledge map, and `docs/_current-session.md` reference the IDE remediation outcome.
- How-to guides (`docs/2-how-to/configure-persistence-profiles.md`, IDE hygiene notes) describe manual validation steps.
- Release notes mention the absence of automatic fallback logic for warnings.

## Fixtures & Sample Data
- No new fixtures introduced; existing attestation/TOTP vectors reused when strengthening assertions.

## Spec DSL
```
scenarios:
  - id: S-028-01
    focus: governance-docs
  - id: S-028-02
    focus: application-core
  - id: S-028-03
    focus: cli-rest-ui
  - id: S-028-04
    focus: toolchain-quality
requirements:
  - id: FR-028-01
    maps_to: [S-028-01]
  - id: FR-028-02
    maps_to: [S-028-02]
  - id: FR-028-03
    maps_to: [S-028-03]
  - id: FR-028-04
    maps_to: [S-028-04]
non_functional:
  - id: NFR-028-01
    maps_to: [S-028-02, S-028-03, S-028-04]
```
