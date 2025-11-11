# Feature Plan 041 – Operator Console JavaScript Modularization & Test Harness

| Field | Value |
|-------|-------|
| Status | In planning |
| Last updated | 2025-11-10 |
| Linked specification | `docs/4-architecture/features/041/spec.md` |
| Linked tasks | `docs/4-architecture/features/041/tasks.md` |

## Vision & Success Criteria
- Consolidate all operator-console scripts into standalone modules with clearly documented contracts.
- Provide a reusable Node-based DOM harness so protocol scripts gain unit tests without Selenium.
- Add HOTP, TOTP, OCRA, FIDO2, and EMV suites to the harness and aggregate them via a Gradle task wired into `check`.
- Keep the specification in Draft until governance confirms every protocol participates in the harness.

## Dependencies & Interfaces
- Assets under `rest-api/src/main/resources/static/ui/**`.
- Node 20+ availability in CI (already required for EMV tests).
- Gradle build changes in `rest-api/build.gradle.kts` plus contributor docs (README/CONTRIBUTING).
- Shared helpers: SecretFieldBridge, VerboseTraceConsole, protocol tab switcher.

## Implementation Drift Gate
- Trigger when T4101–T4106 are ✅ and `./gradlew operatorConsoleJsTest check` succeeds.
- Evidence to capture in this plan:
  - Table mapping FR41-01…FR41-05 to code/test references (module files, harness utilities, Gradle task).
  - Console harness README excerpt plus sample Node test output per protocol.
  - Screenshot/log of Gradle task wired into `check`.
  - Governance checklist entry describing future-protocol onboarding.

## Increment Map
0. **I0 – Asset inventory & extraction notes (T4101)**  
   - Catalogue inline scripts, document extraction seams, update roadmap/current-session.  
   - Commands: documentation only.
1. **I1 – Shared harness scaffolding (T4102)**  
   - Build DOM harness utilities, port EMV tests, create README.  
   - Commands: `node --test rest-api/src/test/javascript/emv/console.test.js`.
2. **I2 – Protocol adoption wave (HOTP/TOTP/FIDO2) (T4103)**  
   - Extract helpers, add Node suites, ensure fixtures cover evaluate/replay + verbose traces.  
   - Commands: `node --test rest-api/src/test/javascript/{hotp,totp,fido2}/*.test.js`.
3. **I3 – OCRA extraction & coverage (T4104)**  
   - Move OCRA scripts out of Thymeleaf, expose controller factories, add Node suites.  
   - Commands: `node --test rest-api/src/test/javascript/ocra/*.test.js`.
4. **I4 – Gradle integration & documentation (T4105)**  
   - Replace `emvConsoleJsTest` with `operatorConsoleJsTest`, wire under `check`, add filtering property, update docs.  
   - Commands: `./gradlew --no-daemon operatorConsoleJsTest check`.
5. **I5 – Governance & future-proofing (T4106)**  
   - Extend tasks checklist for new protocols, document drift gate expectations, update knowledge map.  
   - Commands: documentation + `./gradlew --no-daemon spotlessApply check`.

## Scenario Tracking
| Scenario ID | Increment reference | Notes |
|-------------|--------------------|-------|
| S41-01 | I1 | EMV suite runs on shared harness. |
| S41-02 | I1 | Harness utilities validated. |
| S41-03 | I2 | HOTP/TOTP coverage added. |
| S41-04 | I2 | FIDO2 coverage added. |
| S41-05 | I3 | OCRA controllers extracted/tests passing. |
| S41-06 | I1–I3 | Shared widgets covered. |
| S41-07 | I4 | Gradle aggregator integrated with `check`. |
| S41-08 | I5 | Governance checklist + onboarding docs ready. |

## Quality & Tooling Gates
- Node suites executed via `./gradlew --no-daemon operatorConsoleJsTest`; support `-PconsoleTestFilter` for local focus.
- JavaScript linting/formatting to follow repository defaults (eslint/prettier) when introduced; note commands in tasks.
- Each increment finishes with `./gradlew --no-daemon spotlessApply check` if any JVM files change.

## Analysis Gate
- To run once I5 completes; confirm spec/plan/tasks alignment, no open questions, harness README present, and Gradle task wired into CI.

## Exit Criteria
- All protocols plus shared widgets run under Node harness with deterministic fixtures.
- `operatorConsoleJsTest` included in `./gradlew check`, supporting filtering.
- Documentation (spec, plan, tasks, knowledge map, how-to guides) outlines onboarding workflow.
- Drift gate artefacts archived; roadmap/current-session updated.

## Follow-ups / Backlog
- Consider adding mutation or coverage thresholds for JS harness.
- Evaluate replacing Selenium smoke tests once Node coverage is comprehensive.
- Track future protocol additions (e.g., EUDIW) that must adopt the harness immediately.
