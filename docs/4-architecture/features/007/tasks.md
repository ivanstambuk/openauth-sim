# Feature 007 Tasks – Operator-Facing Documentation Suite

_Status:_ Complete  
_Last updated:_ 2025-11-10_

> Checklist mirrors the Feature 007 plan increments. All tasks finished on 2025-09-30; entries remain for auditability during the template migration sweep.

## Checklist
- [x] T-007-01 – Close clarifications, sync roadmap/knowledge map references, and outline each guide (FR-007-05, S-007-05).  
  _Intent:_ Ensure documentation scope is settled and cross-link targets are identified before drafting prose.  
  _Verification commands:_  
  - `less docs/4-architecture/features/007/spec.md`  
  - `rg -n "Feature 007" docs/4-architecture/roadmap.md`  
  _Notes:_ Completed 2025-09-30; open-questions log confirmed empty before moving forward.

- [x] T-007-02 – Author the Java operator integration guide (FR-007-01, S-007-01).  
  _Intent:_ Provide runnable Java snippets and troubleshooting guidance for external JVM teams.  
  _Verification commands:_  
  - `rg -n "Java operator" docs/2-how-to`  
  - `markdownlint docs/2-how-to/use-ocra-from-java.md`  
  _Notes:_ Examples validated against deterministic fixtures before publication.

- [x] T-007-03 – Produce the CLI operations guide covering import/list/delete/evaluate/maintenance commands (FR-007-02, S-007-02).  
  _Intent:_ Capture command syntax, credential-store expectations, telemetry notes, and troubleshooting cues.  
  _Verification commands:_  
  - `rg -n "ocra" docs/2-how-to/use-ocra-cli-operations.md`  
  - `./gradlew :cli:runOcraCli --args="help"` (spot-check)  
  _Notes:_ Command outputs compared against the current CLI build on 2025-09-30.

- [x] T-007-04 – Replace the REST evaluation how-to with a comprehensive REST operations guide (FR-007-03, S-007-03).  
  _Intent:_ Document every REST endpoint, request/response schema, Swagger UI entry point, and troubleshooting steps.  
  _Verification commands:_  
  - `rg -n "Swagger" docs/2-how-to/use-ocra-rest-operations.md`  
  - `./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshot*"` (optional snapshot confirmation)  
  _Notes:_ Legacy doc removed after validating new coverage.

- [x] T-007-05 – Refresh `README.md` and cross-links to the new guides (FR-007-04, FR-007-05, S-007-04, S-007-05).  
  _Intent:_ Keep top-level messaging aligned with shipped capabilities and reference every new guide.  
  _Verification commands:_  
  - `rg -n "Swagger" README.md`  
  - `markdownlint README.md`  
  _Notes:_ README verified against roadmap + knowledge map entries.

- [x] T-007-06 – Run `./gradlew spotlessApply check`, capture doc lint output, and log closure notes (FR-007-05, S-007-05).  
  _Intent:_ Finish the feature with a clean build and documented verification trail.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ Verification log updated 2025-09-30.

## Verification Log (Optional)
- 2025-09-30 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Historical D07x drafting table remains available in git history prior to 2025-11-09 for anyone needing finer-grained context.
