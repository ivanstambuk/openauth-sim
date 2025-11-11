# Feature 013 – Java 17 Language Enhancements

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/013/plan.md` |
| Linked tasks | `docs/4-architecture/features/013/tasks.md` |
| Roadmap entry | #13 |

## Overview
Apply targeted Java 17 language features across the OCRA CLI and REST layers to tighten compile-time guarantees and improve documentation readability without altering external contracts. The work seals the CLI command hierarchy, introduces sealed/pattern-matched request variants inside REST normalization, and replaces verbose escaped JSON examples with Java text blocks. Behaviour, APIs, and telemetry remain unchanged; only internals and documentation improve.

## Clarifications
- 2025-10-01 – Scope is limited to the OCRA CLI and REST modules; core cryptography remains untouched (Option A).
- 2025-10-01 – Only the `OcraCli` abstract command hierarchy is sealed; other command trees stay unchanged unless future specs require it (Option B).
- 2025-10-01 – REST request normalization exposes sealed variants consumed internally; REST controller payloads and schemas stay the same (Option A).
- 2025-10-01 – Text-block migration targets OpenAPI example payloads embedded in controller annotations. Additional escaped JSON will be queued separately (Option A).
- 2025-10-01 – No other CLI command hierarchies require sealing; future hierarchies should follow the same pattern (Option A).
- 2025-10-01 – New REST endpoints with inline examples must use Java text blocks by default (Option A).

## Goals
- Leverage Java 17 language features (sealed classes/records, text blocks, pattern matching) to simplify CLI and REST internals while keeping behaviour identical.
- Maintain or improve automated coverage (qualityGate, ArchUnit, PIT, Jacoco) after the refactors.
- Document reproduction commands and policies so future contributors continue using sealed hierarchies and text blocks consistently.

## Non-Goals
- Increasing the minimum Java version beyond 17.
- Refactoring protocols, schemas, or telemetry contracts outside the defined CLI/REST scope.
- Introducing new dependencies or CLI/REST features.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-013-01 | Seal the `OcraCli` abstract command hierarchy with an explicit permit list covering the existing Picocli subcommands. | `AbstractOcraCommand` declared sealed, CLI tests demonstrate all permitted subcommands execute normally. | `./gradlew --no-daemon :cli:test` asserts CLI behaviour unchanged and no reflective access occurs. | Picocli wiring fails, unsealed subclasses persist, or new reflection appears. | No telemetry impact; guard via CLI tests. | Clarifications 1–3.
| FR-013-02 | Replace REST OCRA evaluation/verification normalization with sealed request variants and pattern matching to remove nullable discriminators. | REST services emit sealed `StoredCredential`/`InlineSecret` variants; existing DTOs remain unchanged externally. | `./gradlew --no-daemon :rest-api:test --tests "*Ocra*ServiceTest"` covers both variants plus error paths. | Null pointers or serialization regressions appear; pattern matching misses a branch. | Telemetry unchanged; REST logs unaffected. | Clarifications 1–3.
| FR-013-03 | Convert REST controller OpenAPI example strings to Java text blocks without changing the rendered payloads. | Controllers compile with text blocks; example snapshots remain identical. | OpenAPI snapshot tests or controller serialization tests compare outputs before vs after. | Snapshot diffs, formatting regressions, or controller compilation failures. | None; documentation-only change. | Clarifications 4 & 6.
| FR-013-04 | Keep documentation guidance in `AGENTS.md`/specs updated so future features reuse sealed hierarchies and text blocks. | Spec/plan/tasks reference the policy; roadmap entry documents completion. | `rg -n "sealed" AGENTS.md` (already updated in Feature 011) plus this spec cross-references. | Lack of documentation causes future drift. | None. | Goals.

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-013-01 | Maintain automation quality gates (spotless, ArchUnit, Jacoco, PIT) after Java 17 refactors. | Prevent regressions when language features ship. | `./gradlew qualityGate` passes without threshold changes. | Gradle toolchain, quality plugins. | Goals.
| NFR-013-02 | Avoid REST performance regressions from sealed variant normalization. | Keep request processing at baseline latency. | Service tests/benchmarks show ≤5% deviation from prior runs. | REST application module. | Goals.
| NFR-013-03 | Keep sealed hierarchies encapsulated (package-private or nested) so public APIs remain stable. | Prevent public API churn. | CLI/REST public signatures unchanged; ArchUnit checks confirm package-level encapsulation. | CLI, REST modules. | Clarifications 2 & 3.

## UI / Interaction Mock-ups
_Not applicable – no UI work is included._

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-013-01 | CLI sealed hierarchy enforces the permitted Picocli subcommands while tests confirm behaviour parity. |
| S-013-02 | REST OCRA evaluation/verification services use sealed variants + pattern matching to normalise stored vs inline requests. |
| S-013-03 | OpenAPI example payloads become Java text blocks without altering rendered JSON. |
| S-013-04 | Full quality gate (spotless, ArchUnit, Jacoco, PIT) remains green after the Java 17 refactors. |

## Test Strategy
- **CLI:** Extend command tests to assert sealed hierarchy behaviour and confirm no unauthorized subclasses exist.
- **REST:** Update evaluation/verification service tests to exercise both sealed variants, validation paths, and failure handling.
- **Docs/Contracts:** Run OpenAPI snapshot tests to confirm text block conversions preserve payloads.
- **Quality Gate:** Run `./gradlew qualityGate` to ensure ArchUnit/PIT/Jacoco remain green post-refactor.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-013-01 | `StoredCredentialRequestVariant` – sealed variant representing stored credential normalization. | rest-api |
| DO-013-02 | `InlineSecretRequestVariant` – sealed variant representing inline secret normalization. | rest-api |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-013-01 | REST `/api/v1/ocra/evaluate` | Internal normalization now uses sealed variants; external schema unchanged. | Snapshots validated via tests. |
| API-013-02 | REST `/api/v1/ocra/verify` | Same as above for verification flows. | — |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-013-01 | `./gradlew --no-daemon :cli:test` | Validates sealed CLI hierarchy and behaviour parity. |
| CLI-013-02 | `./gradlew --no-daemon :rest-api:test --tests "*Ocra*ServiceTest"` | Exercises sealed REST variants and text blocks via service tests. |
| CLI-013-03 | `./gradlew --no-daemon qualityGate` | Ensures automation stays green after Java 17 enhancements. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-013-NA | — | Telemetry contracts unchanged. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-013-01 | `rest-api/src/test/java/.../OcraEvaluationServiceTest.java` | Validates sealed variant normalization paths. |
| FX-013-02 | `rest-api/src/test/java/.../OcraVerificationServiceTest.java` | Same for verification flows. |
| FX-013-03 | `cli/src/test/java/.../OcraCliTest.java` | Confirms sealed CLI hierarchy behaviour. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-013-NA | — | Not applicable. |

## Telemetry & Observability
Runtime telemetry is unchanged. Enforcement relies on existing tests plus the quality gate; any regressions surface via Gradle output and OpenAPI snapshots.

## Documentation Deliverables
- Update this spec/plan/tasks with sealed hierarchy and text block guidance (Complete).
- Reference the policy in roadmap/session snapshot so future features reuse sealed hierarchies and text blocks.

## Fixtures & Sample Data
- Jacoco and OpenAPI snapshot outputs remain the authoritative evidence for refactors; no new fixture directories required beyond existing CLI/REST tests.

## Spec DSL
```
cli_commands:
  - id: CLI-013-01
    command: ./gradlew --no-daemon :cli:test
    description: Validates the sealed CLI hierarchy
  - id: CLI-013-02
    command: ./gradlew --no-daemon :rest-api:test --tests "*Ocra*ServiceTest"
    description: Exercises sealed REST request variants
  - id: CLI-013-03
    command: ./gradlew --no-daemon qualityGate
    description: Confirms automation stays green post-refactor
fixtures:
  - id: FX-013-01
    path: rest-api/src/test/java/.../OcraEvaluationServiceTest.java
    purpose: Tests sealed evaluation request variants
  - id: FX-013-02
    path: rest-api/src/test/java/.../OcraVerificationServiceTest.java
    purpose: Tests sealed verification request variants
  - id: FX-013-03
    path: cli/src/test/java/.../OcraCliTest.java
    purpose: Validates sealed CLI command hierarchy
scenarios:
  - id: S-013-01
    description: CLI sealed hierarchy verified via tests
  - id: S-013-02
    description: REST sealed variants normalise stored vs inline flows
  - id: S-013-03
    description: Text block conversion preserves OpenAPI examples
  - id: S-013-04
    description: Quality gate remains green after Java 17 enhancements
```

## Appendix (Optional)
- Quality gate evidence (2025-10-01): `./gradlew qualityGate` green; PIT/Jacoco thresholds unchanged.
- Accepted limitation: Defensive guard `parsed == null` remains unreachable; documented for future refactors.
