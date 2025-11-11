# Feature Plan 010 – CLI Exit Testing Maintenance

_Linked specification:_ `docs/4-architecture/features/010/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Keep CLI exit-code verification green on Java 17+ without relying on deprecated `SecurityManager` hooks.
- Provide deterministic success (`--help`) and failure (`import`) coverage that mirrors production Picocli exit codes.
- Preserve JaCoCo coverage accounting by forwarding any active `-javaagent` argument to the forked JVM harness.
- Document the harness commands (`./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`) so maintainers rerun them after CLI changes.
- Keep runtime CLI sources untouched; all diffs live in test/doc directories.

## Scope Alignment
- **In scope:** CLI launcher tests, harness utilities that spawn JVMs, documentation reflecting the new process, roadmap/session log updates.
- **Out of scope:** Runtime CLI command changes, REST/UI/API modules, dependency additions, telemetry schema updates.

## Dependencies & Interfaces
- `cli` module tests (`OcraCliLauncherTest`).
- Picocli exit codes and usage messaging for `OcraCliLauncher`.
- Java 17 toolchain with optional JaCoCo `-javaagent` argument.
- Gradle tasks `:cli:test` and `spotlessApply check` for verification.

## Assumptions & Risks
- **Assumptions:**
  - JaCoCo agent argument appears inside `ManagementFactory.getRuntimeMXBean().getInputArguments()` when coverage is enabled.
  - Launching a nested JVM from tests is permitted in CI environments.
  - CLI launcher contract (`--help`, `import`) remains stable while this maintenance task executes.
- **Risks / Mitigations:**
  - _Process leaks:_ Always close streams and forcibly destroy the spawned JVM if it outlives the test.
  - _Coverage gaps:_ Attach the detected `-javaagent` argument to the spawned JVM and inspect Jacoco HTML output before/after.
  - _Flaky exit codes:_ Use deterministic arguments (`--help`, `import`) and assert on Picocli constants to avoid brittle expectations.

## Implementation Drift Gate
- **Trigger:** 2025-10-01 after T-010-01–T-010-04 were completed.
- **Evidence:** `git diff --stat cli/src/test/java/io/openauth/sim/cli`, Jacoco HTML snapshot, `rg SecurityManager` output, and session log entries referencing the harness.
- **Outcome:** Gate passed with `./gradlew --no-daemon spotlessApply check` on Java 17 (SecurityManager-free) and `:cli:test` covering both exit paths.

## Increment Map
1. **I1 – Scope confirmation & harness design** _(T-010-01)_  
   - _Goal:_ Capture clarifications, document why `SecurityManager` must be removed, and outline the direct-invocation vs spawn strategy.  
   - _Preconditions:_ Feature specification in Draft status, roadmap pointer to Feature 010.  
   - _Steps:_
     - Review existing CLI launcher tests and note SecurityManager usage.
     - Record clarifications + analysis gate checklist in docs/4-architecture/open-questions.md (none pending).
   - _Commands:_ `less docs/4-architecture/features/010/spec.md`, `rg -n "SecurityManager" cli/src/test/java`.  
   - _Exit:_ Open questions cleared, harness approach documented inside the spec/plan/tasks.

2. **I2 – Direct invocation harness updates** _(T-010-02)_  
   - _Goal:_ Replace in-process assertions with explicit exit-code checks using `OcraCliLauncher.execute` and `CommandLine.ExitCode`.  
   - _Preconditions:_ I1 complete, tests still referencing SecurityManager.  
   - _Steps:_
     - Update tests covering `--help` success path and `import` failure via `execute` helper.
     - Ensure usage text assertions remain intact without intercepting System exit.
   - _Commands:_ `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`.  
   - _Exit:_ Tests pass in-process without SecurityManager usage.

3. **I3 – Forked JVM harness & coverage parity** _(T-010-03)_  
   - _Goal:_ Add spawned JVM test that observes `System.exit` for failure cases and forwards the JaCoCo agent when present.  
   - _Preconditions:_ I2 complete, JaCoCo agent path detectable.  
   - _Steps:_
     - Build command list (java binary, optional agent, classpath, launcher class, `import`).
     - Capture stdout/stderr, assert exit code equals Picocli usage, and clean up resources.
     - Compare Jacoco reports before/after to confirm coverage unaffected.
   - _Commands:_ `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`, `ls build/reports/jacoco/test/html`.  
   - _Exit:_ Process-based harness passes locally and in CI with intact coverage reports.

4. **I4 – Quality gate & documentation sync** _(T-010-04)_  
   - _Goal:_ Run the standard formatting/tests gate and update roadmap/session/migration docs.  
   - _Preconditions:_ I2–I3 succeeded.  
   - _Steps:_
     - Execute `./gradlew --no-daemon spotlessApply check`.
     - Update plan/spec/tasks statuses plus docs/roadmap/session to describe the harness.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`, `rg -n "Feature 010" docs`.  
   - _Exit:_ Documentation synced, gate green, feature marked complete.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-010-01 | I2 / T-010-02 | Direct invocation success path without `System.exit`. |
| S-010-02 | I3 / T-010-03 | Forked JVM harness observes `CommandLine.ExitCode.USAGE`. |
| S-010-03 | I1–I4 / T-010-01–T-010-04 | Repository scan shows zero `SecurityManager` references. |
| S-010-04 | I4 / T-010-04 | Runtime CLI untouched; documentation updated. |

## Analysis Gate (2025-10-01)
- ✅ Clarifications logged in the specification; no open questions remained.
- ✅ SecurityManager removal approach recorded in spec/plan/tasks before edits.
- ✅ Verification commands (`rg SecurityManager`, `:cli:test`, `spotlessApply check`) enumerated.
- ✅ Scope limited to CLI tests; dependencies/risks acknowledged.

## Exit Criteria
- `rg SecurityManager cli/src/test/java` returns zero results post-change.
- `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"` passes, covering direct invocation + forked JVM flows.
- `./gradlew --no-daemon spotlessApply check` succeeds on Java 17.
- Documentation (spec/plan/tasks, roadmap/session snapshot) references the harness and its commands.

## Follow-ups / Backlog
- Consider sharing the forked JVM helper across other CLI command tests if additional exit-code coverage is required.
- Evaluate whether similar harnesses are needed for REST smoke tests before renumbering begins.
