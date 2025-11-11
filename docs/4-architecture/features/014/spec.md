# Feature 014 – Architecture Harmonization

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/014/plan.md` |
| Linked tasks | `docs/4-architecture/features/014/tasks.md` |
| Roadmap entry | #14 |

## Overview
Unify OCRA orchestration, persistence provisioning, telemetry adapters, and DTO normalization so CLI, REST, and UI facades delegate through the same application services and infrastructure seams. The work also splits `core` into shared vs protocol-specific modules and refreshes documentation/telemetry references, reducing duplicate wiring and preparing the codebase for future protocols.

## Clarifications
- 2025-10-01 – Owner confirmed all five architecture improvements ship in one feature (Option A).
- 2025-10-01 – Scope targets existing OCRA flows; new protocol behaviour remains out of scope (Option B).
- 2025-10-01 – Telemetry consolidation preserves existing structured logging shapes while routing through shared contracts (Option A).

## Goals
- Introduce a shared OCRA application layer consumed by all facades.
- Centralize persistence provisioning and DTO normalization to eliminate duplicate wiring.
- Standardize telemetry emission via shared contracts and adapters.
- Restructure core modules and documentation to reflect the harmonized architecture.

## Non-Goals
- Shipping new user-facing features or additional protocols.
- Replacing MapDB or adding new persistence engines.
- Changing CLI/REST/UI public contracts (flags, endpoints, UI flows).

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-014-01 | Introduce shared OCRA application services (evaluation/verification) so all facades delegate through them instead of instantiating domain components directly. | CLI/REST/UI create `OcraEvaluationApplicationService` / `OcraVerificationApplicationService`; integration tests cover success/failure branches via the new services. | `./gradlew :application:test`, `:cli:test`, `:rest-api:test`, `:ui:test` confirm delegation; ArchUnit guard enforces facade → application dependency. | Facades continue touching `OcraCredentialFactory` or domain stores directly. | Telemetry routed via shared adapters; behaviours logged consistently. | Clarifications 1–3.
| FR-014-02 | Centralize persistence provisioning behind `infra-persistence`’s `CredentialStoreFactory`, enabling configurable paths and in-memory swaps. | CLI/REST bootstrap MapDB stores through the factory; tests swap in-memory stores without custom helpers. | `./gradlew :infra-persistence:test`, facade tests verifying store provisioning; ArchUnit guard forbids direct MapDB access. | Facades instantiate MapDB stores manually or tests cannot override persistence. | Telemetry unchanged; store events logged through existing adapters. | Goals.
| FR-014-03 | Define a shared telemetry contract + adapters for CLI/REST/UI, ensuring sanitized event emission across success/validation/failure cases. | `TelemetryContracts` interface plus adapter implementations; tests and architecture guards confirm usage. | `./gradlew :application:test --tests "*Telemetry*"`, `:core-architecture-tests:test --tests "*TelemetryContract*"`, facade tests verifying sanitized output. | Facades emit bespoke logs or leak secrets. | Telemetry snapshots remain consistent; sanitized fields enforced. | Clarifications 1–3.
| FR-014-04 | Split `core` into `core-shared` and `core-ocra`, updating Gradle + ArchUnit rules and ensuring facades depend only on application-level APIs. | Packages migrated per plan; `CoreModuleSplitArchitectureTest` passes; quality gate remains green. | `./gradlew architectureTest`, `./gradlew qualityGate` confirm module boundaries and coverage. | Facades reference `core-ocra` directly or tests fail due to module wiring issues. | None beyond existing telemetry. | Goals.
| FR-014-05 | Expose shared DTOs/normalization helpers for inline vs stored OCRA requests and reuse them across all facades. | CLI/REST/UI validation uses shared DTOs; tests prove identical error messaging and normalization. | `./gradlew :application:test`, targeted facade tests covering stored/inline flows. | Duplicate validation logic persists or scenarios drift between facades. | Telemetry continues to reference normalized DTO fields. | Goals.

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-014-01 | Maintain quality gate runtime within ±10% after module splits and shared services. | Avoid regressions in CI tooling. | Record `qualityGate` runtime before/after; target met. | Gradle build, automation tasks. | Goals.
| NFR-014-02 | Preserve telemetry sanitization guarantees while consolidating adapters. | Prevent secret leakage. | Telemetry contract tests assert sanitized fields only. | Application module, adapters. | Clarifications 1–3.
| NFR-014-03 | Keep CLI/REST/UI external contracts backwards compatible. | Avoid breaking existing operators. | Snapshot tests (OpenAPI, CLI command outputs, UI fixtures) show no changes. | CLI, REST, UI modules. | Goals.

## UI / Interaction Mock-ups
_Not applicable – UI flows reuse existing components; no new screens introduced._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-014-01 | Shared application services own OCRA orchestration, with facades delegating through them. |
| S-014-02 | `CredentialStoreFactory` provisions persistence for all facades with configurable storage backends. |
| S-014-03 | Telemetry contracts/adapters emit sanitized events consistently across success/validation/failure paths. |
| S-014-04 | Shared DTOs/normalization helpers align inline vs stored request handling across facades. |
| S-014-05 | `core` split enforced via ArchUnit; facades depend on application services, not protocol internals. |
| S-014-06 | Documentation (AGENTS, roadmap, knowledge map) reflects the harmonized architecture. |

## Test Strategy
- **Application:** Unit tests for shared evaluation/verification services, telemetry contracts, DTO normalization.
- **CLI/REST/UI:** Integration tests verifying delegation, persistence factory usage, telemetry adapters, and DTO parity.
- **Architecture tests:** ArchUnit suites enforce façade delegation and module boundaries.
- **Documentation checks:** Snapshot/OpenAPI tests confirm no external contract drift.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-014-01 | `OcraEvaluationApplicationRequest` – shared DTO normalizing stored vs inline requests. | application |
| DO-014-02 | `OcraVerificationApplicationRequest` – shared verification DTO with helpers. | application |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-014-01 | Application service API (internal) | Evaluation/verification orchestration consumed by facades. | Not exposed externally. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-014-01 | `./gradlew --no-daemon :cli:test` | Validates facade delegation through application services and telemetry adapters. |
| CLI-014-02 | `./gradlew --no-daemon :application:test` | Covers shared services, DTOs, and telemetry helpers. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-014-01 | `ocra.request.received`, `ocra.request.validated`, `ocra.request.failed` | Shared adapters ensure `sanitized=true`, hashed context fields, consistent reason codes. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-014-01 | `application/src/test/java/.../TelemetryContractTestSupport.java` | Shared telemetry fixtures for all facades. |
| FX-014-02 | `infra-persistence/src/test/java/.../CredentialStoreFactoryTest.java` | Covers persistence factory configuration. |
| FX-014-03 | `core-architecture-tests/src/test/java/.../CoreModuleSplitArchitectureTest.java` | Enforces module boundaries. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-014-NA | — | UI reuses existing flows; no new states added. |

## Telemetry & Observability
Telemetry emission relies on the shared contract and adapters. Observability improvements come from consistent event structures across facades; no new runtime sinks introduced.

## Documentation Deliverables
- Update AGENTS, roadmap, knowledge map, and how-to guides to describe the shared application layer, persistence factory, telemetry contract, and core module split.
- Record architecture diagrams showing `application`, `infra-persistence`, `core-shared`, `core-ocra` relationships.

## Fixtures & Sample Data
- Telemetry contract fixtures and persistence factory tests serve as reusable artifacts for future protocols.
- Jacoco/PIT reports demonstrate coverage after the architecture changes.

## Spec DSL
```
domain_objects:
  - id: DO-014-01
    name: OcraEvaluationApplicationRequest
    modules: application
  - id: DO-014-02
    name: OcraVerificationApplicationRequest
    modules: application
cli_commands:
  - id: CLI-014-01
    command: ./gradlew --no-daemon :cli:test
    description: Validates CLI delegation to application services
  - id: CLI-014-02
    command: ./gradlew --no-daemon :application:test
    description: Runs shared service + telemetry tests
telemetry_events:
  - id: TE-014-01
    event: ocra.request.*
    fields: sanitized=true, hashed context fields, reasonCode
fixtures:
  - id: FX-014-01
    path: application/src/test/java/.../TelemetryContractTestSupport.java
  - id: FX-014-02
    path: infra-persistence/src/test/java/.../CredentialStoreFactoryTest.java
  - id: FX-014-03
    path: core-architecture-tests/src/test/java/.../CoreModuleSplitArchitectureTest.java
scenarios:
  - id: S-014-01
    description: Facades delegate via shared application services
  - id: S-014-02
    description: CredentialStoreFactory provisions persistence for all facades
  - id: S-014-03
    description: Telemetry contracts/adapters emit sanitized frames
  - id: S-014-04
    description: Shared DTOs normalize inline vs stored flows
  - id: S-014-05
    description: Core split enforced via ArchUnit
  - id: S-014-06
    description: Documentation reflects harmonized architecture
```

## Appendix (Optional)
- Quality gate evidence (2025-10-02): `./gradlew qualityGate` runtime remained within the ±10% target after module splitting (≈75s baseline → 78s post-change).
- Telemetry analyzer output confirmed sanitized fields remained intact after adapters migrated.
