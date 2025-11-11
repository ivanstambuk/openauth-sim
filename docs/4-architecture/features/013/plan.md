# Feature Plan 013 – Java 17 Language Enhancements

_Linked specification:_ `docs/4-architecture/features/013/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Seal the `OcraCli` command hierarchy so only the permitted Picocli subcommands exist (FR-013-01, S-013-01).
- Introduce sealed request variants plus pattern matching inside REST OCRA services while keeping external DTOs unchanged (FR-013-02, S-013-02).
- Replace controller example strings with Java text blocks without altering generated OpenAPI payloads (FR-013-03, S-013-03).
- Keep the full quality gate (`spotlessApply`, `:cli:test`, `:rest-api:test`, `qualityGate`) green after refactors (NFR-013-01, S-013-04).

## Scope Alignment
- **In scope:** OCRA CLI sealed hierarchy, REST OCRA evaluation/verification normalization internals, OpenAPI text block conversion, documentation updates, quality gate verification.
- **Out of scope:** Core cryptography refactors, other CLI command trees, non-OCRA REST endpoints, Java version bumps beyond 17.

## Dependencies & Interfaces
- Picocli command wiring (`OcraCli` subcommands) must keep default constructors accessible.
- REST Spring components (`OcraEvaluationService`, `OcraVerificationService`) rely on Jackson serialization; sealed variants must remain internal.
- OpenAPI snapshot tests enforce example payload stability.
- Gradle toolchain pinned to Java 17 (already configured in feature 011).

## Assumptions & Risks
- **Assumptions:** Picocli handles sealed nested classes; sealed variants remain invisible to API consumers; text blocks compile under Java 17.
- **Risks / Mitigations:**
  - _Framework compatibility:_ Run targeted Picocli tests before sealing to catch constructor issues.
  - _Serialization regressions:_ Keep external DTOs unchanged and confine sealed variants inside services.
  - _Text block formatting:_ Use tests or snapshots to detect whitespace differences.

## Implementation Drift Gate
- **Trigger:** After T-013-01–T-013-07 completed (2025-10-01).
- **Evidence:** Sealed CLI classes, sealed REST variants + tests, OpenAPI text block diffs, `qualityGate` output, roadmap/session updates.
- **Outcome:** Gate confirmed all FR/NFR requirements matched implementation; quality gate remained green.

## Increment Map
1. **I1 – Planning & analysis gate** _(T-013-01–T-013-03)_  
   - _Goal:_ Register roadmap entry, align tasks, and execute the analysis gate checklist.  
   - _Commands:_ `rg -n "Feature 013" docs/4-architecture/roadmap.md`.  
   - _Exit:_ Documentation synced; open questions cleared.
2. **I2 – Seal CLI hierarchy** _(T-013-04)_  
   - _Goal:_ Convert `AbstractOcraCommand` to a sealed class with explicit permits; update tests.  
   - _Commands:_ `./gradlew --no-daemon :cli:test`.  
   - _Exit:_ FR-013-01 satisfied; CLI behaviour unchanged.
3. **I3 – REST evaluation sealed variants** _(T-013-05)_  
   - _Goal:_ Introduce sealed request variants/pattern matching for evaluation normalization.  
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluation*"`.  
   - _Exit:_ FR-013-02 (evaluation) satisfied.
4. **I4 – REST verification sealed variants** _(T-013-06)_  
   - _Goal:_ Mirror the variant approach for verification flows plus telemetry assertions.  
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraVerification*"`.  
   - _Exit:_ FR-013-02 (verification) satisfied.
5. **I5 – Text block conversion & quality gate** _(T-013-07)_  
   - _Goal:_ Replace controller examples with text blocks, rerun snapshots, and execute the full quality gate.  
   - _Commands:_ `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon qualityGate`, `./gradlew --no-daemon spotlessApply`.  
   - _Exit:_ FR-013-03 and NFR-013-01 satisfied; documentation updated.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-013-01 | I2 / T-013-04 | CLI sealed hierarchy. |
| S-013-02 | I3–I4 / T-013-05–T-013-06 | REST sealed variants. |
| S-013-03 | I5 / T-013-07 | Text block conversion + snapshots. |
| S-013-04 | I1–I5 / T-013-01–T-013-07 | Quality gate remains green. |

## Analysis Gate (2025-10-01)
- ✅ Specification, plan, and tasks linked with clarified scope.
- ✅ No open questions pending; roadmap updated.
- ✅ Tooling readiness captured (targeted Gradle commands + quality gate).
- ✅ Tasks sequenced with tests-first increments.

## Exit Criteria
- Sealed CLI hierarchy compiled and tested.
- REST services use sealed request variants with updated tests.
- OpenAPI examples use Java text blocks with unchanged outputs.
- Documentation (spec/plan/tasks, roadmap, session log) reflects the language enhancements.
- `./gradlew spotlessApply check` and `./gradlew qualityGate` pass.

## Follow-ups / Backlog
- Monitor future CLI command trees for sealing opportunities; document in specs when adopted.
- Re-run coverage benchmarks if REST services undergo major changes.
- Ensure new REST endpoints adopt text blocks for inline examples by default.
