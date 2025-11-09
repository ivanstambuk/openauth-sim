# Feature 008 Tasks – OCRA Quality Automation

_Status:_ Complete  
_Last updated:_ 2025-11-09_

## Checklist
- [x] T0801 – Capture clarifications in spec/plan, update roadmap/knowledge map, and note baseline coverage metrics (S08-01–S08-06).
  _Intent:_ Establish current thresholds and dependencies before adding new guards.
  _Verification commands:_
  - `less docs/4-architecture/features/008/spec.md`
  - `rg -n "Feature 008" docs/4-architecture/roadmap.md`

- [x] T0802 – Add failing ArchUnit suites plus Jacoco verification tasks to enforce module boundaries and ≥90% thresholds (QA-OCRA-001, QA-OCRA-003, S08-01, S08-02).
  _Intent:_ Guard architecture boundaries and codify coverage requirements prior to implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*Architecture*"`
  - `./gradlew --no-daemon jacocoAggregatedReport`

- [x] T0803 – Configure PIT mutation testing with ≥85% threshold and integrate reports (QA-OCRA-002, S08-03).
  _Intent:_ Ensure mutation analysis participates in the gate with actionable reports.
  _Verification commands:_
  - `./gradlew --no-daemon pitest`
  - `ls -1 build/reports/pitest`

- [x] T0804 – Create the `qualityGate` Gradle task bundling ArchUnit, PIT, Jacoco, Spotless, Checkstyle, and SpotBugs (QA-OCRA-004, S08-04).
  _Intent:_ Provide a single local entry point for all quality automation.
  _Verification commands:_
  - `./gradlew --no-daemon qualityGate -Ppit.skip=true`

- [x] T0805 – Update GitHub Actions workflows to run `./gradlew qualityGate` on push/PR with the same thresholds (QA-OCRA-005, S08-05).
  _Intent:_ Keep CI enforcement aligned with local expectations.
  _Verification commands:_
  - `rg -n "qualityGate" .github/workflows`

- [x] T0806 – Document gate usage, skip flags, and troubleshooting in docs/how-to plus roadmap notes (QA-OCRA-006, S08-06).
  _Intent:_ Teach contributors how to interpret failures and locate reports.
  _Verification commands:_
  - `rg -n "quality gate" docs/5-operations`
  - `markdownlint docs/5-operations/quality-gate.md`

- [x] T0807 – Run the full gate (no skips), capture metrics in the plan, and log closure status (S08-01–S08-06).
  _Intent:_ Demonstrate the automation passes with the new guards enabled.
  _Verification commands:_
  - `./gradlew --no-daemon qualityGate`
  - `ls build/reports/quality`

## Notes / TODOs
- Earlier Q10x/Q13x micro-tasks (coverage uplift series) remain preserved in history prior to 2025-11-09.
