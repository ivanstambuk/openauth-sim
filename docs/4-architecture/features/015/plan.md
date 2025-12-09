# Feature Plan 015 – MCP Agent Facade

_Linked specification:_ `docs/4-architecture/features/015/spec.md`  
_Status:_ Draft  
_Last updated:_ 2025-11-18

> Guardrail: Keep this plan traceable back to the governing spec. Reference FR/NFR/Scenario IDs from `spec.md` where relevant, log any new high- or medium-impact questions in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md), and assume clarifications are resolved only when the spec’s normative sections (requirements/NFR/behaviour/telemetry) and, where applicable, ADRs under `docs/6-decisions/` have been updated.

## Vision & Success Criteria
Deliver an MCP-first agent facade that:
- Surfaces a curated, versioned tool catalogue with discoverable metadata (FR-015-01).
- Implements helper tools and session APIs that provide tangible value beyond REST (FR-015-02/03).
- Preserves REST behaviour while enforcing MCP-specific telemetry and auditing (FR-015-04, NFR-015-01..04), leaving rate limiting to upstream gateways and LLM platform controls per ADR-0013.
- Ships documentation/how-to content so AI agents can integrate with zero bespoke glue.

## Scope Alignment
- **In scope:** MCP catalogue metadata, helper flows (initially fixtures + TOTP), session/context APIs, telemetry/auditing, documentation, roadmap updates, and transcripts.
- **Out of scope:** Non-agent facades (CLI/REST/UI) except where helper endpoints must be exposed for MCP; Native Java API changes; persistence redesigns; new REST contracts unrelated to MCP helpers.

## Dependencies & Interfaces
- `tools/mcp-server` Gradle module (existing skeleton from Feature 013).
- Application helper services (e.g., `TotpCurrentOtpHelperService`) and any new helper services required.
- REST helper endpoints (where needed) plus OpenAPI snapshots.
- Telemetry adapters & logging config (`TelemetryContracts`).
- Documentation surfaces: README, AGENTS.md, knowledge map, future MCP how-to.

## Assumptions & Risks
- **Assumptions:** Existing REST endpoints remain authoritative; helper services can reuse current persistence; no additional third-party libraries required (per NFR-015-02) unless explicitly approved.
- **Risks / Mitigations:**
  - _Scope creep_—limit helper backlog to enumerated scenarios (S-015-02) per increment; record future helpers in backlog section.
  - _Telemetry drift_—coordinate with Feature 013 to ensure new events are captured in `_current-session.md` and telemetry snapshots.
  - _Agent ergonomics_—validate helper payloads with sample MCP clients (update `build/mcp-feedback.json`).

## Implementation Drift Gate
- Run after all increments land. Evidence:
  - Updated spec/plan/tasks with final dates.
  - Command log: `./gradlew --no-daemon :tools-mcp-server:test :rest-api:test`, `./gradlew --no-daemon generateJsonLd check`, `./gradlew --no-daemon qualityGate`, `./gradlew --no-daemon reflectionScan` (if helpers touch governance).
  - MCP transcript (`build/mcp-feedback.json`) covering catalogue, helper, and session tools.
  - README/AGENTS/how-to updates in place.
  - Record gate completion notes in this plan + `_current-session.md`.

## Increment Map
1. **I1 – Tool Catalogue & Versioning (FR-015-01, S-015-01)**
   - _Goal:_ Promote MCP catalogue metadata (schemas, prompt hints, version info) and expose it via `tools/list`.
   - _Preconditions:_ Spec/plan approved; existing tool definitions present.
   - _Steps:_
     - Define schema registry JSON; add prompt hints + version metadata per tool.
     - Implement catalogue builder + tests (`tools-mcp-server`).
     - Emit `mcp.catalog.listed` telemetry; update README/AGENTS + transcripts.
   - _Commands:_ `./gradlew --no-daemon :tools-mcp-server:test`, `./gradlew --no-daemon generateJsonLd check`.
   - _Exit:_ Tool catalogue returns enriched metadata; docs mention MCP facade + discovery.

2. **I2 – Helper Tool Expansion (FR-015-02, S-015-02)**
   - _Goal:_ Ship helper flows (existing TOTP helper + fixtures helper baseline) with sanitized payloads.
   - _Steps:_
     - Finalize helper service contracts (application + REST, where needed).
     - Implement new MCP tool handlers with redaction tests.
     - Add MCP telemetry events (`mcp.totp.helper.lookup`, `mcp.fixtures.list`).
     - Update transcripts + documentation.
   - _Commands:_ `./gradlew --no-daemon :application:test --tests "*Helper*"`, `./gradlew --no-daemon :rest-api:test --tests "*Helper*"`, `./gradlew --no-daemon :tools-mcp-server:test`, `./gradlew --no-daemon generateJsonLd check`.
   - _Exit:_ Helpers callable via MCP, REST remains untouched except supporting endpoints, docs updated.
   - **Implementation status (2025-11-18):** Reused `TotpCurrentOtpHelperService` and the `/api/v1/totp/helper/current` endpoint, extended `McpServer` to attach sanitized telemetry for `totp.helper.currentOtp` (`mcp.totp.helper.lookup`) and `fixtures.list` (`mcp.fixtures.list`), and updated MCP tests/transcripts so helper flows now satisfy NFR-015-01 without adding non-JDK dependencies.

3. **I3 – Session & Capabilities APIs (FR-015-03, S-015-03)**
   - _Goal:_ Provide `session.describe`, `session.reset`, `capabilities.describe` tools.
   - _Steps:_
     - Design session context storage + tests (MCP layer only).
     - Implement tools + telemetry (`mcp.session.describe`, `mcp.session.reset`).
     - Document usage + sample payloads; refresh transcripts.
   - _Commands:_ `./gradlew --no-daemon :tools-mcp-server:test`, `./gradlew --no-daemon generateJsonLd check`.
   - _Exit:_ Session APIs available with governance-compliant telemetry.

4. **I4 – Auditing and Quality Gate (FR-015-04, NFR-015-01..04, S-015-04)**
   - _Goal:_ Align MCP proxy behaviour with REST while emitting proxy/audit telemetry and satisfying governance/quality gates (no in-process rate limiting).
   - _Steps:_
     - Ensure audit logs & telemetry include tool name, caller hash (where applicable), latency, and REST status for forwarded calls.
     - Run full `qualityGate` + `reflectionScan`; finalize docs/how-to + roadmap update.
   - _Commands:_ `./gradlew --no-daemon qualityGate`, `./gradlew --no-daemon reflectionScan`, `./gradlew --no-daemon generateJsonLd check`.
   - _Exit:_ MCP facade meets governance requirements (per ADR-0013) and documentation is complete.

## Scenario Tracking

| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-015-01 | I1 / T-015-01 | Tool catalogue metadata + telemetry.
| S-015-02 | I2 / T-015-02 | Helper flows + transcripts.
| S-015-03 | I3 / T-015-03 | Session/context APIs.
| S-015-04 | I4 / T-015-04 | Proxy parity, rate limits, auditing.

## Analysis Gate
- To be completed after I1 design completes and before implementation continues. Capture notes in `_current-session.md` referencing this plan.

## Exit Criteria
- All increments complete; tasks checklist marked ✅.
- Spec/plan/tasks updated with final dates.
- README/AGENTS/how-to + knowledge map mention MCP facade.
- `build/mcp-feedback.json` refreshed with helper + session flows.
- Telemetry snapshot + governance logs recorded; Implementation Drift Gate log appended.

## Follow-ups / Backlog
- Additional helper tools (HOTP counter peek, EMV preset inspector, FIDO2 attestation summarizer) tracked once core facade stabilizes.
- Potential ADR if future MCP work introduces additional dependencies or auth providers.
