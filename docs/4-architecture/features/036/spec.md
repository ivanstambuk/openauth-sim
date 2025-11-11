# Feature 036 - Verbose Trace Tier Controls

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/036/plan.md` |
| Linked tasks | `docs/4-architecture/features/036/tasks.md` |
| Roadmap entry | #36 |

## Overview
Standardise verbose trace redaction tiers across HOTP, TOTP, OCRA, and FIDO2 so operators can toggle between a "normal" operational view and progressively richer "educational" / "lab-secrets" diagnostics without duplicating filtering logic. The feature introduces a shared tier helper, applies consistent attribute tagging inside every trace builder, and harmonises documentation/tests so future protocols inherit the behaviour automatically.

## Clarifications
- 2025-10-25 - `normal` tier must emit final outcomes plus curated metadata (algorithms, counters, drift windows, relying-party identifiers, signature counters) while suppressing secrets and truncation internals. `educational` retains today''s detail and `lab-secrets` may extend it. (Owner directive)
- 2025-10-25 - FIDO2 traces may expose COSE public-key metadata (algorithm identifiers, curve names, public coordinates, RFC 7638 thumbprints) in the `normal` tier; only secret/private-key material remains restricted. (Owner directive)
- 2025-10-25 - Shared tier helper must drive every facade (REST, CLI, UI) even though user-facing toggle surfaces arrive in a later UX feature. (Owner directive)
- 2025-10-25 - Mutation/coverage tooling must continue to exercise tier-tagging paths so future refactors cannot remove attributes silently. (Owner directive)

## Goals
- Provide a single tier-aware filtering helper invoked by every verbose trace builder.
- Retain the existing full-detail (`educational`) experience by default while adding deterministic `normal` and `lab-secrets` tiers.
- Ensure CLI, REST, and UI snapshots cover all three tiers so payloads remain consistent across facades.
- Document per-protocol tier matrices and governance so new protocols extend the model safely.

## Non-Goals
- Shipping UI toggle controls (handled by a follow-up UX feature).
- Persisting traces or changing telemetry redaction policies beyond verbose trace payloads.
- Backporting the helper to pre-Feature 035 trace implementations.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-036-01 | Introduce `TraceTier` enum + `TraceTierFilter` helper that accepts tagged attributes, filters by minimum tier, and returns sorted payloads. | Helper returns attributes whose `minimumTier` <= requested tier, masks sensitive values, and preserves metadata order. | Core unit tests cover comparisons, masking hints, and invalid tier errors. | Helper throws validation exception, builders surface `INVALID_TIER` and default to `normal`. | Emits `telemetry.trace.filtered` per request with `tier`, `attributeCount`, `maskedCount`. | Owner directives 2025-10-25; Feature 035 trace contract. |
| FR-036-02 | HOTP, TOTP, OCRA, and FIDO2 verbose trace builders must tag each attribute with a minimum tier and invoke the helper before serialising payloads. | Builders emit deterministic JSON/text for each tier and keep secrets masked outside `lab-secrets`. | Builder/unit tests assert tag coverage vs tier matrix fixtures. | Missing tags block build; telemetry logs `telemetry.trace.invalid_tier`. | Tiered fixtures stored under `docs/test-vectors/trace-tiers/**`. | Owner directives; Feature 022/023/024 specs. |
| FR-036-03 | REST, CLI, and UI facades must propagate tier selection (query param, CLI flag, configuration) and refresh snapshots/fixtures accordingly. | CLI `--verbose-tier`, REST `?tier=`, and UI config render matching payloads across tiers. | Contract/CLI snapshot/UI Selenium tests compare canonical payloads. | Invalid tier returns error code 3 (CLI) or HTTP 400 and defaults to `normal`. | Telemetry logs list source facade. | Owner directive + telemetry governance. |
| FR-036-04 | Documentation + governance artefacts (spec/plan/tasks, roadmap, knowledge map, how-to guides) describe tier behaviour, UX dependency, and telemetry policies. | Docs list tier matrices per protocol plus governance notes. | Analysis gate confirms doc updates before implementation. | Feature remains Draft if docs diverge. | Knowledge map + roadmap entries reference tier helper. | Constitution ÂSection 4, migration directive 2025-11-10. |
| FR-036-05 | Quality automation (`spotless`, PMD, ArchUnit, Selenium) must remain green with tier filtering enabled and record coverage evidence. | `./gradlew spotlessApply check` + targeted module suites pass with tier tests. | JaCoCo/mutation reports show helper/ builder coverage. | Red builds block increments until fixed or TODO logged. | Verification log in tasks file captures commands. | Constitution Principles 3 & 5. |

## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-036-01 | Tier filtering remains linear in attribute count and adds <=5 ms per trace on developer hardware. | Prevent sluggish verbose traces for operators. | Benchmark helper inside `TraceTierFilterTest`; document timing. | Core module micro-bench harness. | Owner directive 2025-10-25. |
| NFR-036-02 | All tiers yield deterministic payloads (masked values, digest slices) across runs. | Snapshot fixtures + contract tests rely on stable output. | CLI/REST snapshot diffs remain unchanged across two consecutive runs. | Tier fixtures under `docs/test-vectors/trace-tiers/**`. | Feature 035 telemetry audit. |
| NFR-036-03 | Telemetry events log tier selection and attribute counts without exposing raw data. | Maintain observability while keeping secrets out of telemetry sinks. | `telemetry.trace.filtered` + `telemetry.trace.invalid_tier` contain only metadata fields. | Application telemetry adapters. | Constitution Principle 4. |

## UI / Interaction Mock-ups
No UI surfaces change in this feature; tier toggles remain invisible until the follow-up UX feature exposes them. Verbose trace docks continue to display whichever tier is configured by CLI/REST settings.

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-036-01 | Core helper defines tiers, enforces comparisons, and rejects untagged attributes. |
| S-036-02 | Application builders for HOTP, TOTP, OCRA, and FIDO2 tag attributes with minimum tiers and filter correctly. |
| S-036-03 | CLI, REST, and UI facades expose tier-aware payloads, snapshots, and fixtures. |
| S-036-04 | Documentation (spec/plan/how-tos/knowledge map) captures tier behaviour and UX dependencies. |
| S-036-05 | Quality and telemetry gates run tier-aware commands and stay green. |

## Test Strategy
- **Core:** `TraceTierFilterTest`, mutation tests for tier comparisons and invalid tier errors (S-036-01).
- **Application:** HOTP/TOTP/OCRA/FIDO2 builder suites verifying tag completeness and tier outputs (S-036-02).
- **REST:** MockMvc suites ensuring `?tier=` overrides responses and OpenAPI snapshots stay deterministic (S-036-03).
- **CLI:** Picocli tests verifying `--verbose-tier` behaviour, exit codes, and text output parity (S-036-03).
- **UI (JS/Selenium):** Verbose dock snapshot plus Selenium assertion that JSON payload matches requested tier even without UI toggle controls (S-036-03).
- **Docs/Contracts:** Roadmap, knowledge map, migration plan, and how-to diffs recorded alongside telemetry snapshots (S-036-04/S-036-05).

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-036-01 | `TraceTier` enum (`normal`, `educational`, `lab_secrets`) with comparison helpers and parsing logic. | core |
| DO-036-02 | `TraceAttribute` record containing `id`, `value`, `minimumTier`, masking hints. | core, application |
| DO-036-03 | `TieredTracePayload` DTO returned to REST/CLI/UI facades. | application, rest-api, cli, ui |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-036-01 | REST verbose trace endpoints (`/api/v1/*/trace`) | Accept optional `tier` query param, defaulting to `educational`. | Returns `TieredTracePayload` JSON. |
| API-036-02 | Application service `TraceTierService` | Central helper filtering builder attributes and emitting telemetry. | Used by all protocol builders. |

### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-036-01 | `hotp evaluate --verbose-tier=<tier>` | Applies tier filter before printing JSON/text traces; invalid tiers return error code 3. |
| CLI-036-02 | `fido2 replay --verbose-tier=<tier>` | Mirrors REST tier selection and masks secrets accordingly. |

### Telemetry Events
| ID | Event name | Fields / Redaction summary |
|----|------------|----------------------------|
| TE-036-01 | `telemetry.trace.filtered` | `protocol`, `tier`, `attributeCount`, `maskedCount`; no raw values emitted. |
| TE-036-02 | `telemetry.trace.invalid_tier` | Captures tier name, protocol, request source, and sanitised reason. |

### Fixtures & Sample Data
| ID | Path | Description |
|----|------|-------------|
| FX-036-01 | `docs/test-vectors/trace-tiers/hotp/*.json` | Canonical tier matrices for HOTP/TOTP. |
| FX-036-02 | `docs/test-vectors/trace-tiers/fido2/*.json` | Fixture payloads validating COSE metadata handling. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|----------------------------|
| UI-036-01 | Verbose trace dock (normal tier) | CLI/REST/UI default; shows high-level metadata only. |
| UI-036-02 | Verbose trace dock (educational tier) | Developer toggle or config; exposes diagnostic data while masking secrets. |
| UI-036-03 | Verbose trace dock (lab-secrets placeholder) | Disabled toggle pending UX feature; documented for future rollout. |

## Telemetry & Observability
- `telemetry.trace.filtered` records protocol, tier, totals, and source facade for every verbose trace response.
- `telemetry.trace.invalid_tier` fires when a client selects an unknown tier or omits required tags, capturing sanitised metadata to aid debugging without leaking secrets.
- Application loggers must not emit raw attribute values; helper enforces masking before telemetry events are created.

## Documentation Deliverables
- Update `docs/4-architecture/roadmap.md` and `docs/migration_plan.md` with the tier-helper migration notes.
- Refresh `docs/4-architecture/knowledge-map.md` to reference the shared tier helper and facade propagation lines.
- Capture session progress inside `docs/_current-session.md` and link to verification commands in Feature 036 tasks.
- Extend operator how-to guides (`docs/2-how-to/*.md`) with tier selection instructions once CLI/REST flags ship.

## Fixtures & Sample Data
- Seed tier matrices for HOTP/TOTP and FIDO2 under `docs/test-vectors/trace-tiers/**`; ensure builders/tests treat them as canonical.
- Mirror equivalent fixtures under `rest-api/docs/test-vectors/**` when REST snapshots require parity.

## Spec DSL
```yaml
domain_objects:
  - id: DO-036-01
    name: TraceTier
    values: [normal, educational, lab_secrets]
  - id: DO-036-02
    name: TraceAttribute
    fields:
      - name: id
        type: string
      - name: value
        type: any
      - name: minimumTier
        type: TraceTier
      - name: maskingHint
        type: enum(mask, hash, passthrough)
  - id: DO-036-03
    name: TieredTracePayload
    fields:
      - name: protocol
        type: enum(hotp, totp, ocra, fido2)
      - name: tier
        type: TraceTier
      - name: attributes
        type: list<TraceAttribute>
routes:
  - id: API-036-01
    method: GET
    path: /api/v1/{protocol}/trace
    query:
      - name: tier
        type: TraceTier
        default: educational
    response: TieredTracePayload
services:
  - id: API-036-02
    name: TraceTierService
    operations:
      - name: filterAttributes
        params: [requestedTier, attributes]
        returns: list<TraceAttribute>
cli_commands:
  - id: CLI-036-01
    command: hotp evaluate --verbose-tier=<tier>
  - id: CLI-036-02
    command: fido2 replay --verbose-tier=<tier>
telemetry_events:
  - id: TE-036-01
    event: telemetry.trace.filtered
    fields: [protocol, tier, attributeCount, maskedCount, source]
  - id: TE-036-02
    event: telemetry.trace.invalid_tier
    fields: [protocol, tier, source, reason]
fixtures:
  - id: FX-036-01
    path: docs/test-vectors/trace-tiers/hotp/*.json
  - id: FX-036-02
    path: docs/test-vectors/trace-tiers/fido2/*.json
ui_states:
  - id: UI-036-01
    description: Verbose trace dock renders normal tier payload and masks secrets.
  - id: UI-036-02
    description: Verbose trace dock renders educational tier payload with diagnostics.
```

## Appendix
### Tier Attribute Matrix (Initial Draft)
- **HOTP/TOTP**
  - `normal`: outcome OTP (masked), algorithm names, counter values, drift window metadata.
  - `educational`: adds dynamic truncation bytes, HMAC digest slices, intermediate buffers.
  - `lab-secrets`: optional experimental diagnostics (alternate truncation strategies).
- **OCRA**
  - `normal`: request metadata, suite parameters, validation decisions.
  - `educational`: message assembly segments, per-segment hex dumps, dynamic truncation fields.
  - `lab-secrets`: extra instrumentation (alternate challenge encodings, suite comparisons).
- **FIDO2**
  - `normal`: relying-party identifiers, signature counters, COSE public-key metadata, verification verdicts.
  - `educational`: authentication/attestation buffer dumps, hashed secrets, COSE hex payloads.
  - `lab-secrets`: extended previews (signed byte windows) for lab analysis.
```
