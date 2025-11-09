# Feature Plan 030 – Gradle 9 Upgrade

_Linked specification:_ `docs/4-architecture/features/030/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-19

## Objective
Move the project build tooling from Gradle 8.10 to 9.1.0 while keeping all modules, quality plugins, and documentation in sync. The plan follows the constitution-mandated workflow: validate the existing build, upgrade the wrapper, rerun quality gates, and capture any follow-ups discovered along the way.

## Current Context
- The workspace currently uses Gradle 8.10 via the wrapper (`gradle-8.10-bin.zip`).
- All quality plugins (Spotless 8, SpotBugs 6.4.2, ErrorProne 4.3.0, PIT 1.15.0) are compatible with Gradle 7+ and should continue to function on Gradle 9, but configuration cache behaviour may tighten.
- Java 17 is already the enforced toolchain across modules; Gradle 9 maintains Java 17 as the minimum, so no JDK updates are required.
- Reproducible archive defaults in Gradle 9 may change artifact ordering, so generated assets (OpenAPI snapshots, docs zips) must be checked after upgrading.

## Increment Breakdown (≤30 minutes each)
1. **I1 – Governance setup**  
   - Create spec, plan, and tasks artefacts for Feature 030.  
   - Update roadmap and current session snapshot to reflect the new workstream.  
   - Record the approved clarification in the spec.  
   - _Status: Completed 2025-10-19 – Spec/plan/tasks authored, roadmap row added, current-session snapshot updated, and clarification captured._

2. **I2 – Pre-upgrade warning sweep**  
   - Run `./gradlew --warning-mode=all clean check` under Gradle 8.10.  
   - Triage and resolve any deprecation warnings Blocking Gradle 9; document outcomes in the plan.  
   - _Status: Completed 2025-10-19 – Command finished cleanly with no new deprecation warnings; configuration cache stored successfully, so no blockers identified._

3. **I3 – Wrapper upgrade**  
   - Execute `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin` to update properties and wrapper JAR.  
   - Verify `gradle-wrapper.properties` points to 9.1.0 and commit-ready artefacts regenerate.  
   - _Status: Completed 2025-10-19 – Wrapper command updated the distribution URL to 9.1.0 and regenerated `gradle-wrapper.jar`. Verification confirms the new binary and properties are in place._

4. **I4 – Post-upgrade validation**  
   - Run `./gradlew --warning-mode=all clean check` with Gradle 9.1.0.  
   - Execute targeted module tests if not covered by `check` (REST UI Selenium, CLI).  
   - Capture configuration cache status and any new warnings.  
   - _Status: Completed 2025-10-19 – Full `clean check` completed on Gradle 9.1.0 after upgrading `info.solidsoft.pitest` to 1.19.0-rc.2 for compatibility; existing compile warnings about `WebAuthnAssertionResponse` visibility and missing SpotBugs annotations persisted. Configuration cache stored successfully and `./gradlew --configuration-cache help` confirmed cacheability._

5. **I5 – Artifact review & documentation sync**  
   - Inspect reproducible outputs (OpenAPI specs, docs artefacts) for changes; update snapshots if needed.  
   - Update knowledge artefacts (`docs/_current-session.md`, roadmap) and close out tasks.  
   - _Status: Completed 2025-10-19 – No snapshot or artefact diffs detected after the upgrade; roadmap, tasks checklist, and current-session snapshot already reflect the completed increments._

## Analysis Gate
- 2025-10-19 – Checklist completed. Specification, plan, and tasks align; no open questions outstanding; tasks map to ≤30-minute increments with tests sequenced before implementation; commands (`./gradlew --warning-mode=all clean check`, `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`, `./gradlew --configuration-cache help`) documented; principles and tooling guardrails remain satisfied.

## Dependencies
- Requires stable Java 17 environment (`JAVA_HOME`) and existing Gradle wrapper installation.  
- Quality plugins must remain compatible; issues will block progression.  
- Network access is necessary for downloading the Gradle 9.1.0 distribution.

## Risks & Mitigations
- **Risk:** New Gradle version exposes previously ignored configuration issues.  
  **Mitigation:** Run `--warning-mode=all` before and after the upgrade, and address warnings promptly.
- **Risk:** Reproducible archive changes alter snapshot artefacts unexpectedly.  
  **Mitigation:** Diff regenerated artifacts carefully; document any intentional changes.
- **Risk:** Configuration cache may disable tasks that mutate state during execution.  
  **Mitigation:** Review cache reports; adjust offending tasks or disable cache per-module with rationale if unavoidable.

## Validation
- Successful execution of `./gradlew --warning-mode=all clean check` under Gradle 9.1.0.  
- Targeted CLI, REST, and Selenium tests remain green.  
- Roadmap, tasks checklist, and session snapshot document completion and any follow-ups.
