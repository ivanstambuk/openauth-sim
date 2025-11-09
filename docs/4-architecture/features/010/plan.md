# Feature Plan 010 – CLI Exit Testing Maintenance

_Status: Complete_
_Last updated: 2025-10-01_

## Objective
Modernise the CLI launcher tests by eliminating `SecurityManager` while retaining coverage of exit code behaviour, keeping changes scoped to the test suite as defined in Feature 010 specification.

Reference specification: `docs/4-architecture/features/010/spec.md`.

## Success Criteria
- `SecurityManager` no longer appears in CLI tests.
- Updated tests reproduce the original assertions: `main` returns normally for success, and a separate verification confirms exit code propagation for error scenarios.
- `./gradlew spotlessApply check` passes on JDK 17.

## Proposed Increments
- T101 – Capture current SecurityManager usages and design replacement strategy (process-based exit verification). ✅ (2025-10-01)
- T102 – Update CLI launcher tests to remove `SecurityManager` and adopt the new verification approach. ✅ (2025-10-01)
- T103 – Run `./gradlew spotlessApply check`, ensure CLI coverage unaffected, and document outcomes. ✅ (2025-10-01)
- T104 – Sync open questions/plan status, move spec/plan/tasks to complete. ✅ (2025-10-01)

## Checklist Before Implementation
- [x] Specification clarifications resolved.
- [x] Open questions updated with decision (Option A selected).
- [x] No new dependencies required or requested.

## Notes
- 2025-10-01 – Failure-case test now launches a forked JVM with the active JaCoCo agent attached (parsed from runtime arguments) so aggregate coverage remains above the 0.90 branch threshold.
- Process-based verification will launch a dedicated JVM for failure-case assertions using the module classpath derived from `java.class.path`.
- Success-case verification will rely on direct invocation of `OcraCliLauncher.main` and the survival of the test process.

Update this plan as increments complete.
