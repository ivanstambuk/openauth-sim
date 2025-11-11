# Feature 013 Tasks – Java 17 Language Enhancements

_Status:_ Complete  
_Last updated:_ 2025-11-10_

> Checklist mirrors the Feature 013 plan increments; tasks remain checked for audit history while migrating templates.

## Checklist
- [x] T-013-01 – Capture roadmap entry and baseline context (FR-013-04, S-013-04).  
  _Intent:_ Ensure roadmap/session docs reflect the Java 17 scope before coding.  
  _Verification commands:_  
  - `rg -n "Feature 013" docs/4-architecture/roadmap.md`  
  _Notes:_ Roadmap entry 26 updated on 2025-10-01.

- [x] T-013-02 – Document ≤90-minute increments + analysis gate prerequisites (FR-013-04, S-013-04).  
  _Intent:_ Align plan/tasks with the spec and confirm no open questions remain.  
  _Verification commands:_  
  - `rg -n "Analysis Gate" docs/4-architecture/features/013/plan.md`

- [x] T-013-03 – Execute the analysis gate checklist and capture tooling readiness (NFR-013-01, S-013-04).  
  _Intent:_ Verify constitutional guardrails before implementing language features.  
  _Verification commands:_  
  - `less docs/5-operations/analysis-gate-checklist.md`

- [x] T-013-04 – Seal `OcraCli.AbstractOcraCommand` and update CLI tests (FR-013-01, S-013-01).  
  _Intent:_ Limit command inheritance to the permitted Picocli subcommands while keeping behaviour intact.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test`

- [x] T-013-05 – Introduce sealed request variants for REST OCRA evaluation normalization (FR-013-02, S-013-02).  
  _Intent:_ Remove nullable discriminators and rely on pattern matching for evaluation flows.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluation*"`

- [x] T-013-06 – Mirror sealed variant refactor for REST OCRA verification (FR-013-02, S-013-02).  
  _Intent:_ Align verification services/telemetry with the new sealed variants.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraVerification*"`

- [x] T-013-07 – Convert OpenAPI example strings to text blocks and rerun the quality gate (FR-013-03, NFR-013-01, S-013-03, S-013-04).  
  _Intent:_ Improve documentation readability while proving automation remains green.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test`  
  - `./gradlew --no-daemon qualityGate`  
  - `./gradlew --no-daemon spotlessApply`

## Verification Log (Optional)
- 2025-10-01 – `./gradlew --no-daemon :cli:test` (PASS – sealed hierarchy enforced).
- 2025-10-01 – `./gradlew --no-daemon :rest-api:test --tests "*OcraEvaluation*"` (PASS – sealed evaluation variants).
- 2025-10-01 – `./gradlew --no-daemon :rest-api:test --tests "*OcraVerification*"` (PASS – sealed verification variants).
- 2025-10-01 – `./gradlew --no-daemon qualityGate` (PASS – no ArchUnit/PIT regressions).

## Notes / TODOs
- Future CLI command trees should adopt sealed hierarchies by default; document any new cases in their respective specs.
