# Feature 011 Tasks – Reflection Policy Hardening

_Status: Complete_  
_Last updated: 2025-10-02_

## Checklist
- [x] T1101 – Catalogue all reflection usage across modules and log findings in the plan (S11-01).
  _Intent:_ Establish the scope of the ban and document every offending call site before refactoring.
  _Verification commands:_
  - `rg --hidden --glob "*.java" "java.lang.reflect"`

- [x] T1102 – Add failing ArchUnit test that forbids `java.lang.reflect` imports outside allowlists (S11-02).
  _Intent:_ Create a red guard in `core-architecture-tests` so reflection regressions surface immediately.
  _Verification commands:_
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*ReflectionArchitectureTest"`

- [x] T1103 – Add failing regex/Gradle scan that flags reflective API names in strings (S11-03).
  _Intent:_ Ensure build tooling also fails when reflection slips in via string-based lookups.
  _Verification commands:_
  - `./gradlew --no-daemon reflectionScan`

- [x] T1104 – Implement the ArchUnit guard and drive it green after refactors (S11-01, S11-02).
  _Intent:_ Replace reflection with explicit seams, then rerun the guard to confirm compliance.
  _Verification commands:_
  - `./gradlew --no-daemon :core-architecture-tests:test --tests "*ReflectionArchitectureTest"`

- [x] T1105 – Wire the regex scan into `qualityGate` with appropriate allowlists (S11-03).
  _Intent:_ Make the regex guard part of the default pipeline so CI enforces the policy.
  _Verification commands:_
  - `./gradlew --no-daemon reflectionScan qualityGate`

- [x] T1106 – Introduce explicit CLI collaborator seams to replace reflective access (S11-01, S11-05).
  _Intent:_ Provide dedicated interfaces/DTOs so CLI tests reach collaborators without reflection.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

- [x] T1107 – Replace maintenance CLI reflection with structured DTOs and fixtures (S11-01, S11-05).
  _Intent:_ Document the new seams and keep behaviour identical while eliminating reflective calls.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`

- [x] T1108 – Refactor REST OCRA services/tests to remove reflection via shared seams (S11-01, S11-05).
  _Intent:_ Ensure REST controllers rely on injectable services instead of reflective inspection.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*Ocra*"`

- [x] T1109 – Update core tests to use explicit fixtures while documenting any necessary seams (S11-01, S11-05).
  _Intent:_ Keep domain coverage while ensuring zero reflection remains in the test suite.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test`

- [x] T1110 – Update `AGENTS.md` and contributor docs with the anti-reflection policy (S11-04).
  _Intent:_ Communicate the rule and mitigation guidance to future contributors.
  _Verification commands:_
  - `rg -n "reflection" AGENTS.md`

- [x] T1111 – Refresh roadmap/knowledge map, run `spotlessApply check`, and capture outcomes (S11-01, S11-02, S11-03, S11-04).
  _Intent:_ Prove the rule landed cleanly and documentation/tests stayed in sync.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Reflection ban is enforced via both ArchUnit and Gradle scan; future exemptions require updating this spec plus the allowlists.
