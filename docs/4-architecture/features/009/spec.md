# Feature 009 – OCRA Replay & Verification

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/009/plan.md` |
| Linked tasks | `docs/4-architecture/features/009/tasks.md` |
| Roadmap entry | #9 |

## Overview
Deliver strict replay/verification workflows so auditors can confirm previously issued OCRA one-time passwords (OTPs) against stored or inline credentials without regenerating new values. The feature adds CLI and REST entry points backed by a deterministic core verifier, enforces zero tolerance windows, and emits hashed telemetry so operators can correlate requests across facades without exposing secrets.

## Clarifications
- 2025-10-01 – Facade scope covers CLI and REST only; operator UI remains out of scope until a future UX feature (Option A).
- 2025-10-01 – Verification payloads must include the complete OCRA suite context (challenge, session/timestamp, counter, credential reference) so the replay engine can reproduce the original OTP deterministically (Option B).
- 2025-10-01 – Operators may verify against stored credentials or inline secrets; both paths must emit identical telemetry schemas (Option B).
- 2025-10-01 – Replay evidence relies on existing structured telemetry/logging; no persisted receipts are introduced (Option A).
- 2025-10-01 – Strict verification forbids tolerance windows or resynchronisation; mismatches are definitive failures (Option A).
- 2025-10-01 – Performance benchmarks for Requirement R913 run on the WSL2 Linux host (x86_64) with OpenJDK 17.0.16; results recorded with hardware/JDK metadata (Option B).
- 2025-10-01 – Timestamp verification coverage must exercise both stored and inline flows using RFC 6287 timed signature vectors (Option A).

## Goals
- Provide stored and inline verification flows across core, CLI, and REST facades without mutating credential counters or sessions.
- Surface deterministic mismatch messaging and audit-ready telemetry (hashed OTP/context fingerprints, credential source) for every facade.
- Document performance benchmarks, CLI command syntax, REST schema, and troubleshooting guidance for operators.

## Non-Goals
- Re-implementing base OCRA evaluation logic or extending replay to HOTP/TOTP/other protocols.
- Introducing operator UI flows or persistence-layer receipts.
- Adding tolerance windows, resynchronisation helpers, or OTP regeneration utilities.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-009-01 | Core replay engine must verify OTPs for stored or inline credentials without mutating counters, sessions, or timestamps. | `OcraReplayVerifier` reproduces the original OTP deterministically and returns `MATCH`/`MISMATCH`/`INVALID`. | Unit tests cover stored vs inline success, strict mismatch, missing context, and immutability assertions. | Any mutation or indeterminate verdict fails quality gate; mismatch handling must remain deterministic. | `core.ocra.verify` frames include `credentialSource`, `otpHash`, `contextFingerprint`, `outcome`, `durationMs`. | Clarifications 2–5; Goals. |
| FR-009-02 | Provide a CLI command (`ocra verify`) that accepts OTP + context, supports stored or inline credentials, emits sanitized telemetry, and returns deterministic exit codes. | CLI command prints result summary and exits with `0` (match), `2` (mismatch), `64/70` (validation/unexpected). | Picocli integration tests cover stored/inline success, strict mismatch, validation failures, and telemetry assertions. | Missing arguments, mismatched context, or unexpected errors surface sanitized messages and telemetry hashes. | `cli.ocra.verify` frames log `credentialSource`, `outcome`, `reasonCode`, `otpHash`. | Clarifications 1–3. |
| FR-009-03 | Expose REST endpoint `POST /api/v1/ocra/verify` that mirrors CLI payloads/responses and leaves evaluation endpoints untouched. | Endpoint returns JSON body with `outcome`, `reasonCode`, and hashed telemetry identifiers. | REST contract tests exercise success, mismatch, validation failures, and unknown credential cases; OpenAPI snapshot updated. | Missing context or unknown credential IDs return structured 4xx responses without leaking secrets; unexpected errors emit sanitized 5xx responses. | `rest.ocra.verify` telemetry mirrors CLI fields and logs HTTP status/outcome. | Clarifications 1–3, 5. |
| FR-009-04 | Enforce hashed telemetry/logging for OTP/context data and capture performance benchmarks with documented methodology. | Hashes computed per spec (`otpHash`, `contextFingerprint`); benchmark report stored with plan/tasks. | Tests capture logger output ensuring only hashed fields appear; documentation references benchmark command/environment. | Raw secrets/OTP/context appear in logs or telemetry; missing benchmark data blocks completion. | Telemetry frames include `sanitized=true`, hashed fields, `durationMs`. | Clarifications 3–6; Goals. |
| FR-009-05 | Publish operator documentation describing CLI/REST verification flows, telemetry interpretation, and troubleshooting guidance. | Docs under `docs/2-how-to` and roadmap/knowledge map entries reference verification flows and benchmarks. | Spotless/doc lint runs; knowledge map updates recorded. | Missing documentation leaves operators without guidance; roadmap drift flagged in governance reviews. | Documentation references telemetry event names for correlation. | Goals; documentation deliverables. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-009-01 | Strict verification – no tolerance windows or resynchronisation attempts; mismatches remain definitive. | Audit/non-repudiation commitments. | Unit/integration tests enforce strict mismatch messages and status codes. | Core replay verifier, CLI/REST facades. | Clarifications 5. |
| NFR-009-02 | Performance – stored credential verification ≤150 ms P95; inline verification ≤200 ms P95 on reference hardware (WSL2 Linux, OpenJDK 17.0.16). | Operator SLAs. | Benchmark script output recorded in plan/tasks with hardware/JDK metadata. | Benchmark harness, `IO_OPENAUTH_SIM_BENCHMARK` runtime flag. | Clarifications 6. |
| NFR-009-03 | Idempotence – verification requests must be side-effect-free (no counter/session mutation, no credential updates). | Prevents audit drift and replay state changes. | Integration tests snapshot persistence state before/after verification. | `infra-persistence` read operations, CLI/REST flows. | Goals; Clarifications 2–3. |
| NFR-009-04 | Telemetry redaction – hashed OTP/context fields only; sanitize logs and responses. | Security and compliance. | Log-capture tests prove raw values absent; CLI/REST responses avoid sensitive data. | Telemetry contracts, logging framework. | Clarifications 3–4. |

## UI / Interaction Mock-ups
_Not applicable – Feature 009 does not add UI surfaces._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-009-01 | Core replay engine verifies stored and inline OTP submissions without mutating credential state. |
| S-009-02 | CLI verification command enforces argument exclusivity and reports deterministic outcomes/exit codes with telemetry hashes. |
| S-009-03 | REST verification endpoint accepts identical payloads, returns structured JSON, and surfaces validation/mismatch cases with correct HTTP statuses. |
| S-009-04 | Telemetry across core/CLI/REST emits hashed OTP/context fingerprints plus sanitized metadata for audits. |
| S-009-05 | Strict mismatch handling documents definitive failure messaging across facades. |
| S-009-06 | Performance benchmarks capture stored vs inline verification latency on the reference environment and land in documentation. |

## Test Strategy
- **Core:** Unit tests for `OcraReplayVerifier` covering stored/inline success, strict mismatch, missing context, immutability.
- **Application/CLI:** Picocli integration tests for `ocra verify` ensuring argument validation, exit codes, telemetry, and persistence snapshots.
- **REST:** Controller/service tests plus OpenAPI snapshot updates for success/mismatch/validation/credential-not-found cases.
- **Telemetry/logging:** Logger-capture tests assert hashed fields only; integration tests confirm `sanitized=true` metadata.
- **Performance:** Benchmark script records stored vs inline latency on the WSL2 reference host with environment metadata.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-009-01 | `OcraReplayRequest` – normalized OTP + suite context payload used by CLI/REST facades. | core, application, cli, rest-api |
| DO-009-02 | `OcraReplayResult` – outcome enum (`MATCH`, `MISMATCH`, `INVALID`) with hashed telemetry fields. | core, application, cli, rest-api |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-009-01 | REST `POST /api/v1/ocra/verify` | Accepts verification payload, returns `OcraReplayResultResponse`. | Uses same context fields as evaluation endpoint; OpenAPI snapshot maintained. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-009-01 | `./gradlew :cli:runOcraCli --args=\"ocra verify …\"` | Verifies OTPs using stored (`--credential-id`) or inline (`--suite` + `--secret`) credentials; enforces mutual exclusivity and emits sanitized telemetry. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-009-01 | `core.ocra.verify` | `credentialSource`, `outcome`, `otpHash`, `contextFingerprint`, `durationMs`, `sanitized=true`. |
| TE-009-02 | `cli.ocra.verify` | `credentialSource`, `outcome`, `reasonCode`, `otpHash`, `contextFingerprint`. |
| TE-009-03 | `rest.ocra.verify` | `credentialSource`, `httpStatus`, `outcome`, `reasonCode`, `otpHash`, `contextFingerprint`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-009-01 | `docs/test-vectors/ocra/rfc-6287/*.json` | RFC 6287 timed signature vectors reused for stored/inline verification tests. |
| FX-009-02 | `core/src/test/resources/fixtures/ocra/replay/*.json` | Deterministic replay fixtures for core verifier tests. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-009-NA | — | Feature has no UI component. |

## Telemetry & Observability
Verification telemetry relies on hashed OTPs (`otpHash = Base64URL(SHA-256(uppercase OTP bytes))`) and hashed context fingerprints (`contextFingerprint = Base64URL(SHA-256(suite + '|' + normalized challenge payloads + '|' + sessionHex + '|' + timestampHex + '|' + counter))`). CLI, REST, and core events include `credentialSource`, `outcome`, `reasonCode`, `durationMs`, and `sanitized=true`. Logs never contain raw OTPs, secrets, session payloads, or timestamps; tests capture logger output to ensure compliance. Benchmark commands (`IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test --tests "*OcraReplayBenchmark*"`) record latency metrics plus environment metadata.

## Documentation Deliverables
- Update CLI and REST operator guides with verification command/endpoint usage, sample payloads, and troubleshooting steps.
- Record benchmark methodology/results alongside Feature 009 plan/tasks and link from roadmap/knowledge map.
- Keep telemetry expectations documented in `docs/5-operations/analysis-gate-checklist.md` when replay verification is in scope.

## Fixtures & Sample Data
- Reuse RFC 6287 timed signature fixtures (`docs/test-vectors/ocra/`) for deterministic verification tests.
- Store additional replay fixture payloads under `core/src/test/resources/fixtures/ocra/replay/` for regression coverage.

## Spec DSL
```
domain_objects:
  - id: DO-009-01
    name: OcraReplayRequest
    modules: [core, application, cli, rest-api]
  - id: DO-009-02
    name: OcraReplayResult
    modules: [core, application, cli, rest-api]
api_routes:
  - id: API-009-01
    method: POST
    path: /api/v1/ocra/verify
cli_commands:
  - id: CLI-009-01
    command: ./gradlew :cli:runOcraCli --args="ocra verify …"
telemetry_events:
  - id: TE-009-01
    event: core.ocra.verify
  - id: TE-009-02
    event: cli.ocra.verify
  - id: TE-009-03
    event: rest.ocra.verify
fixtures:
  - id: FX-009-01
    path: docs/test-vectors/ocra/rfc-6287/*.json
  - id: FX-009-02
    path: core/src/test/resources/fixtures/ocra/replay/*.json
scenarios:
  - id: S-009-01
    description: Core replay engine verifies stored/inline OTPs without state changes
  - id: S-009-02
    description: CLI command enforces argument exclusivity and deterministic exit codes
  - id: S-009-03
    description: REST endpoint mirrors CLI payloads/responses with correct status codes
  - id: S-009-04
    description: Telemetry/logging emit hashed OTP/context fingerprints only
  - id: S-009-05
    description: Strict mismatch handling documented across facades
  - id: S-009-06
    description: Performance benchmarks recorded with environment metadata
```

## Appendix (Optional)
- None.
