# ADR-0010: MCP Facade Layering Strategy

- **Status:** Accepted
- **Date:** 2025-11-16
- **Related features/specs:** Feature 015 – MCP Agent Facade (`docs/4-architecture/features/015/spec.md`)
- **Related open questions:** None (decision confirmed in-session)

## Context
Feature 015 introduces MCP as an official agent-facing facade. During planning we evaluated whether MCP should:
1. Remain layered on the existing application/REST seams (with helper services in the application module where needed), or
2. Bypass REST entirely and invoke native services directly, creating a “pure MCP” stack.

Key constraints:
- REST already codifies protocol behaviour, validation, telemetry, and persistence across the simulator.
- MCP needs to add helper flows, session APIs, and agent-first tooling without duplicating business logic.
- Governance (spec-driven development, telemetry parity, drift gates) relies on a single behavioural source of truth.

## Decision
MCP will remain **REST-backed with targeted Native Java helpers**:
- Evaluate/simulate tools continue to forward JSON payloads to the REST API, inheriting its validation, telemetry, and persistence semantics.
- MCP-specific helper flows (e.g., `totp.helper.currentOtp`) may call dedicated application services, but those services are still part of the existing modules and use REST endpoints where appropriate.
- MCP focuses on agent-first capabilities (tool catalogue metadata, helper sequencing, session/context APIs, telemetry, rate limits) without becoming a separate execution stack.

## Consequences

### Positive
- Reuses the canonical REST/application behaviour, avoiding duplicated validation/persistence logic.
- Keeps documentation and telemetry aligned across Native Java/REST/CLI/UI/MCP, reducing drift.
- Allows incremental delivery of MCP helper flows while minimizing architectural churn.

### Negative
- MCP helper invocations still incur REST serialization overhead when forwarding requests.
- Truly MCP-only capabilities (e.g., streaming, direct persistence access) will require additional REST/application changes before MCP can expose them.

## Alternatives Considered
- **Pure Native MCP stack:** Would remove REST from the loop, but duplicates validation/persistence, risks behavioural drift, and requires substantial re-documentation. Rejected for now.
- **Hybrid with new MCP-only endpoints:** Considered but postponed until MCP-first requirements emerge that REST cannot host.

## Security / Privacy Impact
- Leveraging existing REST/application services means helper flows inherit established redaction rules and telemetry sanitization. No new attack surface beyond MCP’s tool exposure. MCP-specific helpers must continue to redact secrets per Feature 015 NFR-015-01.

## Operational Impact
- No new deployment topology: MCP still runs alongside the REST service. Existing runbooks, monitoring, and `qualityGate` coverage remain valid, with additional MCP tests layered on top.

## Links
- Feature 015 spec: `docs/4-architecture/features/015/spec.md`
- Feature 015 plan/tasks: `docs/4-architecture/features/015/{plan,tasks}.md`
