# Feature 030 – Gradle 9 Upgrade

_Status: Complete_  
_Last updated: 2025-10-19_

## Overview
Upgrade the build tooling from Gradle 8.10 to Gradle 9.1.0 so the project benefits from the latest performance improvements, reproducible archive defaults, and expanded configuration cache coverage. The change must keep the existing Java 17 toolchain intact, maintain compatibility across all modules and quality plugins, and document any workflow adjustments for future increments.

## Goals
- Run the Gradle 8.10 build in `--warning-mode=all` to surface deprecations that could break on Gradle 9, resolving or documenting them before switching the wrapper.
- Update the Gradle wrapper distribution to 9.1.0 (bin) and regenerate the accompanying `gradle-wrapper.jar` using the new toolchain.
- Validate build stability via `./gradlew spotlessApply check` plus targeted module checks after the upgrade, ensuring Spotless, SpotBugs, ErrorProne, PIT, and Spring Boot builds remain healthy.
- Refresh reproducible artifacts or snapshots that may change ordering under Gradle 9 defaults, documenting the outcome in the feature plan.
- Capture the workflow adjustments (e.g., Kotlin 2.2 DSL nuances, configuration-cache expectations) in the feature plan and tasks so future agents can repeat the process confidently.

## Non-Goals
- Upgrading Java toolchains beyond the existing Java 17 requirement.
- Modifying dependency versions, Gradle plugins, or quality tool configurations unless required for Gradle 9 compatibility.
- Introducing new build scripts or restructuring the multi-module layout.

## Clarifications
- 2025-10-19 – Owner approved upgrading the Gradle wrapper to version 9.1.0 following the documented approach.

## Architecture & Design
- **Wrapper distribution** – Update `gradle/wrapper/gradle-wrapper.properties` to point to `gradle-9.1.0-bin.zip` and regenerate `gradle-wrapper.jar` via `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`.
- **Pre-upgrade validation** – Execute `./gradlew --warning-mode=all clean check` under Gradle 8.10 to capture and address deprecation warnings before the switch.
- **Post-upgrade validation** – Re-run `--warning-mode=all` builds under Gradle 9.1.0, watching for new warnings related to Kotlin DSL, configuration cache, or plugin configuration; adjust scripts if necessary (e.g., tighten generic bounds or replace deprecated APIs).
- **Quality plugins** – Pin compatible versions (e.g., upgrade `info.solidsoft.pitest` to a Gradle 9–ready release) if the upgrade surfaces removed APIs.
- **Artifacts & snapshots** – Recreate any generated documentation, OpenAPI snapshots, or packaged archives whose deterministic order might shift with Gradle 9 reproducible archives; update repository copies if they change.
- **Documentation sync** – Update `docs/_current-session.md`, roadmap, feature plan, and tasks checklist after each increment; note any persistent limitations in the plan.

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S30-01 | Run the Gradle 8.10 build with `--warning-mode=all`, capture deprecations, and resolve/document them before upgrading. |
| S30-02 | Regenerate the Gradle wrapper at 9.1.0, update wrapper artifacts, and pin any required plugin versions (e.g., PIT) for compatibility. |
| S30-03 | Validate the upgraded build with `--warning-mode=all clean check`, targeted module suites, and configuration-cache checks to confirm parity. |
| S30-04 | Review reproducible artifacts/locks, update roadmap/knowledge docs, and record completion/follow-ups once the upgrade is stable. |

## Test Strategy
- Pre-upgrade: `./gradlew --warning-mode=all clean check` (Gradle 8.10)
- Post-upgrade smoke: `./gradlew --warning-mode=all clean check`
- Post-upgrade targeted verifications (rerun if configured to skip during `check`):
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`
  - `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest.attestationGenerationProducesDeterministicPayload"`
  - `./gradlew --no-daemon :cli:test`
- Confirm configuration cache state via `./gradlew --configuration-cache help` and document any fallbacks.

## Rollout & Regression
- Perform the upgrade in a dedicated branch/increment with full documentation updates before merging.
- If Gradle 9 uncovers incompatible plugins or scripts, revert the wrapper change and capture the blocker in the feature plan/open questions for follow-up.
- After successful validation, update the roadmap entry and tasks checklist to mark the feature complete, and note any future optimisations uncovered during the upgrade.
