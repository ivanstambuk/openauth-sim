# Feature 001 – Core Credential Domain

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/001/plan.md` |
| Linked tasks | `docs/4-architecture/features/001/tasks.md` |
| Roadmap entry | #1 |

## Overview
Design an OCRA-focused credential domain inside the `core` module that normalises RFC 6287 credential descriptors, shared secret material, and evaluation helpers for downstream facades. The scope of Feature 001 is limited to OCRA; future protocol packages (FIDO2, EUDI wallets, EMV/CAP, generic credentials) will be delivered through separate specifications once prioritised. The domain must provide deterministic validation and transformation logic so CLI, REST, and UI surfaces consume OCRA data without duplicating cryptographic rules.

## Clarifications
- 2025-09-28 – RFC 6287 vectors (T017–T018): Appendix C of RFC 6287 (Simplified BSD license) publishes the Standard, Challenge/Response, Mutual Challenge/Response, and Plain Signature OCRA suites with sample secrets (`31323334…` / `3132333435363738393031323334353637383930…`), counter/time/session inputs, and expected OTP outputs; we materialised these as test-only fixtures while placeholder tests asserted `UnsupportedOperationException` until the execution helper became available. citeturn1search5
- 2025-09-28 – OCRA response evaluation contract (T019): The execution helper MUST implement RFC 6287 Section 5 and Appendix A semantics by hashing the ASCII suite name, `0x00` delimiter, and enabled data inputs in the canonical order `C | question | password | session | timestamp`, encoding counters and timestamps as 8-byte big-endian values, challenge strings as UTF-8 rendered to uppercase hex, session inputs as uppercase hex padded to the declared `Snnn` byte length (default 64 when omitted), and PIN hashes with their declared digest; apply HOTP dynamic truncation using the suite’s declared digit length. citeturn1search0turn1search1turn1search5
- 2025-09-28 – OCRA session coverage: Follow the IETF OCRA Internet-Draft test-vector guidance, which lists typical session lengths S064, S128, S256, and S512 and ships a reference generator; use it to derive additional fixtures (e.g., S128/S256) beyond RFC 6287 defaults for compliance tests. citeturn0search0turn0search5
- 2025-09-28 – OCRA extended session vectors (T021): Ran the draft generator logic with the standard 32-byte demo key (`3132333435363738393031323334353637383930313233343536373839303132`) and alphanumeric challenge `SESSION01`, deriving session payloads by repeating the published S064 pattern to 64/128/256/512-byte lengths; the resulting OTPs (`17477202`, `18468077`, `77715695`, `05806151`) are captured as test fixtures for S064/S128/S256/S512 suites. citeturn0search0turn0search5
- 2025-09-28 – OCRA session compliance (T022): Extended `OcraRfc6287ComplianceTest` to assert each S064/S128/S256/S512 vector produces its published OTP and that parsed suite metadata exposes the expected session byte lengths without leaking session payloads in validation errors. citeturn0search0turn0search5
- 2025-09-28 – Session-aware helper rollout: Begin wiring the session-enabled execution helpers into the CLI facade first, providing operators a deterministic way to validate S-length inputs before expanding to REST/UI surfaces (Option A selected over REST/UI/shared helper alternatives). citeturn0search0turn0search5
- 2025-09-28 – CLI session helper (T024): Added the `maintenance ocra` command accepting suite/key/challenge/session inputs, routing them through `OcraResponseCalculator`, printing redaction-friendly `suite=`/`otp=` output, and reusing the generator-derived S064/S128/S256/S512 vectors for regression coverage. citeturn0search0turn0search5
- 2025-09-28 – Session helper rollout (next step): REST facade work moved to Feature 003, which tracks the synchronous evaluation endpoint reusing the CLI-tested calculator (Option A). citeturn0search0turn0search5

## Goals
- Model RFC 6287-compliant OCRA credential descriptors, validation rules, shared-secret normalisation, and metadata capture inside the `core` module.
- Provide deterministic evaluation helpers plus persistence-friendly envelopes so downstream facades can consume OCRA credentials without duplicating crypto logic.
- Canonicalise shared secret encodings (raw bytes, hexadecimal, Base64) ahead of descriptor construction and reject invalid payloads with actionable errors.
- Supply serialization/deserialization helpers for persistence and caching layers using versioned envelopes with upgrade hooks.
- Deliver an execution helper that produces RFC 6287-compliant OTP responses for baseline and session-aware suites and expose results to core consumers and CLI helpers.

## Non-Goals
- Introducing HOTP/TOTP/FIDO2/EMV credential models.
- Wiring CLI/REST/UI flows beyond the shared maintenance helper; downstream facades consume this domain via separate features.
- Adding persistence engines beyond the existing MapDB pipeline and versioned envelopes.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-001-01 | Immutable OCRA descriptors capture suite metadata, optional counter/PIN/session inputs, and remain read-only once constructed. | Creating a descriptor with valid suite + metadata exposes deterministic getters for application/CLI/REST consumers. | Missing suite attributes, unsupported metadata keys, or invalid secret references raise descriptive validation errors while redacting sensitive fields. | Schema-upgrade conflicts or metadata collisions abort creation with structured error codes so registries stay consistent. | `core.ocra.validation` frames log `suite`, `credentialNameHash`, `stage`, and `reasonCode` for audit parity. | RFC 6287 Section 4; owner directive dated 2025-09-27. |
| FR-001-02 | Shared-secret canonicalisation normalises RAW/HEX/Base64 inputs into the `SecretMaterial` value object used across modules. | Property-based tests prove round-trip encoding keeps entropy intact and downstream helpers only consume canonical byte arrays. | Codec enforces uppercase hex, Base64 padding, and minimum bit lengths before descriptors accept a secret. | Invalid encodings or entropy shortfalls halt descriptor construction and emit sanitized validation errors. | `core.ocra.secret.validation` frames capture encoding type, validation status, and hashed payload metadata. | RFC 4226/6287 guidance; Feature 001 plan tasks T-001-02/T-001-03. |
| FR-001-03 | Registry metadata enumerates required attributes, optional fields, digests, and session lengths for every supported OCRA suite. | `OcraCredentialRegistry` lookups return deterministic capability descriptions that facades reuse for prompts and validation. | Unsupported suites or stale metadata versions raise structured exceptions that callsites can map to user-friendly errors. | Version mismatches fail closed and instruct operators to reload descriptors before proceeding. | `core.ocra.registry.lookup` frames (TE-001-03) log `suite`, `result`, and `reasonCode` without leaking metadata contents. | Feature 001 plan Phase 2; RFC 6287 Appendix A. |
| FR-001-04 | Versioned persistence envelopes round-trip descriptors and apply upgrade hooks for legacy schema revisions. | Reading stored credentials upgrades them to the latest version transparently and logs each conversion. | Envelope readers validate checksums, metadata fields, and version numbers before applying migrations. | Unsupported envelope versions halt loading and instruct operators to trigger explicit upgrade helpers. | `core.ocra.validation` events tag the descriptor ID and envelope version whenever an upgrade occurs. | MapDB persistence constraints; Feature 001 plan T-001-05. |
| FR-001-05 | `OcraResponseCalculator` reproduces RFC 6287 Appendix C + S064/S128/S256/S512 OTPs for inline helpers and downstream facades. | Given a descriptor and inputs, the calculator yields deterministic OTPs that match fixtures and feed CLI maintenance flows. | Compliance tests compare calculator outputs with published fixtures and assert telemetry coverage per scenario. | Missing fixtures or mismatched OTPs keep tests red until the helper is corrected; runtime failures emit structured diagnostics. | CLI helpers forward verbose trace IDs plus `core.ocra.execution` telemetry for every request. | RFC 6287 Appendix C; IETF draft guidance; Feature 001 plan T-001-06/T-001-07. |
| FR-001-06 | Structured validation telemetry spans descriptor lifecycle, secret canonicalisation, and execution helpers. | Telemetry frames include suite identifiers, credential hashes, stages, reason codes, and timing metadata without exposing raw material. | Tests verify telemetry fields are emitted for success and failure paths and exclude sensitive bytes. | Missing telemetry fields fail ArchUnit contracts and block release until coverage is restored. | `core.ocra.validation` and `core.ocra.execution` events map to `TelemetryContracts`; verbose traces align with CLI maintenance logs. | Telemetry contract directives dated 2025-09-27. |
| FR-001-07 | Deterministic error diagnostics support CLI/REST responses without leaking secret material. | Validation exceptions map to stable identifiers with translation-ready summaries for operators. | Unit tests assert error messages for missing suite elements, invalid encodings, and registry mismatches, keeping payloads redacted. | Exceptions that expose raw secrets fail dedicated redaction tests and cannot ship. | `core.ocra.validation` reason codes correlate with emitted diagnostics for downstream mapping. | Feature 001 plan T-001-07 and CLI helper design notes. |
| FR-001-08 | Metadata and naming invariants ensure each OCRA credential exposes a globally unique `name` plus extensible metadata ignored by crypto flows. | Creating descriptors with unique names persists metadata for downstream registries without affecting execution results. | Collisions or malformed metadata trigger validation failures that reference offending keys while remaining sanitized. | Duplicate names are rejected with `ERR_DUPLICATE_CREDENTIAL_NAME`, requiring operators to rename before retrying. | Registry telemetry captures credential name hashes and failure causes during collisions. | Feature 001 goals; roadmap Workstream 1 alignment. |

## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-001-01 | Remain Java 17 compatible without preview features so Gradle 8.x builds stay deterministic. | Align with the workspace toolchain guardrail and constitution principle 3. | `./gradlew --no-daemon spotlessApply check` on Java 17 succeeds locally and in CI. | Gradle toolchain, Spotless Palantir 2.78.0, shared hooks. | Constitution Principle 3 (ratified 2025-09-27). |
| NFR-001-02 | Constructing or validating a credential executes in <=1 ms at the 99th percentile (single thread). | Ensure registry lookups do not dominate CLI/REST evaluation latency. | Microbenchmarks and regression tests capture allocation and runtime budgets. | Future JMH harness, current unit tests with timing assertions. | Feature 001 objectives. |
| NFR-001-03 | Credential objects stay <=16 KB per instance so thousands can be cached in memory. | Operator consoles preload registries to avoid on-demand allocations. | Heap sampling in tests plus instrumentation counters while caching large registries. | MapDB persistence layer and application cache configuration. | Persistence decision dated 2025-09-27. |
| NFR-001-04 | Stage unit, property-based, and ArchUnit tests before implementation to enforce test-first cadence. | Governance requires test-first execution for every increment. | `./gradlew --no-daemon :core:test`, `:application:test`, and ArchUnit suites run prior to merges. | JUnit, jqwik property tests, ArchUnit guardrails. | Feature 001 plan increments and constitution principles. |
| NFR-001-05 | Validation and transformation steps emit structured telemetry/logs without secrets. | Telemetry/observability parity across CLI/REST/UI is mandatory for audits. | Telemetry contract tests verify required fields, event names, and redaction rules. | `TelemetryContracts`, shared logging helpers, verbose trace instrumentation. | FR-001-06 / FR-001-07 references and telemetry directives dated 2025-09-27. |

## UI / Interaction Mock-ups (not applicable)
```
Core-only domain feature; no UI elements are introduced in this scope.
```

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-001-01 | Immutable OCRA credential descriptors capture suite metadata, optional counter/PIN fields, and reject malformed payloads with descriptive validation errors. |
| S-001-02 | Shared secret normalisation converts RAW/HEX/Base64 inputs into a canonical representation without leaking material in errors or telemetry. |
| S-001-03 | Credential registry exposes capability metadata and factories so downstream modules query supported suites and required fields deterministically. |
| S-001-04 | Versioned persistence envelopes round-trip descriptors, upgrading legacy records through documented migration hooks without altering caller APIs. |
| S-001-05 | `OcraResponseCalculator` reproduces RFC 6287 OTPs (including S064/S128/S256/S512 session variants) for inline helpers and downstream facades. |

## Test Strategy
- **Core:** Property-based tests cover `SecretMaterial`, descriptor validation, and persistence envelopes; `OcraRfc6287ComplianceTest` exercises Appendix C + session vectors; ArchUnit guards package boundaries.
- **Application:** Registry integration tests ensure application services consume descriptors without mutating `core` internals.
- **REST:** Deferred to Feature 003; this spec mandates placeholder coverage only (red tests guarded elsewhere).
- **CLI:** `maintenance ocra` tests assert sanitized output, verbose trace alignment, and failure paths using the shared fixtures.
- **UI (JS/Selenium):** Not applicable for this feature; operator console interactions rely on downstream specs.
- **Docs/Contracts:** Specification, plan, and tasks act as authoritative documentation; telemetry fields are mirrored in `TelemetryContracts` references.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-001-01 | `OcraCredentialDescriptor` encapsulates suite metadata, counters, PIN policies, session requirements, and immutable metadata. | core |
| DO-001-02 | `SecretMaterial` canonicalises RAW/HEX/Base64 secrets with redaction helpers. | core |
| DO-001-03 | `CredentialEnvelopeV1` (and successors) wrap descriptors for persistence upgrades while preserving schema history. | core, application |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-001-01 | Application service | `OcraCredentialRegistry` factory/lookups supplying descriptors to CLI/REST features. | Shared service consumed downstream; no external REST route in this feature. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-001-01 | `./bin/openauth maintenance ocra --suite <value> --secret <value> [--session …]` | Evaluates RFC 6287 suites via `OcraResponseCalculator`, printing sanitized `suite=`/`otp=` output and verbose trace IDs. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-001-01 | `core.ocra.validation` | `suite`, `credentialNameHash`, `stage`, `reasonCode`; shared secrets hashed before emission. |
| TE-001-02 | `core.ocra.execution` | `suite`, `sessionLength`, `otpHash`, `durationMs`; never emits raw OTP or secret bytes. |
| TE-001-03 | `core.ocra.registry.lookup` | `suite`, `result`, `reasonCode`; capability metadata hashed before emission. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-001-01 | `docs/test-vectors/ocra/rfc-6287/*.json` | Canonical RFC 6287 Appendix C fixtures plus derived S064/S128/S256/S512 session payloads. |
| FX-001-02 | `core/src/test/resources/fixtures/ocra/session/*.json` | Derived IETF draft session vectors used by CLI/tests. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| — | Not applicable | Core-only feature; UI changes tracked in downstream specs. |

## Telemetry & Observability
`core.ocra.validation`, `core.ocra.secret.validation`, and `core.ocra.execution` events are emitted through `TelemetryContracts`. Each frame includes sanitized identifiers (suite name hash, credential name hash), `stage`/`reasonCode`, and optional `otpHash` fields for audit replay. Verbose trace integration is limited to CLI maintenance flows for now; REST/UI adoption is deferred to Feature 003.

## Documentation Deliverables
- Update `docs/1-concepts/ocra.md` (or equivalent) with the descriptor and secret handling narrative.
- Keep `docs/4-architecture/knowledge-map.md` and `docs/architecture-graph.json` synchronized with registry/service relationships.
- Capture telemetry expectations in `docs/5-operations/analysis-gate-checklist.md` references when the gate runs for this feature.

## Fixtures & Sample Data
- RFC 6287 Appendix C fixtures and IETF draft session vectors live under `docs/test-vectors/ocra/` and are mirrored in `core` test resources.
- Secrets remain synthetic; plaintext values exist only inside fixture files checked into version control for test determinism.

## Spec DSL
```
domain_objects:
  - id: DO-001-01
    name: OcraCredentialDescriptor
    modules: [core]
    links: [FR-001-01, FR-001-08]
  - id: DO-001-02
    name: SecretMaterial
    modules: [core]
    links: [FR-001-02]
  - id: DO-001-03
    name: CredentialEnvelopeV1
    modules: [core, application]
    links: [FR-001-04]
routes:
  - id: API-001-01
    transport: application
    description: OcraCredentialRegistry factories/lookups supplying descriptors to CLI/REST features.
    links: [FR-001-03, FR-001-08]
cli_commands:
  - id: CLI-001-01
    command: ./bin/openauth maintenance ocra --suite <value> --secret <value> [--session ...]
    behaviour: Evaluates RFC 6287 suites via OcraResponseCalculator with sanitized output and verbose trace IDs.
telemetry_events:
  - id: TE-001-01
    event: core.ocra.validation
    fields: [suite, credentialNameHash, stage, reasonCode]
  - id: TE-001-02
    event: core.ocra.execution
    fields: [suite, sessionLength, otpHash, durationMs]
  - id: TE-001-03
    event: core.ocra.registry.lookup
    fields: [suite, result, reasonCode]
fixtures:
  - id: FX-001-01
    path: docs/test-vectors/ocra/rfc-6287/*.json
    purpose: RFC 6287 Appendix C fixtures
  - id: FX-001-02
    path: core/src/test/resources/fixtures/ocra/session/*.json
    purpose: Derived IETF draft session vectors
ui_states:
  - id: UI-001-NA
    description: Core-only feature; UI handled by downstream specs.
scenarios:
  - id: S-001-01
    description: Immutable descriptors reject malformed payloads.
  - id: S-001-02
    description: Secret normalisation keeps telemetry sanitized.
  - id: S-001-03
    description: Registry metadata stays deterministic across suites.
  - id: S-001-04
    description: Persistence envelopes upgrade legacy records without data loss.
  - id: S-001-05
    description: OcraResponseCalculator reproduces RFC/IETF fixtures.
```
