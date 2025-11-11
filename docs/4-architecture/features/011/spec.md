# Feature 011 – Reflection Policy Hardening

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/011/plan.md` |
| Linked tasks | `docs/4-architecture/features/011/tasks.md` |
| Roadmap entry | #11 |

## Overview
Eliminate reflection-dependent seams across production and test code, codify the anti-reflection policy inside contributor guidance, and wire reusable automation that prevents reflective APIs from re-entering the repository. The effort spans core, CLI, REST, and shared architecture tests plus the shared Gradle toolchain.

## Clarifications
- 2025-10-01 – The no-reflection policy covers production and test sources; legitimate cases must be rewritten without reflection or explicitly exempted through governance (Option A).
- 2025-10-01 – Broad refactors are acceptable when needed to expose collaborators for tests, provided they are documented in this spec and validated through the test-first cadence (Option A).
- 2025-10-01 – Anti-reflection guidance must be encoded inside `AGENTS.md` so every contributor sees it before planning; derivative templates may link to it later (Option A).

## Goals
- Remove reflection usage from project-owned sources and replace it with explicit seams that keep CLI/REST/core behaviour intact.
- Land automated guards (ArchUnit + Gradle `reflectionScan`) that fail quickly whenever reflection sneaks back in.
- Document the policy and mitigation strategies in `AGENTS.md`, knowledge map entries, and related runbooks so future work remains compliant.

## Non-Goals
- Changing legitimate framework-level reflection that ships with vetted dependencies (e.g., Spring).
- Introducing new testing frameworks or dependency upgrades beyond what the specification already covers.
- Expanding the policy to other dynamic constructs (e.g., proxies) without a follow-on feature.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-011-01 | Remove project-owned reflection usage, replacing it with explicit APIs or test seams. | Repository scan finds no `java.lang.reflect.*` calls or reflective string lookups outside approved exemptions. | `rg --hidden --glob "*.java" "java.lang.reflect"` returns zero hits; targeted module tests confirm behaviour parity. | Scan or tests detect reflection usage, keeping the gate red. | No runtime telemetry changes; enforcement happens via build output. | Clarifications 1–2; Goals. |
| FR-011-02 | Expose deterministic collaborators (core, CLI, REST) so previously reflective tests call public/package-private seams. | Updated tests interact with explicit helpers/records and all suites remain green. | `./gradlew :core:test`, `:cli:test`, `:rest-api:test` cover new seams; ArchUnit guard observes no forbidden imports. | Tests fail or rely on reflection to access collaborators. | No telemetry changes; guard logs reference offending classes. | Clarifications 2; Goals. |
| FR-011-03 | Update `AGENTS.md` and supporting docs to capture the anti-reflection policy and mitigation tactics. | Documentation enumerates the ban, rationale, and enforcement commands with links to Feature 011. | `rg -n "reflection" AGENTS.md` highlights the updated policy language. | Missing or outdated guidance leaves future contributors unaware of the guardrails. | Not applicable. | Clarification 3; Goals. |
| FR-011-04 | Add automated guards (ArchUnit + Gradle `reflectionScan`) wired into `./gradlew qualityGate`. | ArchUnit rule plus regex scan execute during the quality gate and fail when reflection reappears. | `./gradlew :core-architecture-tests:test --tests "*Reflection*"` and `./gradlew reflectionScan qualityGate` run green post-refactor, red when new reflection is added. | Guard tasks pass even when reflection exists, or block legitimate framework usage without allowlists. | Build logs capture offending classes/files for traceability. | Goals. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-011-01 | Maintainability – new seams must minimise API churn. | Avoid unnecessary public APIs while keeping tests deterministic. | Package-private helpers and records documented in spec/plan; tests cover them without reflection. | Core/CLI/REST modules. | Goals. |
| NFR-011-02 | Developer Feedback – reflection guards should execute quickly. | Keep local guard latency within existing gates. | `reflectionScan` completes <10s locally; quality gate runtime stays ≤30 min. | Gradle tooling, ArchUnit suite. | Goals. |
| NFR-011-03 | Traceability – any exemptions require documentation. | Governance needs audit trails for intentional reflection usage. | Spec/plan/tasks reference exemptions and guard allowlists; knowledge map entries note new seams. | Docs, knowledge map, governance process. | Clarifications 1 & 3. |

## UI / Interaction Mock-ups
_Not applicable – Feature 011 does not add UI surfaces._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-011-01 | All project-owned sources eliminate reflection, and repository scans stay clean outside documented exemptions. |
| S-011-02 | ArchUnit guard (`ReflectionPolicyTest`) fails builds when forbidden imports appear. |
| S-011-03 | Gradle `reflectionScan` task fails on reflective strings and is part of the quality gate. |
| S-011-04 | `AGENTS.md` and contributor docs capture the anti-reflection rule with mitigation strategies. |
| S-011-05 | CLI/REST/core seams added during refactors are exercised by updated tests, proving behaviour parity without reflection. |

## Test Strategy
- **Core:** Update unit tests to call explicit seams (e.g., MapDB credential store helpers) and ensure `:core:test` runs without reflection.
- **Application/CLI:** Picocli/CLI suites call package-private helpers directly; `:cli:test` verifies exit behaviour without reflective access.
- **REST:** Controller/service tests call injectable collaborators; OpenAPI snapshot unaffected but integration tests validate seams.
- **Architecture Tests:** `core-architecture-tests` ArchUnit suite enforces the ban and remains part of the quality gate.
- **Build Tooling:** `reflectionScan` task executes alongside quality gate tasks to detect string-based reflective calls.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-011-NA | No new runtime domain objects introduced; feature adds internal seams only. | — |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-011-NA | — | No REST contract changes. | — |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-011-01 | `./gradlew --no-daemon reflectionScan` | Runs the regex-based reflection guard and fails on banned patterns. |
| CLI-011-02 | `./gradlew --no-daemon :core-architecture-tests:test --tests "*ReflectionPolicyTest"` | Executes the ArchUnit guard enforcing the anti-reflection rule. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-011-NA | — | Build-time enforcement only; no runtime telemetry added. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-011-01 | `core-architecture-tests/src/test/java/io/openauth/sim/architecture/ReflectionPolicyTest.java` | ArchUnit test enforcing the reflection ban. |
| FX-011-02 | `core-architecture-tests/src/test/java/io/openauth/sim/architecture/ReflectionRegexScanTest.java` | Regex-based guard validating `reflectionScan` coverage. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-011-NA | — | Not applicable. |

## Telemetry & Observability
Build output from `reflectionScan`, ArchUnit tests, and `qualityGate` serves as the observable signal. When the guard fails, Gradle logs list the offending classes or file paths. No production telemetry changes occur, so existing logging remains untouched.

## Documentation Deliverables
- `AGENTS.md` – Anti-reflection policy, mitigation guidance, and guard commands.
- `docs/4-architecture/knowledge-map.md` – Document new seams and guard automation.
- `docs/_current-session.md` & migration tracker – Record template adoption and guard deployment progress.

## Fixtures & Sample Data
- Maintain the ArchUnit and regex guard sources listed above; keep them synchronized with the quality gate configuration.
- Store scan reports/logs within standard Gradle build directories when troubleshooting guard failures.

## Spec DSL
```
domain_objects: []
cli_commands:
  - id: CLI-011-01
    command: ./gradlew --no-daemon reflectionScan
    description: Regex-based guard for reflective APIs
  - id: CLI-011-02
    command: ./gradlew --no-daemon :core-architecture-tests:test --tests "*ReflectionPolicyTest"
    description: ArchUnit enforcement of the reflection ban
telemetry_events: []
fixtures:
  - id: FX-011-01
    path: core-architecture-tests/src/test/java/io/openauth/sim/architecture/ReflectionPolicyTest.java
    purpose: Build-time guard for reflection imports
  - id: FX-011-02
    path: core-architecture-tests/src/test/java/io/openauth/sim/architecture/ReflectionRegexScanTest.java
    purpose: Regex-based guard backing the reflectionScan task
scenarios:
  - id: S-011-01
    description: Repository sources remain reflection-free
  - id: S-011-02
    description: ArchUnit guard blocks reflective imports
  - id: S-011-03
    description: Gradle reflectionScan task fails on banned strings
  - id: S-011-04
    description: Contributor docs communicate the policy and commands
  - id: S-011-05
    description: Refactored CLI/REST/core seams keep behaviour parity without reflection
```

## Appendix (Optional)
- Reflection inventory snapshot (2025-10-01): CLI tests (`OcraCliTest`, `MaintenanceCliTest`), core tests (`MapDbCredentialStoreTest`, `OcraCredentialFactoryLoggingTest`), REST tests (`RestPersistenceConfigurationTest`, `OcraEvaluationServiceTest`, `OcraVerificationServiceTest`), and documentation samples were the original offenders; all were refactored before enabling the guards.
