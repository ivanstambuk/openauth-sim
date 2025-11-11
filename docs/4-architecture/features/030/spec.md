# Feature 030 – Gradle 9 Upgrade

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/030/plan.md` |
| Linked tasks | `docs/4-architecture/features/030/tasks.md` |
| Roadmap entry | #13 – Toolchain & Quality Platform |

## Overview
Upgrade the build tooling from Gradle 8.10 to Gradle 9.1.0 to benefit from the latest performance improvements,
reproducible archive defaults, and expanded configuration-cache coverage. The upgrade must preserve the existing Java 17
requirement, keep all quality plugins operational, and document any workflow adjustments for future increments.

## Clarifications
- 2025-10-19 – Owner approved upgrading the Gradle wrapper to version 9.1.0 following the documented validation plan.

## Goals
- G-030-01 – Capture the pre-upgrade warning inventory and remediation approach in governance docs.
- G-030-02 – Regenerate the Gradle wrapper at 9.1.0 and ensure dependent plugins remain compatible.
- G-030-03 – Validate the upgraded build with `--warning-mode=all` plus targeted suites and configuration-cache checks.
- G-030-04 – Refresh reproducible artefacts/docs and record any workflow adjustments or follow-ups.

## Non-Goals
- N-030-01 – Upgrading Java toolchains beyond the existing Java 17 requirement.
- N-030-02 – Modifying dependency versions or Gradle plugins unless required for Gradle 9 compatibility.
- N-030-03 – Restructuring the multi-module layout or adding new build scripts.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-030-01 | Governance artefacts record the Gradle 8.10 warning sweep and remediation plan before upgrading (S-030-01). | Roadmap/current-session/spec detail the IDE snapshot, commands, and conclusions. | Docs review confirms only current instructions remain. | Missing documentation or stale warning references. | Build logs stored for traceability; no telemetry change. | Clarifications 2025-10-19. |
| FR-030-02 | Wrapper and plugin inventory updated to Gradle 9.1.0, including required plugin bumps (S-030-02). | `gradle-wrapper.properties` points to `gradle-9.1.0-bin.zip`, wrapper JAR regenerated, PIT plugin upgraded. | Wrapper command + git status validate artefacts; tests exercise plugin dependent tasks. | Wrapper artefacts mismatch or plugin incompatibility remains. | Build logs capture wrapper regeneration. | G-030-02. |
| FR-030-03 | Post-upgrade validation runs `./gradlew --warning-mode=all clean check`, configuration-cache, and targeted module suites (S-030-03). | All modules complete under Gradle 9.1.0; CLI/REST/Selenium smoke tests remain green. | Recorded Gradle commands plus targeted module outputs. | New warnings, cache failures, or failing suites. | Build scan/logs note cache status only; no new telemetry. | G-030-03. |
| FR-030-04 | Reproducible artefacts/docs updated (or confirmed unchanged) and completion logged in roadmap/tasks/session snapshot (S-030-04). | Release notes/how-to docs mention Gradle 9; snapshots diff clean or updated intentionally. | Documentation review + git history confirm updates. | Missing documentation or unchecked artefact drift. | None. | G-030-04. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-030-01 | Maintain Java 17 toolchain compatibility while running Gradle 9. | Constitution + toolchain guardrail. | `JAVA_HOME` validation + Gradle wrapper output. | local JDK + Gradle wrapper. | Project constitution. |
| NFR-030-02 | Keep Gradle builds reproducible with `--warning-mode=all clean check` and configuration cache enabled. | Build hygiene. | Command exit codes + configuration cache reports. | All modules + quality plugins. | FR-030-03. |
| NFR-030-03 | Document workflow adjustments (wrapper usage, plugin bumps, cache notes) for downstream agents. | Governance traceability. | Docs/plan/tasks mention new steps; migration tracker entry added. | docs hierarchy. | G-030-04. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-030-01 | Run Gradle 8.10 `--warning-mode=all clean check`, capture deprecations, and resolve/document them before upgrading. |
| S-030-02 | Regenerate the Gradle wrapper at 9.1.0, update artifacts, and bump any required plugins (e.g., PIT). |
| S-030-03 | Validate the upgraded build with `--warning-mode=all clean check`, targeted module suites, and configuration-cache runs. |
| S-030-04 | Review reproducible artefacts, roadmap, and documentation to close out the upgrade. |

## Test Strategy
- Pre-upgrade sweep: `./gradlew --warning-mode=all clean check` (Gradle 8.10).
- Wrapper update: `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`.
- Post-upgrade validation: `./gradlew --warning-mode=all clean check`, `./gradlew --configuration-cache help`.
- Targeted smoke tests: `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload"` (run if not covered by `check`).

## Interface & Contract Catalogue
### Build Artefacts
| ID | Description | Modules |
|----|-------------|---------|
| BA-030-01 | `gradle/wrapper/gradle-wrapper.properties` (distribution URL, checksum). | Root build |
| BA-030-02 | `gradle/wrapper/gradle-wrapper.jar` regenerated with Gradle 9.1.0. | Root build |

### Tooling / Plugins
| ID | Component | Notes |
|----|-----------|-------|
| TP-030-01 | `info.solidsoft.pitest` Gradle plugin | Bumped to 1.19.0-rc.2 for Gradle 9 compatibility. |

## Documentation Deliverables
- Update roadmap, knowledge map, and `docs/_current-session.md` with Gradle 9 status and verification commands.
- Record wrapper/plugin changes plus cache behaviour in the feature plan/tasks and migration tracker.

## Fixtures & Sample Data
- Not applicable (build tooling change only).

## Spec DSL
```
scenarios:
  - id: S-030-01
    focus: pre-upgrade-warning-sweep
  - id: S-030-02
    focus: wrapper-and-plugin-upgrade
  - id: S-030-03
    focus: post-upgrade-validation
  - id: S-030-04
    focus: documentation-and-artifact-review
requirements:
  - id: FR-030-01
    maps_to: [S-030-01]
  - id: FR-030-02
    maps_to: [S-030-02]
  - id: FR-030-03
    maps_to: [S-030-03]
  - id: FR-030-04
    maps_to: [S-030-04]
non_functional:
  - id: NFR-030-01
    maps_to: [S-030-02]
  - id: NFR-030-02
    maps_to: [S-030-03]
  - id: NFR-030-03
    maps_to: [S-030-01, S-030-04]
```
