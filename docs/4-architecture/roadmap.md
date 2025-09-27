# Implementation Roadmap

_Last updated: 2025-09-27_

This roadmap tracks the major workstreams required to reach a feature-complete OpenAuth Simulator. Update this file whenever scope or status changes so future sessions can pick up without replaying prior conversations.

## Guiding Principles

- Prioritise core cryptographic capabilities and deterministic emulator behaviour before user-facing interfaces.
- Maintain incremental delivery: break every workstream into self-contained changes taking &lt;= 10 minutes each, with a passing build after every step.
- Persist all context (plans, ADRs, follow-up actions) in `docs/` so sessions remain stateless.

## High-Level Workstreams

| # | Workstream | Goal | Status | Notes |
|---|------------|------|--------|-------|
| 1 | Core credential domain | Model protocol-specific credential types (FIDO2, OATH/OCRA, EUDI mDL, EMV/CA) with validation and crypto helpers | In planning | Feature plan: [Feature Plan 001](feature-plan-001-core-domain.md) |
| 2 | Persistence & caching hardening | Tune MapDB + Caffeine (encryption options, compaction, metrics) | Not started | Depends on Workstream 1 definitions |
| 3 | CLI tooling (Picocli) | Provide command-line flows to import, list, update, and delete credentials; accept secrets via arguments/stdin | Not started | Should integrate with `CredentialStore` API |
| 4 | REST API (Spring Boot) | Expose emulator capabilities over REST with OpenAPI documentation | Not started | Requires Workstreams 1 & 2 |
| 5 | Server-rendered UI | Build an operator UI consuming the REST API for manual operations | Not started | Shares DTOs with Workstream 4 |
| 6 | JMeter plugin facade | Offer a load-testing UI facade to drive the emulator for performance scenarios | Not started | Depends on REST API surface |
| 7 | ArchUnit & quality automation | Enforce architecture boundaries, expand test/coverage checks, integrate CI badges | Not started | Should accompany each new module |
| 8 | Specification alignment | Document references and compliance notes for FIDO2/WebAuthn, OCRA, EUDI, EMV | Not started | Capture findings in `docs/3-reference` |

## Upcoming Milestones

1. Finalise credential domain design (Workstream 1) and land supporting unit tests.
2. Establish CLI surface for credential lifecycle management (Workstream 3).
3. Implement REST API skeleton with security considerations and generate OpenAPI docs (Workstream 4).

## Action Items & Follow-ups

- [ ] Create detailed feature plans for Workstreams 2–6 (Feature Plan 001 covers Workstream 1).
- [ ] Capture ADRs for crypto design decisions (key formats, hashing algorithms, etc.).
- [ ] Identify specification links for inclusion in docs once network lookups are permitted.

Keep this roadmap synced with each significant decision or completion event.
