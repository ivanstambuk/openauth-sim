# Feature Plan 011 – Reflection Policy Hardening

_Linked specification:_ `docs/4-architecture/features/011/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Repository contains zero project-owned reflection usages; all collaborators/tests rely on explicit seams.
- ArchUnit + Gradle `reflectionScan` guards execute inside `./gradlew qualityGate` and fail deterministically when reflection is added.
- CLI, REST, and core modules expose package-private helpers/records so tests avoid reflection without bloating public APIs.
- `AGENTS.md`, knowledge map, and runbooks capture the anti-reflection policy and guard commands for future contributors.
- `./gradlew spotlessApply check` and `qualityGate` remain green once the refactor completes.

## Scope Alignment
- **In scope:** Reflection inventory, guard creation, CLI/core/REST seam refactors, documentation updates, knowledge map sync, migration tracker updates.
- **Out of scope:** Dependency upgrades, UI work, framework-level reflection already vetted (e.g., Spring proxies), policy extensions beyond reflection.

## Dependencies & Interfaces
- `core-architecture-tests` ArchUnit suite and Gradle build (for `reflectionScan`).
- CLI/REST/core modules whose tests previously used reflection.
- Documentation repositories (`AGENTS.md`, knowledge map, roadmap, session snapshot).
- Governance artefacts (analysis gate checklist, migration tracker).

## Assumptions & Risks
- **Assumptions:**
  - CI environments allow spawning the reflection scan and ArchUnit guards without additional permissions.
  - Introducing package-private seams is acceptable when justified in the spec/plan.
  - Developers will rerun `reflectionScan` locally before committing.
- **Risks / Mitigations:**
  - _False positives:_ Implement allowlists for legitimate framework usage; document them in the spec/plan.
  - _API churn:_ Keep new seams package-private/internal and document them in the knowledge map.
  - _Guard latency:_ Ensure `reflectionScan` executes in <10 s and reuse configuration cache to avoid bloating the quality gate.

## Implementation Drift Gate
- **Trigger:** After T-011-01–T-011-11 completed (2025-10-01).
- **Evidence:** `rg` scan logs, ArchUnit test output, `reflectionScan` reports, CLI/REST/core test transcripts, knowledge map diff.
- **Outcome:** Gate confirmed every REF/FR requirement mapped to implementation/tests; `./gradlew spotlessApply check` and `qualityGate` both green with guards enabled.

## Increment Map
1. **I1 – Inventory & failing guards** _(T-011-01–T-011-03)_  
   - _Goal:_ Catalogue all reflection usage and add red guards (ArchUnit + regex scan).  
   - _Preconditions:_ Spec/clarifications approved; roadmap references Feature 011.  
   - _Steps:_ Run repository-wide `rg` scans, log offenders, land failing ArchUnit/regex tests.  
   - _Commands:_ `rg --hidden --glob "*.java" "java.lang.reflect"`, `./gradlew :core-architecture-tests:test --tests "*Reflection*"`, `./gradlew reflectionScan`.  
   - _Exit:_ Inventory captured in plan notes; guards fail as expected.
2. **I2 – Guard implementation & wiring** _(T-011-04–T-011-05)_  
   - _Goal:_ Replace reflection usages to drive the guards green and wire `reflectionScan` into `qualityGate`.  
   - _Steps:_ Refactor offending areas iteratively, keep ArchUnit/regex tests green, add quality gate dependency.  
   - _Commands:_ `./gradlew :core-architecture-tests:test --tests "*Reflection*"`, `./gradlew reflectionScan qualityGate`.  
   - _Exit:_ Guards pass when repository is clean and fail otherwise.
3. **I3 – CLI seam refactors** _(T-011-06–T-011-07)_  
   - _Goal:_ Expose CLI collaborators/DTOs so tests avoid reflection while preserving behaviour.  
   - _Steps:_ Introduce package-private helpers, update Picocli tests, verify exit codes.  
   - _Commands:_ `./gradlew :cli:test --tests "*OcraCli*" --tests "*MaintenanceCli*"`.  
   - _Exit:_ CLI suite green without reflection.
4. **I4 – REST & core seam refactors** _(T-011-08–T-011-09)_  
   - _Goal:_ Update REST services and core credential fixtures to avoid reflection.  
   - _Steps:_ Adjust service constructors, inject collaborators, refresh tests and telemetry assertions.  
   - _Commands:_ `./gradlew :rest-api:test --tests "*Ocra*"`, `./gradlew :core:test`.  
   - _Exit:_ REST/core suites green, guards still pass.
5. **I5 – Governance & closure** _(T-011-10–T-011-11)_  
   - _Goal:_ Update `AGENTS.md`, roadmap/knowledge map, migration tracker, and capture final verification runs.  
   - _Steps:_ Document policy, log commands in session snapshot, run full gate.  
   - _Commands:_ `rg -n "reflection" AGENTS.md`, `./gradlew spotlessApply check`.  
   - _Exit:_ Docs synced; feature marked complete with recorded verification log.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-011-01 | I1–I4 / T-011-01–T-011-09 | Reflection inventory + removal across modules. |
| S-011-02 | I1–I2 / T-011-02–T-011-05 | ArchUnit guard creation + enforcement. |
| S-011-03 | I1–I2 / T-011-03–T-011-05 | `reflectionScan` wiring and enforcement. |
| S-011-04 | I5 / T-011-10 | Documentation/AGENTS updates. |
| S-011-05 | I3–I4 / T-011-06–T-011-09 | Behaviour parity across CLI/REST/core seams. |

## Analysis Gate (2025-10-01)
- ✅ Specification captured clarifications, requirements, and scope.
- ✅ Open questions cleared; none pending in `docs/4-architecture/open-questions.md`.
- ✅ Plan/tasks mapped to FR/NFR IDs with ≤30 minute increments and tests-first ordering.
- ✅ Tooling readiness documented (rg scans, ArchUnit, reflectionScan, spotless/check).
- ✅ Governance artefacts (roadmap, knowledge map) queued for updates during increments.

## Exit Criteria
- `rg` and `reflectionScan` report zero forbidden reflection usages.
- `./gradlew :core-architecture-tests:test --tests "*Reflection*"` and `./gradlew reflectionScan qualityGate` pass on Java 17.
- CLI/REST/core suites remain green without reflection.
- `AGENTS.md`, knowledge map, roadmap, migration tracker, and session snapshot document the policy and status.
- `./gradlew spotlessApply check` passes after documentation updates.

## Follow-ups / Backlog
- Evaluate whether additional guards are needed for dynamic proxies or other JVM instrumentation patterns.
- Consider promoting shared seam helpers into dedicated testing utilities if other protocols need them.
- Monitor guard latency; if reflectionScan grows past 10 s, revisit implementation or caching strategy.
