# Feature 012 – Core Cryptography & Persistence

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Linked plan | [docs/4-architecture/features/012/plan.md](docs/4-architecture/features/012/plan.md) |
| Linked tasks | [docs/4-architecture/features/012/tasks.md](docs/4-architecture/features/012/tasks.md) |
| Roadmap entry | #12 – Core Cryptography & Persistence |

## Overview
Feature 012 aggregates every shared persistence guideline for the simulator: credential-store profiles, cache tuning,
telemetry contracts, maintenance helpers, optional encryption, default filenames, and documentation of recent IDE warning
remediation. It builds on ADR-0001 (Core Credential Store Stack: MapDB plus Caffeine) and codifies the deployment profiles
(`IN_MEMORY`, `FILE`, `CONTAINER`) with their cache sizes and expiry policies, the
`persistence.credential.lookup|mutation|maintenance|profile.override` telemetry fields (including `storeProfile`,
`credentialNameHash`, `cacheHit`, `source`, `latencyMicros`, `operation`, `redacted=true`), maintenance helpers for
compaction and integrity checks via `MapDbCredentialStore.Builder` and CLI hooks, the unified `credentials.db` default with
explicit override logging, and IDE remediation steps (assertions, DTO extraction, SpotBugs annotations exported via
`compileOnlyApi`). Persistence improvements remain layered instrumentation/metrics → cache tuning → storage hygiene →
optional encryption inside the existing `CredentialStore` abstraction while sustaining the performance targets in
NFR-012-01. Facade modules consume the same storage APIs and operators rely on this specification as the single governance
source for persistence guidance.

Cross-facade conventions (Native Java/CLI/REST/UI/MCP/standalone) are centralised in
[docs/4-architecture/facade-contract-playbook.md](docs/4-architecture/facade-contract-playbook.md).

## Goals
- G-012-01 – Document the shared credential-store contract (profiles, cache settings, telemetry, maintenance helpers,
  encryption) so every module references consistent behaviour.
- G-012-02 – Standardise persistence defaults (`credentials.db`) and manual migration guidance across docs/how-tos/runbooks.
- G-012-03 – Keep persistence documentation warning-free by capturing IDE remediation steps, toolchain updates, and
  verification commands.
- G-012-04 – Maintain roadmap/knowledge-map/architecture-graph references and log verification commands in
  `_current-session.md` + session log ([docs/_current-session.md](docs/_current-session.md)) entries for every persistence documentation change.

## Non-Goals
- Shipping new persistence code, storage backends, or schema changes in this documentation-only iteration.
- Reintroducing automatic legacy-filename detection or fallback logic.
- Modifying CLI/REST/UI behaviour beyond documenting persistence defaults, maintenance helpers, and telemetry.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-012-01 | Define deployment profiles (`IN_MEMORY`, `FILE`, `CONTAINER`) with curated Caffeine/MapDB settings and documented override hooks, including reference defaults: `IN_MEMORY` (250k entries, expire-after-access 2 min), `FILE` (150k entries, expire-after-write 10 min), `CONTAINER` (500k entries, expire-after-access 15 min). | Builders expose profile enums + overrides; cache hit ratios stay >90% in smoke tests. | Unit/integration tests confirm profile settings; docs list defaults. | Profiles missing or overrides misconfigured. | `persistence.cache.profile` logs include `profileId`, overrides hash. | Spec |
| FR-012-02 | Emit structured telemetry for credential lookups/mutations/maintenance without leaking secrets (fields defined in the Telemetry & Observability section). | Telemetry events appear in tests with `redacted=true`; verbose traces reuse existing plumbing. | Contract tests assert fields + redaction; docs describe telemetry usage. | Logs leak secrets or omit fields. | `persistence.credential.lookup|mutation` events. | Spec |
| FR-012-03 | Provide maintenance helpers (compaction/integrity/CLI entry points) surfaced via `MapDbCredentialStore.Builder` and documented CLI flows. | Operators trigger maintenance via CLI/Builder without stopping the simulator; docs explain commands and expected logs. | CLI integration tests + how-to walkthrough; plan/tasks list commands. | Maintenance steps undocumented or risky. | `persistence.credential.maintenance` logs. | Spec |
| FR-012-04 | Offer optional AES-GCM at-rest encryption via `PersistenceEncryption` interface without changing default plaintext behaviour. | Enabling encryption stores redacted ciphertext while defaults remain plaintext; docs describe key management/risks. | Unit tests cover encryption on/off; documentation outlines configuration. | Encryption toggles misbehave or leak data. | Telemetry remains redacted; encryption logs note mode only. | Spec |
| FR-012-05 | Standardise the default credential-store filename (`credentials.db`) across all facades and document manual migration guidance (rename legacy files or supply explicit paths). | Factories default to `credentials.db`; CLI help/REST config/UI copy mention the shared default; docs explain manual migration steps. | `rg "credentials.db"` across docs; CLI/REST tests assert defaults; Selenium flows show updated copy. | Legacy filenames referenced or fallback logic reintroduced. | Logs only mention overrides (no noise for defaults). | Spec |
| FR-012-06 | Remove legacy filename detection and require explicit overrides for custom paths; log overrides deterministically. | `CredentialStoreFactory` rejects implicit legacy file detection; logs structured override lines with `explicit=true`. | Unit tests assert default vs override cases; docs mention `--credential-store-path`. | Silent fallback occurs or logs spam defaults. | `persistence.defaultPathSelected` log (explicit only). | Spec |
| FR-012-07 | Record IDE warning remediation steps (assertion tightening, DTO extraction, SpotBugs annotation export, transient REST exception fields) so persistence modules remain warning-free. | Docs capture remediation scope; application/core/CLI/REST/UI tests cover new assertions; SpotBugs annotations exported via `compileOnlyApi`. | `./gradlew --no-daemon :application:test :core:test :cli:test :rest-api:test :ui:test spotlessApply check`; IDE snapshot referenced in plan/tasks. | Warnings reappear or documentation omits context. | No telemetry change. | Spec |
| FR-012-08 | Log every persistence documentation update (spec/plan/tasks, roadmap, knowledge map, architecture graph, session log ([docs/_current-session.md](docs/_current-session.md))) and list executed commands in `_current-session.md`. | Session logs mention commands (`rg`, `spotlessApply`, doc edits); open questions stay empty. | Manual review before closing tasks. | Auditors cannot trace persistence doc changes. | None. | Spec |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-012-01 | Performance: sustain ≥10 000 read-heavy RPS with cache-hit P99 ≤5 ms / MapDB-hit P99 ≤15 ms under documented profiles. | Persistence SLA | Benchmark notes stored in plan/tasks; regression tests reference targets. | `MapDbCredentialStore`, Caffeine caches. | Spec |
| NFR-012-02 | Observability: telemetry + logs must redact secrets and remain consistent across facades. | Security/compliance | Contract tests enforce `redacted=true`; docs describe telemetry consumption. | TelemetryContracts, verbose traces. | Spec |
| NFR-012-03 | Deterministic defaults: `credentials.db` remains the only implicit filename; overrides logged once per boot. | Operator predictability | Tests compare resolved path; docs mention manual migrations. | `CredentialStoreFactory`, CLI/REST config. | Spec |
| NFR-012-04 | IDE hygiene: persistence-related modules stay warning-free and SpotBugs annotations compile everywhere without extra dependencies. | Developer experience | IDE inspection snapshots + regression gate results recorded in plan/tasks. | application, core, cli, rest-api, ui modules. | Spec |
| NFR-012-05 | Documentation traceability: roadmap/knowledge-map/architecture-graph entries reference Feature 012; `_current-session.md` logs verification commands. | Governance | Doc review + session snapshot before closing tasks. | docs hierarchy. | Spec |
| NFR-012-06 | Facade seam enforcement: Facade modules (CLI/REST/UI/MCP/standalone/Native Java) must obtain persistence exclusively via `CredentialStoreFactory` and delegate through application services rather than touching `core` persistence internals or constructing `MapDbCredentialStore` directly. | ArchUnit facade-boundary tests and contract tests prevent direct `core`/persistence usage. | application, infra-persistence, facades. | Spec. |

## UI / Interaction Mock-ups

### Persistence Profile Selector (UI-012-01)
```
┌────────────── Persistence Profiles ──────────────┐
│ Active profile: [ FILE ▼ ]                       │
│ Description  : File-backed MapDB (150k entries)  │
│ Cache config : expire-after-write 10m            │
│                                                  │
│ Profile cards                                   │
│  ┌──────────┬──────────┬──────────┐             │
│  │IN_MEMORY │   FILE   │ CONTAINER│             │
│  │hits 92%  │hits 95%  │hits 97%  │             │
│  │cache 250k│cache 150k│cache 500k│             │
│  └──────────┴──────────┴──────────┘             │
│ Overrides                                        │
│  Path         [ data/credentials.db        ]     │
│  Encryption   (•) Off  ( ) AES-GCM              │
│  Buttons: [Save overrides] [Reset to default]   │
└──────────────────────────────────────────────────┘
```

### Maintenance Helper Logs (UI-012-02)
```
┌────────────── Maintenance Helper ────────────────┐
│ Operation: [ Compact ▼ ]   Profile: FILE        │
│ Status   : [ RUNNING ]                          │
│                                                  │
│ Log stream (auto-scroll)                         │
│  12:30:01 start compact data/credentials.db      │
│  12:30:04 flushing cache window (30k entries)    │
│  12:30:10 compaction chunk 2/3 (progress bar)    │
│  12:30:14 finished; duration 13.2s               │
│  12:30:14 telemetry id → persistence.credential… │
│                                                  │
│ Footer: [Download log] [Copy telemetry id]       │
└──────────────────────────────────────────────────┘
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-012-01 | Deployment profiles + cache tuning documented and exercised via tests. |
| S-012-02 | Telemetry/observability (lookup, mutation, maintenance) remains redacted and consistent. |
| S-012-03 | Maintenance helpers + CLI commands operate without downtime and are documented. |
| S-012-04 | Optional encryption toggles documented and tested. |
| S-012-05 | Unified `credentials.db` default adopted across factories, CLI/REST/UI, and documentation. |
| S-012-06 | Manual migration guidance explains renaming legacy files or providing explicit paths. |
| S-012-07 | IDE warning remediation recorded; tests/assertions strengthened without regressions. |
| S-012-08 | Roadmap/knowledge map/session log ([docs/_current-session.md](docs/_current-session.md))/session logs capture persistence doc updates + commands. |

## Test Strategy
- **Benchmarks/Profiles:** Reference existing performance notes when running persistence benchmarks; ensure cache-hit metrics
  meet NFR targets.
- **Telemetry:** Contract tests for `persistence.credential.lookup|mutation|maintenance` covering success/failure branches and
  redaction rules.
- **Maintenance helpers:** CLI/Builder integration tests executing compaction/integrity operations, verifying logs.
- **Encryption toggle:** Unit/integration tests enabling/disabling `PersistenceEncryption` and ensuring plaintext defaults.
- **Defaults:** CLI/REST/UI regression suites assert `credentials.db` usage and override logging.
- **IDE remediation:** Regression gate (`./gradlew --no-daemon :infra-persistence:test :application:test :cli:test :rest-api:test :ui:test spotlessApply check` plus `./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreTest*"` for encryption coverage) keeps warnings at zero; plan/tasks log command output.
- **Documentation:** `./gradlew --no-daemon spotlessApply check` after doc edits; `_current-session.md` records commands per increment.

## Interface & Contract Catalogue
### Domain Objects / Builders
| ID | Description | Modules |
|----|-------------|---------|
| DO-012-01 | `CredentialStoreProfile` enum + builder overrides (cache sizes, eviction rules, MapDB options). | infra-persistence, application |
| DO-012-02 | `PersistenceTelemetryEvent` structure for lookup/mutation/maintenance operations. | application, telemetry adapters |
| DO-012-03 | `MaintenanceCommand` CLI wiring (compact, verify). | cli, rest-api (future) |
| DO-012-04 | `PersistenceEncryption` interface (enable/disable AES-GCM at-rest). | infra-persistence |

### API Routes / Services
| ID | Method | Path | Description |
|----|--------|------|-------------|
| API-012-01 | GET | `/api/v1/persistence/profiles` | Lists deployment profiles, cache defaults, and override knobs for UI/CLI consumers. |
| API-012-02 | POST | `/api/v1/persistence/cache/compact` | Triggers shared compaction helper; logs telemetry/maintenance events. |
| API-012-03 | POST | `/api/v1/persistence/cache/verify` | Runs integrity checks, reports status codes, and emits maintenance telemetry. |
| API-012-04 | GET | `/api/v1/persistence/credentials/default-path` | Returns the canonical `credentials.db` filename + override guidelines for docs. |

### CLI / Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-012-01 | `./gradlew --no-daemon :cli:run --args="maintenance compact"` (or equivalent helper script) | Triggers compaction via shared maintenance helper.
| CLI-012-02 | `./gradlew --no-daemon :cli:run --args="maintenance verify"` | Runs integrity check; logs structured result.
| CLI-012-03 | `./gradlew --no-daemon spotlessApply check` / `:infra-persistence:test` / `:core:test --tests "*MapDbCredentialStoreTest*"` | Verification commands recorded after doc updates.

### Telemetry Events
| ID | Event | Fields / Notes |
|----|-------|----------------|
| TE-012-01 | `persistence.credential.lookup` | `storeProfile`, `credentialNameHash`, `cacheHit`, `source`, `latencyMicros`, `redacted=true`. |
| TE-012-02 | `persistence.credential.mutation` | Same fields plus `operation` (insert/update/delete). |
| TE-012-03 | `persistence.credential.maintenance` | `operation` (compact/verify), `durationMicros`, `result`, `storeProfile`. |
| TE-012-04 | `persistence.credential.profile.override` | Fired when `credentials.db` path or overrides change; includes `oldPath`, `newPath`, `owner`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-012-01 | `docs/test-vectors/ocra/` + credential-store snapshots | Used for cache tuning + maintenance smoke tests.
| FX-012-02 | [data/credentials.db](data/credentials.db) | Unified default path referenced in docs/tests.
| FX-012-03 | Performance benchmark notes (appendix referenced from plan) | Record throughput/P99 measurements.

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-012-01 | Persistence profile selector open | Admin UI, CLI docs, or README shows profile cards with cache/default descriptions (triggered via `/persistence/profiles`). |
| UI-012-02 | Maintenance helper logs streaming | When `/persistence/cache/{compact,verify}` APIs run, UI/CLI output shows telemetry fields and allow download of reports. |

## Telemetry & Observability

Persistence telemetry uses the `TelemetryContracts` adapters so CLI/REST/UI logging stays consistent and secrets stay redacted:
- `persistence.credential.lookup`/`mutation` events always include `storeProfile`, `credentialNameHash`, `cacheHit`, `source`, `latencyMicros`, and `redacted=true` (plus `operation` for mutations); viewers compare them against benchmark targets and guard approver notes.
- Maintenance helpers emit `persistence.credential.maintenance` (operation + duration + result) and `persistence.credential.profile.override` when `credentials.db` defaults change, ensuring audit trails for CLI/REST/Docs actions.
- Telemetry contract tests (Core/Application) assert the fields, redaction flags, and optional `operation`/`result` combinations for success/failure branches before docs update; `docs/2-how-to` references these telemetry contracts to guide operator expectations.

## Documentation Deliverables
- Roadmap, knowledge map, and architecture graph mention Feature 012 as the persistence/polishing owner.
- How-to guides ([docs/2-how-to/configure-persistence-profiles.md](docs/2-how-to/configure-persistence-profiles.md), credential store maintenance docs) describe profiles,
  defaults, maintenance commands, encryption toggles, and manual migration steps.
- `_current-session.md` logs each documentation change + verification command.

## Fixtures & Sample Data
- [data/credentials.db](data/credentials.db) is the canonical default and appears in docs/test updates plus CLI/REST smoke tests.
- `docs/test-vectors/ocra/` credential-store snapshots power cache tuning and maintenance regression suites, ensuring consistent fixture states.
- Performance benchmark notes (see Feature 012 plan appendix) capture throughput/P99 metrics referenced by the documentation.

## Spec DSL
```
domain_objects:
  - id: DO-012-01
    name: CredentialStoreProfile
    fields:
      - name: id
        type: enum[IN_MEMORY, FILE, CONTAINER]
      - name: config
        type: map
  - id: DO-012-02
    name: PersistenceTelemetryEvent
    fields:
      - name: type
        type: enum[lookup,mutation,maintenance]
      - name: storeProfile
        type: string
      - name: credentialNameHash
        type: string
      - name: latencyMicros
        type: long
      - name: redacted
        type: boolean
  - id: DO-012-03
    name: MaintenanceCommand
    fields:
      - name: operation
        type: enum[compact,verify]
      - name: args
        type: map
  - id: DO-012-04
    name: PersistenceEncryptionConfig
    fields:
      - name: enabled
        type: boolean
      - name: algorithm
        type: string
        default: AES-GCM
cli_commands:
  - id: CLI-012-01
    command: gradlew cli:run --args="maintenance compact"
  - id: CLI-012-02
    command: gradlew cli:run --args="maintenance verify"
  - id: CLI-012-03
    command: gradlew :cli:run --args="persistence profiles"
routes:
  - id: API-012-01
    method: GET
    path: /api/v1/persistence/profiles
  - id: API-012-02
    method: POST
    path: /api/v1/persistence/cache/compact
  - id: API-012-03
    method: POST
    path: /api/v1/persistence/cache/verify
  - id: API-012-04
    method: GET
    path: /api/v1/persistence/credentials/default-path
telemetry_events:
  - id: TE-012-01
    event: persistence.credential.lookup
  - id: TE-012-02
    event: persistence.credential.mutation
  - id: TE-012-03
    event: persistence.credential.maintenance
  - id: TE-012-04
    event: persistence.credential.profile.override
fixtures:
  - id: FX-012-01
    path: data/credentials.db
  - id: FX-012-02
    path: docs/test-vectors/ocra/
  - id: FX-012-03
    path: docs/4-architecture/features/012/plan.md
ui_states:
  - id: UI-012-01
    description: Persistence profile selector open.
  - id: UI-012-02
    description: Maintenance helper logs streaming.
```
