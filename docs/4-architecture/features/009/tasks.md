# Feature 009 Tasks – OCRA Replay & Verification

_Status:_ Complete  
_Last updated:_ 2025-11-10_

> Checklist mirrors the Feature 009 plan increments. Tasks remain checked for audit history during the template refinement sweep.

## Checklist
- [x] T-009-01 – Finalise specification, roadmap, and knowledge map context; capture strict replay decisions (FR-009-01–FR-009-05, S-009-01–S-009-06).  
  _Intent:_ Ensure façade scope, telemetry hashing, and performance expectations are settled before coding.  
  _Verification commands:_  
  - `less docs/4-architecture/features/009/spec.md`  
  - `rg -n "Feature 009" docs/4-architecture/roadmap.md`

- [x] T-009-02 – Add failing core replay tests for stored vs inline credentials, match/mismatch outcomes, and immutability (FR-009-01, S-009-01, S-009-05).  
  _Intent:_ Define deterministic behaviour and failure messaging ahead of implementation.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test --tests "*OcraReplay*"`

- [x] T-009-03 – Implement the core verification service plus telemetry emitters, keeping counters/session data immutable (FR-009-01, FR-009-04, S-009-01, S-009-04).  
  _Intent:_ Drive the red tests green while ensuring inline secrets never leak to logs/persistence.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test`  
  - `rg -n "core.ocra.verify" */src`

- [x] T-009-04 – Add failing and then passing Picocli verification command tests for stored/inline flows, mismatch messaging, and exit codes (FR-009-02, S-009-02, S-009-05).  
  _Intent:_ Provide operator tooling with deterministic outputs plus telemetry parity.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*OcraVerify*"`

- [x] T-009-05 – Add REST contract/tests and implementation for the verification endpoint, including OpenAPI schemas and validation errors (FR-009-03, S-009-03, S-009-05).  
  _Intent:_ Mirror CLI capabilities over HTTP with structured responses and status codes.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraVerification*"`  
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`

- [x] T-009-06 – Capture telemetry schema docs, hash requirements, and performance benchmark notes (FR-009-04, FR-009-05, S-009-04, S-009-06).  
  _Intent:_ Document audit expectations and the WSL2 latency benchmark that validated the implementation.  
  _Verification commands:_  
  - `rg -n "ocra verify" docs/2-how-to`  
  - `rg -n "benchmark" docs/4-architecture/features/009/plan.md`

- [x] T-009-07 – Run `./gradlew qualityGate`, confirm PIT/Jacoco thresholds after replay tests, and log closure metrics (FR-009-01–FR-009-05, S-009-01–S-009-06).  
  _Intent:_ Finish the feature with a full passing gate and recorded coverage/mutation scores.  
  _Verification commands:_  
  - `./gradlew --no-daemon qualityGate`  
  - `ls build/reports/pitest`  
  - `ls build/reports/jacoco/aggregated`

## Verification Log (Optional)
- 2025-10-01 – `./gradlew --no-daemon qualityGate` (PASS, stored P95 0.060 ms / inline P95 0.024 ms on WSL2 + OpenJDK 17.0.16; mutation score 91.83%).

## Notes / TODOs
- Historical R90x micro-tasks (design/implementation increments) remain available in git history before 2025-11-09 for granular tracing.
