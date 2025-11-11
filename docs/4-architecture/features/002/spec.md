# Feature 002 – Persistence & Caching Hardening

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/002/plan.md` |
| Linked tasks | `docs/4-architecture/features/002/tasks.md` |
| Roadmap entry | #2 |

## Overview
Elevate the persistence layer backing the shared `CredentialStore` so it reliably supports ultra-low latency and high-throughput workloads (≥10,000 RPS) across local, embedded, and containerised deployments. The work tunes MapDB + Caffeine cache profiles, adds targeted observability, introduces optional at-rest protection, and exposes maintenance helpers while retaining the existing `CredentialStore` abstraction for every facade.

## Clarifications
- 2025-09-28 – Prioritise improvements in the order: instrumentation/metrics → cache tuning → storage hygiene → optional encryption so performance validation can happen incrementally.
- 2025-09-28 – Deployment scope covers in-memory/embedded tests, local disk developer installs, and container/volume-backed runtimes with documented override knobs per profile.
- 2025-09-28 – Performance target: sustain ≥10,000 read-heavy requests per second with cache-hit P99 ≤5 ms and MapDB-hit P99 ≤15 ms.
- 2025-09-28 – API surface: stay within the existing `CredentialStore` abstraction; no downstream module should see a breaking change.
- 2025-09-28 – Auditing & redaction: enforce secret redaction in logs/metrics and continue honouring log-level driven auditing behaviour.
- 2025-09-28 – Telemetry contract (T202): emit Level.FINE `persistence.credential.lookup` and `persistence.credential.mutation` frames with `storeProfile`, `credentialNameHash`, `cacheHit`, `source`, `latencyMicros`, `operation`, and `redacted=true`; never include raw secrets.
- 2025-09-28 – Cache tuning (T203): Default Caffeine settings—`IN_MEMORY` (max 250k entries, expire-after-access 2 min), `FILE` (max 150k, expire-after-write 10 min), `CONTAINER` (max 500k, expire-after-access 15 min)—with builder overrides for advanced scenarios.
- 2025-09-28 – Maintenance helper (T205–T206): Provide synchronous compaction/integrity operations via `MapDbCredentialStore.Builder` plus CLI entry points so operators can trigger maintenance without downtime.
- 2025-09-28 – Encryption scope (T207): Offer AES-GCM based at-rest protection through a `PersistenceEncryption` interface that callers can supply keys to; default configuration remains plaintext until enabled.

## Goals
- Harden MapDB + Caffeine for simulator-sized datasets via curated cache profiles, maintenance hooks, and optional encryption.
- Provide structured telemetry/metrics so persistence behaviour is observable from CLI/REST/UI facades without leaking secrets.
- Document configuration guidance, maintenance commands, and benchmark steps for operators and future contributors.

## Non-Goals
- Replacing MapDB with another storage engine.
- Changing higher-level credential APIs or introducing new persistence abstractions.
- Delivering REST/UI maintenance endpoints (tracked by downstream features once persistence stabilises).

## Functional Requirements

### FR1 – Deployment profiles & cache tuning (FR-201, S02-01)
- **Requirement:** Provide curated configuration profiles for in-memory, file-backed, and container deployments (cache sizes, eviction policies, MapDB knobs) plus documented override hooks.
- **Success path:** Each profile boots with no manual tuning, loads sample credentials, and keeps hit ratios above 90% in smoke tests.
- **Validation path:** Builder rejects invalid overrides and logs redacted diagnostics when misconfigured.
- **Failure path:** Missing or conflicting profile settings raise structured exceptions before MapDB opens.
- **Telemetry & traces:** `persistence.cache.profile` log/trace entries capture `profileId`, cache settings hash, and source of overrides.
- **Source:** T203 cache tuning directive (2025-09-28).

### FR2 – Persistence telemetry & diagnostics (FR-202, S02-02)
- **Requirement:** Emit structured metrics/logs for cache hits/misses, load latency, mutations, and maintenance events without exposing secrets.
- **Success path:** `persistence.credential.lookup`/`mutation`/`maintenance` events appear in tests and include `credentialNameHash`, `storeProfile`, `latencyMicros`, and `redacted=true`.
- **Validation path:** Tests assert telemetry for success + failure branches and block logging of raw secrets.
- **Failure path:** Any telemetry emission lacking redaction fails ArchUnit/contract suites; build blocks until fixed.
- **Telemetry & traces:** Events stream through `TelemetryContracts` and verbose trace plumbing.
- **Source:** Clarification T202 + Feature plan telemetry notes.

### FR3 – Maintenance helper & CLI entry points (FR-203, S02-03)
- **Requirement:** Provide compaction/integrity operations via a builder-produced helper plus CLI commands that expose structured `MaintenanceResult` payloads.
- **Success path:** CLI and integration tests trigger compaction/integrity flows that complete without corrupting data and return result records.
- **Validation path:** Negative tests simulate partial failures and ensure status=`WARN`/`FAIL` results surface via CLI and telemetry.
- **Failure path:** If maintenance cannot start (e.g., store locked), commands exit non-zero with actionable diagnostics and no partial writes.
- **Telemetry & traces:** `persistence.maintenance` event logs `operation`, `durationMs`, `entriesScanned`, `issues`, `status`.
- **Source:** T205–T206 maintenance scope (2025-09-28).

### FR4 – Optional AES-GCM at-rest protection (FR-204, S02-04)
- **Requirement:** Introduce a `PersistenceEncryption` interface with AES-GCM provider + key callbacks while keeping plaintext as the default.
- **Success path:** Tests confirm encrypted payloads decrypt correctly when the supplied key matches; plaintext mode remains unchanged when encryption disabled.
- **Validation path:** Mismatched keys and tampered payloads raise structured errors and telemetry alerts.
- **Failure path:** Encryption failures never leak partial plaintext; store rejects writes and surfaces failure via telemetry + CLI.
- **Telemetry & traces:** `persistence.encryption` events contain `profile`, `status`, and `reasonCode` without exposing key material.
- **Source:** T207 encryption directive (2025-09-28).

### FR5 – Benchmarks & operator documentation (S02-05, S02-06)
- **Requirement:** Capture ≥10k RPS benchmarks with documented configuration and update roadmap/how-to/knowledge map entries outlining maintenance + encryption usage.
- **Success path:** Benchmark harness results recorded in the plan/tasks, and docs (`docs/2-how-to/configure-persistence-profiles.md`, roadmap, knowledge map) reflect the new capabilities.
- **Validation path:** Re-running the harness reproduces results within ±5% and doc updates link to relevant commands.
- **Failure path:** If benchmarks regress or docs drift, the feature stays open until alignment is restored.
- **Telemetry & traces:** Benchmark runs record telemetry snapshots for comparison.
- **Source:** Plan T0201–T0209 closure notes.

## Non-Functional Requirements

### NFR1 – Throughput (NFR-201)
- **Requirement:** Sustain ≥10k requests per second with ≥90% cache hit-rate under synthetic benchmarks.
- **Driver:** Prevent persistence from bottlenecking simulator protocols.
- **Measurement:** `MapDbCredentialStoreBaselineBenchmark` harness with `IO_OPENAUTH_SIM_BENCHMARK=true` and recorded ops/sec metrics.
- **Dependencies:** Benchmark harness, tuned cache profiles.
- **Source:** Objectives + T201/T208 benchmarking tasks.

### NFR2 – Latency (NFR-202)
- **Requirement:** P99 latency ≤5 ms for cache hits and ≤15 ms for MapDB reads.
- **Driver:** Maintain operator responsiveness under load.
- **Measurement:** Benchmark harness histograms.
- **Dependencies:** Caffeine eviction strategy, MapDB configuration.
- **Source:** Clarifications 2025-09-28.

### NFR3 – Observability (NFR-203)
- **Requirement:** Emit structured logs/metrics referencing credential names only (hashed) with cache + maintenance context.
- **Driver:** Enable diagnosis without sensitive data exposure.
- **Measurement:** Telemetry contract tests and manual log inspection.
- **Dependencies:** Telemetry adapters, CLI logging hooks.
- **Source:** FR2 + telemetry clarification.

### NFR4 – Security & redaction (NFR-204)
- **Requirement:** Enforce redaction of secret material across logs, telemetry, benchmarks, and maintenance outputs.
- **Driver:** Prevent accidental leakage in high-volume diagnostic flows.
- **Measurement:** Redaction unit tests + ArchUnit rules verifying no secret fields leave the encryption boundary.
- **Dependencies:** `SecretMaterial` helpers, telemetry serializers.
- **Source:** Clarifications & FR4.

## UI / Interaction Mock-ups
```
Not applicable – persistence changes are exercised via CLI commands and automated tooling only.
```

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S02-01 | Deployment profiles expose tuned cache defaults with override hooks. |
| S02-02 | Structured metrics/telemetry report cache hit/miss, latency, and maintenance events without leaking secrets. |
| S02-03 | Maintenance helper executes compaction/integrity operations and surfaces structured CLI results. |
| S02-04 | Optional at-rest protection hooks guard MapDB payloads while default config stays plaintext. |
| S02-05 | Benchmark harness demonstrates ≥10k RPS throughput with recorded P99 latencies. |
| S02-06 | Documentation captures configuration guidance, maintenance instructions, and encryption usage. |

## Test Strategy
- **Core:** `MapDbCredentialStoreTest`, `SecretMaterialCodecTest`, `MapDbCredentialStoreBaselineBenchmark`, and telemetry/maintenance unit tests validate cache profiles, encryption, and maintenance flows.
- **Application:** `CredentialStoreFactory` integration tests ensure application services continue to consume the store without API drift.
- **CLI:** `MaintenanceCliTest` covers command wiring, exit codes, and structured result output.
- **REST/UI:** No direct changes; contract and UI tests rely on the unchanged `CredentialStore` abstraction and will be updated in downstream features if maintenance endpoints are added.
- **Docs/Contracts:** How-to guides and roadmap entries capture persistence profiles, maintenance commands, and benchmark instructions; telemetry schemas documented alongside `TelemetryContracts`.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-002-01 | `MapDbCredentialStore` with profile-aware builder options and cache settings. | core, application |
| DO-002-02 | `CacheSettings` / profile descriptors enumerating capacity + eviction knobs. | core |
| DO-002-03 | `MaintenanceResult` record with `operation`, `duration`, `entriesScanned`, `entriesRepaired`, `issues`, `status`. | core, cli |
| DO-002-04 | `PersistenceEncryption` interface + AES-GCM implementation with key callbacks. | core |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-002-01 | Application service | `CredentialStoreFactory` provisioning profile-tuned stores for facades. | Exposes builder overrides but preserves `CredentialStore` interface. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-002-01 | `./bin/openauth maintenance store --operation <compact|verify>` | Invokes maintenance helper, prints `MaintenanceResult`, and propagates telemetry IDs. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-002-01 | `persistence.credential.lookup` | `storeProfile`, `credentialNameHash`, `cacheHit`, `source`, `latencyMicros`, `redacted=true`. |
| TE-002-02 | `persistence.credential.mutation` | `operation`, `storeProfile`, `latencyMicros`, `credentialNameHash`, `redacted=true`. |
| TE-002-03 | `persistence.maintenance` | `operation`, `durationMs`, `entriesScanned`, `issues[]`, `status`, `redacted=true`. |
| TE-002-04 | `persistence.encryption` | `profile`, `status`, `reasonCode`, `redacted=true`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-002-01 | `core/src/test/java/io/openauth/sim/core/store/MapDbCredentialStoreBaselineBenchmark.java` | Benchmark harness driving throughput/latency measurements. |
| FX-002-02 | `docs/2-how-to/configure-persistence-profiles.md` | Operator guidance for profiles, maintenance, and encryption usage. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| — | Not applicable | Persistence feature has no UI impact; future REST/UI work will extend this table. |

## Telemetry & Observability
Telemetry flows through `TelemetryContracts` adapters; CLI/REST/operator UI consumers reuse these events without custom loggers. Each event redacts credential names via hashing, excludes secret material, and includes profile identifiers so benchmarks can compare deployments. Verbose trace integration links maintenance commands to their telemetry IDs, enabling trace-to-log correlation.

## Documentation Deliverables
- `docs/2-how-to/configure-persistence-profiles.md` – configuration profiles, maintenance instructions, encryption guidance.
- `docs/4-architecture/knowledge-map.md` – persistence relationships between core, application, CLI.
- `docs/4-architecture/roadmap.md` – record feature completion and downstream dependency notes.
- `docs/5-operations/analysis-gate-checklist.md` – persistence telemetry expectations referenced during the gate.

## Fixtures & Sample Data
- Benchmark harness inside `core` module plus generated datasets recorded in plan/tasks.
- Maintenance helper regression data captured via CLI tests; no static secrets stored in fixtures.

## Spec DSL
```
domain_objects:
  - id: DO-002-01
    name: MapDbCredentialStore
    fields:
      - name: profile
        type: enum(IN_MEMORY, FILE, CONTAINER)
      - name: cacheSettings
        type: object
        constraints: tuned defaults + override hooks
  - id: DO-002-03
    name: MaintenanceResult
    fields:
      - name: operation
        type: enum(COMPACTION, INTEGRITY_CHECK)
      - name: status
        type: enum(SUCCESS, WARN, FAIL)
  - id: DO-002-04
    name: PersistenceEncryption
    fields:
      - name: keySupplier
        type: functional interface
routes:
  - id: API-002-01
    method: service
    path: application.persistence.CredentialStoreFactory
cli_commands:
  - id: CLI-002-01
    command: ./bin/openauth maintenance store --operation <compact|verify>
telemetry_events:
  - id: TE-002-01
    event: persistence.credential.lookup
    fields:
      - name: credentialNameHash
        redaction: hash
      - name: cacheHit
        redaction: none
  - id: TE-002-03
    event: persistence.maintenance
    fields:
      - name: status
        redaction: none
fixtures:
  - id: FX-002-01
    path: core/src/test/java/io/openauth/sim/core/store/MapDbCredentialStoreBaselineBenchmark.java
ui_states: []
```

## Appendix
- `docs/4-architecture/features/002/plan.md`
- `docs/4-architecture/features/002/tasks.md`
- `core/src/main/java/io/openauth/sim/core/store/MapDbCredentialStore.java`
