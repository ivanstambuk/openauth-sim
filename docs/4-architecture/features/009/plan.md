# Feature Plan 009 – OCRA Replay & Verification

_Linked specification:_ `docs/4-architecture/features/009/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Operators and automated systems can verify historical OCRA OTP submissions against stored or inline credentials without mutating counters or sessions.
- CLI (`ocra verify`) and REST (`POST /api/v1/ocra/verify`) facades expose identical payloads, responses, and telemetry.
- Replay telemetry emits hashed OTP/context fingerprints plus deterministic outcomes for audits.
- Verification runs within the documented performance budget (≤150 ms P95 stored, ≤200 ms P95 inline) on the reference hardware.
- Documentation (how-to guides, roadmap, knowledge map) explains verification flows, telemetry interpretation, and benchmarking steps.
- `./gradlew qualityGate` remains green with the new tests and coverage.

## Scope Alignment
- **In scope:** Core replay verifier, CLI command, REST endpoint, telemetry hashing, performance benchmarking, documentation updates, roadmap/knowledge map sync.
- **Out of scope:** Operator UI flows, tolerance windows or resynchronisation helpers, non-OCRA protocol support, persisted verification receipts.

## Dependencies & Interfaces
- Core OCRA domain types (`OcraCredentialDescriptor`, replay fixtures) and persistence module for immutable reads.
- CLI Picocli launcher, REST Spring controllers, OpenAPI snapshot tooling.
- Telemetry contracts (`core.ocra.verify`, `cli.ocra.verify`, `rest.ocra.verify`).
- Docs/roadmap/knowledge-map entries, benchmark harness gated by `IO_OPENAUTH_SIM_BENCHMARK`.

## Assumptions & Risks
- **Assumptions:** Existing fixtures cover RFC 6287 timed signatures; persistence reads remain side-effect-free; telemetry hashing functions already shared across modules.
- **Risks & mitigations:**
  - _Strict mismatch UX confusion:_ Document exit codes/HTTP statuses clearly in how-to guides.
  - _Performance regressions:_ Capture benchmark data and rerun when dependencies change.
  - _Telemetry leaks:_ Enforce logger tests and ArchUnit checks ensuring only hashed fields are emitted.

## Implementation Drift Gate
- Triggered once T-009-01–T-009-07 finished; evidence stored within this plan and the tasks checklist.
- Evidence package: mapping of FR/NFR IDs to code/tests, telemetry hash samples, performance benchmark logs, and CLI/REST smoke-test transcripts.
- Outcome (2025-10-01): Gate completed with `./gradlew qualityGate` green, stored P95 0.060 ms / inline P95 0.024 ms on WSL2 OpenJDK 17.0.16 host.

## Increment Map
1. **I1 – Clarify scope & analysis gate** _(T-009-01)_  
   - _Goal:_ Confirm clarifications, update roadmap/knowledge map, and record strict verification decisions.  
   - _Commands:_ `less docs/4-architecture/features/009/spec.md`, `rg -n "Feature 009" docs/4-architecture/roadmap.md`.  
   - _Exit:_ Analysis gate checklist completed; open questions cleared.

2. **I2 – Core replay verifier coverage & implementation** _(T-009-02, T-009-03)_  
   - _Goal:_ Add failing core tests for stored/inline success, strict mismatch, immutability, then implement `OcraReplayVerifier`.  
   - _Commands:_ `./gradlew --no-daemon :core:test --tests "*OcraReplay*"` (red → green).  
   - _Exit:_ Core tests pass; telemetry hashing helpers in place.

3. **I3 – CLI verification command** _(T-009-04)_  
   - _Goal:_ Add Picocli tests for stored/inline success, strict mismatch, and validation errors; wire command + telemetry + exit codes.  
   - _Commands:_ `./gradlew --no-daemon :cli:test --tests "*OcraVerify*"`; CLI smoke tests documented.  
   - _Exit:_ CLI command available with deterministic outputs and sanitized telemetry.

4. **I4 – REST verification endpoint** _(T-009-05)_  
   - _Goal:_ Define request/response DTOs, add controller/service tests (success/mismatch/validation/unknown credential), update OpenAPI snapshot, implement endpoint.  
   - _Commands:_ `./gradlew --no-daemon :rest-api:test --tests "*OcraVerification*"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.  
   - _Exit:_ REST endpoint mirrors CLI payloads with sanitized responses; OpenAPI snapshot committed.

5. **I5 – Telemetry, documentation, and benchmarks** _(T-009-06)_  
   - _Goal:_ Document telemetry hashing, CLI/REST troubleshooting, and performance methodology; capture benchmark data on reference hardware.  
   - _Commands:_ `rg -n "ocra verify" docs/2-how-to`, `rg -n "benchmark" docs/4-architecture/features/009/plan.md`.  
   - _Exit:_ Docs updated, telemetry schema recorded, benchmarks logged with environment metadata.

6. **I6 – Full quality gate & closure** _(T-009-07)_  
   - _Goal:_ Run `./gradlew qualityGate`, confirm PIT/Jacoco thresholds, archive reports, and record closure notes.  
   - _Commands:_ `./gradlew --no-daemon qualityGate`; collect `build/reports/pitest` and `build/reports/jacoco/aggregated` artifacts.  
   - _Exit:_ Gate green; metrics captured in plan/tasks and session log.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-009-01 | I2 / T-009-02 / T-009-03 | Core replay verifier coverage + implementation. |
| S-009-02 | I3 / T-009-04 | CLI verification command behaviour/telemetry. |
| S-009-03 | I4 / T-009-05 | REST endpoint payloads, responses, OpenAPI snapshot. |
| S-009-04 | I2–I5 / T-009-02–T-009-06 | Telemetry hashing/logging applied across modules. |
| S-009-05 | I2–I4 / T-009-02–T-009-05 | Strict mismatch messaging across core/CLI/REST. |
| S-009-06 | I5–I6 / T-009-06 / T-009-07 | Benchmark recording + governance documentation. |

## Analysis Gate (2025-10-01)
- ✅ Specification captured clarifications, requirements, and interface designs.
- ✅ Open questions cleared in `docs/4-architecture/open-questions.md`.
- ✅ Plan/tasks aligned with scope; roadmap & knowledge map updated.
- ✅ Tasks ≤30 minutes with verification commands sequenced before implementation.
- ✅ Tooling readiness documented (`./gradlew :core:test`, `:cli:test`, `:rest-api:test`, `qualityGate`, OpenAPI snapshot command).

## Exit Criteria
- Core replay verifier, CLI command, and REST endpoint shipped with deterministic outcomes and telemetry hashing.
- Documentation (how-to guides, roadmap, knowledge map) reflects verification workflows and performance benchmarks.
- OpenAPI snapshot, telemetry schema, and benchmark logs updated.
- `./gradlew qualityGate` passes with PIT/Jacoco thresholds satisfied; reports archived under `build/reports/`.
- Session snapshot and migration tracker reference Feature 009 template/telemetry updates.

## Follow-ups / Backlog
- Future feature to extend replay verification into operator UI once the UX scope is prioritised.
- Consider expanding the verification gate to HOTP/TOTP once specifications exist.
- Monitor benchmark deltas after major persistence/telemetry changes; rerun `IO_OPENAUTH_SIM_BENCHMARK` workflow as needed.
