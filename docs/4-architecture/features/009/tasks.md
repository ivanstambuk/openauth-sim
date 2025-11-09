# Feature 009 Tasks – OCRA Replay & Verification

_Status:_ Complete  
_Last updated:_ 2025-11-09_

## Checklist
- [x] T0901 – Finalise specification, roadmap, and knowledge map context; capture strict replay decisions (S09-01–S09-06).
  _Intent:_ Ensure all clarifications (facade scope, telemetry hashing, performance expectations) are recorded before coding.
  _Verification commands:_
  - `less docs/4-architecture/features/009/spec.md`
  - `rg -n "Feature 009" docs/4-architecture/roadmap.md`

- [x] T0902 – Add failing core replay engine tests for stored vs inline credentials, match/mismatch outcomes, and hash generation (ORV-003, S09-01, S09-05).
  _Intent:_ Define deterministic behaviour and failure messaging ahead of implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test --tests "*OcraReplay*"`

- [x] T0903 – Implement the core verification service plus telemetry emitters, keeping counters immutable and hashes sanitized (S09-01, S09-04).
  _Intent:_ Drive the red tests green while ensuring inline secrets never leak to logs/persistence.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test`
  - `rg -n "core.ocra.verify" */src`

- [x] T0904 – Add failing and then passing Picocli verification command tests for stored/inline flows, mismatch messaging, and exit codes (ORV-001, S09-02, S09-05).
  _Intent:_ Provide operator tooling with deterministic outputs plus telemetry parity.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*OcraVerify*"`

- [x] T0905 – Add REST contract/tests and implementation for the verification endpoint, including OpenAPI schemas and validation errors (ORV-002, ORV-003, S09-03, S09-05).
  _Intent:_ Mirror CLI capabilities over HTTP with structured responses and status codes.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:test --tests "*OcraVerification*"`
  - `./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshot*"`

- [x] T0906 – Capture telemetry schema docs, hash requirements, and performance benchmark notes (S09-04, S09-06).
  _Intent:_ Document audit expectations and the WSL2 latency benchmark that validated the implementation.
  _Verification commands:_
  - `rg -n "ocra verify" docs/3-reference`
  - `rg -n "benchmark" docs/4-architecture/features/009/plan.md`

- [x] T0907 – Run `./gradlew qualityGate`, confirm PIT/Jacoco thresholds after replay tests, and log closure metrics (S09-01–S09-06).
  _Intent:_ Finish the feature with a full passing gate and recorded coverage/mutation scores.
  _Verification commands:_
  - `./gradlew --no-daemon qualityGate`
  - `ls build/reports/pitest`

## Notes / TODOs
- The original R90x checklist (detailed design/implementation increments) remains available in git history prior to 2025-11-09.
