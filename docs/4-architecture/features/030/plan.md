# Feature Plan 030 – Gradle 9 Upgrade

_Linked specification:_ `docs/4-architecture/features/030/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/030/tasks.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
Upgrade the Gradle wrapper from 8.10 to 9.1.0 while keeping all quality plugins, configuration-cache behaviour, and
documentation in sync. Success requires:
- FR-030-01 – Governance notes (roadmap/current-session) capture the warning sweep + remediation summary.
- FR-030-02 – Wrapper artefacts and plugin bumps (e.g., PIT) land with zero mismatches.
- FR-030-03 – `./gradlew --warning-mode=all clean check` plus targeted module suites succeed under Gradle 9.
- FR-030-04 – Reproducible artefacts/docs reviewed and tracker entries updated.

## Scope Alignment
- **In scope:** Wrapper regeneration, plugin compatibility updates, warning-mode sweeps, documentation sync, configuration
  cache verification, migration tracker updates.
- **Out of scope:** Java toolchain upgrades, unrelated dependency bumps, restructuring Gradle scripts beyond compatibility
  tweaks.

## Dependencies & Interfaces
- Gradle wrapper files under `gradle/wrapper/`.
- Quality plugins: Spotless, SpotBugs, ErrorProne, PIT, Spring Boot.
- Docs: roadmap, knowledge map, `docs/_current-session.md`, migration plan.
- Network access to download Gradle 9.1.0 distribution.

## Assumptions & Risks
- **Assumptions:** Java 17 is installed and stable; plugin versions have Gradle 9–ready releases (PIT 1.19.0-rc.2 chosen).
- **Risks:**
  - Plugin incompatibility → mitigate by bumping to Gradle-9-compatible versions or reverting wrapper until resolved.
  - Reproducible artefact drift → mitigate via diff review and documentation.
  - Configuration cache regressions → mitigate with targeted `--configuration-cache` runs and fallback documentation.

## Implementation Drift Gate
- Evidence captured 2025-10-19: pre/post-upgrade `--warning-mode=all clean check` logs, wrapper diff, PIT plugin bump,
  configuration-cache report, documentation updates.
- Gate remains satisfied; rerun only if future Gradle work reopens the feature.

## Increment Map
1. **I1 – Governance setup (S-030-01, S-030-04)**
   - Create spec/plan/tasks, update roadmap + session snapshot, log clarification.
   - Commands: docs edits, `./gradlew --warning-mode=all clean check` (baseline).
   - Exit: documentation references Feature 030.
2. **I2 – Pre-upgrade warning sweep (S-030-01)**
   - Run `./gradlew --warning-mode=all clean check` on Gradle 8.10; document warnings (none blocking).
   - Exit: warning inventory recorded.
3. **I3 – Wrapper upgrade & plugin bump (S-030-02)**
   - Run `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`.
   - Update PIT plugin to 1.19.0-rc.2.
   - Exit: wrapper + plugin diff verified.
4. **I4 – Post-upgrade validation (S-030-03)**
   - Execute `./gradlew --warning-mode=all clean check` under Gradle 9.
   - Run targeted CLI/REST/Selenium tests if needed; run `./gradlew --configuration-cache help`.
   - Exit: builds green, cache stored, no new warnings.
5. **I5 – Artifact review & documentation (S-030-04)**
   - Review reproducible artefacts; update docs, roadmap, migration tracker, and session snapshot.
   - Exit: tracker updated, no follow-ups outstanding.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-030-01 | I1, I2 / T-030-01, T-030-02 | Governance + pre-upgrade warning sweep. |
| S-030-02 | I3 / T-030-03 | Wrapper + plugin updates. |
| S-030-03 | I4 / T-030-04 | Post-upgrade validation + cache check. |
| S-030-04 | I1, I5 / T-030-05 | Documentation + artifact review. |

## Analysis Gate
- Completed 2025-10-19 after warning sweep + wrapper plan were documented; no open questions remain.

## Exit Criteria
- FR-030-01…FR-030-04 satisfied with recorded commands/logs.
- `./gradlew --warning-mode=all clean check` (Gradle 9) captured in tasks/plan.
- Roadmap, knowledge map, migration plan, and session snapshot reference the completed upgrade.

## Follow-ups / Backlog
- None. Future Gradle upgrades should begin with a fresh feature once new requirements emerge.
