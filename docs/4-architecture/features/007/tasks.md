# Feature 007 Tasks – Operator-Facing Documentation Suite

_Status:_ Complete  
_Last updated:_ 2025-11-09_

## Checklist
- [x] T0701 – Close clarifications, update plan/roadmap/knowledge map entries, and outline each guide (S07-01–S07-05).
  _Intent:_ Ensure documentation scope is settled and cross-link targets are known before drafting prose.
  _Verification commands:_
  - `less docs/4-architecture/features/007/spec.md`
  - `rg -n "Feature 007" docs/4-architecture/roadmap.md`

- [x] T0702 – Author the Java operator integration guide with runnable snippets and troubleshooting (FR1, S07-01).
  _Intent:_ Give external JVM teams a copy-paste path for simulator embedding.
  _Verification commands:_
  - `rg -n "Java operator" docs/2-how-to`
  - `markdownlint docs/2-how-to/*java*.md`

- [x] T0703 – Produce the CLI operations guide covering import/list/delete/evaluate/maintenance commands plus telemetry expectations (FR2, S07-02).
  _Intent:_ Capture every CLI workflow end-to-end for operators.
  _Verification commands:_
  - `rg -n "ocra" docs/2-how-to/use-ocra-cli-operations.md`

- [x] T0704 – Replace the REST evaluation how-to with a comprehensive REST operations guide (FR3, S07-03).
  _Intent:_ Document all endpoints, request/response schemas, Swagger UI usage, and troubleshooting steps.
  _Verification commands:_
  - `rg -n "Swagger" docs/2-how-to/use-ocra-rest-operations.md`
  - `./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshot*"`

- [x] T0705 – Refresh `README.md`, add the Swagger UI link, and ensure doc set cross-links consistently (FR4, FR5, S07-04, S07-05).
  _Intent:_ Keep top-level messaging aligned with shipped capabilities.
  _Verification commands:_
  - `rg -n "Swagger" README.md`
  - `markdownlint README.md`

- [x] T0706 – Run `./gradlew spotlessApply check`, capture doc lint output, and log closure notes (FR5, S07-01–S07-05).
  _Intent:_ Finish the feature with a clean build and documented verification trail.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Earlier D07x table remains in git history before 2025-11-09 for anyone needing granular drafting steps.
