# Feature 013 – Toolchain & Quality Platform

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/013/plan.md` |
| Linked tasks | `docs/4-architecture/features/013/tasks.md` |
| Roadmap entry | #13 – Toolchain & Quality Platform |

## Overview
Feature 013 unifies every toolchain and quality-automation improvement: CLI exit harnesses, Maintenance CLI coverage
buffers, reflection policy enforcement, Java 17 language upgrades, architecture harmonization, SpotBugs dead-state
detectors, PMD rule hardening, Gradle wrapper + plugin upgrades, and the removal of legacy CLI/JS entry points. No new
code ships in this documentation-only iteration; instead, Feature 013 becomes the authoritative documentation for the
quality gates, verification commands, and governance rules that keep the simulator’s tooling coherent.

## Goals
- G-013-01 – Keep CLI tooling healthy (exit-harness rewrite, Maintenance CLI coverage buffer, corrupted-db tests) with
  documented `jacocoAggregatedReport`/hotspot commands.
- G-013-02 – Enforce governance guardrails (no reflection, sealed hierarchies, shared architecture modules) via ArchUnit and
  documented verification steps.
- G-013-03 – Maintain the static-analysis platform (SpotBugs dead-state detectors, PMD 7 rules, Law-of-Demeter scoping,
  NonSerializableClass/NonExhaustiveSwitch) with shared configuration files and suppression etiquette.
- G-013-04 – Ensure build-tool upgrades (Gradle 9 wrapper, plugin bumps) and removal of legacy entry points are documented
  with the required verification commands (`qualityGate`, `pmdMain pmdTest`, `spotbugsMain`, `./gradlew --warning-mode=all clean check`).

## Non-Goals
- Shipping new runtime behaviour or modifying quality thresholds beyond what the legacy specs already enforce.
- Reintroducing deprecated entry points, telemetry fallbacks, or reflection-based seams.
- Relaxing SpotBugs/PMD detectors or the Jacoco coverage buffer without a follow-up governance feature.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-013-01 | CLI exit harness removes `SecurityManager`, covers success (`CommandLine.ExitCode.OK`) and failure (`USAGE`, corrupted DB) paths via direct invocation + forked JVM while preserving JaCoCo instrumentation. | `OcraCliLauncherTest` + forked harness assert exit codes; `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"` stays green. | CLI tests + Jacoco report show coverage > prior baseline. | Tests fail or rely on forbidden APIs. | None. | Spec.
| FR-013-02 | Maintenance CLI coverage buffer ≥0.90 line/branch (once relaxation lifted) is documented with hotspot analysis, forked JVM failure tests, corrupted-db tests, and CLI-specific commands. | Plan/tasks list hotspot metrics + commands; tests cover failure branches; `jacocoAggregatedReport` evidence captured. | `./gradlew jacocoAggregatedReport`, targeted CLI tests, `_current-session.md` log. | Coverage unknown or buffer slips without documentation. | None. | Spec.
| FR-013-03 | Reflection usage removed; ArchUnit + Gradle `reflectionScan` enforce policy; spec/plan/tasks/AGENTS mention the guardrail. | `rg -n "java.lang.reflect"` returns no project-owned matches; ArchUnit/`reflectionScan` run in `qualityGate`. | `./gradlew reflectionScan :core-architecture-tests:test`. | Reflection reintroduced or documentation missing. | None. | Spec.
| FR-013-04 | Java 17 enhancements (sealed CLI hierarchy, sealed REST request variants, text-block OpenAPI examples) ship without changing external contracts. | CLI/REST tests cover sealed hierarchies; OpenAPI snapshots unchanged; `qualityGate` passes. | `./gradlew :cli:test :rest-api:test qualityGate`. | Behavioural regressions or snapshot diffs occur. | None. | Spec.
| FR-013-05 | Architecture harmonization retains shared OCRA application services, persistence factory, telemetry adapter, DTO normalization, and `core` split enforced via ArchUnit. | Facades depend on application services only; ArchUnit tests guard module boundaries; documentation describes architecture. | `./gradlew :application:test :core-architecture-tests:test`. | Facades bypass application layer or telemetry diverges. | Telemetry remains via adapters. | Spec.
| FR-013-06 | SpotBugs dead-state detectors (`URF_*`, `UUF_*`, `UWF_*`, `NP_UNWRITTEN_*`) enforced via shared include filter; violations remediated or justified. | `config/spotbugs/dead-state-include.xml` referenced by all SpotBugs tasks; `./gradlew spotbugsMain spotbugsTest` red→green history recorded. | SpotBugs logs show zero dead-state findings; suppressions documented. | Builds fail or suppressions lack rationale. | None. | Spec.
| FR-013-07 | PMD 7 upgrade + rule hardening (error-prone/best-practice/design, Law-of-Demeter with whitelist, NonSerializableClass, NonExhaustiveSwitch) captured with remediation plan and commands. | `config/pmd/ruleset.xml` + whitelist committed; `pmdMain pmdTest` run green; plan references whitelist entries. | `./gradlew pmdMain pmdTest`. | PMD fails or documentation missing. | Optional `quality.pmd.run` logs if enabled. | Spec.
| FR-013-08 | Gradle wrapper upgraded to 9.1.0 with plugin bumps (e.g., PIT 1.19.0-rc.2); warning-mode sweeps before/after upgrade documented; configuration cache validated. | `gradle/wrapper/gradle-wrapper.properties` pin 9.1; `./gradlew --warning-mode=all clean check` passes; `./gradlew --configuration-cache help` stored in logs. | Wrapper diff + commands recorded in `_current-session.md`. | Build fails or warnings unresolved. | None. | Spec.
| FR-013-09 | Legacy CLI/JS entry points (legacy telemetry fallbacks, router shims, old presets) removed; docs/tests reference canonical adapters/routes only. | CLI/REST/UI telemetry uses `TelemetryContracts`; router state keys canonical; docs highlight change. | `rg "legacyEmit"` returns none; Selenium/REST tests confirm canonical routing. | Old entry points linger, causing drift. | Telemetry unaffected (only canonical). | Spec.
| FR-013-10 | Roadmap, knowledge map, session log (docs/_current-session.md), and `_current-session.md` capture every toolchain change plus the commands executed (`qualityGate`, `spotbugsMain`, `pmdMain pmdTest`, `gradlew wrapper`, CLI tests). | Logs updated per increment; tasks reference command list. | Manual review before closing tasks. | Auditors cannot trace toolchain updates. | None. | Spec.

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-013-01 | Maintain aggregated quality gate runtime within ±15% despite added checks (SpotBugs, PMD, reflection scan). | Developer ergonomics | Record runtimes before/after each increment in plan/tasks. | Gradle build. | Spec.
| NFR-013-02 | Jacoco coverage buffer (≥0.90 line/branch) restored for Maintenance CLI once the relaxation ends; hotspot reports kept current. | Quality assurance | Jacoco report excerpts stored in plan/tasks; `jacocoCoverageVerification` thresholds documented. | CLI module. | Spec.
| NFR-013-03 | Reflection guard + sealed hierarchies remain enforced via ArchUnit/`reflectionScan`; local and CI gates share identical configuration. | Governance | ArchUnit + reflectionScan part of `qualityGate`; docs mention command. | `core-architecture-tests`, buildSrc. | Spec.
| NFR-013-04 | PMD/SpotBugs configuration remains deterministic (shared include/whitelist files, single version pins) and adds ≤2 minutes to CI. | Build stability | CI job durations recorded; configuration stored in repo. | SpotBugs, PMD, buildSrc. | Spec.
| NFR-013-05 | Build upgrades (Gradle 9, plugin bumps) remain reproducible with warning-mode sweeps documented and configuration cache validated. | Tooling reliability | `./gradlew --warning-mode=all clean check` + `--configuration-cache help` outputs logged during upgrade. | Gradle wrapper. | Spec.

## UI / Interaction Mock-ups

### Toolchain Dashboard View (UI-013-01)
```
┌──────────────────── Toolchain Dashboard ──────────────────────┐
│ Quality Gate status: [ PASS ]                                │
│ Filters: [ All modules ▼ ] [ Last run ▼ ]                    │
│ ------------------------------------------------------------- │
│ Metric cards                                                 │
│  ┌──────────┬─────────┬─────────┬─────────┐                  │
│  │SpotBugs  │  PMD    │ Jacoco  │ Reflection │               │
│  │0 issues  │1 warn  │92% cov  │0 hits    │                  │
│  └──────────┴─────────┴─────────┴─────────┘                  │
│ Detail grid                                                  │
│  Module        Last run       Duration   Notes               │
│  application   12:34:10       02:15      jacoco ok           │
│  cli           12:34:15       01:05      reflection clean    │
│  rest-api      12:34:20       01:40      PMD suppression doc │
│ ------------------------------------------------------------- │
│ Footer actions: [Copy summary] [Open plan/tasks] [Export TAP]│
└──────────────────────────────────────────────────────────────┘
```

### Gradle Wrapper Upgrade Modal (UI-013-02)
```
┌────────────── Gradle Wrapper Upgrade ──────────────┐
│ Current version : 8.10                             │
│ Target version  : [ 9.1 ▼ ]                        │
│ Plugin bumps    : PIT 1.19.0-rc.2, Spotless 6.25   │
│ Warning mode    : [ ] Run --warning-mode=all       │
│                                                    │
│ Command preview                                   │
│  ./gradlew wrapper --gradle-version 9.1 --bin      │
│  ./gradlew --warning-mode=all clean check          │
│                                                    │
│ Checklist                                          │
│  [ ] Update gradle-wrapper.properties              │
│  [ ] Update gradle/libs.versions.toml               │
│  [ ] Log telemetry.toolchain.wrapperUpgrade        │
│                                                    │
│ Buttons: [Run upgrade] [Cancel]                    │
└────────────────────────────────────────────────────┘
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-013-01 | CLI exit harness + Maintenance CLI coverage buffer verified via CLI tests and Jacoco hotspot reports. |
| S-013-02 | Reflection-free codebase enforced via ArchUnit + reflectionScan, documented in governance artefacts. |
| S-013-03 | Java 17 features adopted in CLI/REST while maintaining public contracts + quality gates. |
| S-013-04 | Architecture harmonization keeps shared services/persistence/telemetry/DTOs intact with module boundaries enforced. |
| S-013-05 | SpotBugs + PMD detectors configured, running, and documented with remediation guidance. |
| S-013-06 | Gradle wrapper/plugin upgrades validated with warning-mode sweeps + configuration cache checks. |
| S-013-07 | Legacy entry points removed; telemetry/router states rely on canonical adapters only. |
| S-013-08 | Roadmap/knowledge map/session log (docs/_current-session.md)/session logs capture commands + follow-ups for every toolchain change. |

## Test Strategy
- **CLI Tooling:** `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"` and Maintenance CLI forked JVM tests;
  `./gradlew jacocoAggregatedReport jacocoCoverageVerification` for hotspot buffers.
- **Reflection/Architecture:** `./gradlew reflectionScan :core-architecture-tests:test`.
- **Java 17/Shared services:** `./gradlew :cli:test :rest-api:test :application:test :ui:test` plus OpenAPI snapshot tests.
- **SpotBugs/PMD:** `./gradlew spotbugsMain spotbugsTest pmdMain pmdTest` (module-specific tasks as needed).
- **Gradle upgrade:** `./gradlew --warning-mode=all clean check`, `./gradlew --configuration-cache help`, `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`.
- **Telemetry/router cleanup:** Selenium + REST suites verifying canonical routing/telemetry connectors.
- **Documentation:** `./gradlew spotlessApply check`; `_current-session.md` notes commands per increment.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-013-01 | `ToolchainGuardrail` bundles enforcement commands, owner features, and archival notes. | docs, build |
| DO-013-02 | `CLIExitHarness` defines exit-code coverage paths, forked JVM helpers, and measurement collectors. | cli, core-architecture-tests |
| DO-013-03 | `ReflectionPolicy` enumerates allowed packages, scanning commands, and violation responses. | archunit, core |
| DO-013-04 | `QualityGatePipeline` records stage ordering (Spotless → ArchUnit → SpotBugs/PMD → Jacoco/PIT) plus wrapper metadata. | build, application |

### API Routes / Services
| ID | Method | Path | Description |
|----|--------|------|-------------|
| API-013-01 | GET | `/api/v1/toolchain/quality` | Reports current quality gate status, thresholds, and hotspot/permutation links for dashboards. |
| API-013-02 | POST | `/api/v1/toolchain/architecture/reflection` | Triggers `reflectionScan`/ArchUnit verification runs for ad-hoc audits with toolchain telemetry. |
| API-013-03 | POST | `/api/v1/toolchain/analysis/spotbugs` | Validates SpotBugs/PMD rule sets; returns findings with whitelist references. |
| API-013-04 | POST | `/api/v1/toolchain/gradle/wrapper` | Launches Gradle wrapper regeneration and documents plugin upgrades/`--warning-mode=all` sweeps. |

### CLI / Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-013-01 | `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"` | Validates CLI exit harness + forked JVM failure paths.
| CLI-013-02 | `./gradlew jacocoAggregatedReport jacocoCoverageVerification` | Captures Maintenance CLI coverage buffer + hotspot data.
| CLI-013-03 | `./gradlew reflectionScan :core-architecture-tests:test` | Enforces anti-reflection policy + architecture rules.
| CLI-013-04 | `./gradlew spotbugsMain spotbugsTest pmdMain pmdTest` | Runs dead-state detectors + expanded PMD rules.
| CLI-013-05 | `./gradlew --warning-mode=all clean check --configuration-cache help` | Validates Gradle upgrade + warning sweeps.
| CLI-013-06 | `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin` | Regenerates wrapper during upgrades.

### Telemetry Events
| ID | Event | Fields / Notes |
|----|-------|----------------|
| TE-013-01 | `telemetry.toolchain.qualityGate` | `stage`, `durationMs`, `status`, `hotspotReport`. |
| TE-013-02 | `telemetry.toolchain.reflectionScan` | `result`, `violations`, `scanDurationMs`. |
| TE-013-03 | `telemetry.toolchain.staticAnalysis` | `tool`, `ruleSet`, `violations`, `suppressed`. |
| TE-013-04 | `telemetry.toolchain.wrapperUpgrade` | `gradleVersion`, `plugins`, `warningFlags`, `result`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-013-01 | `config/spotbugs/dead-state-include.xml` | Defines include/exclude filters referenced by SpotBugs runs.
| FX-013-02 | `config/pmd/ruleset.xml` + `config/pmd/law-of-demeter-excludes.txt` | Law-of-Demeter + unused-field/method PMD rules.
| FX-013-03 | `docs/4-architecture/features/013/plan.md` | Hotspot tables, command lists, and wrapper/pit notes referenced in docs.
| FX-013-04 | `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml` | Gradle 9/ plugin version state captured for verification.

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-013-01 | Toolchain dashboard view | Quality gate, SpotBugs, PMD, reflection metrics rendered for reviewers; triggered via documentation/perf review sessions.
| UI-013-02 | Gradle wrapper upgrade modal | Displays plugin change log + verification commands when `/tools/gradle/wrapper` is invoked or docs request regeneration.

## Telemetry & Observability

Toolchain telemetry flows through `TelemetryContracts` adapters so CLI/REST/UI dashboards stay consistent:
- `telemetry.toolchain.qualityGate` emits `stage`, `durationMs`, `status`, and `hotspotReport` whenever `qualityGate` runs; verify jobs use this event to sign off on output coverage.
- `telemetry.toolchain.reflectionScan` reports `result`, `violations`, and `scanDurationMs` so auditors can spot reflection drift before it hits production.
- `telemetry.toolchain.staticAnalysis` captures `tool` (SpotBugs/PMD), `ruleSet`, `violations`, and `suppressed` counts for the shared detectors and obscures suppressed entries with documented whitelists.
- `telemetry.toolchain.wrapperUpgrade` logs `gradleVersion`, `pluginVersions`, `warningFlags`, and `result` when wrapper regeneration or `--warning-mode=all clean check` sweeps run.

## Documentation Deliverables
- Update roadmap/knowledge map/session log (docs/_current-session.md) with toolchain status, wrapper upgrades, and verification commands.
- Ensure AGENTS.md references reflection policy, sealed hierarchies, SpotBugs/PMD expectations, and CLI/Gradle command lists.
- Maintain hotspot reports, whitelist files, and wrapper/plugin version notes inside plan/tasks for future auditors.

## Fixtures & Sample Data
- `config/spotbugs/dead-state-include.xml` defines include/exclude filters referenced by SpotBugs runs.
- `config/pmd/ruleset.xml` plus `config/pmd/law-of-demeter-excludes.txt` capture the PMD rule set and exceptions used by CI.
- `docs/4-architecture/features/013/plan.md` hosts hotspot tables, command lists, and wrapper/PIT notes referenced by the documentation.
- `gradle/wrapper/gradle-wrapper.properties` and `gradle/libs.versions.toml` document Gradle/ plugin versions for upgrade tracking.

## Spec DSL
```
domain_objects:
  - id: DO-013-01
    name: ToolchainGuardrail
    fields:
      - name: name
        type: string
      - name: enforcementCommands
        type: list<string>
      - name: sourceFeature
        type: string
  - id: DO-013-02
    name: CLIExitHarness
    fields:
      - name: scenario
        type: enum[success,failure,corruptedDb]
      - name: instrumentation
        type: list<string>
  - id: DO-013-03
    name: ReflectionPolicy
    fields:
      - name: allowedPackages
        type: list<string>
      - name: scanCommand
        type: string
  - id: DO-013-04
    name: QualityGatePipeline
    fields:
      - name: stages
        type: list<string>
      - name: thresholds
        type: map
cli_commands:
  - id: CLI-013-01
    command: ./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"
  - id: CLI-013-02
    command: ./gradlew jacocoAggregatedReport jacocoCoverageVerification
  - id: CLI-013-03
    command: ./gradlew reflectionScan :core-architecture-tests:test
  - id: CLI-013-04
    command: ./gradlew spotbugsMain spotbugsTest pmdMain pmdTest
  - id: CLI-013-05
    command: ./gradlew --warning-mode=all clean check --configuration-cache help
  - id: CLI-013-06
    command: ./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin
routes:
  - id: API-013-01
    method: GET
    path: /api/v1/toolchain/quality
  - id: API-013-02
    method: POST
    path: /api/v1/toolchain/architecture/reflection
  - id: API-013-03
    method: POST
    path: /api/v1/toolchain/analysis/spotbugs
  - id: API-013-04
    method: POST
    path: /api/v1/toolchain/gradle/wrapper
telemetry_events:
  - id: TE-013-01
    event: telemetry.toolchain.qualityGate
  - id: TE-013-02
    event: telemetry.toolchain.reflectionScan
  - id: TE-013-03
    event: telemetry.toolchain.staticAnalysis
  - id: TE-013-04
    event: telemetry.toolchain.wrapperUpgrade
fixtures:
  - id: FX-013-01
    path: config/spotbugs/dead-state-include.xml
  - id: FX-013-02
    path: config/pmd/ruleset.xml
  - id: FX-013-03
    path: docs/4-architecture/features/013/plan.md
  - id: FX-013-04
    path: gradle/wrapper/gradle-wrapper.properties
ui_states:
  - id: UI-013-01
    description: Toolchain dashboard view.
  - id: UI-013-02
    description: Gradle wrapper upgrade modal.
```
