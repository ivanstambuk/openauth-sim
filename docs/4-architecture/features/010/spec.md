# Feature 010 – CLI Exit Testing Maintenance

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/010/plan.md` |
| Linked tasks | `docs/4-architecture/features/010/tasks.md` |
| Roadmap entry | #10 |

## Overview
Retire the deprecated `SecurityManager` interceptors inside the CLI launcher tests so the suite keeps verifying exit-code behaviour on Java 17+ toolchains. The work is strictly test-scoped: direct invocation must still cover success paths while a forked JVM harness observes failure exits without modifying the production `OcraCliLauncher`.

## Clarifications
- 2025-10-01 – Scope is limited to the CLI launcher tests; production `OcraCliLauncher` stays untouched (Option A, owner decision).
- 2025-10-01 – Prefer bespoke harnesses over third-party helpers; reuse built-in JDK facilities even if extra fixtures are required (Option A).
- 2025-10-01 – Preserve assertions validating Picocli usage exit codes so success/failure coverage remains equivalent to the SecurityManager-based approach (Option A).

## Goals
- Standardise CLI exit-code verification so automation can rely on deterministic pass/fail signals.
- Maintain success-path coverage through direct invocation and failure-path coverage via a forked JVM harness that respects JaCoCo instrumentation.
- Document the test-only nature of the change so future agents avoid touching runtime CLI wiring when maintaining exit-code tests.

## Non-Goals
- Introducing new CLI commands, flags, or telemetry.
- Modifying REST, UI, or application modules.
- Adding third-party testing dependencies for process interception.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-010-01 | Remove all `SecurityManager` references from CLI launcher tests while keeping coverage intact. | `OcraCliLauncherTest` relies on direct invocation and process forking instead of SecurityManager interception. | `rg SecurityManager cli/src/test/java/io/openauth/sim/cli` returns no matches; `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"` passes. | Compilation/runtime failures triggered by forbidden APIs or lingering SecurityManager hooks. | No new telemetry; CLI logging remains unchanged. | Clarifications 1–3. |
| FR-010-02 | Cover both success (`--help`) and failure (`import`) flows with deterministic exit codes using direct invocation plus a forked JVM harness that copies the active JaCoCo agent. | Direct invocation asserts CommandLine.ExitCode.OK and survives without System.exit; spawned JVM observes `CommandLine.ExitCode.USAGE` and captures usage text. | Tests `mainPrintsUsageWithoutExitingWhenExitCodeZero`, `mainInvokesSystemExitForNonZeroCode`, and `mainExitsProcessWhenExitCodeNonZero` execute via `:cli:test` and assert exit codes/output. | Harness fails to propagate the agent, cannot capture output, or produces flaky exit codes. | Test output only; no production telemetry changes. | Goals; Clarifications 2–3. |
| FR-010-03 | Keep runtime CLI behaviour untouched so diffs remain constrained to tests/docs, preventing accidental exit-code regressions. | `git diff` shows only test/doc updates; CLI smoke tests still succeed. | `./gradlew --no-daemon :cli:test` plus targeted CLI integration tests confirm runtime behaviour unchanged. | Any runtime CLI change, new dependency, or altered exit code surfaces in review/tests. | No telemetry changes; existing CLI traces continue to function. | Clarification 1; Non-Goals. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-010-01 | Test harness must operate on Java 17+ where `SecurityManager` is disabled. | Java 17 toolchain baseline for OpenAuth Simulator. | `./gradlew --no-daemon spotlessApply check` on JDK 17 passes without SecurityManager warnings. | Gradle toolchain config, CLI module tests. | Goals. |
| NFR-010-02 | Forked JVM harness must propagate the active JaCoCo agent so coverage metrics remain accurate. | Prevent coverage regressions when verifying exit paths. | Harness inspects `ManagementFactory.getRuntimeMXBean().getInputArguments()` and forwards any `-javaagent:*jacocoagent*` argument; aggregated Jacoco reports remain unchanged. | JaCoCo agent, CLI build. | Plan Notes (2025-10-01). |

## UI / Interaction Mock-ups
_Not applicable – Feature 010 does not introduce UI changes._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-010-01 | Direct invocation of `OcraCliLauncher.execute("--help")` returns `0`, prints usage text, and never calls `System.exit`. |
| S-010-02 | Forked JVM harness launches `OcraCliLauncher import`, receives `CommandLine.ExitCode.USAGE`, streams usage text for assertions, and reuses the JaCoCo agent argument when present. |
| S-010-03 | Repository scan confirms zero `SecurityManager` references under `cli/src/test/**`, proving Java 17 compatibility. |
| S-010-04 | Production CLI entry point remains untouched; regression checks show runtime behaviour unchanged and diffs limited to tests/docs. |

## Test Strategy
- **Core/Application/REST/UI:** Not impacted; no new tests required.  
- **CLI:** `OcraCliLauncherTest` covers success vs failure flows using direct invocation plus a spawned JVM harness that forwards the active JaCoCo agent.  
- **Docs/Contracts:** Plan/tasks/spec artefacts document the harness; no OpenAPI or telemetry snapshots change.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-010-NA | No new runtime domain objects introduced; work is limited to CLI test harnesses. | — |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-010-NA | — | No REST or service changes. | — |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-010-01 | `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliLauncherTest"` | Executes the launcher exit harness (direct invocation + forked JVM) to verify deterministic exit codes without SecurityManager usage. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-010-NA | — | No telemetry additions; existing CLI logs remain intact. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-010-01 | `cli/src/test/java/io/openauth/sim/cli/OcraCliLauncherTest.java` | Houses the direct invocation and forked JVM harness replacing `SecurityManager`. |
| FX-010-02 | `build/reports/jacoco/test/html/` | Jacoco reports verifying coverage parity after the harness refactor. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-010-NA | — | Not applicable. |

## Telemetry & Observability
No production telemetry changes occur. Test logs rely on standard JUnit output, while CLI logging remains untouched. Operators can continue inspecting CLI verbose logs via existing commands; exit-harness assertions run exclusively in the test suite.

## Documentation Deliverables
- `docs/2-how-to/README.md` – Add a Testing section note describing the CLI exit harness command and rationale for avoiding `SecurityManager`.
- `docs/5-operations/session-quick-reference.md` – Reference the `:cli:test --tests "*OcraCliLauncherTest"` command under regression packs so future agents rerun it after CLI modifications.
- `docs/_current-session.md` – Capture harness updates and verification commands for hand-offs.

## Fixtures & Sample Data
- Preserve the forked JVM harness inside `cli/src/test/java/io/openauth/sim/cli/OcraCliLauncherTest.java` alongside JaCoCo agent forwarding logic.  
- Retain Jacoco HTML reports under `build/reports/jacoco/test/` as evidence that coverage remains intact after the migration.

## Spec DSL
```
domain_objects: []
cli_commands:
  - id: CLI-010-01
    command: ./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliLauncherTest"
    description: Runs the CLI exit-code harness (direct invocation + forked JVM)
telemetry_events: []
fixtures:
  - id: FX-010-01
    path: cli/src/test/java/io/openauth/sim/cli/OcraCliLauncherTest.java
    purpose: Replaces SecurityManager with explicit harness helpers
scenarios:
  - id: S-010-01
    description: Direct invocation covers success without terminating the process
  - id: S-010-02
    description: Forked JVM captures failure exit codes and usage text
  - id: S-010-03
    description: Repo scan confirms zero SecurityManager references under CLI tests
  - id: S-010-04
    description: Production CLI entry point remains untouched (test-only change)
```

## Appendix (Optional)
- SecurityManager deprecation timeline: JDK 17 disables the API by default, motivating the bespoke harness captured here. Future Java releases may remove the class entirely, so the repository must avoid reintroducing it.
