# Feature 032 – Palantir Formatter Adoption

_Status: Complete_  
_Last updated: 2025-10-19_

## Overview
Replace the repository-wide Google Java Format policy with Palantir Java Format so Spotless, the managed pre-commit hook, and IDE contributors share a single formatter that supports 120-character lines and the Palantir continuation heuristics. This feature codifies the new formatting mandate, updates build tooling, and prepares the team for the inevitable whole-repo reformat required once the policy is approved.

## Goals
- Switch the Spotless Java target from `googleJavaFormat(...)` to `palantirJavaFormat(...)`, sourcing the agreed Palantir Java Format release via the version catalog.
- Refresh dependency locks (root + subprojects) so Gradle consistently resolves the Palantir formatter artifact across Spotless and annotation-processor configurations.
- Update the managed Git hooks, contributor docs (`AGENTS.md`, `CONTRIBUTING.md`, roadmap/knowledge artefacts), and IDE setup guidance to reflect the formatter policy change and the new 120-character wrap limit.
- Capture the reformat rollout plan (tooling command, sequencing, expected diff magnitude, and conflict mitigation guidance) in the feature plan/tasks before running Spotless.

## Non-Goals
- Performing the actual repository-wide reformat (tracked as a follow-up increment once the spec is approved).
- Adjusting PMD, Checkstyle, or SpotBugs rules beyond incidental documentation updates.
- Introducing fallback formatters or per-module exceptions—the policy applies uniformly across all JVM modules.

## Clarifications
1. Palantir Java Format version pin (2025-10-19 – Option B approved)
   - Options:
     - A) Pin to `2.74.0`, the latest GitHub release cut on 2025-09-11. Pros: Tagged release with changelog; aligns with the most recent documented drop that already bundles the 120-character default. Cons: Slightly older than Spotless’ published integration, may lag bug fixes that Palantir consumes internally before tagging.
     - B) Pin to `2.78.0`, the latest version published to the Gradle Plugin Portal (2025-10-14). Pros: Reflects the formatter revision Spotless already exposes via `palantirJavaFormat`, includes the newest parser fixes; easier to mirror with IDE plugins that follow the same publishing cadence. Cons: Upstream GitHub release notes are not yet tagged, so changelog diffs require inspecting commit history; marginally higher risk if the plugin was cut ahead of a full release.
   - Decision: **Option B** – Pin Spotless and dependency locks to Palantir Java Format 2.78.0 so Gradle builds, IDE integrations, and future Spotless upgrades stay in sync.

## Architecture & Design
- Update `gradle/libs.versions.toml` with a dedicated `palantirJavaFormat` entry and, if needed, remove or repurpose the current `googleJavaFormat` alias once migration completes.
- Modify `build.gradle.kts` Spotless configuration to call `palantirJavaFormat(libsCatalog.version("palantirJavaFormat"))`, adjust the global `resolutionStrategy.force` list to reference the Palantir artifact, and ensure annotation-processor configurations no longer request Google Java Format.
- Regenerate root and subproject `gradle.lockfile`s using `./gradlew --no-daemon --write-locks spotlessApply check` (and targeted module compile tasks if necessary) so the Palantir dependency is captured across all configurations.
- Review the managed `githooks/pre-commit` script to verify no Google Java Format assumptions remain, updating log output or helper messages if they reference the prior formatter.
- Produce guidance for IDE users (IntelliJ, VS Code) outlining how to install/configure Palantir Java Format and align formatter settings with the new policy; reference this guidance from the spec-linked how-to documents.

## Test Strategy
- `./gradlew --no-daemon spotlessCheck` (ensures the Palantir integration configures correctly without applying changes).
- `./gradlew --no-daemon spotlessApply check` (post-migration validation once the formatter swap is complete).
- Targeted module compiles (`:core:compileJava`, `:rest-api:compileJava`, etc.) to confirm no annotation-processor classpath regressions after removing Google Java Format.
- Optional: Run the managed pre-commit hook locally with staged sample files to ensure the Palantir formatter runs as expected before triggering the full repository reformat.

## Rollout & Regression
- Sequence the work in two increments: (1) toolchain swap + documentation updates (no source reformat), (2) whole-repo `spotlessApply` commit once the policy is approved. Communicate this plan in the feature tasks checklist.
- Advise contributors to pause concurrent large diffs or be prepared for rebase conflicts; document recommended workflows (e.g., rebase after the formatter commit lands).
- Monitor Spotless and Gradle plugin updates for any regressions; if Palantir releases a tagged follow-up (e.g., 2.78.x) before rollout, re-evaluate the pin before finalizing the migration.
- Update roadmap, knowledge map, and session artefacts to reflect the formatter policy decision and implementation status.
- ✅ 2025-10-19 – Palantir formatter policy landed; Spotless/Git hooks now run 2.78.0, repository was reformatted, docs/roadmap were updated, and quality gate (`spotlessApply check`) reran green.
