# Feature 040 – EUDIW OpenID4VP Simulator

_Status: Draft_  
_Last updated: 2025-11-02_

## Overview
Deliver a deterministic simulator for remote (cross-device) OpenID for Verifiable Presentations (OpenID4VP 1.0) flows that align with the High Assurance Interoperability Profile (HAIP). The simulator plays both verifier and wallet roles so REST, CLI, and operator UI facades can demonstrate complete presentation exchanges without external wallets. The scope includes SD-JWT VC (`application/dc+sd-jwt`) and ISO/IEC 18013-5 mdoc (`mso_mdoc`) credential formats, HAIP-mandated encryption for `direct_post.jwt`, Trusted Authorities filtering, and PID fixtures (`eu.europa.ec.eudi.pid.1`). Synthetic fixtures ship first with an ingestion seam for official EU conformance vectors once released.

## Goals
- Generate HAIP-compliant OpenID4VP authorization requests (DCQL queries, nonce/state, optional JAR) and surface QR / deep-link handoffs for cross-device wallets.
- Produce deterministic wallet responses (SD-JWT VC + optional KB-JWT, ISO/IEC 18013-5 DeviceResponse) and validate them against per-format rules and Trusted Authorities constraints.
- Wire simulator services through existing modules (`core`, `application`, `rest-api`, `cli`, `ui`) with shared telemetry, verbose traces, and test vector loaders.
- Maintain test-first delivery with fixture-backed regression coverage, encryption verification, and end-to-end integrations across facades.

## Non-Goals
- Same-device/DC-API transport flows and wallet-device communication (track separately).
- OpenID4VCI issuance simulators or credential provisioning pipelines.
- Trust list federation resolution beyond the initial Authority Key Identifier matching (future work will address `etsi_tl` and OpenID Federation).

## Clarifications
- 2025-11-01 – Remote OpenID4VP redirect/QR flows only; DC-API journeys remain out of scope (user confirmation).
- 2025-11-01 – Support both SD-JWT VC and ISO/IEC 18013-5 mdoc presentations with deterministic fixtures (user confirmation).
- 2025-11-01 – Align behaviour with the HAIP profile (encryption, identifiers, trust marks) while allowing toggles for non-HAIP baseline testing (user confirmation).
- 2025-11-01 – Simulator covers both verifier request orchestration and wallet presentations so each facade can demonstrate the complete exchange (user confirmation).
- 2025-11-01 – Hybrid fixture strategy: ship synthetic vectors now, ingest official OpenID4VP conformance bundles (with provenance metadata) once available (user confirmation).
- 2025-11-01 – Operator console must distinguish between **Generate** (fixture-backed) and **Validate** (paste/upload VP Token + optional metadata) modes so operators can either simulate or verify external submissions (user directive).
- 2025-11-01 – Generate mode must support preset wallet selection and inline credential entry (SD-JWT disclosures or mdoc DeviceResponse) so operators can simulate ad-hoc credentials without editing fixture files (user directive).
- 2025-11-01 – Authorization requests receive internal simulator identifiers for telemetry/replay; the UI hides them except within verbose traces (user directive).
- 2025-11-01 – DCQL preview renders formatted JSON in a read-only text area with the simulator’s standard read-only background styling (user directive).
- 2025-11-01 – Result panels expose VP Token JSON inline (read-only, horizontal scroll) so operators can inspect payloads without enabling verbose trace (user directive).
- 2025-11-01 – Synthetic issuer/holder key material (SD-JWT signer, KB-JWT keys, mdoc issuer certs) ship with fixtures and are scoped to simulator use only (user directive).
- 2025-11-01 – Inline sample selector loads fixture-defined vectors into SD-JWT/disclosure/device response fields for quick demonstrations (user directive).
- 2025-11-01 – Authority Key Identifier (DCQL `aki`) remains the initial Trusted Authority filter; presets store friendly labels as metadata so the UI can surface a name alongside the `aki` value without altering the DCQL payload (user directive).
- 2025-11-01 – ETSI Trust List ingestion (fixtures + loader) precedes simulator implementation; UI defaults to `(pending)` until the ingestion step populates identifiers (user directive).
- 2025-11-01 – Operator UI provides separate **Evaluate** and **Replay** sub-tabs: Evaluate hosts Generate mode, Replay hosts Validate mode to mirror existing HOTP/TOTP/OCRA/FIDO2 layout (user directive).
- 2025-11-01 – Verbose tracing is controlled via the global console toggle and shared trace dock; EUDIW panels must not introduce local verbose checkboxes (user directive).
- 2025-11-01 – When HAIP-required encryption fails, the simulator surfaces an `invalid_request` problem detail and only offers a retry when HAIP enforcement is disabled (user directive).
- 2025-11-01 – Console deep links (`?protocol=eudiw&tab=<evaluate|replay>&mode=<inline|stored>`) must hydrate EUDIW state to keep parity with other tabs (user directive).
- 2025-11-01 – When DCQL requests multiple credentials, the result view renders one collapsible section per presentation with descriptor identifiers and per-section copy actions (user directive).
- 2025-11-01 – Stored credential mode mirrors other simulators: provide a “Seed sample presentations” action that imports fixture-backed records into the MapDB store for quick selection (user directive).

## Architecture & Design
- Extend `core` with format adapters (`core.eudi.openid4vp`) for DCQL evaluation, SD-JWT VC cryptography (disclosure hashing, KB-JWT), and ISO mdoc DeviceResponse parsing using CBOR/COSE helpers. Inline credential submission reuses the same adapters to hydrate ad-hoc wallet inputs during Generate mode.
- Introduce `application.eudi.openid4vp` services that assemble authorization requests, manage deterministic state (nonce, state, request identifiers), dispatch telemetry, and orchestrate wallet simulations against fixtures.
- Reuse facade modules:
  - `rest-api`: REST controllers for request creation, wallet simulation, and telemetry streams; integrate with existing problem-details handling and OpenAPI snapshots.
  - `cli`: commands to create requests, render ASCII QR codes, drive simulated responses, and validate VP Tokens.
  - `ui`: upgrade the EUDIW tab to render request metadata, ASCII QR preview, simulation actions, and trace inspection, reusing the global verbose dock and trace payload contract.
- Fixtures under `docs/test-vectors/eudiw/openid4vp/` capture credential payloads, disclosures, DeviceResponse blobs, trust metadata, deterministic seeds, synthetic issuer/holder keys (stored under `keys/`), and stored presentation batches consumed by the seeding workflow.
- Telemetry passes through `TelemetryContracts` with new `oid4vp.*` event families, ensuring PII redaction (hash or count metrics only) while allowing correlation via request identifiers.

## Functional Requirements
| ID | Requirement | Source |
|----|-------------|--------|
| F-040-01 | Authorization requests MUST use `response_type=vp_token` and include `nonce` for replay protection. | OID4VP §5.2, §5.6, §14.1 |
| F-040-02 | Requests MUST supply a DCQL query (`dcql_query`) or `scope` alias; supplying both or neither returns `invalid_request`. | OID4VP §5.5, §6, §8.5 |
| F-040-03 | Cross-device transport MUST support QR/URL (request by value) and `request_uri` (request by reference with optional POST retrieval). | OID4VP §5.4, §5.7, §5.10 |
| F-040-04 | Response modes MUST include `fragment` (default) and `direct_post`; `direct_post.jwt` MUST be supported when HAIP mode is active. | OID4VP §8.2–§8.3 |
| F-040-05 | Signed requests MUST follow HAIP guidance (`x509_hash` client identifier prefix, JAR with `request_uri`). Toggle enables unsigned requests for baseline mode. | HAIP §5.1 |
| F-040-06 | Simulator MUST implement DCQL Credential/Claims/Trusted Authorities queries with Claims Path Pointer semantics for JSON and ISO mdoc, and reject SD-JWT VC queries lacking `meta.vct_values` or mdoc queries lacking `meta.doctype_value`. | OID4VP §6–§7 |
| F-040-07 | Wallet simulator MUST resolve DCQL credential IDs to fixture-backed presentations and return a VP Token keyed by credential ID. | OID4VP §8.1 |
| F-040-08 | SD-JWT VC responses MUST emit `application/dc+sd-jwt` payloads, optional KB-JWT (holder binding) with `sd_hash`, `aud`, `nonce`. | SD-JWT VC §3.1; SD-JWT §4.3 |
| F-040-09 | ISO/IEC 18013-5 flows MUST return DeviceResponse containers (`mso_mdoc`), one per credential query when multiple are requested, with descriptor identifiers preserved for UI rendering. | HAIP §5.3.1; ISO/IEC 18013-5 §6 |
| F-040-10 | Verifier validation MUST verify SD-JWT signatures/disclosures, KB-JWT holder binding (`aud`, `nonce`, `sd_hash`), DeviceResponse COSE signatures/MSO hashes, and Claims Path Pointer selections, failing with `invalid_presentation` when any check fails. | OID4VP §8.6; SD-JWT §4.2; ISO/IEC 18013-5 |
| F-040-11 | Trusted Authorities filtering MUST at least implement Authority Key Identifier matching and return `invalid_request` when no issuers satisfy the query; placeholders for `etsi_tl` and OpenID Federation remain for follow-up. | OID4VP §6.1.1 |
| F-040-12 | VP Token error handling MUST map to OID4VP §8.5 (`invalid_request`, `invalid_scope`, `wallet_unavailable`, `invalid_presentation`) with problem-details payloads across facades. | OID4VP §8.5 |
| F-040-13 | Deterministic seed handling MUST ensure identical inputs yield identical nonces, request IDs, QR payloads, and wallet outputs. | Project requirement |
| F-040-14 | Telemetry MUST emit `oid4vp.request.created`, `oid4vp.request.qr.rendered`, `oid4vp.wallet.responded`, `oid4vp.response.validated`, `oid4vp.response.failed` with sanitized fields. | Project telemetry policy |
| F-040-15 | REST API MUST expose endpoints for request creation, wallet simulation, telemetry retrieval, and fixture ingestion, all covered by OpenAPI snapshots. | Project REST conventions |
| F-040-16 | CLI MUST provide commands to create requests, render QR ASCII, simulate wallet responses, and validate VP Tokens. | CLI parity directive |
| F-040-17 | Operator UI MUST present an updated tab with request metadata, ASCII QR preview, simulation controls, response traces, and status indicators. | UI placeholder upgrade directive |
| F-040-18 | Fixture loader MUST support synthetic datasets and ingest official OpenID4VP conformance bundles with provenance metadata. | Feature clarification |
| F-040-19 | PID fixtures for namespace `eu.europa.ec.eudi.pid.1` MUST be available in both SD-JWT and mdoc encodings for end-to-end tests. | PID Rulebook |
| F-040-20 | Encryption path MUST support `direct_post.jwt` using JWE `ECDH-ES` P-256 with `A128GCM`, gated behind HAIP flag but enabled by default. | HAIP §5; OID4VP §8.3 |
| F-040-20a | When HAIP-required encryption fails (missing keys, JWE error), the simulator MUST surface an `invalid_request` problem detail, log sanitized telemetry, and only offer a retry when HAIP enforcement is disabled. | HAIP §5; OID4VP §8.3 |
| F-040-21 | Simulator MUST accept and produce unsigned requests/responses when HAIP enforcement is disabled to support baseline experimentation, and the UI MUST display a prominent “Baseline (non-HAIP)” banner when this mode is active. | Implementation flexibility |
| F-040-22 | Facades MUST expose separate **Generate** (fixture-backed) and **Validate** (input VP Token) flows; UI MUST present these as Evaluate vs Replay sub-tabs, and validate mode MUST verify supplied VP Tokens and surface errors consistent with F-040-10/F-040-12. Inline vs stored selectors follow the project radio-button pattern. | Operator directive |
| F-040-22a | When DCQL returns multiple credential presentations, the UI MUST render one collapsible section per presentation with descriptor identifiers and per-section copy controls, and traces MUST annotate presentations with matching IDs. | Operator directive |
| F-040-23 | Generate mode MUST allow both preset wallet credentials (fixtures) and inline credential entry for SD-JWT (compact + disclosures + optional KB-JWT) and ISO mdoc DeviceResponse uploads before issuing presentations. | Operator directive |
| F-040-24 | UI MUST display a formatted DCQL JSON preview inline beneath the preset selector (read-only textarea matching existing styling) so operators can inspect credential IDs, claims, and Trusted Authorities before generation. | Operator directive |
| F-040-25 | DCQL presets MUST include friendly issuer labels for Trusted Authorities (`aki`, `etsi_tl`, etc.) as metadata, and the UI MUST render the label alongside the hash/value in a read-only display (e.g., “EU PID Issuer (aki: s9tIpP…)”) while emitting standard DCQL payloads without the label attribute. | Operator directive |
| F-040-26 | Result panels MUST render the VP Token presentation JSON inline (read-only, monospaced, horizontal scrolling enabled) so operators can inspect the payload without relying on verbose traces. | Operator directive |
| F-040-27 | Fixture bundles MUST include synthetic issuer signing keys, holder binding keys, and trust anchors sufficient to regenerate SD-JWT VC, KB-JWT, and mdoc presentations deterministically; these keys must remain confined to test fixtures. | Operator directive |
| F-040-28 | Inline mode must expose a "Load sample vector" selector that pre-populates all inline fields (SD-JWT, disclosures, DeviceResponse) from fixtures; stored mode remains unaffected. | Operator directive |
| F-040-29 | Console deep-links (`?protocol=eudiw&tab=<evaluate|replay>&mode=<inline|stored>`) MUST hydrate the EUDIW tab state just like other protocol tabs. | Operator directive |
| F-040-30 | REST/CLI verbose flags MUST reuse the shared `verbose` convention and emit traces via the existing `VerboseTracePayload` contract. | Project telemetry policy |
| F-040-31 | Provide a stored presentation seeding workflow (UI action, CLI command, REST endpoint) that loads fixture presentations into the MapDB store for later selection in stored mode. | Operator directive |

## Non-Functional Requirements
| ID | Requirement |
|----|-------------|
| N-040-01 | Preserve deterministic behaviour under a fixed seed (nonce, state, key order, QR payloads). |
| N-040-02 | Honour the project’s no-reflection policy across production and tests. |
| N-040-03 | Complete remote presentation round-trips in ≤200 ms in non-encrypted mode on a developer workstation; record encryption overhead metrics. |
| N-040-04 | Redact PII in logs/telemetry (only hash claim structures and counts). |
| N-040-05 | Keep documentation/roadmap/knowledge map in sync; run `./gradlew --no-daemon spotlessApply check` after every increment. |

## Success, Validation, and Failure Branches
### Success
1. Build DCQL query and authorization request (nonce/state, HAIP metadata).
2. Render QR/deep link; wallet fetches request (URL or `request_uri` POST).
3. Deterministic wallet composes SD-JWT VC or DeviceResponse, packages VP Token, returns via selected response mode.
4. Verifier validates presentations, Trusted Authorities, records telemetry, and presents sanitized results (one section per presentation if multiple) to facades.

### Validation Checks
- Reject requests missing DCQL/scope or supplying both.
- Enforce nonce freshness and request ID uniqueness.
- Ensure DCQL meta fields (`meta.vct_values`, `meta.doctype_value`) are present and well-formed.
- Validate VP Token structure, per-format cryptography, KB-JWT binding (`aud`, `nonce`, `sd_hash`), DeviceResponse COSE/MSO signatures, and Trusted Authorities criteria.
- Verify encryption round-trip for `direct_post.jwt`.
- Confirm multi-presentation responses render collapsible sections with matching trace identifiers.

### Failure Paths
- Invalid request parameters → `invalid_request`.
- Unsupported or missing Trusted Authorities match → `invalid_scope`.
- Wallet simulator disabled or fixture missing → `wallet_unavailable`.
- Presentation validation failure (signature, disclosure mismatch, claims pointer violation) → `invalid_presentation`.
- Encryption failure or missing HAIP keys → `invalid_request` with actionable messaging.

## Profile & Response Mode Options
- **Profile presets**: `HAIP` (default), `Baseline` (unenforced), future profile slots must be documented before use.
- **DCQL query presets**: `pid-haip-baseline`, `pid-minimal`, `custom` (loads inlined JSON). Additional presets must declare name, credential IDs, claim paths, and trusted authority filters (Authority Key Identifier, ETSI TL, etc.) in fixtures.
- **Response modes**: `fragment`, `direct_post`, `direct_post.jwt` (HAIP default).

## Data & Fixture Strategy
- Directory layout under `docs/test-vectors/eudiw/openid4vp/`:
  - `keys/` stores synthetic issuer/holder key material (JWKs, PEM cert chains) referenced by fixtures.
  - `fixtures/synthetic/sdjwt-vc/<fixture-id>/` (cleartext claim JSON, salted digest map, optional compact SD-JWT, disclosures, KB-JWT body, metadata).
  - `fixtures/synthetic/mdoc/<fixture-id>/` (Base64 DeviceResponse CBOR, CBOR diagnostic text, metadata).
  - `trust/anchors/x509/<issuer>/` (PEM chain fragments) and `trust/policy/trusted_authorities.dcql.json`.
  - `seeds/default.seed` for deterministic nonce/state.
- Loader toggles between synthetic and imported conformance bundles; ingestion captures provenance metadata (source version, hash).
- Validation utilities recompute SD-JWT disclosure hashes, KB-JWT `sd_hash`, DeviceResponse COSE signatures/MSO hashes, and Trusted Authority membership.

## Telemetry & Observability
- Emit JSON telemetry via `TelemetryContracts` with event families listed in F-040-14.
- Include duration metrics, encryption flags, credential counts, and Trusted Authority decision results while masking nonces (show trailing six characters only).
- Provide verbose trace payloads (disabled by default for REST/CLI, opt-in via query flag) showing sanitized request/response envelopes.

## REST Surface Contract

All endpoints live under `/api/v1/eudiw/openid4vp` and accept/return UTF-8 JSON. A `verbose=true` query parameter (or request field where noted) enables verbose traces; omit or set `false` to exclude the `trace` object. Unless explicitly stated, hashed values use lowercase hex SHA-256 and all identifiers are opaque simulator-generated strings.

### `POST /api/v1/eudiw/openid4vp/requests`

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

### `POST /api/v1/eudiw/openid4vp/wallet/simulate`

Generate a deterministic wallet response for a given request. Inline credential payloads override fixture presets when supplied.

Request:
```json
{
  "requestId": "7K3D-XF29",
  "walletPreset": "pid-haip-baseline",
  "inlineSdJwt": {
    "compactSdJwt": "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9…",
    "disclosures": [
      "WyJjbGFpbXMiLCJnaXZlbl9uYW1lIiwiQWxpY2lhIl0=",
      "WyJjbGFpbXMiLCJmYW1pbHlfbmFtZSIsIlJpdmVyYSJd"
    ],
    "kbJwt": "eyJhbGciOiJFZERTQSJ9…"
  },
  "inlineMdoc": null,
  "trustedAuthorityPolicy": "aki:s9tIpP…",
  "profile": "HAIP"
}
```

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

### `POST /api/v1/eudiw/openid4vp/validate`

Validate an externally supplied VP Token (inline JSON or stored preset). Response mirrors the wallet simulate schema with `status` signalling `SUCCESS` or `FAILED`, plus an `errors` array when validation fails. Trace includes per-credential diagnostics (hashes, Claims Path Pointer matches, Trusted Authority verdict, encryption verification flags).

### `POST /api/v1/eudiw/openid4vp/presentations/seed`

Seed stored presentations from fixtures or imported conformance bundles. Request specifies the source (`"synthetic"` or `"conformance"`), optional fixture identifiers, and provenance metadata. Response includes counts of created/updated presentations and omits trace data.

All endpoints surface problem-details errors with `type`, `title`, `status`, and `detail`. Validation errors include a `violations[]` array (`field`, `message`) consistent with existing simulator conventions.

## CLI Contract

The Picocli surface adopts three top-level commands under `eudiw` and mirrors REST payloads. All commands honour `--output-json` (pretty-print REST-equivalent payloads) and `--verbose`/`--no-verbose` flags (default `--no-verbose`).

- `eudiw request create`  
  Required options: `--profile`, `--response-mode`, `--dcql-preset` _or_ `--dcql-json`. Optional toggles: `--unsigned` (disable HAIP JAR), `--qr` (render ASCII QR), `--seed <path>` (override deterministic seed). Returns request metadata and, when verbose, a trace section containing masked nonce/state and DCQL hash.

- `eudiw wallet simulate`  
  Required: `--request-id`, `--profile`. Either `--wallet-preset <id>` or inline credential inputs via `--sdjwt <path>` / `--disclosure <path>` / `--kb-jwt <path>` / `--mdoc <path>`. Optional `--trusted-authority <policy-id>`. Text output summarises credential IDs, response mode, Trusted Authority matches; verbose output appends trace diagnostics (hashes, latency).

- `eudiw validate`  
  Accepts `--preset <id>` or `--vp-token <path>` (inline JSON), optional `--response-mode-override` and `--trusted-authority`. Reports success/failure, problem-details when invalid, and verbose traces with per-credential diagnostics. Exit codes: `0` success, `2` validation failure, `3` request/setup error.

Command help must reference the REST endpoints for cross-facade parity and highlight that verbose tracing exposes masked but still sensitive hashes.

## Result & Trace Presentation Matrix

| Context | Result payload (always present) | Trace payload (`verbose=true` / `--verbose`) | Notes |
|---------|---------------------------------|----------------------------------------------|-------|
| REST/CLI responses | `status`, `profile`, `responseMode`, `presentations[].credentialId`, `presentations[].format`, `presentations[].trustedAuthorityMatch`, inline VP Token JSON (per F-040-26), `telemetry` | `requestId`, masked `nonce`/`state`, `dcqlHash`, `vpTokenHash`, `kbJwtHash`, `trustedAuthorities[]`, `latencyMs`, encryption verdicts (`direct_post.jwt`), per-credential diagnostics (`claimsSatisfied`, `akiMatch`, `deviceResponseHash`) | `trace` omitted entirely when verbose disabled. Multi-presentation traces include `presentations[n].id` keys matching result order. |
| Operator UI – result card | Status badge, response mode, credential summary rows (format, Trusted Authority labels), VP Token JSON viewer | Shared console trace dock entries keyed by presentation ID; includes same hashes/diagnostics as REST plus QR render metadata and baseline/HAIP flag state | The right-hand panel never repeats hash fields; users switch to the global trace dock for details. |
| Operator UI – baseline banner | “Baseline mode – HAIP enforcement disabled” banner (only in result card) | Trace records `haipEnforced=false` to support telemetry correlation | Banner shown whenever profile ≠ `HAIP`. |

When verbose tracing is enabled, facades must log a warning mirroring existing HOTP/TOTP/OCRA copy: “Verbose traces expose hashed identifiers and diagnostic metadata. Use only in trusted environments.” Hashes remain, but master keys and raw disclosures never appear.

## Test Strategy

- **Core (`core.eudi.openid4vp`)** – Property-based and fixture-driven tests covering DCQL evaluation, Trusted Authority policies (positive/negative), SD-JWT disclosure hashing/KJ-JWT binding, DeviceResponse COSE signature validation, encryption helpers (round-trip JWE), and deterministic seed replay.
- **Application (`application.eudi.openid4vp`)** – Unit tests asserting telemetry frames (events, sanitized fields), trace construction (masked nonce/state, hashed payloads), HAIP enforcement toggles (`invalid_request` on encryption failure), multi-presentation handling, and baseline profile banner semantics.
- **REST** – MockMvc suites for each endpoint verifying status codes, JSON schema (required fields, enums), verbose toggling, problem-details responses, and parity with the specification examples. Snapshot tests capture OpenAPI changes introduced by the new contract.
- **CLI** – Picocli command tests covering option validation, preset vs inline credential flows, `--verbose` toggling, JSON parity with REST responses, exit codes, and help output.
- **UI** – JS unit and Selenium tests validating two-column layout, baseline banner visibility, DCQL preview read-only behaviour, sample loader, global trace dock integration (including copy/download controls), and multi-presentation collapsible sections with matching trace keys.
- **Fixtures & ingestion** – Tests ensuring synthetic vs conformance fixture toggles load the expected records, provenance metadata is captured, and seeding endpoints/commands remain idempotent.
- **Performance/metrics** – Lightweight regression capturing latency for encrypted vs non-encrypted flows (≤200 ms target) with assertions on telemetry latency fields.
## Operator UI Mock-ups

The console mirrors the EMV/CAP layout: request inputs at the top, result and trace panels rendered side by side (stacked on narrow screens). Square brackets indicate editable inputs; uppercase labels are static. Verbose tracing is controlled globally via the console header toggle and shared trace dock, so the individual panels do not render a local checkbox. When the profile switch selects **Baseline**, a banner appears above the panel (“Baseline mode – HAIP enforcement disabled”) to warn operators about the relaxed profile.

When DCQL requests multiple credentials, the result view renders one collapsible section per credential descriptor and surfaces matching metadata in the shared trace dock.

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

## Dependency Considerations
- Base implementation uses existing crypto helpers; however, HAIP encryption and mdoc COSE handling likely require additional libraries:
  - Nimbus JOSE + JWT for `ECDH-ES` + `A128GCM` JWE handling.
  - COSE-JAVA (or equivalent) and a CBOR parser for DeviceResponse.
  - ZXing (optional) for QR PNG generation; ASCII QR renderer remains default.
- Any new dependency must receive explicit owner approval before adoption; until approved, keep functionality behind interfaces so fixture-driven tests can operate with in-house stubs.

## References
- OpenID for Verifiable Presentations 1.0 (Final, 2025-07-09): response types, DCQL, response modes, validation, error semantics.
- OpenID4VC High Assurance Interoperability Profile 1.0 (draft 05): HAIP profile, encryption, identifiers, Trusted Authorities use.
- IETF draft-ietf-oauth-selective-disclosure-jwt-22: SD-JWT selective disclosure, KB-JWT structure.
- IETF draft-ietf-oauth-sd-jwt-vc-12: SD-JWT VC media types (`application/dc+sd-jwt`).
- ISO/IEC 18013-5 (mobile driving licence) – DeviceResponse enforcement.
- EUDI PID Rulebook Annex 3.1: PID namespace `eu.europa.ec.eudi.pid.1`.
- OpenID4VP conformance suite repository (OpenID Foundation).

## Follow-up Items
- Complete ETSI Trust List (`etsi_tl`) and OpenID Federation ingestion before extending further trust features (tracked by T3999/T40F3).
- Evaluate conformance test automation to replay official OpenID4VP vectors regularly.
- Track same-device/DC-API journeys as a separate specification once prioritized.
