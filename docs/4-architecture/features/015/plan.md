# Feature Plan 015 – SpotBugs Dead-State Enforcement

_Linked specification:_ `docs/4-architecture/features/015/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Shared SpotBugs include filter enables dead-state detectors (FR-015-01, S-015-01).
- Remediation completes so SpotBugs tasks and `spotlessApply check` pass with detectors active (FR-015-02, S-015-02).
- Documentation/runbooks capture detector configuration, commands, and suppression policy (FR-015-03, S-015-03).
- PMD `UnusedPrivateField` / `UnusedPrivateMethod` rules run in the quality gate after code cleanup (FR-015-04, S-015-04).

## Scope Alignment
- **In scope:** SpotBugs include filter, Gradle wiring, violation cleanup, documentation updates, PMD rule enforcement.
- **Out of scope:** Additional detector families, replacement of SpotBugs, major runtime refactors.

## Dependencies & Interfaces
- Gradle SpotBugs plugin configuration for each JVM module.
- PMD configuration shared across modules.
- Documentation artefacts (`docs/5-operations/analysis-gate-checklist.md`, developer tooling guides).

## Assumptions & Risks
- **Assumptions:** Developers run SpotBugs tasks locally; CI uses the same include filter; detector runtime stays within the NFR target.
- **Risks / Mitigations:**
  - _Runtime increase:_ Monitor `./gradlew check` timings; if >15% delta, investigate caching/parallelism.
  - _Suppressions:_ Enforce justification logging in this plan to prevent silent overrides.
  - _Rule drift:_ Keep filter/ruleset centralized to avoid divergent module behaviour.

## Implementation Drift Gate
- **Trigger:** After T-015-01–T-015-07 completed (2025-10-03).
- **Evidence:** Filter file, Gradle configuration, SpotBugs/PMD outputs, documentation diffs, plan Notes capturing suppressions.
- **Outcome:** Gate confirmed FR/NFR coverage; quality gate remains green with detectors active.

## Increment Map
1. **I1 – Governance & failing detector run** _(T-015-01–T-015-02)_  
   - _Goal:_ Register the feature, add the shared SpotBugs include filter, wire Gradle tasks, and observe the expected red run.  
   - _Commands:_ `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks`.  
   - _Exit:_ Filter referenced by all tasks; failing output captured in plan notes.
2. **I2 – Remediation & verification** _(T-015-03)_  
   - _Goal:_ Remove or justify violations, rerun SpotBugs, and execute `spotlessApply check`.  
   - _Commands:_ `./gradlew --no-daemon :application:spotbugsMain --rerun-tasks`, `./gradlew --no-daemon spotlessApply check`.  
   - _Exit:_ SpotBugs + check green with detectors enabled.
3. **I3 – Documentation updates** _(T-015-04–T-015-05)_  
   - _Goal:_ Update runbooks/tooling guides and log detector impact in the plan.  
   - _Commands:_ `rg -n "dead-state" docs/5-operations/analysis-gate-checklist.md`.  
   - _Exit:_ Docs describe detectors and suppression policy; feature artefacts synced.
4. **I4 – PMD rule enforcement** _(T-015-06–T-015-07)_  
   - _Goal:_ Enable PMD unused-field/method rules, remediate violations, rerun PMD + check.  
   - _Commands:_ `./gradlew --no-daemon :rest-api:pmdTest`, `./gradlew --no-daemon check`.  
   - _Exit:_ PMD rules active with clean results; quality gate remains green.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-015-01 | I1 / T-015-01–T-015-02 | Shared filter + wiring. |
| S-015-02 | I2 / T-015-03 | Remediation ensuring SpotBugs passes. |
| S-015-03 | I3 / T-015-04–T-015-05 | Documentation updates. |
| S-015-04 | I4 / T-015-06–T-015-07 | PMD unused-field/method rules enforced. |

## Analysis Gate (2025-10-03)
- ✅ Spec captured objectives/requirements/clarifications.
- ✅ Open questions clear for this feature.
- ✅ Plan/tasks mapped to FR/NFR IDs with ≤30-minute increments.
- ✅ Tooling readiness recorded (SpotBugs, PMD, spotless/check commands).

## Exit Criteria
- SpotBugs filter wired across projects with detectors enabled.
- Violations cleaned; SpotBugs + `spotlessApply check` pass.
- Documentation/runbooks reference the enforcement and suppression policy.
- PMD unused-field/method rules enforced with clean runs.

## Follow-ups / Backlog
- Evaluate enabling additional SpotBugs patterns (`SS_SHOULD_BE_STATIC`, `SI_INSTANCE_BEFORE_FINALS_ASSIGNED`) in a future feature.
- Monitor build runtime impact; consider optimisations if quality gate approaches NFR limits.
