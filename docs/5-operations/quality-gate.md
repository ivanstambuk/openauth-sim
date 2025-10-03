# Quality Gate – Usage & Troubleshooting

_Last updated: 2025-10-03_

The `qualityGate` Gradle task aggregates the architectural, coverage, and mutation checks that protect the OCRA stack. Run it locally before shipping changes touching `core`, `cli`, or `rest-api`, and rely on the CI workflow (`.github/workflows/ci.yml`) for enforcement on every push and pull request.

## Commands
- **Format before commit:** `./gradlew spotlessApply`
  - Runs Google Java Format via Spotless across all Java sources so contributors (human or AI) stage diffs that already satisfy the gate.
  - Execute this before every commit to minimise formatter churn and keep `spotlessCheck` green during `qualityGate`.
- **Full run:** `./gradlew qualityGate`
  - Executes `spotlessCheck`, `check`, `architectureTest`, `jacocoAggregatedReport`, `jacocoCoverageVerification`, and `mutationTest`.
  - Baseline runtime (warm cache, Apple M1 Pro 16 GB): ~1m56s.
- **Skip mutation locally:** `./gradlew qualityGate -Ppit.skip=true`
  - Useful for rapid iteration when PIT would dominate runtime (~27s). Always rerun the full gate before committing or raising a PR.

Gradle will fail fast if any underlying task fails; check the task section of the output to see which guard triggered.

## Thresholds & Reports
- **Jacoco coverage:** aggregated line ≥77%, branch ≥62% for OCRA packages. Reports live at `build/reports/jacoco/aggregated/` (HTML + XML).
- **PIT mutation testing:** core OCRA classes must reach ≥85% mutation score. Reports live under `core/build/reports/pitest/` (HTML + XML). CLI/REST facades will be added once dedicated tests land.
- **Architecture checks:** `:core-architecture-tests:test` guards cross-module dependencies; failures point to the offending class/package.

## Troubleshooting
- **Coverage dips below threshold:** inspect `build/reports/jacoco/aggregated/html/index.html` to locate untested lines. Add or adjust tests, then rerun `qualityGate`.
- **Mutation score below 85%:** open `core/build/reports/pitest/index.html` and review surviving mutants. Strengthen tests or refine helper utilities. Avoid relying on `-Ppit.skip=true` to bypass legitimate failures.
- **Runtime considerations:** use Gradle’s configuration cache (already enabled) and keep daemons warm. For CI, caching is handled by `actions/setup-java` with Gradle caching enabled.
- **Flaky PIT timeouts:** rerun with `./gradlew --info mutationTest` to capture detailed minion logs. Consider raising timeouts via `PIT_TIMEOUT_FACTOR` (settable with `-Dpit.timeoutFactor=…`) if you encounter legitimate long-running scenarios.
- **Seeding legacy maintenance fixtures:** CLI tests use the system property `openauth.sim.persistence.skip-upgrade=true` to suppress automatic MapDB migrations when exercising historical records. Leave this unset (the default) for normal runs so production code continues upgrading persisted data on start.

## SpotBugs Dead-State Detectors (Feature 015)
- Every `spotbugsMain` task now applies `config/spotbugs/dead-state-include.xml`, which promotes the `URF_UNREAD_FIELD`, `URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD`, `UUF_UNUSED_FIELD`, `UWF_UNWRITTEN_FIELD`, and `NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD` patterns to hard failures.
- Use `./gradlew :<module>:spotbugsMain --rerun-tasks` to reproduce findings locally; the task fails with the offending class and line number. Clean up unused/unwritten fields or add `@SuppressFBWarnings` with a justification captured in the feature plan.
- The include filter runs alongside existing SpotBugs checks, so previously enforced detectors continue to apply.
- PMD now enforces `UnusedPrivateField` (best practices category) via `config/pmd/ruleset.xml`. Run `./gradlew :<module>:pmdMain` or `:pmdTest` to surface unused private members—remove the dead field or justify it before re-running `qualityGate`.

## CI Integration
GitHub Actions runs `./gradlew --no-daemon qualityGate` on every push and pull request (`ci.yml`). Mutations must **not** be skipped in CI; ensure the repository builds cleanly without `-Ppit.skip=true` before pushing.

Keep this document in sync whenever thresholds, task wiring, or report locations change.
