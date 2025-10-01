# Quality Gate – Usage & Troubleshooting

_Last updated: 2025-09-30_

The `qualityGate` Gradle task aggregates the architectural, coverage, and mutation checks that protect the OCRA stack. Run it locally before shipping changes touching `core`, `cli`, or `rest-api`, and rely on the CI workflow (`.github/workflows/ci.yml`) for enforcement on every push and pull request.

## Commands
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

## CI Integration
GitHub Actions runs `./gradlew --no-daemon qualityGate` on every push and pull request (`ci.yml`). Mutations must **not** be skipped in CI; ensure the repository builds cleanly without `-Ppit.skip=true` before pushing.

Keep this document in sync whenever thresholds, task wiring, or report locations change.
