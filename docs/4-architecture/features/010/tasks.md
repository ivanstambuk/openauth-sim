# Feature 010 Tasks – CLI Exit Testing Maintenance

_Status:_ Complete  
_Last updated:_ 2025-11-10_

> Checklist mirrors the Feature 010 plan increments; boxes remain checked for audit history while migrating to the refreshed template.

## Checklist
- [x] T-010-01 – Confirm CLI exit coverage and document the SecurityManager replacement plan (FR-010-01, FR-010-03, S-010-03, S-010-04).  
  _Intent:_ Capture clarifications, verify no additional dependencies are needed, and record the harness approach before touching tests.  
  _Verification commands:_  
  - `less docs/4-architecture/features/010/spec.md`  
  - `rg -n "SecurityManager" cli/src/test/java`
  _Notes:_ Confirmed no high/medium-impact open questions required logging; specification captures the clarifications inline.

- [x] T-010-02 – Refactor direct invocation tests to drop `SecurityManager` while keeping success/failure assertions (FR-010-01, FR-010-02, S-010-01, NFR-010-01).  
  _Intent:_ Ensure `OcraCliLauncher.execute("--help")` and `execute("import")` cover zero vs non-zero exit codes without intercepting `System.exit`.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`
  _Notes:_ Retained usage text assertions to prove Picocli output is still surfaced.

- [x] T-010-03 – Add the forked JVM harness that forwards the JaCoCo agent and observes `System.exit` (FR-010-02, NFR-010-02, S-010-02, S-010-03).  
  _Intent:_ Launch a nested JVM for failure cases, capture stdout, assert `CommandLine.ExitCode.USAGE`, and keep coverage metrics unchanged.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`  
  - `ls build/reports/jacoco/test/html`
  _Notes:_ Harness inspects `ManagementFactory.getRuntimeMXBean().getInputArguments()` for any `-javaagent:*jacocoagent*` argument and appends it to the spawned command.

- [x] T-010-04 – Run the gate and sync documentation/status artefacts (FR-010-03, NFR-010-01, S-010-04).  
  _Intent:_ Prove the repository stays green on Java 17 and record the harness details across roadmap/session/migration docs.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  - `rg -n "Feature 010" docs`  
  - `git status --short docs/4-architecture/features/010`
  _Notes:_ Session snapshot references the harness for hand-offs; migration tracker marks Feature 010 as template-complete.

## Verification Log (Optional)
- 2025-10-01 – `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"` (PASS: direct invocation + forked JVM).
- 2025-10-01 – `./gradlew --no-daemon spotlessApply check` (PASS on OpenJDK 17.0.16, no SecurityManager warnings).

## Notes / TODOs
- Forked JVM helper is intentionally scoped to `OcraCliLauncherTest`; reuse it verbatim if other CLI commands need exit-code verification.
