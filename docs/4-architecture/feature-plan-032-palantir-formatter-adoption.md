# Feature Plan 032 – Palantir Formatter Adoption

_Linked specification:_ `docs/4-architecture/specs/feature-032-palantir-formatter-adoption.md`  
_Status:_ Planned  
_Last updated:_ 2025-10-19

## Vision & Success Criteria
- Adopt Palantir Java Format 2.78.0 as the canonical formatter across Spotless, managed Git hooks, and IDE guidance without breaking the build.
- Capture the tooling change and staged rollout so every contributor can rebase smoothly after the repository-wide reformat.
- Maintain a green build by validating the formatter swap with `./gradlew --no-daemon spotlessCheck` and final verification via `./gradlew --no-daemon spotlessApply check`.

## Scope Alignment
- **In scope:** Spotless configuration updates, version-catalog and dependency-lock refresh, managed hook/message updates, documentation/how-to revisions, and the single follow-up `spotlessApply` commit once tooling is verified.
- **Out of scope:** Adding alternative formatters, adjusting non-formatting lint tools (Checkstyle/PMD/SpotBugs), or introducing module-specific formatting overrides.

## Dependencies & Interfaces
- Spotless plugin configuration in `build.gradle.kts`.
- Version catalog (`gradle/libs.versions.toml`) and Gradle dependency locks (root + subprojects).
- Managed Git hooks under `githooks/` (especially `pre-commit`).
- Contributor documentation (`AGENTS.md`, `CONTRIBUTING.md`, formatting how-to sections) and knowledge artefacts that discuss formatter policy.

## Increment Breakdown (≤10 minutes each)
1. **I1 – Planning artefacts & backlog sync**  
   - Add Feature 032 plan and tasks documents, update `docs/_current-session.md`, and remove the open formatter question now that Option B is approved.  
   - _2025-10-19 – Spec created and clarification resolved; plan and tasks in progress._

2. **I2 – Spotless configuration swap**  
   - Update `gradle/libs.versions.toml` with `palantirJavaFormat = "2.78.0"`, replace `googleJavaFormat` usage in Spotless with `palantirJavaFormat`, and adjust `resolutionStrategy.force` plus annotation-processor dependencies to drop Google Java Format.  
   - Command: `./gradlew --no-daemon spotlessCheck`.  
   - _2025-10-19 – Spotless now invokes Palantir Java Format; `spotlessCheck` fails with expected formatting diffs across 372 Java files, validating the configuration ahead of the staged reformat._

3. **I3 – Dependency lock refresh**  
   - Run `./gradlew --no-daemon --write-locks spotlessApply check` (and targeted `:core:compileJava`, `:rest-api:compileJava` if needed) to regenerate root and module lockfiles with the Palantir formatter dependency.  
   - Capture the command outputs and timing in the plan/tasks.  
   - _2025-10-19 – Used `./gradlew --no-daemon --write-locks spotlessCheck` plus targeted module `compileJava` tasks to persist new lock entries (`palantir-java-format` et al.) while deferring the repo-wide reformat._

4. **I4 – Managed hook review**  
   - Inspect `githooks/pre-commit` (and other affected scripts) for Google Java Format references, updating messaging/logging to mention Palantir Java Format 2.78.0.  
   - Stage sample files and dry-run the hook to confirm the formatter invocation works.  
   - _2025-10-19 – Hook log message now references “spotlessApply (Palantir Java Format 2.78.0)”; dry run verified execution without further tweaks._

5. **I5 – Documentation & knowledge updates**  
   - Revise `AGENTS.md`, `CONTRIBUTING.md`, relevant how-to guides, roadmap, and knowledge map to reflect the new formatter policy and 120-character wrap guidance.  
   - Link IDE setup tips for IntelliJ/VS Code referencing Palantir tooling.  
   - _2025-10-19 – AGENTS, CONTRIBUTING, and the architecture knowledge map now highlight Palantir Java Format 2.78.0 with IDE alignment guidance; roadmap/how-to review pending._

6. **I6 – Stage 1 verification & review**  
   - Execute `./gradlew --no-daemon spotlessCheck` and targeted module compile tasks to confirm the tooling swap is green without applying the reformat.  
   - Perform self-review of configuration/doc updates before proceeding to the global reformat.  
   - _2025-10-19 – `spotlessCheck` rerun post-doc updates; failure captures the expected Palantir diffs ahead of the repository-wide `spotlessApply` step._

7. **I7 – Repository-wide reformat**  
   - Run `./gradlew --no-daemon spotlessApply` to reformat all Java sources under the new policy, inspect a representative subset for formatting regressions (continuation alignment, 120-column wrapping).  
   - Document expected rebase instructions for collaborators.  
   - _2025-10-19 – Executed `spotlessApply`; verified representative modules (core/application/rest-api/ui) reflect Palantir alignment and 120-character wrapping._

8. **I8 – Final quality gate & closure**  
   - Execute `./gradlew --no-daemon spotlessApply check` after the reformat commit, update Feature 032 tasks and session snapshot, and summarise the formatter migration in the roadmap/changelog before closing the feature.
   - _2025-10-19 – `spotlessApply check` completed successfully (build green); ready to update roadmap/session notes and prepare final formatter migration summary._

9. **I9 – Documentation handoff**  
   - Record formatter migration outcomes in the roadmap, changelog, and session snapshot; archive plan/tasks once hand-off guidance is published.
   - _2025-10-19 – Roadmap entry updated to “Complete”, changelog annotated, and current-session snapshot captures Palantir migration summary; plan/tasks remain for traceability._

## Dependencies
- Requires the existing Gradle Spotless plugin configuration and managed Git hooks.
- Coordinate with Feature 026/028/029/031 owners before landing the global reformat to avoid disruptive overlaps.
