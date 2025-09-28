# Architecture Knowledge Map

_Status: Draft_
_Last updated: 2025-09-28_

This living map captures the explicit relationships between modules, data flows, and external interfaces so future agents can reason about change impact quickly. Update it after every iteration that introduces or modifies a component, dependency, or contract.

## Update Procedure
1. Review the latest feature plan and closed work to identify new elements or interactions.
2. Describe the change in prose (one line per relationship) under **Current Links**.
3. Update the PlantUML view (or replace it with another diagram) so visualisations stay in sync. Regenerate any exported assets in `docs/_assets/` if required.
4. Note outstanding follow-ups or ambiguities in the **Open Items** table and mirror them in `docs/4-architecture/open-questions.md`.

## Current Links
- Core module currently exposes the OCRA credential domain consumed by facade modules; additional protocol packages will be introduced via future specifications.
- Future work will surface issuance and presentation simulators for EU Digital Identity Wallet flows once the dedicated features are prioritised.
- Core OCRA package normalises RFC 6287 suites into descriptor records consumed by the credential registry and future factory helpers.
- Core persistence serialization contracts convert protocol descriptors into versioned credential records, now stored by `MapDbCredentialStore` with schema-aware migrations.
- OCRA validation telemetry emits structured debug events that future observability modules can ingest without exposing secret material.
- `docs/1-concepts/README.md` documents the OCRA capability matrix and telemetry contract operators should consult when integrating facades.
- FIDO2, EUDI, and EMV packages remain pending; future knowledge-map updates will add their relationships once plans land.
- MapDB-backed persistence layer (planned) will expose repositories consumed by CLI, REST API, and UI facades.
- MapDB-backed persistence now emits Level.FINE telemetry events (`persistence.credential.lookup` / `persistence.credential.mutation`) capturing cache hit/miss and latency metrics without leaking secret material.
- MapDB maintenance helper (T205) provides synchronous compaction and integrity checks with structured `MaintenanceResult` outputs and telemetry so admin facades can surface maintenance outcomes.
- MapDB persistence supports optional AES-GCM secret encryption via in-memory key callbacks, keeping secrets encrypted at rest while preserving the existing `CredentialStore` contract.
- CLI module now exposes `maintenance <compact|verify>` commands that orchestrate the helper for operators working on local MapDB stores.
- CLI module now orchestrates OCRA credential import/list/delete/evaluate commands, delegating to MapDB persistence and core OCRA adapters while emitting sanitized telemetry.
- Caffeine cache layer exposes per-profile defaults (in-memory, file-backed, container) with expire-after-access/write strategies to sustain ≥10k RPS targets while keeping overrides available via builder hooks; operators can reference `docs/2-how-to/configure-persistence-profiles.md` for configuration steps.
- Quality automation (ArchUnit, mutation testing, security prompts) enforces boundary rules across modules.
- REST API spec for session-aware OCRA evaluation was drafted 2025-09-28; broader facade endpoints remain pending.
- REST API now serves `/api/v1/ocra/evaluate`, delegating to `OcraResponseCalculator` and emitting redaction-friendly telemetry aligned with the CLI helper.
- REST API publishes an auto-generated OpenAPI contract (`/v3/api-docs`) with a checked-in snapshot at `docs/3-reference/rest-openapi.json` for downstream integrations.
- REST OCRA telemetry snapshot is archived under `docs/3-reference/rest-ocra-telemetry-snapshot.md` to illustrate redaction guarantees for operators.
- REST OCRA telemetry events now emit `reasonCode` and `sanitized` attributes so downstream alerting can distinguish validation failures from unexpected errors without leaking secrets.
- REST OCRA evaluation supports credential lookup via `credentialId`, resolving descriptors from the persistence adapter while preserving the existing inline secret mode.
- REST module now hosts Thymeleaf-backed operator views under `/ui/ocra`, delegating evaluations to the existing REST endpoint while emitting sanitized telemetry and enforcing session-backed CSRF tokens.

## Open Items
| Date | Topic | Question | Status | Follow-up |
|------|-------|----------|--------|-----------|
| 2025-09-27 | Persistence design | Confirm MapDB caching topology (shared vs per facade) | Resolved | Resolved 2025-09-27 – Shared MapDB store with core-managed shared cache for all facades |

## PlantUML Sketch
```plantuml
@startuml ArchitectureKnowledgeMap
skinparam backgroundColor #ffffff

rectangle Core {
  component "Credential Domain" as CredentialDomain
  component "Crypto Helpers" as CryptoHelpers
}

package "Facades" {
  component CLI
  component "REST API" as RestAPI
  component UI
}

component "Persistence" as Persistence
component "Quality Automation" as Quality

CredentialDomain --> CryptoHelpers
CLI --> CredentialDomain
RestAPI --> CredentialDomain
UI --> RestAPI
Persistence --> CredentialDomain
CredentialDomain --> Persistence
Quality --> Core
Quality --> Facades

@enduml
```

Update the diagram together with the bullet list whenever the architecture shifts. If PlantUML becomes unwieldy, replace it with another markdown-embedded viewpoint and document how to regenerate it.
