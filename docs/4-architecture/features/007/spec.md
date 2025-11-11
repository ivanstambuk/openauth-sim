# Feature 007 – Operator-Facing Documentation Suite

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/007/plan.md` |
| Linked tasks | `docs/4-architecture/features/007/tasks.md` |
| Roadmap entry | #7 |

## Overview
Operators need a consolidated, human-friendly guide that explains how to work with the OpenAuth Simulator without reading internal architecture notes or code. Feature 007 delivers a documentation suite that teaches operators how to drive the simulator through external Java applications, the command-line interface, and the REST API while keeping `README.md` aligned with shipped capabilities. The effort restructures existing how-to content so operators can complete common OATH-OCRA tasks end to end (generate OTPs, replay assertions, audit stored credentials) with deterministic, copy-ready instructions.

## Clarifications
- 2025-09-30 – Java-focused documentation targets operators integrating external applications, demonstrating how to invoke simulator APIs for OTP/assertion generation and replay scenarios via `OcraCredentialFactory` + `OcraResponseCalculator`; it is not a developer API reference.
- 2025-09-30 – CLI coverage must describe every available command (`import`, `list`, `delete`, `evaluate`, `maintenance compact`, `maintenance verify`) in a single operator-friendly guide stored under `docs/`.
- 2025-09-30 – REST coverage will be a brand-new human-friendly guide that incorporates and supersedes `docs/2-how-to/use-ocra-evaluation-endpoint.md`, documenting all REST endpoints and operations, not just evaluation, plus Swagger UI usage.
- 2025-09-30 – README updates must reflect only the current feature set; scrub or relocate language about future/planned work when adding the Swagger UI link.
- 2025-09-30 – Java operator guide will showcase simulator-provided utilities or REST/CLI bridges; lower-level descriptor factories remain out of scope for operators.

## Goals
- Produce operator-facing documentation explaining how to run CLI, REST, and Java flows for currently shipped OCRA capabilities.
- Ensure guides capture telemetry expectations, preset seeding, credential storage defaults, and trace toggles so operators can troubleshoot without digging through code.

## Non-Goals
- Change runtime behaviour or add new simulator endpoints/commands.
- Document protocols beyond the OATH-OCRA scope covered here.
- Automate documentation publishing or introduce static-site tooling.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-007-01 | Provide a Java operator integration guide (`docs/2-how-to/use-ocra-from-java.md`) that walks external JVM teams through simulator embedding via helpers or REST/CLI bridges. | Operators follow prerequisites, Gradle snippets, and runnable samples to generate/replay OTPs without reading source code. | Guide enumerates prerequisites, references supported helper classes, and calls out verification steps (expected OTP, telemetry hashes). | Missing or outdated instructions block Java operators from integrating the simulator. | Guide references existing `core.ocra.*` telemetry frames and `includeTrace` semantics rather than introducing new events. | Clarifications 2025-09-30 (Java scope). |
| FR-007-02 | Publish a CLI operations guide (`docs/2-how-to/use-ocra-cli-operations.md`) covering every shipped command with copy-ready examples and troubleshooting. | Operators can run import/list/delete/evaluate/maintenance commands exactly as documented and match sanitized output. | Guide explains flags, credential store defaults, and validation errors; commands validated via CLI help output. | Gaps or mismatched flags force operators to consult code or fail commands. | Emphasises `cli.ocra.*` telemetry and hashed credential identifiers already emitted by the CLI. | Clarifications 2025-09-30 (CLI scope). |
| FR-007-03 | Replace the legacy REST evaluation document with a comprehensive REST operations guide (`docs/2-how-to/use-ocra-rest-operations.md`). | Operators execute every documented endpoint (evaluation, credential directory, sample loader, Swagger UI) via sample cURL/HTTPie requests with expected responses. | Guide references OpenAPI snapshot locations, schema fragments, and Swagger UI navigation; hyperlinks validated via `./gradlew spotlessApply check`. | Missing endpoints or stale payloads cause REST users to misconfigure requests. | References existing `rest.ocra.*` telemetry fields plus verbose trace toggles; no new telemetry required. | Clarifications 2025-09-30 (REST scope). |
| FR-007-04 | Update `README.md` to describe only shipped capabilities, include Swagger UI entry points, and remove stale future-work placeholders. | README reflects current CLI/REST/UI features and links to the new how-to guides. | Documentation review confirms language matches current roadmap items and does not over-promise features. | Stale README content creates user confusion about unsupported protocols. | References telemetry at a high level and links to how-to guides instead of duplicating details. | Clarifications 2025-09-30 (README scope). |
| FR-007-05 | Keep roadmap, knowledge map, and related docs synchronized once the guides ship. | Cross-links in roadmap/knowledge map reference the new guides and operator workflows. | Verification step ensures docs catalog and `docs/_current-session.md` mention the published guides. | Drift between documentation sources erodes traceability/auditability. | No telemetry impact; doc-only change recorded in session snapshot + migration plan. | Goals section; clarification on consistent cross-links. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-007-01 | Maintain inclusive, operator-friendly tone that clearly separates prerequisites from execution steps. | Operators span varying skill levels; tone must remain approachable. | Peer/self review and doc lint confirm voice and structure. | Docs directory, review checklist. | Goals + clarifications. |
| NFR-007-02 | Provide copy-and-paste ready commands/snippets that align with repository paths, default credential stores, and telemetry toggles. | Reduce operator error when reproducing flows. | Example commands tested before publication; links kept relative. | CLI/REST helpers, credential store defaults. | Goals + dependencies. |
| NFR-007-03 | Keep Markdown style compliant (link checks, fenced code hints, ≤120-char soft limit) and ensure `./gradlew spotlessApply check` remains green after edits. | Documentation shares the same formatting/lint gates as code. | `./gradlew spotlessApply check` success, optional `markdownlint`. | Gradle spotless, doc lint configuration. | Dependencies & Risks section. |
| NFR-007-04 | Log documentation updates in roadmap, knowledge map, migration plan, and session snapshot for traceability. | Template migration + governance rely on recorded progress. | Entries added to `docs/4-architecture/knowledge-map.md`, `docs/migration_plan.md`, and `docs/_current-session.md`. | Documentation governance workflow. | Goals + migration plan. |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-007-01 | Java operator integration guide demonstrates how external applications drive simulator helpers or REST/CLI bridges with runnable snippets. |
| S-007-02 | CLI operations guide documents every shipped command with examples, flags, and troubleshooting cues. |
| S-007-03 | REST operations guide consolidates endpoints, schemas, Swagger UI usage, and troubleshooting. |
| S-007-04 | README reflects current simulator capabilities, adds the Swagger UI link, and removes references to unimplemented interfaces. |
| S-007-05 | Documentation set cross-links consistently and updates roadmap/knowledge map/migration log entries after publication. |

## Test Strategy
- **Docs:** Run `./gradlew --no-daemon spotlessApply check` (includes Markdown formatting) after each doc change; optionally run `markdownlint` for focused validation.
- **Manual verification:** Execute sample Java, CLI, and REST commands described in the guides to confirm outputs match the documented expectations before publishing.
- **Links/reference checks:** Verify README + how-to hyperlinks resolve (Swagger UI, OpenAPI snapshots) and that credential store paths align with current defaults.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-007-NA | Doc-only feature; no new domain objects introduced. | — |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-007-01 | REST `POST /api/v1/ocra/evaluate` | Evaluation endpoint documented in REST guide. | References OpenAPI snapshot and Swagger UI usage. |
| API-007-02 | REST `GET /api/v1/ocra/credentials` | Credential directory endpoint documented for dropdown seeding. | Shares payload schema with CLI/backoffice clients. |
| API-007-03 | REST `GET /api/v1/ocra/credentials/{id}/sample` | Sample payload endpoint described for replay helpers. | Returns 404/204 for missing samples. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-007-01 | `./gradlew :cli:runOcraCli --args="import …"` | Import credential command coverage in CLI guide. |
| CLI-007-02 | `./gradlew :cli:runOcraCli --args="list"` | List credentials command coverage. |
| CLI-007-03 | `./gradlew :cli:runOcraCli --args="evaluate …"` | Evaluate commands (inline/stored) documented. |
| CLI-007-04 | `./gradlew :cli:runOcraCli --args="maintenance …"` | Maintenance compact/verify workflows documented. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-007-01 | `core.ocra.*` | Referenced by Java guide; secrets hashed before logging. |
| TE-007-02 | `cli.ocra.*` | Referenced by CLI guide; credential IDs hashed. |
| TE-007-03 | `rest.ocra.*` | Referenced by REST guide; `telemetryId` hashed, `includeTrace` optional. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-007-01 | `docs/test-vectors/ocra/` | Fixture catalogue referenced by all guides for deterministic OTPs. |
| FX-007-02 | `data/credentials.db` | Default credential store path noted in the docs. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-007-NA | None | Operator documentation does not introduce UI changes. |

## Telemetry & Observability
The guides reinforce existing telemetry expectations so operators know how to interpret `core.ocra.*`, `cli.ocra.*`, and `rest.ocra.*` events as well as the `includeTrace` toggles. No new telemetry is introduced; documentation simply references the sanitized fields already emitted by the simulator.

## Documentation Deliverables
- `docs/2-how-to/use-ocra-from-java.md` – Java operator integration walkthrough with runnable samples.
- `docs/2-how-to/use-ocra-cli-operations.md` – CLI operations guide documenting import/list/delete/evaluate/maintenance flows.
- `docs/2-how-to/use-ocra-rest-operations.md` – REST operations guide covering endpoints, schemas, and Swagger UI usage.
- `README.md` – Current capabilities summary, Swagger UI link, doc cross-links.
- `docs/4-architecture/knowledge-map.md`, `docs/migration_plan.md`, `docs/_current-session.md` – Traceability updates once docs ship.

## Fixtures & Sample Data
- Continue using the synthetic OCRA vectors stored under `docs/test-vectors/ocra/` for every documented example; never embed secrets directly in README/how-to snippets.

## Dependencies & Risks
- Requires up-to-date awareness of CLI and REST capabilities; stale knowledge risks misdocumenting commands or endpoints.
- README changes must not contradict other workstreams or public messaging.
- Documentation must keep persistence defaults (`data/credentials.db`) synchronized with the latest infra-persistence behaviour.

## Verification
- `./gradlew --no-daemon spotlessApply check` ensures Markdown formatting stays in compliance.
- Spot-check Java/CLI/REST samples against the current simulator build before publishing.
- Confirm README hyperlinks (including Swagger UI at `http://localhost:8080/swagger-ui/index.html`) resolve when the simulator runs locally.

## Spec DSL
```
domain_objects: []
api_routes:
  - id: API-007-01
    method: POST
    path: /api/v1/ocra/evaluate
  - id: API-007-02
    method: GET
    path: /api/v1/ocra/credentials
  - id: API-007-03
    method: GET
    path: /api/v1/ocra/credentials/{id}/sample
cli_commands:
  - id: CLI-007-01
    command: ./gradlew :cli:runOcraCli --args="import …"
  - id: CLI-007-02
    command: ./gradlew :cli:runOcraCli --args="list"
  - id: CLI-007-03
    command: ./gradlew :cli:runOcraCli --args="evaluate …"
  - id: CLI-007-04
    command: ./gradlew :cli:runOcraCli --args="maintenance …"
telemetry_events:
  - id: TE-007-01
    event: core.ocra.*
  - id: TE-007-02
    event: cli.ocra.*
  - id: TE-007-03
    event: rest.ocra.*
fixtures:
  - id: FX-007-01
    path: docs/test-vectors/ocra/
  - id: FX-007-02
    path: data/credentials.db
scenarios:
  - id: S-007-01
    description: Java operators can reproduce OTP generation/replay via the how-to guide.
  - id: S-007-02
    description: CLI guide covers import/list/delete/evaluate/maintenance commands end to end.
  - id: S-007-03
    description: REST guide documents all endpoints, schemas, and Swagger UI flows.
  - id: S-007-04
    description: README reflects shipped capabilities with Swagger UI links.
  - id: S-007-05
    description: Knowledge map, roadmap, and migration/session logs reference the published guides.
```

## Appendix (Optional)
- None.
