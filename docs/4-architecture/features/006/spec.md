# Feature 006 – EUDIW OpenID4VP Simulator

| Field | Value |
|-------|-------|
| Status | In progress |
| Last updated | 2025-11-13 |
| Owners | Ivan (project owner) |
| Linked plan | [docs/4-architecture/features/006/plan.md](docs/4-architecture/features/006/plan.md) |
| Linked tasks | [docs/4-architecture/features/006/tasks.md](docs/4-architecture/features/006/tasks.md) |
| Roadmap entry | #6 – EUDIW OpenID4VP Simulator |

## Overview
Deliver a deterministic simulator for remote (cross-device) OpenID for Verifiable Presentations (OpenID4VP 1.0) flows that align with the High Assurance Interoperability Profile (HAIP). The simulator plays both verifier and wallet roles so REST, CLI, and operator UI facades can demonstrate complete presentation exchanges without external wallets while remaining scoped to redirect/QR journeys (DC-API/same-device paths stay out of scope per Non-Goals). Operators choose between HAIP-enforced and Baseline profiles via a shared toggle: HAIP mode enforces signed/encrypted `direct_post.jwt` journeys, whereas Baseline relaxes enforcement but surfaces a warning banner. Each authorization request mints deterministic `requestId`, `nonce`, and `state` values for telemetry/replay; those identifiers stay internal to telemetry/trace payloads so the UI does not expose them outside verbose traces. The scope includes SD-JWT VC (`application/dc+sd-jwt`) and ISO/IEC 18013-5 mdoc (`mso_mdoc`) credential formats, HAIP-mandated encryption for `direct_post.jwt`, Trusted Authorities filtering with friendly labels, and PID fixtures (`eu.europa.ec.eudi.pid.1`). Synthetic fixtures ship first (issuer/holder keys, disclosures, DeviceResponses) together with ingestion seams for official EU conformance bundles and ETSI Trust List metadata once released, keeping Generate/Validate flows consistent across CLI/REST/UI.

## Goals
- Generate HAIP-compliant OpenID4VP authorization requests (DCQL queries, nonce/state, optional JAR) and surface QR / deep-link handoffs for cross-device wallets.
- Produce deterministic wallet responses (SD-JWT VC + optional KB-JWT, ISO/IEC 18013-5 DeviceResponse) and validate them against per-format rules and Trusted Authorities constraints.
- Wire simulator services through existing modules (`core`, `application`, `rest-api`, `cli`, `ui`) with shared telemetry, verbose traces, and test vector loaders.
- Maintain test-first delivery with fixture-backed regression coverage, encryption verification, and end-to-end integrations across facades.

## Non-Goals
- Same-device/DC-API transport flows and wallet-device communication (track separately).
- OpenID4VCI issuance simulators or credential provisioning pipelines.
- Trust list federation resolution beyond the initial Authority Key Identifier matching (future work will address `etsi_tl` and OpenID Federation).

## Functional Requirements
The requirements below capture each behaviour with explicit sources.

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-006-01 | Authorization requests MUST use `response_type=vp_token` and include `nonce` for replay protection. | Authorization requests MUST use `response_type=vp_token` and include `nonce` for replay protection. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §5.2, §5.6, §14.1 |
| FR-006-02 | Requests MUST supply a DCQL query (`dcql_query`) or `scope` alias; supplying both or neither returns `invalid_request`. | Requests MUST supply a DCQL query (`dcql_query`) or `scope` alias; supplying both or neither returns `invalid_request`. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §5.5, §6, §8.5 |
| FR-006-03 | Cross-device transport MUST support QR/URL (request by value) and `request_uri` (request by reference with optional POST retrieval). | Cross-device transport MUST support QR/URL (request by value) and `request_uri` (request by reference with optional POST retrieval). | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §5.4, §5.7, §5.10 |
| FR-006-04 | Response modes MUST include `fragment` (default) and `direct_post`; `direct_post.jwt` MUST be supported when HAIP mode is active. | Response modes MUST include `fragment` (default) and `direct_post`; `direct_post.jwt` MUST be supported when HAIP mode is active. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §8.2–§8.3 |
| FR-006-05 | Signed requests MUST follow HAIP guidance (`x509_hash` client identifier prefix, JAR with `request_uri`). Toggle enables unsigned requests for baseline mode. | Signed requests MUST follow HAIP guidance (`x509_hash` client identifier prefix, JAR with `request_uri`). Toggle enables unsigned requests for baseline mode. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | HAIP §5.1 |
| FR-006-06 | Simulator MUST implement DCQL Credential/Claims/Trusted Authorities queries with Claims Path Pointer semantics for JSON and ISO mdoc, and reject SD-JWT VC queries lacking `meta.vct_values` or mdoc queries lacking `meta.doctype_value`. | Simulator MUST implement DCQL Credential/Claims/Trusted Authorities queries with Claims Path Pointer semantics for JSON and ISO mdoc, and reject SD-JWT VC queries lacking `meta.vct_values` or mdoc queries lacking `meta.doctype_value`. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §6–§7 |
| FR-006-07 | Wallet simulator MUST resolve DCQL credential IDs to fixture-backed presentations and return a VP Token keyed by credential ID. | Wallet simulator MUST resolve DCQL credential IDs to fixture-backed presentations and return a VP Token keyed by credential ID. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §8.1 |
| FR-006-08 | SD-JWT VC responses MUST emit `application/dc+sd-jwt` payloads, optional KB-JWT (holder binding) with `sd_hash`, `aud`, `nonce`. | SD-JWT VC responses MUST emit `application/dc+sd-jwt` payloads, optional KB-JWT (holder binding) with `sd_hash`, `aud`, `nonce`. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | SD-JWT VC §3.1; SD-JWT §4.3 |
| FR-006-09 | ISO/IEC 18013-5 flows MUST return DeviceResponse containers (`mso_mdoc`), one per credential query when multiple are requested, with descriptor identifiers preserved for UI rendering. | ISO/IEC 18013-5 flows MUST return DeviceResponse containers (`mso_mdoc`), one per credential query when multiple are requested, with descriptor identifiers preserved for UI rendering. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | HAIP §5.3.1; ISO/IEC 18013-5 §6 |
| FR-006-10 | Verifier validation MUST verify SD-JWT signatures/disclosures, KB-JWT holder binding (`aud`, `nonce`, `sd_hash`), DeviceResponse COSE signatures/MSO hashes, and Claims Path Pointer selections, failing with `invalid_presentation` when any check fails. | Verifier validation MUST verify SD-JWT signatures/disclosures, KB-JWT holder binding (`aud`, `nonce`, `sd_hash`), DeviceResponse COSE signatures/MSO hashes, and Claims Path Pointer selections, failing with `invalid_presentation` when any check fails. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §8.6; SD-JWT §4.2; ISO/IEC 18013-5 |
| FR-006-11 | Trusted Authorities filtering MUST at least implement Authority Key Identifier matching and return `invalid_request` when no issuers satisfy the query; placeholders for `etsi_tl` and OpenID Federation remain for follow-up. | Trusted Authorities filtering MUST at least implement Authority Key Identifier matching and return `invalid_request` when no issuers satisfy the query; placeholders for `etsi_tl` and OpenID Federation remain for follow-up. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §6.1.1 |
| FR-006-12 | VP Token error handling MUST map to OID4VP §8.5 (`invalid_request`, `invalid_scope`, `wallet_unavailable`, `invalid_presentation`) with problem-details payloads across facades. | VP Token error handling MUST map to OID4VP §8.5 (`invalid_request`, `invalid_scope`, `wallet_unavailable`, `invalid_presentation`) with problem-details payloads across facades. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | OID4VP §8.5 |
| FR-006-13 | Deterministic seed handling MUST ensure identical inputs yield identical nonces, request IDs, QR payloads, and wallet outputs. | Deterministic seed handling MUST ensure identical inputs yield identical nonces, request IDs, QR payloads, and wallet outputs. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Project requirement |
| FR-006-14 | Telemetry MUST emit `oid4vp.request.created`, `oid4vp.request.qr.rendered`, `oid4vp.wallet.responded`, `oid4vp.response.validated`, `oid4vp.response.failed` with sanitized fields. | Telemetry MUST emit `oid4vp.request.created`, `oid4vp.request.qr.rendered`, `oid4vp.wallet.responded`, `oid4vp.response.validated`, `oid4vp.response.failed` with sanitized fields. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Project telemetry policy |
| FR-006-15 | REST API MUST expose endpoints for request creation, wallet simulation, telemetry retrieval, and fixture ingestion, all covered by OpenAPI snapshots. | REST API MUST expose endpoints for request creation, wallet simulation, telemetry retrieval, and fixture ingestion, all covered by OpenAPI snapshots. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Project REST conventions |
| FR-006-16 | CLI MUST provide commands to create requests, render QR ASCII, simulate wallet responses, and validate VP Tokens. | CLI MUST provide commands to create requests, render QR ASCII, simulate wallet responses, and validate VP Tokens. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | CLI parity directive |
| FR-006-17 | Operator UI MUST present an updated tab with request metadata, ASCII QR preview, simulation controls, response traces, and status indicators. | Operator UI MUST present an updated tab with request metadata, ASCII QR preview, simulation controls, response traces, and status indicators. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | UI placeholder upgrade directive |
| FR-006-18 | Fixture loader MUST support synthetic datasets and ingest official OpenID4VP conformance bundles with provenance metadata. | Fixture loader MUST support synthetic datasets and ingest official OpenID4VP conformance bundles with provenance metadata. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-19 | PID fixtures for namespace `eu.europa.ec.eudi.pid.1` MUST be available in both SD-JWT and mdoc encodings for end-to-end tests. | PID fixtures for namespace `eu.europa.ec.eudi.pid.1` MUST be available in both SD-JWT and mdoc encodings for end-to-end tests. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | PID Rulebook |
| FR-006-20 | Encryption path MUST support `direct_post.jwt` using JWE `ECDH-ES` P-256 with `A128GCM`, gated behind HAIP flag but enabled by default. | Encryption path MUST support `direct_post.jwt` using JWE `ECDH-ES` P-256 with `A128GCM`, gated behind HAIP flag but enabled by default. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | HAIP §5; OID4VP §8.3 |
| FR-006-21 | When HAIP-required encryption fails (missing keys, JWE error), the simulator MUST surface an `invalid_request` problem detail, log sanitized telemetry, and only offer a retry when HAIP enforcement is disabled. | When HAIP-required encryption fails (missing keys, JWE error), the simulator MUST surface an `invalid_request` problem detail, log sanitized telemetry, and only offer a retry when HAIP enforcement is disabled. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | HAIP §5; OID4VP §8.3 |
| FR-006-22 | Simulator MUST accept and produce unsigned requests/responses when HAIP enforcement is disabled to support baseline experimentation, and the UI MUST display a prominent “Baseline (non-HAIP)” banner when this mode is active. | Simulator MUST accept and produce unsigned requests/responses when HAIP enforcement is disabled to support baseline experimentation, and the UI MUST display a prominent “Baseline (non-HAIP)” banner when this mode is active. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-23 | Facades MUST expose separate **Generate** (fixture-backed) and **Validate** (input VP Token) flows; UI MUST present these as Evaluate vs Replay sub-tabs, and validate mode MUST verify supplied VP Tokens and surface errors consistent with FR-006-10/FR-006-12. Inline vs stored selectors follow the project radio-button pattern. | Facades MUST expose separate **Generate** (fixture-backed) and **Validate** (input VP Token) flows; UI MUST present these as Evaluate vs Replay sub-tabs, and validate mode MUST verify supplied VP Tokens and surface errors consistent with FR-006-10/FR-006-12. Inline vs stored selectors follow the project radio-button pattern. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-24 | When DCQL returns multiple credential presentations, the UI MUST render one collapsible section per presentation with descriptor identifiers and per-section copy controls, and traces MUST annotate presentations with matching IDs. | When DCQL returns multiple credential presentations, the UI MUST render one collapsible section per presentation with descriptor identifiers and per-section copy controls, and traces MUST annotate presentations with matching IDs. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-25 | Generate mode MUST allow both preset wallet credentials (fixtures) and inline credential entry for SD-JWT (compact + disclosures + optional KB-JWT) and ISO mdoc DeviceResponse uploads before issuing presentations. | Generate mode MUST allow both preset wallet credentials (fixtures) and inline credential entry for SD-JWT (compact + disclosures + optional KB-JWT) and ISO mdoc DeviceResponse uploads before issuing presentations. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-26 | UI MUST display a formatted DCQL JSON preview inline beneath the preset selector (read-only textarea matching existing styling) so operators can inspect credential IDs, claims, and Trusted Authorities before generation. | UI MUST display a formatted DCQL JSON preview inline beneath the preset selector (read-only textarea matching existing styling) so operators can inspect credential IDs, claims, and Trusted Authorities before generation. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-27 | DCQL presets MUST include friendly issuer labels for Trusted Authorities (`aki`, `etsi_tl`, etc.) as metadata, and the UI MUST render the label alongside the hash/value in a read-only display (e.g., “EU PID Issuer (aki: s9tIpP…)”) while emitting standard DCQL payloads without the label attribute. | DCQL presets MUST include friendly issuer labels for Trusted Authorities (`aki`, `etsi_tl`, etc.) as metadata, and the UI MUST render the label alongside the hash/value in a read-only display (e.g., “EU PID Issuer (aki: s9tIpP…)”) while emitting standard DCQL payloads without the label attribute. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-28 | Result panels MUST render the VP Token presentation JSON inline (read-only, monospaced, horizontal scrolling enabled) so operators can inspect the payload without relying on verbose traces. | Result panels MUST render the VP Token presentation JSON inline (read-only, monospaced, horizontal scrolling enabled) so operators can inspect the payload without relying on verbose traces. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-29 | Fixture bundles MUST include synthetic issuer signing keys, holder binding keys, and trust anchors sufficient to regenerate SD-JWT VC, KB-JWT, and mdoc presentations deterministically; these keys must remain confined to test fixtures. | Fixture bundles MUST include synthetic issuer signing keys, holder binding keys, and trust anchors sufficient to regenerate SD-JWT VC, KB-JWT, and mdoc presentations deterministically; these keys must remain confined to test fixtures. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-30 | Inline mode must expose a "Load sample vector" selector that pre-populates all inline fields (SD-JWT, disclosures, DeviceResponse) from fixtures; stored mode remains unaffected. | Inline mode must expose a "Load sample vector" selector that pre-populates all inline fields (SD-JWT, disclosures, DeviceResponse) from fixtures; stored mode remains unaffected. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-31 | Console deep-links (`?protocol=eudiw&tab=<evaluate\|replay>&mode=<inline\|stored>`) MUST hydrate the EUDIW tab state just like other protocol tabs. | Deep-links restore presets, mode selection, baseline banner visibility, and inline/stored selectors so operators can share links reliably. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |
| FR-006-32 | REST/CLI verbose flags MUST reuse the shared `verbose` convention and emit traces via the existing `VerboseTracePayload` contract. | REST/CLI verbose flags MUST reuse the shared `verbose` convention and emit traces via the existing `VerboseTracePayload` contract. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Project telemetry policy |
| FR-006-33 | Provide a stored presentation seeding workflow (UI action, CLI command, REST endpoint) that loads fixture presentations into the MapDB store for later selection in stored mode. | Provide a stored presentation seeding workflow (UI action, CLI command, REST endpoint) that loads fixture presentations into the MapDB store for later selection in stored mode. | Requests or presentations that violate this rule return the appropriate problem detail (see Scenario Matrix). | Simulator aborts processing and surfaces the matching error outcome (invalid_request / invalid_scope / invalid_presentation / wallet_unavailable). | Refer to `oid4vp.*` events in Telemetry & Observability; verbose traces capture the redacted payloads and diagnostics. | Spec. |

## Non-Functional Requirements
Quality constraints that must stay true regardless of transport/profile.

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-006-01 | Preserve deterministic behaviour under a fixed seed (nonce, state, key order, QR payloads). | Preserve deterministic behaviour under a fixed seed (nonce, state, key order, QR payloads). | Enforced via targeted tests, telemetry, or build tooling per plan/tasks references. | Core/application telemetry, build tooling, and scenario instrumentation. | Project governance |
| NFR-006-02 | Honour the project’s no-reflection policy across production and tests. | Honour the project’s no-reflection policy across production and tests. | Enforced via targeted tests, telemetry, or build tooling per plan/tasks references. | Core/application telemetry, build tooling, and scenario instrumentation. | Project governance |
| NFR-006-03 | Complete remote presentation round-trips in ≤200 ms in non-encrypted mode on a developer workstation; record encryption overhead metrics. | Complete remote presentation round-trips in ≤200 ms in non-encrypted mode on a developer workstation; record encryption overhead metrics. | Enforced via targeted tests, telemetry, or build tooling per plan/tasks references. | Core/application telemetry, build tooling, and scenario instrumentation. | Project governance |
| NFR-006-04 | Redact PII in logs/telemetry (only hash claim structures and counts). | Redact PII in logs/telemetry (only hash claim structures and counts). | Enforced via targeted tests, telemetry, or build tooling per plan/tasks references. | Core/application telemetry, build tooling, and scenario instrumentation. | Project governance |
| NFR-006-05 | Keep documentation/roadmap/knowledge map in sync; run `./gradlew --no-daemon spotlessApply check` after every increment. | Keep documentation/roadmap/knowledge map in sync; run `./gradlew --no-daemon spotlessApply check` after every increment. | Enforced via targeted tests, telemetry, or build tooling per plan/tasks references. | Core/application telemetry, build tooling, and scenario instrumentation. | Project governance |
| NFR-006-06 | Facade seam – All EUDIW OpenID4VP facades (CLI/REST/UI/MCP/standalone/Native Java) delegate only through `application.eudi.openid4vp` services and obtain persistence via `CredentialStoreFactory` when storage is involved; facades must not depend directly on `core` internals or construct `MapDbCredentialStore`. | ArchUnit facade-boundary tests and contract tests prevent direct `core`/persistence usage. | application, infra-persistence, facades. | Spec. |

## UI / Interaction Mock-ups

The console mirrors the EMV/CAP layout: request inputs at the top, result and trace panels rendered side by side (stacked on narrow screens). Square brackets indicate editable inputs; uppercase labels are static. Verbose tracing is controlled globally via the console header toggle and shared trace dock, so the individual panels do not render a local checkbox. When the profile switch selects **Baseline**, a banner appears above the panel (“Baseline mode – HAIP enforcement disabled”) to warn operators about the relaxed profile.

Evaluate tab hosts the Generate flow (preset wallet fixtures or inline SD-JWT/mdoc payloads plus the “Load sample vector” selector), whereas Replay hosts Validate mode for pasted VP Tokens or stored presentations; both tabs use the shared inline/stored radio pattern to keep parity with other simulators. The DCQL preview directly under the preset selector is read-only, uses the standard grey background, and lets operators inspect credential descriptors + Trusted Authorities before generating a request. Trusted Authority metadata lists a friendly label plus the hash (`aki`, `etsi_tl`, etc.); until ETSI Trust List ingestion populates the label, the UI surfaces “(pending)” to set expectations. `requestId` values power telemetry/trace alignment but stay hidden from the main UI—operators only see them inside the verbose trace dock. Result cards always embed VP Token JSON inline (monospaced, horizontal scroll) so operators can inspect payloads without toggling verbose trace, and multi-presentation responses render one collapsible section per descriptor with matching trace annotations and copy controls. A “Seed sample presentations” affordance mirrors other simulators and hydrates stored-mode presets via the fixture loader.

### Evaluate Tab – Request Panel
```
┌────────────────────────────────────────────────────────────────────────────┐
│ EUDIW OpenID4VP Simulator                                                  │
├────────────────────────────────────────────────────────────────────────────┤
│ Tabs: [ Evaluate ▣ ] [ Replay □ ]                                          │
│                                                                            │
│ Choose evaluation mode                                                     │
│   (•) Inline parameters      Provide SD-JWT or mdoc data directly.         │
│   ( ) Stored credential       Use a preset wallet fixture.                 │
│ Profile: [ HAIP ▼ ]  Mode: [ direct_post.jwt ▼ ]                           │
│ DCQL Query preset: [ pid-haip-baseline ▼ ]                                 │
│ DCQL preview                                                               │
│   {                                                                        │
│     "type": "pid-haip-baseline",                                           │
│     "credentials": [ ... ],                                                │
│     "trusted_authorities": [                                               │
│       { "type": "aki", "values": [ "s9tIpP..." ] }                         │
│     ]                                                                      │
│   }                                                                        │
│                                                                            │
│ Trusted authorities:                                                       │
│   • EU PID Issuer (aki: s9tIpP...)                                         │
│   • ETSI Trust List (pending)                                              │
│                                                                            │
│ Wallet credential (Generate mode)                                          │
│   Load a sample vector (inline mode)                                       │
│     Preset sample        [ Select sample ▼ ]   ( Apply )                   │
│                                                                            │
│   If inline credential selected:                                           │
│     SD-JWT compact        [..............................................] │
│     Disclosures (JSON)    [..............................................] │
│     KB-JWT compact (opt.) [..............................................] │
│   or provide ISO mdoc inline:                                              │
│     DeviceResponse (Base64) [............................................] │
│   If stored credential selected:                                           │
│     Preset wallet         [ pid-eu.europa.ec.eudi.pid.1 ▼ ]   ( Load )     │
│                                                                            │
│ Seed sample presentations  [ Seed sample presentations ]                   │
│                                                                            │
│ Scan with wallet (remote device):                                          │
│                                                                            │
│ █▀█ █ █▀█  ▄█▀ …  (ASCII QR placeholder – rendered by helper)              │
│                                                                            │
│ ALT URL: https://sim.example/authorize?client_id=x509_hash...              │
│                                                                            │
│ [ Simulate wallet response ]                                               │
└────────────────────────────────────────────────────────────────────────────┘
```
### Result Panel
```
┌───────────────────────────────────────────────────────────┐
│ Presentation summary                                      │
├───────────────────────────────────────────────────────────┤
│ Format: dc+sd-jwt                                         │
│ Holder binding: true                                      │
│ Credential IDs: pid-eu.europa.ec.eudi.pid.1               │
│ Claims released (3):                                      │
│   • family_name = "Rivera"                                │
│   • given_name = "Alicia"                                 │
│ Trusted authority match: aki                              │
│ Encryption: direct_post.jwt (ECDH-ES P-256)               │
│ Source: [ Generate fixture pid-haip-baseline ]            │
│ Presentation JSON                                         │
│   {                                                       │
│     "vp_token": "...",                                    │
│     "presentation_submission": { ... }                    │
│   }                                                       │
│   ─────────────────────────────────────── ─────────────▶  │
│ Status: [ SUCCESS ]                                       │
└───────────────────────────────────────────────────────────┘
```

### Trace Panel
```
┌───────────────────────────────────────────────┐
│ Verbose trace (sanitised)                     │
├───────────────────────────────────────────────┤
│ request_id               7K3D-XF29 (trace-only) │
│ vp_token.sd_jwt_hash      6f5a…               │
│ kb_jwt.sd_hash            6f5a…               │
│ nonce (masked)            ******2F29          │
│ trusted_authority_match   aki                 │
│ response_mode             direct_post.jwt     │
│ latency_ms                142                 │
│                                               │
│ Trace detail: [ Summary ▼ ]  Download JSON [ ] │
└───────────────────────────────────────────────┘
```

### Replay Tab – Validation Panel
```
┌────────────────────────────────────────────────────────────────────────────┐
│ EUDIW OpenID4VP Simulator                                                  │
├────────────────────────────────────────────────────────────────────────────┤
│ Tabs: [ Evaluate □ ] [ Replay ▣ ]                                         │
│                                                                            │
│ Choose replay mode                                                         │
│   (•) Inline parameters     Paste VP Token JSON from an external wallet.   │
│   ( ) Stored credential     Use an ingested VP Token fixture.              │
│                                                                            │
│ Load a sample vector (inline mode)                                         │
│   Preset sample        [ Select sample ▼ ]   ( Apply )                     │
│                                                                            │
│ Inline VP Token JSON                                                       │
│   { "vp_token": "...", "presentation_submission": { ... } }                │
│                                                                            │
│ Stored VP Token preset [ pid-haip-replay ▼ ]   ( Load )                    │
│                                                                            │
│ Response mode override [ fragment ▼ ]                                      │
│                                                                            │
│ Trusted authorities policy [ EU PID Issuer (aki) ▼ ]                       │
│                                                                            │
│ [ Validate submitted presentation ]                                        │
└────────────────────────────────────────────────────────────────────────────┘
```


## Branch & Scenario Matrix
**Success branches**
1. Build DCQL query and authorization request (nonce/state, HAIP metadata).
2. Render QR/deep link; wallet fetches request (URL or `request_uri` POST).
3. Deterministic wallet composes SD-JWT VC or DeviceResponse, packages VP Token, returns via selected response mode.
4. Verifier validates presentations, Trusted Authorities, records telemetry, and presents sanitized results (one section per presentation if multiple) to facades.

**Validation checks**
- Reject requests missing DCQL/scope or supplying both.
- Enforce nonce freshness and request ID uniqueness.
- Ensure DCQL meta fields (`meta.vct_values`, `meta.doctype_value`) are present and well-formed.
- Validate VP Token structure, per-format cryptography, KB-JWT binding (`aud`, `nonce`, `sd_hash`), DeviceResponse COSE/MSO signatures, and Trusted Authorities criteria.
- Verify encryption round-trip for `direct_post.jwt`.
- Confirm multi-presentation responses render collapsible sections with matching trace identifiers.

**Failure paths**
- Invalid request parameters → `invalid_request`.
- Unsupported or missing Trusted Authorities match → `invalid_scope`.
- Wallet simulator disabled or fixture missing → `wallet_unavailable`.
- Presentation validation failure (signature, disclosure mismatch, claims pointer violation) → `invalid_presentation`.
- Encryption failure or missing HAIP keys → `invalid_request` with actionable messaging.

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-006-01 | HAIP authorization request (signed + encrypted) succeeds with deterministic nonce/state, QR/deep-link payloads, and telemetry `oid4vp.request.created`. |
| S-006-02 | Missing DCQL vs scope conflict returns `invalid_request` problem details, surfaces the error inside the UI result card, and logs `oid4vp.auth.validation_failure`. |
| S-006-03 | Wallet simulate (Evaluate inline, multi-presentation preset) emits deterministic VP Token JSON, per-presentation trace IDs, and consistent output across CLI/REST/UI. |
| S-006-04 | Validation failures (disclosure mismatch / Trusted Authority miss) yield `invalid_presentation` or `invalid_scope`, highlight the failing credential, and propagate violations to CLI/REST/UI. |
| S-006-05 | Global include-trace toggle disables verbose traces while telemetry records `includeTrace=false` and the UI hides the trace dock. |
| S-006-06 | Baseline (unenforced) profile keeps encryption optional, shows the baseline warning banner, and preserves read-only DCQL previews. |
| S-006-07 | Stored replay/evaluate journeys hydrate presets, keep sensitive fields hidden, and emit stored-presentation telemetry plus digest-only traces. |
| S-006-08 | Inline “Load sample vector” fills SD-JWT inputs without switching modes and remains editable prior to submission. |
| S-006-09 | Fixture ingestion toggles synthetic vs conformance bundles, captures provenance metadata, and publishes `oid4vp.fixtures.ingested`. |
| S-006-10 | Trusted Authority metadata (labels, AKI, policy) flows through CLI/REST/UI traces without exposing raw secrets. |
| S-006-11 | Multi-presentation results render collapsible sections with copy/download hooks and `data-trace-id` attributes aligned to the verbose trace dock. |
| S-006-12 | Deep-link query params (`?protocol=eudiw&tab=…&mode=…`) hydrate Evaluate/Replay state and survive back/forward navigation history. |
| S-006-13 | DeviceResponse (mdoc) simulations produce deterministic hashes, satisfy claim pointers, and trigger encryption hooks in HAIP mode. |
| S-006-14 | HAIP encryption failures (missing verifier keys / decrypt errors) raise `invalid_request` with actionable messaging and matching telemetry. |
| S-006-15 | Problem-details propagation keeps status codes, violation arrays, and telemetry aligned across CLI/REST/UI when validation fails. |
| S-006-16 | Live Trusted Authority ingestion (ETSI TL / OpenID Federation) refreshes trusted metadata snapshots and emits provenance telemetry. _Pending scenario tracked via T-006-23._ |


## Test Strategy

- **Core (`core.eudi.openid4vp`)** – Property-based and fixture-driven tests covering DCQL evaluation, Trusted Authority policies (positive/negative), SD-JWT disclosure hashing/KJ-JWT binding, DeviceResponse COSE signature validation, encryption helpers (round-trip JWE), and deterministic seed replay.
- **Application (`application.eudi.openid4vp`)** – Unit tests asserting telemetry frames (events, sanitized fields), trace construction (masked nonce/state, hashed payloads), HAIP enforcement toggles (`invalid_request` on encryption failure), multi-presentation handling, and baseline profile banner semantics.
- **REST** – MockMvc suites for each endpoint verifying status codes, JSON schema (required fields, enums), verbose toggling, problem-details responses, and parity with the specification examples. Snapshot tests capture OpenAPI changes introduced by the new contract.
- **CLI** – Picocli command tests covering option validation, preset vs inline credential flows, `--verbose` toggling, JSON parity with REST responses, exit codes, and help output.
- **UI** – JS unit and Selenium tests validating two-column layout, baseline banner visibility, DCQL preview read-only behaviour, sample loader, global trace dock integration (including copy/download controls), and multi-presentation collapsible sections with matching trace keys.
- **Fixtures & ingestion** – Tests ensuring synthetic vs conformance fixture toggles load the expected records, provenance metadata is captured, and seeding endpoints/commands remain idempotent.
- **Performance/metrics** – Lightweight regression capturing latency for encrypted vs non-encrypted flows (≤200 ms target) with assertions on telemetry latency fields.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-006-01 | `OpenId4VpAuthorizationRequest` (profile, responseMode, nonce/state, DCQL query metadata) | core, application, REST |
| DO-006-02 | `OpenId4VpWalletPresentation` (SD-JWT/mDoc payloads, Trusted Authority verdicts, telemetry hashes) | core, application, REST, CLI, UI |
| DO-006-03 | `TrustedAuthorityRecord` (AKI, ETSI TL/OpenID Federation metadata, friendly label) | core, application |
| DO-006-04 | `FixtureDataset` (synthetic vs conformance provenance, stored presentations) | core, application, REST |
| DO-006-05 | `Oid4vpTracePayload` (request/wallet/validation diagnostics, masked identifiers) | application, REST, CLI, UI |

### API Routes / Services

All endpoints live under `/api/v1/eudiw/openid4vp` and accept/return UTF-8 JSON. A `verbose=true` query parameter (or request field where noted) enables verbose traces; omit or set `false` to exclude the `trace` object. Unless explicitly stated, hashed values use lowercase hex SHA-256 and all identifiers are opaque simulator-generated strings.

| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-006-01 | REST `POST /api/v1/eudiw/openid4vp/requests` | Create HAIP-compliant authorization requests plus QR/deep-link payloads. | `includeQrAscii` toggles ASCII QR; `verbose=true` appends sanitized trace + telemetry echoes. |
| API-006-02 | REST `POST /api/v1/eudiw/openid4vp/wallet/simulate` | Generate deterministic wallet responses for stored or inline credentials. | Inline SD-JWT/mdoc payloads override presets; optional Trusted Authority policy parameter. |
| API-006-03 | REST `POST /api/v1/eudiw/openid4vp/validate` | Validate VP Tokens or DeviceResponses supplied by external wallets. | Accepts stored preset IDs or inline JSON; emits problem-details failures with violation arrays. |
| API-006-04 | REST `POST /api/v1/eudiw/openid4vp/presentations/seed` | Seed stored presentations from synthetic or conformance fixtures. | Records provenance metadata, returns counts only, no trace payload. |

#### `POST /api/v1/eudiw/openid4vp/requests`

Create a HAIP-aligned authorization request and optional QR payload.

Request:
```json
{
  "profile": "HAIP",
  "responseMode": "DIRECT_POST_JWT",
  "dcqlPreset": "pid-haip-baseline",
  "dcqlOverride": null,
  "signedRequest": true,
  "includeQrAscii": true
}
```

Response (`trace` only present when `?verbose=true`):
```json
{
  "requestId": "7K3D-XF29",
  "profile": "HAIP",
  "requestUri": "https://sim.example/oid4vp/request/7K3D-XF29",
  "authorizationRequest": {
    "clientId": "x509_hash:3b07…",
    "nonce": "******F29",
    "state": "******RZ1",
    "responseMode": "direct_post.jwt",
    "presentationDefinition": { "...": "sanitised" }
  },
  "qr": {
    "ascii": "████ ▓▓▓ …",
    "uri": "openid-vp://?request_uri=https://…/7K3D-XF29"
  },
  "trace": {
    "requestId": "7K3D-XF29",
    "profile": "HAIP",
    "dcqlHash": "08c2…",
    "trustedAuthorities": [ "aki:s9tIpP…" ],
    "nonceFull": "e8618e14723cf29"
  },
  "telemetry": {
    "event": "oid4vp.request.created",
    "durationMs": 12,
    "encryptionEnforced": true
  }
}
```

#### `POST /api/v1/eudiw/openid4vp/wallet/simulate`

Generate a deterministic wallet response for a given request. Inline credential payloads override fixture presets when supplied.

Request:
```json
{
  "requestId": "7K3D-XF29",
  "walletPreset": "pid-haip-baseline",
  "inlineSdJwt": {
    "credentialId": "pid-eu.europa.ec.eudi.pid.1",
    "format": "dc+sd-jwt",
    "compactSdJwt": "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9…",
    "disclosures": [
      "WyJjbGFpbXMiLCJnaXZlbl9uYW1lIiwiQWxpY2lhIl0=",
      "WyJjbGFpbXMiLCJmYW1pbHlfbmFtZSIsIlJpdmVyYSJd"
    ],
    "kbJwt": "eyJhbGciOiJFZERTQSJ9…",
    "trustedAuthorityPolicies": [
      "aki:s9tIpP…"
    ]
  },
  "inlineMdoc": null,
  "trustedAuthorityPolicy": "aki:s9tIpP…",
  "profile": "HAIP"
}
```

If `walletPreset` is omitted, `inlineSdJwt.credentialId` and `inlineSdJwt.format` become required so the simulator can stamp the presentation metadata without consulting fixtures. The optional `inlineSdJwt.trustedAuthorityPolicies[]` array seeds Trusted Authority matching for inline-only journeys. Disclosure hashes are always recomputed from the supplied `inlineSdJwt.disclosures` (or preset disclosures when no inline overrides) to satisfy Option A’s determinism requirement.

Response:
```json
{
  "requestId": "7K3D-XF29",
  "status": "SUCCESS",
  "responseMode": "direct_post.jwt",
  "presentations": [
    {
      "credentialId": "pid-eu.europa.ec.eudi.pid.1",
      "format": "dc+sd-jwt",
      "holderBinding": true,
      "trustedAuthorityMatch": "aki:s9tIpP…",
      "vpToken": {
        "vp_token": "eyJ2…",
        "presentation_submission": { "...": "…" }
      }
    }
  ],
  "trace": {
    "walletPreset": "pid-haip-baseline",
    "vpTokenHash": "6f5a…",
    "kbJwtHash": "f2c1…",
    "nonceFull": "e8618e14723cf29",
    "latencyMs": 142
  },
  "telemetry": {
    "event": "oid4vp.wallet.responded",
    "durationMs": 142,
    "presentations": 1
  }
}
```

#### `POST /api/v1/eudiw/openid4vp/validate`

Validate an externally supplied VP Token (inline JSON or stored preset). Response mirrors the wallet simulate schema with `status` signalling `SUCCESS` or `FAILED`, plus an `errors` array when validation fails. Trace includes per-credential diagnostics (hashes, Claims Path Pointer matches, Trusted Authority verdict, encryption verification flags).

Validation mode MUST:
- Invoke the shared `TrustedAuthorityEvaluator` before emitting telemetry or traces so `trustedAuthorityMatch` values align with wallet simulations (S1/S2).
- Reuse `Oid4vpProblemDetails`/`Oid4vpValidationException` for error propagation; Trusted Authority misses raise `invalid_scope`, encryption issues raise `invalid_request`, and wallet unavailability maps to `wallet_unavailable`.
- Emit the same telemetry events (`oid4vp.response.validated`/`oid4vp.response.failed`) and redaction rules documented earlier, ensuring only hashed payloads and friendly Trusted Authority labels leave the service.

#### `POST /api/v1/eudiw/openid4vp/presentations/seed`

Seed stored presentations from fixtures or imported conformance bundles. Request specifies the source (`"synthetic"` or `"conformance"`), optional fixture identifiers, and provenance metadata. Response includes counts of created/updated presentations and omits trace data.

All endpoints surface problem-details errors with `type`, `title`, `status`, and `detail`. Validation errors include a `violations[]` array (`field`, `message`) consistent with existing simulator conventions.

### CLI Commands / Flags

The Picocli surface adopts three top-level commands under `eudiw` and mirrors REST payloads. All commands honour `--output-json` (pretty-print REST-equivalent payloads using the shared `event/status/reasonCode/telemetryId/sanitized/data` envelope from ADR-0014) and `--verbose`/`--no-verbose` flags (default `--no-verbose`).

| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-006-01 | `eudiw request create` | Build HAIP/baseline authorization requests, optionally render ASCII QR, emit masked trace data when verbose. |
| CLI-006-02 | `eudiw wallet simulate` | Replay fixture presets or inline SD-JWT/mdoc payloads with Trusted Authority policy overrides. |
| CLI-006-03 | `eudiw validate` | Validate VP Tokens/DeviceResponses (preset or inline), emit problem-details on failure, mirror REST schema. |

**CLI-006-01 – `eudiw request create`**
Required options: `--profile`, `--response-mode`, `--dcql-preset` _or_ `--dcql-json`. Optional toggles: `--unsigned` (disable HAIP JAR), `--qr` (render ASCII QR), `--seed <path>` (override deterministic seed). Returns request metadata and, when verbose, a trace section containing masked nonce/state and DCQL hash.

**CLI-006-02 – `eudiw wallet simulate`**
Required: `--request-id`, `--profile`. Either `--wallet-preset <id>` or inline credential inputs via `--sdjwt <path>` / `--disclosure <path>` / `--kb-jwt <path>` / `--mdoc <path>`. Optional `--trusted-authority <policy-id>`. Text output summarises credential IDs, response mode, Trusted Authority matches; verbose output appends trace diagnostics (hashes, latency).

**CLI-006-03 – `eudiw validate`**
Accepts `--preset <id>` or `--vp-token <path>` (inline JSON), optional `--response-mode-override` and `--trusted-authority`. Reports success/failure, problem-details when invalid, and verbose traces with per-credential diagnostics. Exit codes: `0` success, `2` validation failure, `3` request/setup error.

Command help must reference the REST endpoints for cross-facade parity and highlight that verbose tracing exposes masked but still sensitive hashes.

### Telemetry Events

| ID | Event name | Fields / Redaction summary |
|----|------------|----------------------------|
| TE-006-01 | `oid4vp.request.created` | `event`, `requestId`, `profile`, `responseMode`, `durationMs`, `haipEnforced`, masked nonce/state suffixes, Trusted Authority labels. |
| TE-006-02 | `oid4vp.request.qr.rendered` | Adds `qrRendered`, `qrFormat`, and optional ASCII preview hash; never emits raw URIs. |
| TE-006-03 | `oid4vp.wallet.responded` | `presentations` (count), `durationMs`, `trustedAuthorityDecision`, profile/mode metadata; hashed VP Token/KJ-JWT/deviceResponse values restricted to traces. |
| TE-006-04 | `oid4vp.response.validated` | Mirrors wallet responded but scoped to validation flows; includes sanitized credential identifiers and Trusted Authority verdicts. |
| TE-006-05 | `oid4vp.response.failed` | Same fields as TE-006-04 plus `failureReason` aligned to problem-details `type`. |
| TE-006-06 | `oid4vp.fixtures.ingested` | Reports dataset source, count of records, provenance hash, `synthetic|conformance` flag; omits path-level details. |

### Fixtures & Sample Data

| ID | Path | Purpose |
|----|------|---------|
| FX-006-01 | ``docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/sdjwt-vc`/*.json` | SD-JWT VC cleartext claims, disclosures, KB-JWT payloads, metadata driving Generate mode. |
| FX-006-02 | ``docs/test-vectors/eudiw/openid4vp/fixtures/synthetic/mdoc`/*.json` | ISO/IEC 18013-5 DeviceResponse payloads plus diagnostic CBOR text for deterministic tests. |
| FX-006-03 | ``docs/test-vectors/eudiw/openid4vp/trust`/**/*` | Trusted Authority anchors, policies, and snapshots (friendly labels + AKI metadata). |
| FX-006-04 | ``docs/test-vectors/eudiw/openid4vp/stored/presentations`/*.json` | Stored VP Tokens mapped to presets for Replay mode. |
| FX-006-05 | ``docs/trust/snapshots`/<timestamp>/manifest.json` + ``docs/test-vectors/eudiw/openid4vp/fixtures`/*/provenance.json` + `presentations/seed` payloads | Fixture dataset provenance for ingestion, including source hashes, LOTL/Member TL sequences, and ingest timestamps surfaced via telemetry + REST metadata. |

### UI States

| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-006-01 | REST/CLI response payloads | Always include `status`, `profile`, `responseMode`, `presentations[].credentialId/format/trustedAuthorityMatch`, inline VP Token JSON (FR-006-28), and `telemetry`. `trace` only appears when `verbose=true`/`--verbose`, exposing masked `nonce`/`state`, `dcqlHash`, `vpTokenHash`, `kbJwtHash`, `trustedAuthorities[]`, latency, and per-credential diagnostics; absent otherwise. |
| UI-006-02 | Operator UI result cards | Evaluate/Replay panels render status badge, response mode, credential summary rows, Trusted Authority labels, inline VP Token viewer, and per-presentation copy/download controls. Trace dock entries share `data-trace-id` values with card sections for cross-highlighting. |
| UI-006-03 | Operator UI baseline banner | When profile ≠ `HAIP`, display “Baseline mode – HAIP enforcement disabled” banner in the result card and set `haipEnforced=false` in trace payloads for telemetry correlation. |
| UI-006-04 | Verbose trace alerts | Global console verbose toggle governs the shared trace dock; when enabled, show the standard warning (“Verbose traces expose hashed identifiers and diagnostic metadata. Use only in trusted environments.”). No protocol-specific verbose checkbox is allowed on the EUDIW tab. |

When verbose tracing is enabled, facades must log the shared warning while keeping hashes masked; master keys and raw disclosures never appear.

## Telemetry & Observability
- Mint deterministic `requestId` tokens for every authorization request and treat them as internal identifiers: telemetry and trace payloads may include them, but the UI/REST payloads surface them only inside verbose trace envelopes.
- Emit JSON telemetry via `TelemetryContracts` with event families listed in FR-006-14 (see Interface & Contract Catalogue → Telemetry Events for the canonical matrix).
- Include duration metrics, encryption flags, credential counts, and Trusted Authority decision results while masking nonces (show trailing six characters only).
- Provide verbose trace payloads (disabled by default for REST/CLI, opt-in via query flag) showing sanitized request/response envelopes.

### Telemetry Redaction Guidance
- `oid4vp.request.created` / `oid4vp.request.qr.rendered`
  - Allowed fields: `event`, `requestId`, `profile`, `responseMode`, `durationMs`, `qrRendered` (boolean), `haipEnforced`, and `trustedAuthorities[]` (each entry limited to `{ "aki": "…", "label": "…" }`).
  - Redaction rules: mask `nonce`/`state` everywhere outside verbose traces (show trailing six characters only), hash DCQL payloads to `dcqlHash`, and exclude raw QR/request URIs from telemetry—full values remain restricted to trace payloads.
- `oid4vp.wallet.responded`
  - Allowed fields: `event`, `requestId`, `presentations` (count only), `durationMs`, `haipEnforced`, `trustedAuthorityDecision` (`MATCH`/`MISS`), `profile`, `responseMode`.
  - Redaction rules: never log VP Token, KB-JWT, disclosure, or DeviceResponse payloads. Emit `vpTokenHash`, `kbJwtHash`, and `deviceResponseHash` inside verbose traces instead. Holder-binding data surfaces as a boolean flag only.
- `oid4vp.response.validated` / `oid4vp.response.failed`
  - Validation services MUST reuse `TrustedAuthorityEvaluator` decisions so telemetry remains comparable with wallet simulations. Include sanitized identifiers (`requestId`, `credentialId`, `trustedAuthorityMatch`) and hashed payload diagnostics only; violation details stay inside `Oid4vpProblemDetails` and verbose traces.
- All telemetry frames MUST set `sanitized=true` and avoid echoing inline credential metadata beyond `credentialId`, `format`, and Trusted Authority labels. Claims Path Pointer matches, AKI comparison inputs, encryption verdicts, and similar diagnostics belong exclusively in the verbose `trace` envelope.

Validation flows (S5) inherit this policy: they must trigger the same telemetry events, call `TrustedAuthorityEvaluator` before returning results, and raise `Oid4vpValidationException` (`invalid_scope`) whenever Trusted Authority requirements fail so REST/CLI facades surface consistent problem-details.

## Documentation Deliverables
- Update roadmap entry #40 with simulator milestones and Trusted Authority ingestion guardrails.
- Refresh knowledge map relationships for `application.eudi.openid4vp`, TrustedAuthorityEvaluator, and fixture ingestion services.
- Maintain how-to guides for REST, CLI, and operator UI (`docs/2-how-to/use-eudiw-*`).
- Append telemetry snapshot reference ([docs/3-reference/eudiw-openid4vp-telemetry-snapshot.md](docs/3-reference/eudiw-openid4vp-telemetry-snapshot.md)).

## Fixtures & Sample Data
- Refer to Interface & Contract Catalogue → Fixtures & Sample Data for canonical IDs/paths.
- Directory layout under ``docs/test-vectors/eudiw/openid4vp`/`:

  - `keys/` stores synthetic issuer/holder key material (JWKs, PEM cert chains) referenced by fixtures.
  - `fixtures/synthetic/sdjwt-vc/<fixture-id>/` (cleartext claim JSON, salted digest map, optional compact SD-JWT, disclosures, KB-JWT body, metadata).
  - `fixtures/synthetic/mdoc/<fixture-id>/` (Base64 DeviceResponse CBOR, CBOR diagnostic text, metadata).
  - `trust/anchors/x509/<issuer>/` (PEM chain fragments), `trust/policy/trusted_authorities.dcql.json`, and `trust/snapshots/<preset-id>.json` capturing friendly labels plus stored presentation mappings.
  - `stored/presentations/<presentation-id>.json` describing seeded VP Tokens for stored mode.
  - `seeds/default.seed` for deterministic nonce/state.
- Trusted Authority ingestion must run before operators rely on ETSI Trust List data; until the loader populates friendly labels, UI elements render `(pending)` while still filtering by AKI hashes.
- Encryption helpers derive missing P-256 public coordinates from the fixture private keys whenever a JWK omits them so HAIP `direct_post.jwt` encryption/decryption stays deterministic across presets.
- Loader toggles between synthetic and imported conformance bundles; ingestion captures provenance metadata (source version, hash).
- Validation utilities recompute SD-JWT disclosure hashes, KB-JWT `sd_hash`, DeviceResponse COSE signatures/MSO hashes, and Trusted Authority membership.

## Spec DSL
```
domain_objects:
  - id: DO-006-01
    name: OpenId4VpAuthorizationRequest
    fields: [profile, responseMode, dcqlPreset, dcqlOverride, signedRequest, includeQrAscii]
  - id: DO-006-02
    name: OpenId4VpWalletPresentation
    fields: [credentialId, format, holderBinding, trustedAuthorityMatch, vpToken, deviceResponse]
  - id: DO-006-03
    name: TrustedAuthorityRecord
    fields: [id, label, aki, source]
  - id: DO-006-04
    name: FixtureDataset
    fields: [id, type, provenance, path]
  - id: DO-006-05
    name: Oid4vpTracePayload
    fields: [requestId, profile, nonceFull, stateFull, dcqlHash, vpTokenHash, kbJwtHash, deviceResponseHash]
routes:
  - id: API-006-01
    method: POST
    path: /api/v1/eudiw/openid4vp/requests
    description: Create authorization request, QR/deep link output
  - id: API-006-02
    method: POST
    path: /api/v1/eudiw/openid4vp/wallet/simulate
    description: Generate deterministic wallet responses
  - id: API-006-03
    method: POST
    path: /api/v1/eudiw/openid4vp/validate
    description: Validate external VP Tokens
  - id: API-006-04
    method: POST
    path: /api/v1/eudiw/openid4vp/presentations/seed
    description: Seed stored presentations from fixtures or conformance bundles
cli_commands:
  - id: CLI-006-01
    command: eudiw request create
    description: Build authorization requests with HAIP toggles
  - id: CLI-006-02
    command: eudiw wallet simulate
    description: Replay fixture or inline credentials
  - id: CLI-006-03
    command: eudiw validate
    description: Verify VP Tokens / DeviceResponses
telemetry_events:
  - id: TE-006-01
    event: oid4vp.request.created
  - id: TE-006-02
    event: oid4vp.request.qr.rendered
  - id: TE-006-03
    event: oid4vp.wallet.responded
  - id: TE-006-04
    event: oid4vp.response.validated
  - id: TE-006-05
    event: oid4vp.response.failed
  - id: TE-006-06
    event: oid4vp.fixtures.ingested
fixtures:
  - id: FX-006-01
    path: docs/test-vectors/eudiw/openid4vp/pid-sd-jwt.json
    description: Synthetic SD-JWT PID bundle
  - id: FX-006-02
    path: docs/test-vectors/eudiw/openid4vp/pid-mdoc.json
    description: Synthetic ISO/IEC 18013-5 DeviceResponse bundle
  - id: FX-006-03
    path: docs/test-vectors/eudiw/openid4vp/provenance.json
    description: Fixture dataset metadata for ingestion
ui_states:
  - id: UI-006-01
    description: Generate mode preset workflow (DCQL preview + baseline banner)
  - id: UI-006-02
    description: Validate mode with VP Token JSON upload and error surfacing
  - id: UI-006-03
    description: Multi-presentation result cards with collapsible sections and trace IDs
  - id: UI-006-04
    description: Baseline vs HAIP banner + response mode toggle indicators
```

### Native Java API
| ID | Entry point | Description | Notes |
|----|-------------|-------------|-------|
| NJ-006-01 | `io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService` | Application-level wallet simulation service used as the Native Java API seam for generating HAIP/Baseline VP Tokens from presets or inline SD-JWT/mdoc payloads. | Mirrors CLI/REST wallet simulation semantics; callers construct `SimulateRequest` records and consume `SimulationResult` as the façade DTO. Governed by Feature 014 (FR-014-02/04) and ADR-0007, with usage documented in [docs/2-how-to/use-eudiw-from-java.md](docs/2-how-to/use-eudiw-from-java.md). |
| NJ-006-02 | `io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService` | Application-level validation service used as the Native Java API seam for validating VP Tokens/DeviceResponses and Trusted Authorities decisions. | Mirrors CLI/REST validate semantics; callers construct `ValidateRequest` records and consume `ValidationResult` as the façade DTO. Governed by Feature 014 (FR-014-02/04) and ADR-0007, with usage documented in [docs/2-how-to/use-eudiw-from-java.md](docs/2-how-to/use-eudiw-from-java.md). |

## Appendix

### Architecture & Design Notes
- Extend `core` with format adapters (`core.eudi.openid4vp`) for DCQL evaluation, SD-JWT VC cryptography (disclosure hashing, KB-JWT), and ISO mdoc DeviceResponse parsing using CBOR/COSE helpers. Inline credential submission reuses the same adapters to hydrate ad-hoc wallet inputs during Generate mode.
- Introduce `application.eudi.openid4vp` services that assemble authorization requests, manage deterministic state (nonce, state, request identifiers), dispatch telemetry, and orchestrate wallet simulations against fixtures.
- Reuse facade modules:
  - `rest-api`: REST controllers for request creation, wallet simulation, and telemetry streams; integrate with existing problem-details handling and OpenAPI snapshots.
  - `cli`: commands to create requests, render ASCII QR codes, drive simulated responses, and validate VP Tokens.
  - `ui`: upgrade the EUDIW tab to render request metadata, ASCII QR preview, simulation actions, and trace inspection, reusing the global verbose dock and trace payload contract.
- Fixtures under ``docs/test-vectors/eudiw/openid4vp`/` capture credential payloads, disclosures, DeviceResponse blobs, trust metadata, deterministic seeds, synthetic issuer/holder keys (stored under `keys/`), and stored presentation batches consumed by the seeding workflow.
- Telemetry passes through `TelemetryContracts` with new `oid4vp.*` event families, ensuring PII redaction (hash or count metrics only) while allowing correlation via request identifiers.

### Dependency Considerations
- Base implementation uses existing crypto helpers; HAIP encryption and mdoc COSE handling may require:
  - Nimbus JOSE + JWT for `ECDH-ES` + `A128GCM` JWE handling.
  - COSE-JAVA (or equivalent) plus a CBOR parser for DeviceResponse validation.
  - ZXing (optional) for QR PNG generation; ASCII QR renderer remains default.
- Any new dependency needs explicit owner approval; until then, keep seams behind interfaces so fixture-driven tests can run against in-house stubs.

### References
- OpenID for Verifiable Presentations 1.0 (Final, 2025-07-09): response types, DCQL, response modes, validation, error semantics.
- OpenID4VC High Assurance Interoperability Profile 1.0 (draft 05): HAIP profile, encryption, identifiers, Trusted Authorities use.
- IETF draft-ietf-oauth-selective-disclosure-jwt-22: SD-JWT selective disclosure, KB-JWT structure.
- IETF draft-ietf-oauth-sd-jwt-vc-12: SD-JWT VC media types (`application/dc+sd-jwt`).
- ISO/IEC 18013-5 (mobile driving licence) – DeviceResponse enforcement.
- EUDI PID Rulebook Annex 3.1: PID namespace `eu.europa.ec.eudi.pid.1`.
- OpenID4VP conformance suite repository (OpenID Foundation).

### Follow-up Items
- Complete ETSI Trust List (`etsi_tl`) and OpenID Federation ingestion before extending further trust features (tracked by T3999/T40F3).
- Evaluate conformance test automation to replay official OpenID4VP vectors regularly.
- Track same-device/DC-API journeys as a separate specification once prioritized.

### Response & Profile Options
- **Profile presets**: `HAIP` (default), `Baseline` (unenforced), future profile slots must be documented before use.
- **DCQL query presets**: `pid-haip-baseline`, `pid-minimal`, `custom` (loads inlined JSON). Additional presets must declare name, credential IDs, claim paths, and trusted authority filters (Authority Key Identifier, ETSI TL, etc.) in fixtures.
- **Response modes**: `fragment`, `direct_post`, `direct_post.jwt` (HAIP default).
