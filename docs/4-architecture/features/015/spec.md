# Feature 015 – MCP Agent Facade

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-16 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/015/plan.md` |
| Linked tasks | `docs/4-architecture/features/015/tasks.md` |
| Roadmap entry | #15 – MCP Agent Facade |

> Guardrail: This specification is the single normative source of truth for the feature. Track high- and medium-impact questions in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md), encode resolved answers directly in the Requirements/NFR/Behaviour/Telemetry sections below (no per-feature `## Clarifications` sections), and use ADRs under `docs/6-decisions/` for architecturally significant clarifications (referencing their IDs from the relevant spec sections).

## Overview
Model Context Protocol (MCP) is currently documented as a REST proxy, but agents increasingly need helper flows, structured tool metadata, and session-aware automation that go beyond REST semantics. This feature elevates MCP to a first-class “Agent Facade” with its own contract: a curated tool catalogue, agent-only helper utilities, session APIs, and telemetry/security guarantees tailored for AI orchestrators. MCP remains backed by the existing core/application modules, yet it now has explicit behaviour and quality requirements independent of REST.

## Goals
- Establish MCP as an official consumption facade with documented capabilities, naming conventions, and telemetry expectations.
- Ship MCP-first helper flows (e.g., credential OTP previews, fixture discovery) that do not clutter REST/CLI/UIs but are available to agents.
- Provide structured metadata (JSON Schema, prompt hints, version info) so MCP clients can auto-discover and validate tools.
- Support lightweight session/context APIs that let agents inspect/reset state within a single MCP connection.
- Harden MCP-specific telemetry, auditing, and rate-limit hooks to satisfy governance requirements for AI-driven automation.

## Non-Goals
- Introducing new REST, CLI, or UI endpoints beyond those required to back MCP helpers (REST remains authoritative for end-user flows).
- Broadening Native Java APIs; MCP reuses existing application services instead of defining parallel Java entry points.
- Replacing REST documentation with MCP docs—REST remains the canonical protocol reference. MCP documents how it layers on top.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-015-01 | MCP MUST expose a discoverable tool catalogue (via `tools/list`) containing JSON Schema payloads, prompt hints, version metadata, and descriptive text for each tool. | Agent lists tools and receives structured metadata for HOTP/TOTP/OCRA/EMV/FIDO2/EUDIW evaluate flows plus helpers. | Invalid schema requests return `mcp.error.invalid_tool` with details; unknown clients still get the standard list. | Internal catalogue failure returns `mcp.error.catalog_unavailable` framed as MCP error. | Emit `mcp.catalog.listed` with tool count, schema hash, sanitized payload. | Owner directive; MCP capability analysis. |
| FR-015-02 | MCP MUST provide helper tools (initially `totp.helper.currentOtp`, `fixtures.list`, and future protocol helpers) with explicit request/response contracts and redacted outputs. | Agent invokes helper, receives structured JSON (OTP, timestamps, telemetryId) without exposing shared secrets. | Missing credential or invalid arguments return `mcp.error.validation` with RFC7807 fields. | Backend failure logged as `mcp.error.helper_failed` plus telemetry reason code. | Emit `mcp.totp.helper.lookup`, `mcp.fixtures.list` events referencing protocol, preset IDs, telemetryId. | Feature 013 runbook + new helper strategy. |
| FR-015-03 | MCP MUST provide session/context tools (`session.describe`, `session.reset`, `capabilities.describe`) so agents can inspect/reset MCP state without leaving the connection. | Agent calls `session.describe` and receives connection metadata (baseUrl, authenticated scopes, pending context). | Unauthorized or malformed calls return `mcp.error.invalid_session`. | Transport failures return `mcp.error.session_unavailable`. | Telemetry `mcp.session.describe` / `mcp.session.reset` with sanitized context IDs. | MCP facade requirements. |
| FR-015-04 | MCP MUST honour REST-equivalent validation/telemetry for all evaluate/simulate tools while also enforcing MCP-specific rate limits and host approvals. | Tool invocation forwards to REST, returns HTTP payload plus MCP wrapper fields. Rate-limit headers mirrored in MCP metadata. | Invalid REST response surfaces as `mcp.error.rest_proxy` with reason. | Exhausted rate limit yields `mcp.error.rate_limited` plus retry hints. | Log `mcp.proxy.forwarded` with tool name, latency, sanitized status. | Constitution + governance directives. |

## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-015-01 | MCP helper outputs must never expose shared secrets or unsanitized OTP history; redact sensitive fields before returning. | Security / governance | Unit + integration tests confirm redaction; telemetry flag `sanitized=true`. | Core/application services, telemetry adapters. | Constitution security clause. |
| NFR-015-02 | MCP server remains dependency-light (JDK-only unless owner approves), keeping install footprint ≤5 MB and cold start ≤2 s. | Operational simplicity | Build artifact size + smoke tests. | Gradle module `tools/mcp-server`. | Feature 013 constraints. |
| NFR-015-03 | MCP tool catalogue must be versioned and backwards compatible within a minor release; breaking changes require new tool IDs or version negotiation. | Agent interoperability | Contract tests verifying `version` metadata + changelog; docs in README/AGENTS. | `build/mcp-feedback.json`, planning docs. | Owner directive. |
| NFR-015-04 | Rate limiting and audit logging must capture per-tool invocation (tool name, caller token hash, reasonCode) for every request. | Governance / auditing | Log inspection + telemetry snapshot for new events. | Telemetry adapters, logging config. | Constitution + Feature 013. |

## UI / Interaction Mock-ups
_Not applicable – no user-facing UI changes._

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-015-01 | Agent lists tools and receives metadata (schemas, hints, version info). |
| S-015-02 | Agent invokes MCP helper (e.g., TOTP OTP preview) and receives sanitized result plus telemetry IDs. |
| S-015-03 | Agent inspects/resets MCP session context (describe/reset). |
| S-015-04 | Agent triggers evaluate/simulate tool via MCP with rate-limit + telemetry behaviour matching spec. |

## Test Strategy
- **Core:** No direct changes; reuse existing descriptor/helpers with additional unit tests around helper services (e.g., `TotpCurrentOtpHelperService`).
- **Application:** Add service-level tests for new helper APIs, session context providers, and telemetry adapters.
- **REST:** Only updated when helpers require new endpoints; REST tests ensure helper routes redaction/rfc7807 compliance.
- **CLI:** No direct impact.
- **UI (JS/Selenium):** No UI surface changes; optional doc-only references.
- **Docs/Contracts:** Expand MCP transcript (`build/mcp-feedback.json`), document schemas in README/AGENTS, and add MCP-specific how-to guides.
- **MCP Module:** Add JSON-RPC contract tests (tool catalogue, helper flows, session commands), rate-limit/audit tests, and reflection-free checks.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-015-01 | `McpToolDefinition` – name, summary, JSON Schema, prompt hints, version metadata. | tools/mcp-server |
| DO-015-02 | `McpHelperResponse` – sanitized helper payload (OTP, timestamps, telemetryId). | application, rest-api, tools/mcp-server |
| DO-015-03 | `McpSessionContext` – connection metadata, rate-limit counters, capability flags. | tools/mcp-server |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-015-01 | MCP `tools/list` | Returns structured catalogue with schemas/prompt hints. | Backed by static metadata + schema registry. |
| API-015-02 | MCP `tools/call` (`totp.helper.currentOtp`, future helpers) | Executes helper flows returning sanitized payload. | Uses application helper services + REST helper endpoints. |
| API-015-03 | MCP `tools/call` (`session.describe` / `session.reset`) | Provides session/context operations. | Lives entirely in MCP layer (no REST backing). |

### CLI Commands / Flags
_None._

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-015-01 | `mcp.catalog.listed` | `toolCount`, `schemaHash`, `telemetryId`, sanitized=true. |
| TE-015-02 | `mcp.totp.helper.lookup` | `credentialId` (hashed), `generationEpochSeconds`, `telemetryId`, sanitized=true. |
| TE-015-03 | `mcp.session.describe` | `sessionId`, `capabilities`, sanitized=true. |
| TE-015-04 | `mcp.proxy.forwarded` | `tool`, `latencyMs`, `statusCode`, `telemetryId`, sanitized=true. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-015-01 | `build/mcp-feedback.json` | Canonical transcript covering catalogue, helpers, and session tools. |

### UI States
_None._

## Telemetry & Observability
All MCP invocations MUST emit telemetry frames via `TelemetryContracts` with sanitized payloads. Helper events include hashed credential IDs and omit shared secrets. Rate-limit breaches emit `mcp.rate_limited` with retry intervals. Session commands log context IDs but no sensitive tokens. Telemetry snapshots in `docs/3-reference` must be updated when new events or fields are introduced.

## Documentation Deliverables
- Update README/AGENTS to list MCP as an official facade with setup/run instructions, helper tool catalogue, and session APIs.
- Author `docs/2-how-to/use-mcp-from-agents.md` (or equivalent) covering tool usage, helper flows, and telemetry expectations.
- Refresh roadmap entry and knowledge map to include MCP facade relationships.
- Extend `build/mcp-feedback.json` transcripts whenever tools change.

## Fixtures & Sample Data
- Maintain MCP transcripts under `build/mcp-feedback.json`.
- Add helper-specific fixtures (e.g., sanitized OTP samples) under `docs/test-vectors/mcp/` if needed for documentation/examples.

## Spec DSL

```
domain_objects:
  - id: DO-015-01
    name: McpToolDefinition
    modules: ["tools-mcp-server"]
    fields:
      - name: name
        type: string
      - name: schema
        type: json_schema
      - name: promptHints
        type: array<string>
  - id: DO-015-02
    name: McpHelperResponse
    modules: ["application","rest-api","tools-mcp-server"]
    fields:
      - name: payload
        type: object
      - name: telemetryId
        type: string
  - id: DO-015-03
    name: McpSessionContext
    modules: ["tools-mcp-server"]
    fields:
      - name: sessionId
        type: string
      - name: capabilities
        type: map
routes:
  - id: API-015-01
    transport: MCP
    method: tools/list
    path: n/a
  - id: API-015-02
    transport: MCP
    method: tools/call
    path: totp.helper.currentOtp
  - id: API-015-03
    transport: MCP
    method: tools/call
    path: session.describe
telemetry_events:
  - id: TE-015-01
    event: mcp.catalog.listed
  - id: TE-015-02
    event: mcp.totp.helper.lookup
fixtures:
  - id: FX-015-01
    path: build/mcp-feedback.json
ui_states: []
```

## Appendix (Optional)
- Initial helper backlog ideas: HOTP counter peek (`hotp.helper.counter`), EMV preset inspector, FIDO2 attestation metadata summarizer.
- Consider ADR if we later introduce non-JDK dependencies for schema generation or auth plugins.
