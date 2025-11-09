# Feature 010 Tasks – CLI Exit Testing Maintenance

_Status: Complete_  
_Last updated: 2025-10-01_

## Checklist
- [x] T101 – Confirm existing CLI exit coverage and design the replacement harness for success/failure flows (S10-01, S10-02).
  _Intent:_ Capture baseline behaviour and outline the direct invocation vs spawned-JVM strategy before touching tests.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`

- [x] T102 – Refactor `OcraCliLauncherTest` to drop `SecurityManager`, using direct invocation for success and forked JVM for failure (S10-01, S10-02, S10-03, S10-04).
  _Intent:_ Replace deprecated interception with explicit harnesses while keeping production code untouched.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"`
  - `rg --files -g* cli/src/test | xargs rg SecurityManager`

- [x] T103 – Run `./gradlew spotlessApply check` to ensure the Java 17 toolchain passes without deprecated API usage (S10-01, S10-02, S10-03).
  _Intent:_ Prove the updated tests succeed under the standard gate and that no new warnings occur.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`

- [x] T104 – Update plan/spec/tasks statuses and close out the documentation trail for the refactor (S10-04).
  _Intent:_ Synchronise artefacts (spec, plan, roadmap) so future agents know the CLI entry point stayed untouched.
  _Verification commands:_
  - `rg --files docs/4-architecture -g*010*`

## Notes / TODOs
- No outstanding follow-ups; future CLI exit instrumentation should reuse the spawned-JVM harness captured here.
