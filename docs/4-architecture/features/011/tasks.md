# Feature 011 Tasks – Reflection Policy Hardening

_Status: Complete_  
_Last updated: 2025-11-10_

> Checklist mirrors the Feature 011 plan increments; entries remain checked for audit history while migrating templates. Reference FR/NFR/Scenario IDs to keep traceability intact.

## Checklist
- [x] T-011-01 – Catalogue all reflection usage across modules (FR-011-01, S-011-01).  
  _Intent:_ Establish the full scope of the ban before refactoring.  
  _Verification commands:_  
  - `rg --hidden --glob "*.java" "java.lang.reflect"`  
  - `rg --hidden --glob "*.kt" "Class.forName"`  
  _Notes:_ Inventory recorded in plan notes (2025-10-01 snapshot).

- [x] T-011-02 – Add failing ArchUnit guard for `java.lang.reflect` imports (FR-011-01, FR-011-04, S-011-02).  
  _Intent:_ Ensure reflective imports immediately fail the architecture suite.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*ReflectionPolicyTest"`

- [x] T-011-03 – Add failing regex/Gradle scan for reflective API strings (FR-011-01, FR-011-04, S-011-03).  
  _Intent:_ Catch cases where reflection is invoked via string lookups.  
  _Verification commands:_  
  - `./gradlew --no-daemon reflectionScan`

- [x] T-011-04 – Remove reflection usage until the ArchUnit guard passes (FR-011-01, FR-011-02, S-011-01, S-011-02).  
  _Intent:_ Replace reflective imports with explicit seams across modules.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*ReflectionPolicyTest"`

- [x] T-011-05 – Wire `reflectionScan` into `qualityGate` with necessary allowlists (FR-011-04, NFR-011-02, S-011-03).  
  _Intent:_ Make the regex guard part of the default CI pipeline.  
  _Verification commands:_  
  - `./gradlew --no-daemon reflectionScan qualityGate`

- [x] T-011-06 – Introduce explicit CLI collaborator seams (FR-011-02, S-011-05).  
  _Intent:_ Let CLI tests hit collaborators directly instead of via reflection.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*OcraCli*"`

- [x] T-011-07 – Refactor maintenance CLI paths to structured DTOs (FR-011-02, S-011-05).  
  _Intent:_ Maintain CLI behaviour while eliminating reflective inspection.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

- [x] T-011-08 – Remove reflection from REST services/tests (FR-011-02, S-011-05).  
  _Intent:_ Update controllers and services to rely on injectable seams.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*Ocra*"`

- [x] T-011-09 – Remove reflection from core MapDB/OCRA fixtures (FR-011-02, S-011-05).  
  _Intent:_ Introduce package-private helpers so domain tests stay deterministic without reflection.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test`

- [x] T-011-10 – Update `AGENTS.md` and related docs with the reflection policy (FR-011-03, S-011-04).  
  _Intent:_ Communicate the guardrails and mitigation steps to contributors.  
  _Verification commands:_  
  - `rg -n "reflection" AGENTS.md`

- [x] T-011-11 – Refresh knowledge map/roadmap/session log; run `spotlessApply check` (FR-011-01–FR-011-04, NFR-011-01, NFR-011-02, S-011-01–S-011-04).  
  _Intent:_ Prove the gate stays green and documentation remains synced.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log (Optional)
- 2025-10-01 – `./gradlew --no-daemon :core-architecture-tests:test --tests "*ReflectionPolicyTest"` (PASS after refactors).
- 2025-10-01 – `./gradlew --no-daemon reflectionScan qualityGate` (PASS; guard wired into CI pipeline).
- 2025-10-01 – `./gradlew --no-daemon spotlessApply check` (PASS on OpenJDK 17.0.16 with guards enabled).

## Notes / TODOs
- Future exemptions must update both this specification and the guard allowlists; otherwise, the ArchUnit and regex tasks will block merges.
