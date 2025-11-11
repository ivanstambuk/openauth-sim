# Feature 018 – OCRA Migration Retirement

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/018/plan.md` |
| Linked tasks | `docs/4-architecture/features/018/tasks.md` |
| Roadmap entry | #9 – Operator Console Infrastructure |

## Overview
Retire the deprecated OCRA schema-v0 → schema-v1 upgrade layer so the persistence baseline always assumes schema-v1
records. The goal is to delete legacy migration helpers/tests, keep `OcraStoreMigrations.apply` as the singular entry
point façades call before opening MapDB stores, and document that only schema-v1 data is supported going forward.

## Clarifications
- 2025-10-03 – No shared or production MapDB stores remain on schema-v0; all environments may assume schema-v1 without
  data loss (owner confirmation).
- 2025-10-03 – `OcraStoreMigrations.apply` stays as the single hook façades/CLI/REST use even though it now only enforces
  schema-v1 builders (owner directive).
- 2025-10-03 – Documentation (knowledge map, how-to guides, roadmap) must explicitly state that schema-v1 is the baseline
  and that no automated migrations run in the simulator (owner directive).

## Goals
- G-018-01 – Delete the schema-v0 migration implementation/tests while keeping persistence wiring consistent.
- G-018-02 – Preserve the `OcraStoreMigrations.apply` seam so façades continue to construct builders the same way.
- G-018-03 – Update docs/knowledge map/runbooks to reflect the schema-v1-only baseline.

## Non-Goals
- N-018-01 – Add new persistence schemas or version upgrades.
- N-018-02 – Build tooling to migrate external MapDB files outside this repository.
- N-018-03 – Introduce new telemetry events; existing `ocra.*` frames already capture persistence actions indirectly.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-018-01 | Remove `OcraRecordSchemaV0ToV1Migration` and any schema-v0 helpers while keeping `OcraStoreMigrations` compiling. | `core-ocra` builds without referencing deleted classes; `OcraStoreMigrations.apply` remains callable. | Negative tests assert schema-v0 resources are gone; compilation fails if files referenced. | Build fails if lingering imports remain or MapDB cannot open. | Existing `ocra.*` telemetry unaffected; no new events. | Clarifications 2025-10-03. |
| FR-018-02 | Ensure persistence factories/CLI/REST/UI still invoke `OcraStoreMigrations.apply` prior to opening stores so the seam remains centralised. | Integration tests (CLI/REST/UI) open stores successfully using schema-v1; no call sites removed. | Static analysis ensures builder wiring still routes through `apply`. | Missing invocation causes tests to fail due to uninitialised store settings. | `ocra.evaluate/verify` telemetry implicitly cover flows. | Clarifications 2025-10-03. |
| FR-018-03 | Update knowledge map, how-to guides, and roadmap/plan notes to state schema-v1 is the baseline and migrations no longer exist. | Docs mention schema-v1 baseline and removal of v0 path. | Spell-check/lint ensures no stale references. | Docs referencing schema-v0 trigger review findings. | Documentation change log tracks updates. | Clarifications 2025-10-03. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-018-01 | Preserve build safety by running `./gradlew spotlessApply check` after each increment. | Constitution Principle 3 mandates green gates. | Build passes in CI and locally after migration removal. | Gradle tooling (+ Spotless, tests). | Constitution. |
| NFR-018-02 | Maintain documentation traceability for persistence assumptions. | Future maintainers must know schema baseline. | Knowledge map + how-to diffs mention schema-v1 baseline. | Docs repo + runbooks. | Owner directive 2025-10-03. |

## UI / Interaction Mock-ups
_Not applicable – no UI changes._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-018-01 | Legacy migration classes/tests removed; schema-v1 baseline enforced. |
| S-018-02 | Persistence factories/CLI/REST continue calling `OcraStoreMigrations.apply` without behavioural change. |
| S-018-03 | Documentation highlights schema-v1 baseline and lack of migrations.

## Test Strategy
- **Core:** `OcraStoreMigrationsTest` and related persistence tests ensure builders succeed without schema-v0 helpers.
- **Application:** Regression relies on `OcraSeedApplicationServiceTest` / other persistence consumers to verify store wiring.
- **REST/CLI/UI:** Existing integration tests opening MapDB stores indirectly confirm the seam remains; no new suites required.
- **Docs/Contracts:** Run spell-check/lint (via `spotlessApply`) when updating documentation.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-018-01 | `OcraStoreMigrations` encapsulates builder configuration hooks for schema-v1 stores. | core-ocra |
| DO-018-02 | `OcraCredentialStoreFactory` (and façade-specific factories) invoke `OcraStoreMigrations.apply` before opening stores. | core-ocra, application, rest-api, cli |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| — | — | No external routes changed; persistence seam is internal. | |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-018-01 | `./bin/openauth maintenance ocra --list` (and related CLI commands) | Continue to load stores through the unified seam; no user-visible changes. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| — | — | No new telemetry events introduced. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| — | — | No new fixtures required; existing schema-v1 samples remain valid. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| — | — | No UI states affected. |

## Telemetry & Observability
- No telemetry changes; existing `ocra.evaluate`, `ocra.verify`, and seed events already cover persistence flows.
- Monitor build logs for accidental references to removed classes; compiler errors act as the guardrail.

## Documentation Deliverables
- Update `docs/4-architecture/knowledge-map.md` and related how-to guides to emphasise schema-v1 baseline.
- Note the change in `docs/4-architecture/roadmap.md` or feature summary tables as applicable.

## Fixtures & Sample Data
No new fixtures required; existing schema-v1 fixtures remain in use.

## Spec DSL
```
domain_objects:
  - id: DO-018-01
    name: OcraStoreMigrations
    fields:
      - name: builder
        type: MapDbBuilder
        constraints: "must configure schema-v1"
  - id: DO-018-02
    name: OcraCredentialStoreFactory
    fields:
      - name: applyMigrations
        type: function
        constraints: "must call OcraStoreMigrations.apply"
routes: []
cli_commands:
  - id: CLI-018-01
    command: ./bin/openauth maintenance ocra --list
telemetry_events: []
fixtures: []
ui_states: []
```

## Appendix
None.
