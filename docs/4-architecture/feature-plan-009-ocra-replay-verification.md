# Feature Plan 009 – OCRA Replay & Verification

_Status: Draft_
_Last updated: 2025-10-01_

## Objective
Enable operators and automated systems to verify previously generated OCRA one-time passwords (OTPs) against stored or inline credentials without regenerating new values. The feature must provide strict verification (no tolerance windows), expose CLI and REST interfaces, and emit audit-ready telemetry for non-repudiation.

Reference specification: `docs/4-architecture/specs/feature-009-ocra-replay-verification.md`.

## Success Criteria
- New CLI command verifies OTPs using immutable replay logic with no counter/session mutation.
- REST endpoint accepts OTPs plus full suite context, returning deterministic success/failure responses.
- Verification supports both persisted credentials (by identifier) and inline secrets with identical telemetry metadata.
- Telemetry entries capture operator principal, credential source, and hashed OTP payload for audit reconstruction.
- Verification flows remain within 150 ms P95 (stored) / 200 ms P95 (inline) on the documented benchmark environment.
- `./gradlew qualityGate` passes with added replay tests across core, CLI, and REST modules.

## Proposed Increments
- R901 – Prime knowledge map and roadmap notes; ensure open questions cleared. ☑ (2025-10-01 – roadmap entry updated, knowledge map note added)
- R902 – Run analysis gate checklist for Feature 009; record outcome here. ☑ (2025-10-01 – analysis gate completed)
- R903 – Draft CLI interface design (command syntax, options, telemetry mapping); capture in spec/plan addendum. ☑ (2025-10-01 – CLI design recorded in spec Interface Design section)
- R904 – Draft REST contract (request/response JSON, validation errors) and update OpenAPI doc. ☑ (2025-10-01 – REST contract documented in spec Interface Design)
- R905 – Identify telemetry fields and logging strategy for verification; add to docs. ☑ (2025-10-01 – telemetry schema captured in spec)
- R906 – Add failing core verification tests (stored vs inline, strict mismatch, immutability). ☑ (2025-10-01)
- R907 – Add failing CLI verification tests (stored success, inline success, strict mismatch, missing context). ☑ (2025-10-01)
- R908 – Add failing REST integration tests (controller and service) covering success, strict mismatch, and validation errors. ☑ (2025-10-01 – tests failing pending endpoint implementation)
- R909 – Implement core verification logic ensuring immutability and strict matching. ☑ (2025-10-01)
- R910 – Implement CLI command wiring and telemetry. ☑ (2025-10-01)
- R911 – Implement REST verification endpoint, DTOs, and validation. ☑ (2025-10-01)
- R912 – Ensure telemetry/logging includes required audit fields and aligned documentation. ☑ (2025-10-01)
- R913 – Capture performance benchmark or documented measurements for P95 targets. ☑ (2025-10-01 – stored P95 0.060 ms, inline P95 0.024 ms on WSL2 Linux host with OpenJDK 17.0.16; command: IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test --tests io.openauth.sim.core.credentials.ocra.OcraReplayVerifierBenchmark --rerun-tasks --info)
- R914 – Update docs/how-to guides describing replay usage and audit interpretation. ☑ (2025-10-01 – CLI/REST how-tos cover verification flows; benchmark how-to published for ORV-NFR-002 traceability)
- R915 – Run `./gradlew qualityGate`, capture metrics, and record closure notes. ☑ (2025-10-01 – qualityGate w/ PIT: aggregated line 97.31%, branch 90.08%; PIT 91% (275/302) with test strength 94%; run time ~1m27s)
- R916 – Extend REST verification tests to cover inline timestamp validation and stored credential race handling. ✅
- R917 – Increase CLI verification launcher and telemetry coverage to raise aggregated Jacoco branches. ✅
- R918 – Add timestamp success-path tests for stored and inline replay using RFC 6287 timed signature vectors. ✅

## Checklist Before Implementation
- [x] Specification clarifications resolved (see Clarifications section).
- [x] Open questions log clear for Feature 009.
- [x] Feature plan references correct specification and aligns success criteria.
- [x] Tasks map to functional requirements with ≤10 minute increments prioritising tests first.
- [x] Planned work respects constitution principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
- [x] Tooling readiness noted with expected commands (`./gradlew qualityGate`, module-specific tasks).

## Analysis Gate (2025-10-01)
Checklist executed per `docs/5-operations/analysis-gate-checklist.md`.

- Specification complete with objectives, requirements, clarifications (ORV-001–ORV-005, ORV-NFR-001–NFR-003).
- Open questions log contains no Feature 009 entries.
- Plan/Tasks cross-reference specification and maintain ≤10 minute, test-first increments (R901–R915).
- Work respects constitution guardrails (spec-first, clarification gate, test-first, documentation sync, dependency control).
- Tooling readiness documented (`./gradlew qualityGate`, module tasks, PIT skip guidance).
No blockers identified; proceed to design increments R903–R905.

### Tooling Readiness – `./gradlew qualityGate`
- Expected runtime: ~2 minutes with warm caches when PIT runs; `-Ppit.skip=true` available for local iteration.
- Key tasks: `:core:test`, `:cli:test`, `:rest-api:test`, `mutationTest`, `jacocoAggregatedReport`, new replay tests, telemetry assertions.

## Notes
- 2025-10-01 – R915 completed: `./gradlew qualityGate` (PIT enabled) → Jacoco aggregated line 97.31% (2100/2158), branch 90.08% (808/897); PIT mutation score 91% (275/302), test strength 94% (275/293).
- 2025-10-01 – Operator documentation refreshed: CLI/REST guides now include verification usage and audit guidance; added `docs/2-how-to/benchmark-ocra-verification.md` for running the latency harness.
- 2025-10-01 – R913 benchmark environment confirmed (Option B): WSL2 Linux x86_64 on WSL2 host with OpenJDK 17.0.16; capture stored vs inline P95 latencies and log results here.
- 2025-10-01 – R913 benchmark executed via IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test --tests io.openauth.sim.core.credentials.ocra.OcraReplayVerifierBenchmark --rerun-tasks --info; stored credential P95 0.060 ms (max 2.698 ms), inline credential P95 0.024 ms (max 2.389 ms), comfortably below 150 ms/200 ms targets.
- 2025-10-01 – Draft plan created; awaiting analysis gate completion before implementation begins.
- 2025-10-01 – R901 complete: roadmap now lists Feature 009 in planning with spec/plan links; knowledge map references upcoming replay verification scope.
- 2025-10-01 – R903 complete: CLI verification command design, option table, and exit-code semantics captured in spec.
- 2025-10-01 – R904 complete: REST `/api/v1/ocra/verify` contract defined with payload/response semantics and OpenAPI follow-up noted.
- 2025-10-01 – R905 complete: telemetry events (`cli.ocra.verify`, `rest.ocra.verify`, `core.ocra.verify`) documented with hashing strategy for OTP/context payloads.
- 2025-10-01 – R906 added failing core tests covering stored vs inline verification success, strict mismatch detection, and persistence immutability; build currently red awaiting implementation.
- 2025-10-01 – R907 added CLI integration tests for `ocra verify` covering stored/inline matches, strict mismatch exit code, and missing context validation; command not yet implemented so tests fail (exit 64/usage currently).
- 2025-10-01 – R909 implemented `OcraReplayVerifier` descriptor resolution, inline construction, and OTP comparison (strict mismatch, validation mapping). `./gradlew :core:test -Ppit.skip=true` green.
- 2025-10-01 – R910 wired Picocli `ocra verify` through `OcraReplayVerifier`, emitting match/mismatch telemetry and expected exit codes; `./gradlew :cli:test -Ppit.skip=true` passes and overall `./gradlew check -Ppit.skip=true` now clears the branch coverage gate (≈0.902).
- 2025-10-01 – Clarified R912 scope remains telemetry/logging documentation (Option A); REST implementation continues under R911, keeping plan/tasks numbering aligned.
- 2025-10-01 – R908 added REST controller integration tests + placeholder controller/service contracts; `./gradlew :rest-api:test -Ppit.skip=true` currently red (UnsupportedOperationException + OpenAPI snapshot) pending endpoint implementation.
- 2025-10-01 – R911 implemented REST verification controller/service, regenerated OpenAPI snapshots via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest -Ppit.skip=true`, and confirmed `./gradlew :rest-api:test -Ppit.skip=true` green; telemetry hashing/fingerprints queued for R912.
- 2025-10-01 – R912 implemented verification telemetry hashing & audit context (`rest.ocra.verify` logger), added request context hashing, updated docs snapshot, and expanded test suite (`OcraVerificationServiceTest`/`OcraVerificationControllerTest`) to assert hashed OTP + context fingerprint coverage.
- 2025-10-01 – R916 added to cover REST verification service edge cases (inline timestamp validation, stored credential race) for Jacoco branch coverage uplift.
- 2025-10-01 – R917 covered CLI launcher main branch, CLI emit helper, telemetry blank-reason paths, and controller CSRF reuse, lifting aggregated branch coverage above the 0.90 threshold.
- 2025-10-01 – R918 added stored and inline timestamp success-path tests sourced from RFC 6287 timed signature vectors to harden `validateTimestamp` coverage.

Update this plan after each increment.
