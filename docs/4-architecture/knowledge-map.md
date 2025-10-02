# Architecture Knowledge Map

_Status: Draft_
_Last updated: 2025-10-01_

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
- CLI command hierarchy seals `OcraCli.AbstractOcraCommand` to the known Picocli subcommands, keeping package-private seams for tests while preventing ad-hoc extensions.
- Caffeine cache layer exposes per-profile defaults (in-memory, file-backed, container) with expire-after-access/write strategies to sustain ≥10k RPS targets while keeping overrides available via builder hooks; operators can reference `docs/2-how-to/configure-persistence-profiles.md` for configuration steps.
- Quality automation suite (Feature 008) formalises ArchUnit boundary checks plus PIT/Jacoco thresholds across modules, running locally and in GitHub Actions.
- `core-architecture-tests` Gradle module hosts facade/persistence ArchUnit rules, ensuring CLI/REST/UI modules stay isolated while persistence remains core-internal; invoked via `./gradlew :core-architecture-tests:test` or root `./gradlew architectureTest` and planned to roll into `qualityGate`.
- Reflection guardrails (Feature 011) combine an ArchUnit rule and the Gradle `reflectionScan` task (wired into `qualityGate`) to keep production/tests free of `java.lang.reflect` usage while providing allow-listed seams for testing.
- Aggregated Jacoco tasks (`jacocoAggregatedReport`, `jacocoCoverageVerification`) monitor OCRA module coverage with current thresholds (line ≥77%, branch ≥62%) and output to `build/reports/jacoco/aggregated/` pending future threshold increases.
- Root `mutationTest` task runs PIT against `core` OCRA classes (threshold ≥85%, current score ≈87.6%) and excludes `OcraChallengeFormat`/`OcraCredentialFactory` pending new tests; CLI/REST facades queued for future expansion once mutation coverage improves.
- Root `qualityGate` aggregates `spotlessCheck` and `check` (architecture, coverage, mutation), accepts `-Ppit.skip=true` for faster local iteration, now runs in GitHub Actions via the `ci.yml` workflow, and has operator guidance in `docs/5-operations/quality-gate.md`.
- REST API spec for session-aware OCRA evaluation was drafted 2025-09-28; broader facade endpoints remain pending.
- REST API now serves `/api/v1/ocra/evaluate`, delegating to `OcraResponseCalculator` and emitting redaction-friendly telemetry aligned with the CLI helper.
- REST API publishes an auto-generated OpenAPI contract (`/v3/api-docs`) with checked-in JSON and YAML snapshots at `docs/3-reference/rest-openapi.json` and `docs/3-reference/rest-openapi.yaml` for downstream integrations.
- REST API now exposes `/api/v1/ocra/verify`, delegating to `OcraReplayVerifier` for OTP replay checks and sharing telemetry metadata with CLI verification flows.
- REST verification services publish package-private normalized request records and telemetry emitters so tests and future modules can exercise behaviour without reflection, mirroring the evaluation service seams.
- REST OCRA evaluation and verification services now normalize payloads into sealed variants (stored vs inline), reducing mode detection to pattern matching instead of nullable fields.
- REST OCRA telemetry snapshot is archived under `docs/3-reference/rest-ocra-telemetry-snapshot.md` to illustrate redaction guarantees for operators.
- REST OCRA telemetry events now emit `reasonCode` and `sanitized` attributes so downstream alerting can distinguish validation failures from unexpected errors without leaking secrets.
- REST OCRA evaluation supports credential lookup via `credentialId`, resolving descriptors from the persistence adapter while preserving the existing inline secret mode.
- REST module now hosts Thymeleaf-backed operator views under `/ui/ocra`, delegating evaluations to the existing REST endpoint while emitting sanitized telemetry and enforcing session-backed CSRF tokens.
- REST module exposes `/api/v1/ocra/credentials` to surface sanitized OCRA credential summaries for operator dropdowns without leaking secret material.
- Shared MapDB persistence defaults to `data/ocra-credentials.db` at the repository root, keeping CLI, REST, and UI facades aligned unless overridden.
- MapDB store now exposes a package-private cache accessor used by internal tests, eliminating the need for reflective field access while keeping cache internals encapsulated.
- Ocra credential factory provides a package-private telemetry logger accessor so validation logging assertions remain reflection-free.
- Operator UI templates expose an accessible mode toggle (inline vs credential) and a sanitized telemetry summary panel so escalations reuse REST metadata without revealing secrets.
- Operator UI submissions now rely on asynchronous JSON `fetch` calls (with an XMLHttpRequest fallback for HtmlUnit) to invoke `/api/v1/ocra/evaluate`, rendering result/error panels client-side while the server-side controller remains read-only.
- Inline policy presets surface curated OCRA test vectors in the UI, auto-filling inline evaluation fields for QA and operator training.
- Stored credential mode now ships with a client-side “Auto-fill parameters” helper that parses the selected OCRA suite and generates compatible challenge/counter/session/timestamp values, clearing disallowed inputs to avoid REST validation errors.
- REST facade now boots a MapDB-backed `CredentialStore` bean (path configurable via `openauth.sim.persistence.database-path`), enabling stored credential evaluations in both REST and UI flows.
- `infra-persistence` module exposes `CredentialStoreFactory`, centralising MapDB provisioning so CLI and REST facades obtain `CredentialStore` instances without direct builder usage.
- How-to catalogue documents the Appendix B Java workflow for generating new OCRA test vectors, keeping domain/UI fixtures aligned with the draft reference implementation.
- Operator documentation suite under `docs/2-how-to` now includes REST, CLI, and Java integration guides that point operators to shared MapDB defaults and Swagger UI entry points.
- OCRA replay & verification flows (Feature 009) now extend CLI and REST facades with strict OTP verification using stored or inline credentials while emitting hashed OTP telemetry for audit purposes; shipped 2025-10-01 with shared `core.ocra.verify` instrumentation.

## Open Items
| Date | Topic | Question | Status | Follow-up |
|------|-------|----------|--------|-----------|
| 2025-09-27 | Persistence design | Confirm MapDB caching topology (shared vs per facade) | Resolved | Resolved 2025-09-27 – Shared MapDB store with core-managed shared cache for all facades |
| 2025-10-01 | Architecture harmonization | Capture shared application layer + telemetry relationships once implemented | Open | Update after R1403–R1408 to reflect new modules and contracts |

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
