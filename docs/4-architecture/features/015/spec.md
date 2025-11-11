# Feature 015 – SpotBugs Dead-State Enforcement

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/015/plan.md` |
| Linked tasks | `docs/4-architecture/features/015/tasks.md` |
| Roadmap entry | #15 |

## Overview
Promote SpotBugs dead-state detectors (unread/unwritten/unused fields) and complementary PMD rules across all JVM modules so latent state fails the quality gate. The work introduces a shared include filter, wires Gradle tasks to consume it, remediates the flagged violations, and documents the stricter guardrail so local and CI workflows remain consistent.

## Clarifications
- 2025-10-03 – Detectors enabled: `URF_UNREAD_FIELD`, `URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD`, `UUF_UNUSED_FIELD`, `UWF_UNWRITTEN_FIELD`, `NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD`; optional patterns (e.g., `SS_SHOULD_BE_STATIC`) remain future scope (Option A).
- 2025-10-03 – Enforcement applies to every JVM subproject; suppressions require `@SuppressFBWarnings` with justification logged in the feature plan (Option A).
- 2025-10-03 – Documentation updates live in the quality automation guides (analysis gate checklist + developer tooling) so contributors understand the new detectors and suppression etiquette (Option A).
- 2025-10-03 – PMD adds `UnusedPrivateField` and `UnusedPrivateMethod` rules alongside SpotBugs to guard private members at compile time (Option A).

## Goals
- Activate the shared SpotBugs include filter and make dead-state detectors fail builds consistently.
- Remediate (or justify) existing unread/unwritten fields so the stricter SpotBugs runs pass.
- Expand documentation/runbooks so future work respects the enforcement and suppression policy.
- Add PMD unused-field/method rules to catch private dead code earlier.

## Non-Goals
- Replacing SpotBugs with another static-analysis engine.
- Introducing additional detector families beyond the agreed list.
- Altering runtime behaviour except where required to clean dormant state.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-015-01 | Provide a shared SpotBugs include filter that lists the approved dead-state bug patterns and is consumed by every SpotBugs task. | `config/spotbugs/dead-state-include.xml` lives in the repo and Gradle `spotbugs*` tasks reference it. | `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks` reports the configured patterns before remediation. | Tasks ignore the filter or execute without the detectors. | Not applicable. | Clarifications 1–2.
| FR-015-02 | Configure SpotBugs tasks to fail on the included patterns and remediate existing violations. | After cleanup, SpotBugs tasks and `./gradlew spotlessApply check` pass with zero dead-state findings. | `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks` (red → green) and root `spotlessApply check`. | Builds continue to fail or suppressions lack justification. | Not applicable. | Goals.
| FR-015-03 | Document the enforcement (detector list, suppression policy, remediation steps) in analysis gate/tooling guides. | Docs outline detectors, commands, and suppression etiquette; feature plan notes reference them. | `rg -n "dead-state" docs/5-operations/analysis-gate-checklist.md docs/3-reference/developer-tooling.md`. | Developers lack guidance or runbooks omit enforcement steps. | Not applicable. | Clarifications 3.
| FR-015-04 | Enforce PMD `UnusedPrivateField` and `UnusedPrivateMethod` rules across JVM modules. | PMD configuration enables both rules; `pmdMain`/`pmdTest` run green after cleanup. | `./gradlew --no-daemon :rest-api:pmdTest` and `./gradlew --no-daemon check`. | Rules not enabled or builds keep unused members. | Not applicable. | Clarification 4.

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-015-01 | Build impact | Keep `./gradlew check` runtime within ±15% after enabling detectors. | Compare CI/local runtimes before/after activation. | Gradle SpotBugs/PMD tasks. | Goals.
| NFR-015-02 | Suppression discipline | Ensure any `@SuppressFBWarnings` entries include rationale and plan references. | Plan Notes record suppressions; lint runs stay green. | SpotBugs, documentation. | Clarifications 2–3.
| NFR-015-03 | Consistency | Local developer runs and CI quality gates share the same filter/rules. | Verify Gradle configuration uses a single include file and shared PMD ruleset. | Build tooling. | Goals.

## UI / Interaction Mock-ups
_Not applicable – static-analysis enforcement has no UI._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-015-01 | Shared SpotBugs include filter + Gradle wiring enable dead-state detectors across all JVM modules. |
| S-015-02 | Existing dead-state violations are remediated so SpotBugs tasks pass. |
| S-015-03 | Documentation/runbooks explain the enforcement and suppression policy. |
| S-015-04 | PMD rules `UnusedPrivateField` and `UnusedPrivateMethod` run in the quality gate after code cleanup. |

## Test Strategy
- Run `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks` with the new filter to confirm detectors trigger (red), then rerun after remediation (green).
- Execute `./gradlew --no-daemon spotlessApply check` to ensure aggregate gates remain green once detectors succeed.
- Run `./gradlew --no-daemon :rest-api:pmdTest` (and other PMD tasks as needed) before/after enabling new rules to confirm proper enforcement.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-015-NA | — | Not applicable. |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-015-NA | — | Not applicable. | — |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-015-01 | `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks` | Exercises SpotBugs dead-state detectors. |
| CLI-015-02 | `./gradlew --no-daemon spotlessApply check` | Confirms global gate stays green after remediation. |
| CLI-015-03 | `./gradlew --no-daemon :rest-api:pmdTest` | Runs PMD with unused-field/method rules enabled. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-015-NA | — | Static analysis only; no runtime telemetry changes. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-015-01 | `config/spotbugs/dead-state-include.xml` | Shared include filter for SpotBugs detectors. |
| FX-015-02 | `docs/5-operations/analysis-gate-checklist.md` | Documents the detector list and remediation guidance. |
| FX-015-03 | `config/pmd/ruleset.xml` (or equivalent) | Carries the new PMD rules for unused fields/methods. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-015-NA | — | Not applicable. |

## Telemetry & Observability
SpotBugs/PMD logs act as the observable signal. When detectors fire, Gradle output lists the offending classes; suppressions must reference this feature. No runtime telemetry changes occur.

## Documentation Deliverables
- Update `docs/5-operations/analysis-gate-checklist.md` and the developer tooling guide with detector lists, commands, and suppression policy.
- Note the enforcement and any suppressions in the feature plan Notes section for future auditors.

## Fixtures & Sample Data
- Keep the SpotBugs include filter under version control and reference it from every JVM subproject.
- Maintain PMD ruleset changes and document any allowed exceptions.

## Spec DSL
```
cli_commands:
  - id: CLI-015-01
    command: ./gradlew --no-daemon :application:spotbugsMain --rerun-tasks
    description: Runs SpotBugs with dead-state detectors
  - id: CLI-015-02
    command: ./gradlew --no-daemon spotlessApply check
    description: Verifies the global gate after remediation
  - id: CLI-015-03
    command: ./gradlew --no-daemon :rest-api:pmdTest
    description: Runs PMD with unused-field/method rules
fixtures:
  - id: FX-015-01
    path: config/spotbugs/dead-state-include.xml
    purpose: Shared SpotBugs include filter
  - id: FX-015-02
    path: config/pmd/ruleset.xml
    purpose: PMD rules including unused member detectors
  - id: FX-015-03
    path: docs/5-operations/analysis-gate-checklist.md
    purpose: Documentation describing enforcement
scenarios:
  - id: S-015-01
    description: Dead-state detectors enabled via shared filter
  - id: S-015-02
    description: Violations remediated so SpotBugs tasks pass
  - id: S-015-03
    description: Documentation explains enforcement and suppressions
  - id: S-015-04
    description: PMD unused-field/method rules enforced
```

## Appendix (Optional)
- 2025-10-03 – `./gradlew :application:spotbugsMain --rerun-tasks` failed with `URF_UNREAD_FIELD`; rerun passed after removing `clock` dependency.
- 2025-10-03 – `./gradlew spotlessApply check` runtime remained ≈3m16s post-detector activation (+4% vs baseline).
