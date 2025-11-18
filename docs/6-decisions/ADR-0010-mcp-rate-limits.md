# ADR-0010 – MCP Rate Limiting Responsibility

## Status
- Proposed on: 2025-11-18  
- Status: Accepted  
- Supersedes: None  
- Superseded by: None  

## Context
Feature 015 (“MCP Agent Facade”) currently states that MCP “hardens telemetry, auditing, and rate-limit hooks” and includes FR-015-04, which requires MCP to enforce MCP-specific rate limits and host approvals. Task T-015-04 in `docs/4-architecture/features/015/tasks.md` plans rate-limit middleware plus `mcp.error.rate_limited` handling.

The user clarified that:

- Rate limiting and abuse protection for LLM/agent traffic will live in API gateways and LLM platform controls that front the REST/MCP endpoints.
- The OpenAuth Simulator is a non-production emulator intended for local/CI use, and should not embed its own rate-limit policy or surface MCP-specific rate-limit errors.
- Implementing rate limiting inside the emulator increases complexity and partially duplicates upstream controls, without clear benefit for the intended usage model.

This is a governance/security boundary decision that affects how future features think about responsibility for load-shedding and abuse controls.

## Decision

1. **MCP facade will not implement its own rate limits.**
   - Remove the requirement that MCP enforce per-tool or per-connection rate limits in-process.
   - MCP will not emit `mcp.error.rate_limited` or analogous errors.

2. **Responsibility for rate limiting is delegated to upstream infrastructure.**
   - API gateways, ingress controllers, or LLM platform controls that front the REST/MCP endpoints are responsible for:
     - Throttling abusive clients.
     - Enforcing tenant- or key-level quotas.
     - Returning HTTP-level rate-limit errors (e.g., 429 + retry headers) where required.

3. **MCP proxy behaviour focuses on parity, telemetry, and auditing.**
   - FR-015-04 is re-scoped to require:
     - REST-equivalent validation and telemetry for MCP tools.
     - MCP proxy auditing (tool name, latency, REST status) without local rate limiting.
   - NFR-015-02..04 are updated to describe auditing/telemetry expectations rather than in-process rate limits.

4. **If a deployment requires rate limiting, it must be implemented outside the emulator.**
   - Future features that rely on rate limiting must assume an external gateway or platform control, not the simulator runtime, and document that assumption explicitly.

## Consequences

- **Simpler MCP implementation:** The `tools/mcp-server` module no longer needs rate-limit middleware, error paths, or configuration knobs. Implementation and tests can focus on proxy correctness, helper flows, and telemetry.
- **Clear responsibility boundary:** The simulator remains a pure emulator; rate limiting, quotas, and abuse controls are explicitly a concern for infrastructure that embeds it.
- **Spec and plan updates required:**
  - Update `docs/4-architecture/features/015/spec.md`:
    - Remove rate-limit language from the Overview and Goals.
    - Re-scope FR-015-04 to MCP proxy parity + auditing only (no rate-limit enforcement).
    - Remove references to `mcp.error.rate_limited` and `mcp.rate_limited` telemetry.
  - Update `docs/4-architecture/features/015/plan.md`:
    - Adjust Increment I4 to cover auditing/quality gate only (no rate-limit middleware).
  - Update `docs/4-architecture/features/015/tasks.md`:
    - Descope T-015-04 to auditing/governance updates, or split rate-limiting into a non-planned backlog item.

## Related Artefacts

- Specification: `docs/4-architecture/features/015/spec.md`  
- Plan: `docs/4-architecture/features/015/plan.md`  
- Tasks: `docs/4-architecture/features/015/tasks.md`  
- Governance: `AGENTS.md`, `docs/6-decisions/project-constitution.md` (unchanged; this ADR refines how governance is applied to MCP).

