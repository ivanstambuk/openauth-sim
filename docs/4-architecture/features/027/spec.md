# Feature 027 – Unified Credential Store Naming

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/027/plan.md` |
| Linked tasks | `docs/4-architecture/features/027/tasks.md` |
| Roadmap entry | #27 – Shared credential store hygiene |

## Overview
Align every facade (REST, CLI, operator UI) on the same default MapDB credential-store filename so multi-protocol
operators can share persisted secrets without tweaking configuration flags. The change retires protocol-specific defaults
(e.g., `ocra-credentials.db`) and removes fallback detection logic so the simulator either uses the inclusive default
`credentials.db` or an explicit operator-supplied path.

## Clarifications
- 2025-10-18 – Adopt the inclusive filename `credentials.db` as the shared default for all facades (owner directive).
- 2025-10-18 – Update CLI defaults alongside REST/UI so every facade targets the shared file (owner confirmed Option A).
- 2025-10-19 – Drop automatic detection of legacy filenames; future releases require either the shared default or an
  explicit override (owner directive).

## Goals
- G-027-01 – Standardise the default MapDB path across persistence adapters, CLI commands, and REST configuration.
- G-027-02 – Remove legacy filename probing so credential selection is deterministic.
- G-027-03 – Document the migration path (rename legacy files or pass explicit paths) and update knowledge artefacts.

## Non-Goals
- N-027-01 – Modifying MapDB schemas, cache settings, or introducing new persistence backends.
- N-027-02 – Automatically renaming legacy files on behalf of operators.
- N-027-03 – Adding encryption or new telemetry events to the persistence layer.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-027-01 | Governance artefacts (spec/plan/tasks, roadmap, knowledge map, how-to guides) declare `credentials.db` as the shared default. | Docs and runbooks mention only the unified filename and describe manual migration guidance. | Documentation review and CI link checks confirm references were updated. | Any doc/plan references legacy filenames or fallback behaviour. | No new telemetry; roadmap + knowledge map entries log the decision. | Clarifications 2025-10-18. |
| FR-027-02 | `CredentialStoreFactory` (and adapters) always default to `credentials.db` when no explicit path is provided. | Factory returns the unified filename, logs only explicit overrides, and unit tests assert deterministic behaviour. | `CredentialStoreFactoryTest` and ArchUnit checks ensure no legacy filename probes exist. | Any implicit fallback occurs or tests still reference legacy paths. | Existing persistence logs note whether a custom path is supplied; no telemetry change. | Clarifications 2025-10-19. |
| FR-027-03 | CLI/REST/operator UI defaults, help text, and regression suites reference the unified filename. | CLI help/flags, REST config, Selenium tests, and scripts all point to `credentials.db`. | CLI + REST tests inspect resolved defaults; Selenium flows confirm the UI copy. | Facades reference protocol-specific filenames or fail to load shared credentials. | Telemetry remains unchanged; logs indicate the resolved path once at startup. | G-027-01. |
| FR-027-04 | Operators receive clear manual-migration guidance (rename legacy files or set explicit paths). | How-to guides, release notes, and knowledge map entries describe the manual process and warning language. | Docs linting plus review of `docs/2-how-to/configure-persistence-profiles.md` ensures guidance is present. | Lack of documentation or conflicting instructions about automatic migration. | None (documentation only). | G-027-03. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-027-01 | Deterministic default selection with zero legacy probing. | Predictable persistence for operators and automation. | `CredentialStoreFactoryTest` asserts the returned path equals `credentials.db`. | `infra-persistence`, ArchUnit rules. | Clarifications 2025-10-19. |
| NFR-027-02 | Log/telemetry clarity when explicit overrides are used (no noise for defaults). | Troubleshooting clarity. | Integration tests assert structured log lines only appear when overrides are set. | `CredentialStoreFactory`, logging config. | G-027-02. |
| NFR-027-03 | Documentation parity across roadmap/how-to/session snapshot. | Governance traceability. | Docs review ensures the unified filename appears everywhere and open questions remain empty. | docs/ hierarchy. | G-027-03. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-027-01 | Governance artefacts capture the unified `credentials.db` decision with no remaining legacy references. |
| S-027-02 | `CredentialStoreFactory` and persistence helpers always default to `credentials.db` with updated tests/logging. |
| S-027-03 | CLI/REST/UI layers, tests, and Selenium coverage adopt the unified default and pass regression builds. |
| S-027-04 | Documentation/how-to guides describe manual migration steps and warn that legacy filenames require explicit overrides. |

## Test Strategy
- **Core/Infra:** `CredentialStoreFactoryTest` ensures default selection is deterministic and no legacy probes exist.
- **Application/REST:** Configuration tests confirm REST wiring references the shared filename and that explicit override logging occurs only when necessary.
- **CLI:** Picocli tests verify help text and default path resolution.
- **UI/Selenium:** Operator console tests rely on REST defaults; no UI layout changes required.
- **Docs/Contracts:** Linting + manual review of roadmap, knowledge map, and how-to guides ensure documentation parity.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-027-01 | `CredentialStoreFactory` path resolution result (`path`, `isExplicitOverride`). | infra-persistence, application |

### Telemetry / Logging
| ID | Event name | Fields / Notes |
|----|-----------|----------------|
| TE-027-01 | `persistence.defaultPathSelected` (log) | Emitted only when an explicit override is provided; fields include `path`, `explicit=true/false`. |

## Documentation Deliverables
- Roadmap entry #27, knowledge map, and how-to guides reference `credentials.db` exclusively.
- Release notes and migration guides highlight the manual rename/override requirement.

## Fixtures & Sample Data
- Not applicable; no new fixtures required.

## Spec DSL
```
scenarios:
  - id: S-027-01
    focus: governance-docs
  - id: S-027-02
    focus: credential-store-factory
  - id: S-027-03
    focus: cli-rest-defaults
  - id: S-027-04
    focus: documentation-migration
requirements:
  - id: FR-027-01
    maps_to: [S-027-01]
  - id: FR-027-02
    maps_to: [S-027-02]
  - id: FR-027-03
    maps_to: [S-027-03]
  - id: FR-027-04
    maps_to: [S-027-04]
telemetry:
  - id: TE-027-01
    event: persistence.defaultPathSelected
    fields:
      - path
      - explicit
```
