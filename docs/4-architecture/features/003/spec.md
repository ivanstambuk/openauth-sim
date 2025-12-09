# Feature 003 – OCRA Simulator & Replay

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-13 |
| Owners | Ivan (project owner) |
| Linked plan | [docs/4-architecture/features/003/plan.md](docs/4-architecture/features/003/plan.md) |
| Linked tasks | [docs/4-architecture/features/003/tasks.md](docs/4-architecture/features/003/tasks.md) |
| Roadmap entry | #3 – OCRA Simulator & Replay |

## Overview
Feature 003 consolidates every OCRA-focused capability—core credential domain, deterministic calculation helpers,
persistence envelopes, CLI/REST facades, replay verification, and the operator-console flows—into a single
specification that now serves as the source of truth for HOTP/TOTP-independent OTP work. The scope merges the
previous Feature 001 (Core Credential Domain), Feature 003/004 (REST inline + stored evaluation), Feature 009
(Replay & Verification), Feature 016 (Operator UI Replay workspace), and Feature 018 (schema-v0 migration retirement),
keeping OCRA behaviour, telemetry, and fixtures aligned across modules.

## Goals
- G-003-01 – Provide a canonical OCRA credential domain and calculation stack that reproduces every RFC 6287 vector and
  extended S064/S512 session payload.
- G-003-02 – Expose inline and stored evaluation via REST, CLI, and operator-console surfaces with telemetry parity and
  strict validation.
- G-003-03 – Deliver replay/verification workflows (core, CLI, REST) that hash all sensitive context while documenting
  benchmarks and troubleshooting guidance.
- G-003-04 – Keep fixtures, documentation, knowledge map links, and OpenAPI snapshots synchronised with the consolidated
  feature so changes do not introduce drift.

## Non-Goals
- Supporting HOTP/TOTP or wallet simulators (handled by Features 001/002/006+).
- Introducing tolerance windows, async/batch APIs, or persistence receipts for replay.
- Adding issuance/provisioning flows; credential onboarding remains outside this feature.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-003-01 | OCRA credential descriptors normalize suite metadata, secrets (HEX/Base32/Base64), counters, timestamps, session payloads (S064–S512), and PIN hashes via `SecretMaterial`. | Property/stateful tests satisfy RFC 6287 Appendix C + draft vectors; ArchUnit guards module boundaries. | Invalid encodings or metadata versions raise structured `core.ocra.validation` errors. | Secrets logged or metadata skipped. | `core.ocra.validation` + `core.ocra.secret.validation`. | Legacy Feature 001. |
| FR-003-02 | `OcraResponseCalculator` reproduces every RFC vector, applies canonical hashing order (`suite\x00C|Q|P|S|T`), and surfaces deterministic diagnostics. | Unit/property tests cover S064/S128/S256/S512, alphanumeric sessions, PIN-hash flows. | Telemetry/assertions confirm sanitized outputs; mutation tests enforce dynamic truncation. | OTP mismatches or missing fixtures. | `core.ocra.execution` events (suite, otpHash, durationMs). | Legacy Feature 001. |
| FR-003-03 | Persistence envelopes (schema-v1) store descriptors, upgrade legacy payloads, and allow REST/CLI lookups without additional migrations. | Integration tests load mixed OCRA/HOTP records via `CredentialStoreFactory`; envelope upgrades logged once. | Replay/evaluation tests assert counters mutate only during evaluate flows. | Envelope checksum/version mismatch halts loading. | `core.ocra.validation` emits `stage=envelope_upgrade`. | Legacy Features 001/004/018. |
| FR-003-04 | `POST /api/v1/ocra/evaluate` accepts inline payloads, validates fields, returns `{otp,suite,telemetryId}`, and keeps OpenAPI snapshots in sync. | MockMvc + OpenAPI snapshot tests cover success/error paths, telemetry ID propagation. | Contract tests assert validation branches (missing fields, malformed hex, timestamp drift). | Secret leakage, drifted snapshots, inconsistent telemetry. | `rest.ocra.evaluate` (suite, hasSessionPayload, status, durationMs, sanitized=true). | Legacy Feature 003. |
| FR-003-05 | Stored credential resolution allows `credentialId` lookup + inline override exclusivity, reuses persistence layer, and emits deterministic reason codes. | Integration tests cover found/missing/conflict cases; OpenAPI doc lists mutually exclusive fields. | Validation ensures only one credential source is supplied; response redacts secrets. | Inline+stored submitted together or missing both. | `rest.ocra.evaluate` includes `credentialSource`. | Legacy Feature 004. |
| FR-003-06 | CLI + REST replay (`ocra verify` / `POST /api/v1/ocra/verify`) reproduce OTPs without mutating state, hash OTP/context fields, and expose deterministic mismatch messaging. | Picocli + MockMvc tests cover stored/inline replay, timestamp/session validation, hashed telemetry, benchmark hooks. | Performance benchmark captured with host/JDK metadata; docs updated. | Counters mutate, tolerance windows added, or telemetry logs raw OTPs. | `core.ocra.verify`, `cli.ocra.verify`, `rest.ocra.verify` with `otpHash`, `contextFingerprint`. | Legacy Feature 009. |
| FR-003-07 | Operator console evaluation + replay panels share inline presets, stored credential selectors, “Use current Unix seconds” toggles, verbose trace dock integration, and replay-only workspace referencing `/api/v1/ocra/verify`. | Selenium/UI unit tests exercise stored/inline flows, preset controls, timestamp toggles, CTA spacing, verbose trace wiring. | Accessibility checks ensure focus order + aria labels; docs capture user guidance. | UI diverges from REST contract or toggles drift. | `ui.ocra.evaluate` + `ui.ocra.replay` telemetry proxies. | Legacy Feature 016 + Feature 017 shell. |
| FR-003-08 | Schema-v0 migration code removed; `OcraStoreMigrations.apply` enforces schema-v1 invariants and is called before every persistence operation. | Integration tests load legacy fixtures using test-only flag, verifying migrations short-circuit when already v1. | CLI/REST flows fail fast if mismatched schema encountered; documentation notes only schema-v1 supported. | Hidden schema-v0 paths remain or migrations skipped. | Telemetry logs `migrationApplied=false` for healthy stores. | Legacy Feature 018. |
| FR-003-09 | Documentation (how-to, roadmap, knowledge map) and fixture catalogues remain synchronized with the consolidated OCRA feature, including references to `docs/test-vectors/ocra/*` and operator guides. | Spotless/doc lint runs; knowledge map entry lists Feature 003 as the OCRA source. | Drift gate catches missing doc updates; session log ([docs/_current-session.md](docs/_current-session.md)) logs relevant documentation commands. | References point to obsolete or incorrect paths. | n/a | Spec. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-003-01 | Security & Redaction – secrets, OTPs, session data never leave telemetry/logs in plaintext; hashed fields use Base64URL(SHA-256). | Constitution + telemetry policy. | Log capture tests, replay telemetry assertions, Selenium checks on UI redaction. | TelemetryContracts, logging config. | Legacy Features 001/009. |
| NFR-003-02 | Performance – core calculation ≤1 ms p99 (single thread); REST `/ocra/evaluate` p95 ≤50 ms locally; replay benchmarks recorded with host metadata. | Operator responsiveness. | Microbenchmarks + MockMvc tests; benchmark appendix stored with plan. | `OcraResponseCalculator`, Spring Boot, Picocli. | Legacy Features 001/003/009. |
| NFR-003-03 | Compatibility – Java 17, Spring Boot 3.3.x, Picocli 4.x; persistence remains schema-v1 only. | Toolchain consistency. | `./gradlew --no-daemon spotlessApply check :core:test :application:test :cli:test :rest-api:test :ui:test`. | Gradle build, CredentialStoreFactory. | Legacy specs. |
| NFR-003-04 | Observability – Telemetry events, verbose trace toggles, and documentation stay aligned so operators can map console events to REST/CLI responses. | Troubleshooting + drift gate. | Telemetry contract tests + knowledge-map review during analysis gate. | TelemetryContracts, verbose trace dock. | Legacy Feature 016 + verbose trace guardrails. |
| NFR-003-05 | Quality – Every increment follows Specification-Driven Development, retains scenario/task tables, and reruns `spotlessApply check` plus targeted module suites before closing. | Governance. | Logged commands in `_current-session.md`; session log ([docs/_current-session.md](docs/_current-session.md)) entries. | Gradle, runbooks. | Constitution + migration directive. |

## UI / Interaction Mock-ups
Operator console Evaluate/Replay panels mirror the shared two-column layout (form on the left, result/trace on the right) so operators can swap between stored and inline descriptors without reloading the page.

```
+-------------------------------------------------------------+
| OCRA Evaluate (stored)                                      |
| [Credential preset ▼] [Load sample] [Use current Unix secs] |
|                                                             |
| Challenge (C): [____________________]                       |
| Session (S064…S512): [______________]                       |
| PIN hash / Counter / Timestamp inputs (row aligned)         |
|                                                             |
| [ Evaluate ] [ Toggle verbose trace ]                       |
|                                                             |
| Result card: OTP preview table (Delta accent on 0)          |
| Trace link → VerboseTraceConsole dock (data-trace-id)       |
+-------------------------------------------------------------+

+-------------------------------------------------------------+
| OCRA Replay                                                 |
| Stored credential ◉ / Inline secret ○  (radio toggle)       |
| OTP input: [____________]  Challenge / Session fields       |
|                                                             |
| [ Verify OTP ] → Result badge (Match / Mismatch)            |
|                                                             |
| Trace reference: hashed context + copy-to-clipboard         |
+-------------------------------------------------------------+
```

### UI Mock-up notes
- Evaluate mode highlights preset metadata, Unix-second helper, and OTP preview tables so operators can visualise counter deltas.
- Replay mode keeps OTP inputs and challenge/session fields in one column, surfacing hashed telemetry references only in verbose trace mode to avoid leaking sensitive data.
- Detailed UI states live under `### UI States` in the Interface & Contract Catalogue for reuse in plans, tasks, and tests.

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-003-01 | Core descriptor + calculator reproduce RFC vectors and extended sessions (FR-003-01/02). |
| S-003-02 | REST evaluation handles inline + stored payloads with telemetry parity (FR-003-04/05). |
| S-003-03 | CLI/REST replay deliver deterministic match/mismatch flows (FR-003-06). |
| S-003-04 | Operator console evaluation + replay mirror REST contracts and verbose trace wiring (FR-003-07). |
| S-003-05 | Schema-v1 enforcement + documentation updates remain synchronized (FR-003-08/09). |

## Test Strategy
- **Core:** Property-based tests and RFC fixture sweeps cover `SecretMaterial`, descriptor normalisation, and `OcraResponseCalculator` branches (success, validation, failure).
- **Application:** Service tests validate persistence lookups, credential ID resolution, and deterministic telemetry payloads for evaluate/replay flows.
- **REST:** MockMvc/OpenAPI snapshot tests verify `POST /api/v1/ocra/evaluate` and `/verify` contracts, error envelopes, and telemetry IDs.
- **CLI:** Picocli integration tests exercise stored/inline evaluation, replay mismatch paths, and verbose trace toggles with hashed context assertions.
- **UI:** Selenium suites drive Evaluate/Replay tabs (stored vs inline, Unix seconds helper, trace dock), ensuring result cards and trace IDs stay aligned.
- **Docs/Fixtures:** `./gradlew --no-daemon spotlessApply check` plus manual review keeps how-to guides, knowledge map links, and fixture catalogues consistent.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-003-01 | `OcraCredentialDescriptor` – canonical definition of suite, secret material, counters, timestamps, and session payloads. | core, application |
| DO-003-02 | `SecretMaterial` – shared helper for HEX/Base32/Base64 decoding and hashing. | core |
| DO-003-03 | `OcraResponseCalculator` – deterministic calculator with diagnostics/fixture hooks. | core |
| DO-003-04 | `OcraReplayRequest` – transport DTO for replay/verification flows. | core, application, cli, rest-api |
| DO-003-05 | `OcraReplayResult` – structured replay outcome with hashed identifiers. | core, application, cli, rest-api |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-003-01 | REST `POST /api/v1/ocra/evaluate` | Inline/stored evaluation endpoint returning OTP preview tables, verbose trace IDs, and hashed context. | Mirrors CLI/UI payloads; sanitizes secrets before logging. |
| API-003-02 | REST `POST /api/v1/ocra/verify` | Deterministic replay / verification entry point that never mutates counters. | Emits match/mismatch reason codes + telemetry IDs. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-003-01 | `./bin/openauth maintenance ocra evaluate …` | Evaluates inline/stored descriptors, optionally outputs verbose trace payloads. |
| CLI-003-02 | `./bin/openauth maintenance ocra verify …` | Verifies OTPs without counter mutation; returns hashed telemetry identifiers for auditing. |
| CLI-003-03 | `--output-json` | All OCRA CLI commands emit JSON when present (ADR-0014). |

### Telemetry Events
| ID | Event name | Fields / Notes |
|----|-----------|----------------|
| TE-003-01 | `core.ocra.validation` | `suite`, `inputType`, `reasonCode`, `redacted=true`. |
| TE-003-02 | `core.ocra.execution` | `suite`, `otpHash`, `durationMs`, `traceId`. |
| TE-003-03 | `rest.ocra.evaluate` | `credentialSource`, `hasSessionPayload`, `status`, `traceId`. |
| TE-003-04 | `rest.ocra.verify` | `otpHash`, `status`, `reasonCode`, `traceId`. |
| TE-003-05 | `cli.ocra.evaluate` | `command`, `outcome`, `traceId` (hashed identifiers only). |
| TE-003-06 | `cli.ocra.verify` | `command`, `outcome`, `reasonCode`, `traceId`. |
| TE-003-07 | `ui.ocra.evaluate` / `ui.ocra.replay` | `protocol`, `mode`, `traceId` forwarded from REST responses. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-003-01 | `docs/test-vectors/ocra/rfc-6287/*.json` | Canonical RFC vectors plus extended sessions. |
| FX-003-02 | `core/src/test/resources/fixtures/ocra/session/*.json` | Session payload fixtures consumed by domain + CLI tests. |
| FX-003-03 | `core/src/test/resources/fixtures/ocra/replay/*.json` | Replay/verifier fixtures (match + mismatch cases). |
| FX-003-04 | `docs/test-vectors/ocra/replay/*.json` | Operator/REST how-to samples and benchmark payloads. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-003-01 | Evaluation tab (stored mode) | Select stored credential → seeding helper loads metadata, Evaluate submits to `/api/v1/ocra/evaluate`, counter increments, verbose trace ID appears in result card + trace dock. |
| UI-003-02 | Evaluation tab (inline mode) | Choose inline, auto-fill preset, optional `Use current Unix seconds` toggles quantise timestamp, submission returns OTP preview table + telemetry ID. |
| UI-003-03 | Replay workspace | Inline or stored replay form posts to `/api/v1/ocra/verify`, displays mismatch/match badge with hashed telemetry references; no counters mutate. |

## Telemetry & Observability
- `core.ocra.validation`, `core.ocra.secret.validation`, `core.ocra.execution`, and `core.ocra.verify` provide sanitized event frames for domain-level operations.
- `rest.ocra.evaluate` / `rest.ocra.verify` mirror status/outcome fields plus `credentialSource`, `hasSessionPayload`, `durationMs`, and hashed OTP/context fingerprints.
- `cli.ocra.*` events include `command`, `outcome`, and hashed identifiers; CLI logs never print raw OTPs.
- Operator console telemetry proxies REST traces, forwarding `data-trace-id` attributes so verbose trace dock stays in sync.

## Documentation Deliverables
- Update OpenAPI bundles under `docs/3-reference/rest-openapi.*` whenever request/response fields change.
- Keep operator/CLI/REST how-to guides (Feature 010 ownership) referencing the consolidated OCRA flow, including preset labels and Unix-seconds helpers.
- Refresh [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md), [docs/architecture-graph.json](docs/architecture-graph.json), and `_current-session.md` entries when OCRA modules or telemetry contracts evolve.
- Ensure roadmap + feature plan/task files link back to this spec when increments close to satisfy SDD traceability.

### Native Java API (reference)
- OCRA’s existing Native Java API (documented in [docs/2-how-to/use-ocra-from-java.md](docs/2-how-to/use-ocra-from-java.md)) acts as the reference pattern
  for Feature 014 – Native Java API Facade and ADR-0007:
  - Entry-point helpers live under `core` and `application` (for example, `OcraCredentialFactory` +
    `OcraResponseCalculator`), using simple request/response DTOs and avoiding direct persistence coupling.
  - Error handling relies on `IllegalArgumentException` and domain-specific runtime failures surfaced through clear
    messages rather than checked exceptions.
  - Telemetry flows through existing `TelemetryContracts` adapters; no separate Native-Java-only logging surface exists.
- Future Native Java seams for other protocols (HOTP, TOTP, FIDO2/WebAuthn, EMV/CAP, EUDIW) MUST follow the governance,
  naming, and Javadoc rules codified in Feature 014, treating this OCRA API as the canonical precedent.

## Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-003-01 | `docs/test-vectors/ocra/rfc-6287/*.json` | Canonical RFC vectors plus extended sessions. |
| FX-003-02 | `core/src/test/resources/fixtures/ocra/session/*.json` | Session payload fixtures used by CLI/core tests. |
| FX-003-03 | `core/src/test/resources/fixtures/ocra/replay/*.json` | Replay/verifier fixtures. |
| FX-003-04 | `docs/test-vectors/ocra/replay/*.json` | Operator/REST how-to samples and benchmark payloads. |

## Spec DSL
```
domain_objects:
  - id: DO-003-01
    name: OcraCredentialDescriptor
    modules: [core, application]
    links: [FR-003-01, FR-003-03]
  - id: DO-003-02
    name: SecretMaterial
    modules: [core]
    links: [FR-003-01]
  - id: DO-003-03
    name: OcraResponseCalculator
    modules: [core]
    links: [FR-003-02]
  - id: DO-003-04
    name: OcraReplayRequest
    modules: [core, application, cli, rest-api]
    links: [FR-003-06]
  - id: DO-003-05
    name: OcraReplayResult
    modules: [core, application, cli, rest-api]
    links: [FR-003-06]
api_routes:
  - id: API-003-01
    method: POST
    path: /api/v1/ocra/evaluate
    description: Inline/stored evaluation endpoint
    links: [FR-003-04, FR-003-05]
  - id: API-003-02
    method: POST
    path: /api/v1/ocra/verify
    description: Deterministic replay / verification
    links: [FR-003-06]
cli_commands:
  - id: CLI-003-01
    command: ./bin/openauth maintenance ocra evaluate …
    behaviour: Evaluates inline/stored descriptors with verbose trace IDs.
  - id: CLI-003-02
    command: ./bin/openauth maintenance ocra verify …
    behaviour: Verifies OTPs without mutating counters; emits hashed telemetry.
telemetry_events:
  - id: TE-003-01
    event: core.ocra.validation
  - id: TE-003-02
    event: core.ocra.execution
  - id: TE-003-03
    event: rest.ocra.evaluate
  - id: TE-003-04
    event: rest.ocra.verify
  - id: TE-003-05
    event: cli.ocra.evaluate
  - id: TE-003-06
    event: cli.ocra.verify
  - id: TE-003-07
    event: ui.ocra.evaluate / ui.ocra.replay
fixtures:
  - id: FX-003-01
    path: docs/test-vectors/ocra/rfc-6287/*.json
  - id: FX-003-02
    path: core/src/test/resources/fixtures/ocra/session/*.json
  - id: FX-003-03
    path: core/src/test/resources/fixtures/ocra/replay/*.json
scenarios:
  - id: S-003-01
    description: Core descriptor + calculator reproduce RFC vectors and extended sessions (FR-003-01/02)
  - id: S-003-02
    description: REST evaluation handles inline + stored payloads with telemetry parity (FR-003-04/05)
  - id: S-003-03
    description: CLI/REST replay deliver deterministic match/mismatch flows (FR-003-06)
  - id: S-003-04
    description: Operator console evaluation + replay mirror REST contracts and verbose trace wiring (FR-003-07)
  - id: S-003-05
    description: Schema-v1 enforcement + documentation updates (FR-003-08/09)
```
