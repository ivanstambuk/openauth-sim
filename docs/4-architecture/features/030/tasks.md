# Feature 030 Tasks – Gradle 9 Upgrade

_Linked plan:_ `docs/4-architecture/features/030/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-19

☑ **T3001 – Governance setup (S30-04)**  
  ☑ Create spec/plan/tasks artefacts and capture the owner approval in the spec.  
  ☑ Update `docs/4-architecture/roadmap.md` and `docs/_current-session.md` to reference Feature 030.

☑ **T3002 – Pre-upgrade warning sweep (S30-01)**  
  ☑ Run `./gradlew --warning-mode=all clean check` using the existing Gradle 8.10 wrapper.  
  ☑ Resolve or document any deprecation warnings surfaced by the command (none observed).

☑ **T3003 – Wrapper upgrade (S30-02)**  
  ☑ Execute `./gradlew wrapper --gradle-version 9.1.0 --distribution-type bin`.  
  ☑ Verify `gradle/wrapper/gradle-wrapper.properties` and `gradle/wrapper/gradle-wrapper.jar` reflect the new version.

☑ **T3004 – Post-upgrade validation (S30-03)**  
  ☑ Run `./gradlew --warning-mode=all clean check` under Gradle 9.1.0.  
  ☑ Execute targeted module tests (CLI, REST, Selenium) if not covered by `check` (covered by `check`).  
  ☑ Capture configuration cache status and note any new warnings (none blocking; existing WebAuthn visibility + SpotBugs annotation warnings persist).  
  ☑ Bump `info.solidsoft.pitest` plugin to `1.19.0-rc.2` to restore compatibility with Gradle 9.

☑ **T3005 – Artifact review & documentation sync (S30-04)**  
  ☑ Inspect and refresh any reproducible artifacts or snapshots impacted by the upgrade (none changed).  
  ☑ Update roadmap, tasks, and session snapshot to mark completion and log follow-ups (complete; no outstanding follow-ups).
