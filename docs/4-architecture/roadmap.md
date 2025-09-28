# Implementation Roadmap

_Last updated: 2025-09-28_

This roadmap tracks the major workstreams required to reach a feature-complete OpenAuth Simulator. Update this file whenever scope or status changes so future sessions can pick up without replaying prior conversations.

## Guiding Principles

- Prioritise core cryptographic capabilities and deterministic emulator behaviour before user-facing interfaces.
- Maintain incremental delivery: break every workstream into self-contained changes taking &lt;= 10 minutes each, with a passing build after every step.
- Persist all context (plans, ADRs, follow-up actions) in `docs/` so sessions remain stateless.

## High-Level Workstreams

| # | Workstream | Goal | Status | Notes |
|---|------------|------|--------|-------|
| 1 | Core credential domain | Model protocol-specific credential types (FIDO2, OATH/OCRA, EU Digital Identity Wallet suites, EMV/CAP) with validation, issuance, and presentation helpers | In progress | Spec: [Feature 001](specs/feature-001-core-credential-domain.md), Plan: [Feature Plan 001](feature-plan-001-core-domain.md), Tasks: [Feature 001 Tasks](tasks/feature-001-core-credential-domain.md); Recent increments: T011–T015 (persistence envelopes, telemetry, documentation, knowledge map) |
| 2 | Persistence & caching hardening | Tune MapDB + Caffeine (encryption options, compaction, metrics) | Complete | Spec: [Feature 002](specs/feature-002-persistence-hardening.md), Plan: [Feature Plan 002](feature-plan-002-persistence-hardening.md), Tasks: [Feature 002 Tasks](tasks/feature-002-persistence-hardening.md); Final benchmarks (2025-09-28) record writes ≈2.57k ops/s, reads ≈330k ops/s, P99 ≈0.02283 ms |
| 3 | CLI tooling (Picocli) | Provide command-line flows to import, list, update, and delete credentials; accept secrets via arguments/stdin | Not started | Should integrate with `CredentialStore` API |
| 4 | REST API (Spring Boot) | Expose emulator capabilities over REST with OpenAPI documentation | Complete | Feature 003 delivered OCRA evaluation, Feature 004 added credential lookup + dual-mode requests; future non-OCRA endpoints will be tracked via new specs |
| 5 | Server-rendered UI | Build an operator UI consuming the REST API for manual operations | Not started | Shares DTOs with Workstream 4 |
| 6 | JMeter plugin facade | Offer a load-testing UI facade to drive the emulator for performance scenarios | Not started | Depends on core credential APIs and persistence |
| 7 | ArchUnit & quality automation | Enforce architecture boundaries, expand test/coverage checks, integrate CI badges | Not started | Should accompany each new module |
| 8 | Specification alignment | Document references and compliance notes for FIDO2/WebAuthn, OCRA, EUDI, EMV | Not started | Capture findings in `docs/3-reference` |
| 9 | Replay & verification mode | Enable credential replay flows (OTP/assertion regeneration with supplied challenges/counters) for non-repudiation analysis | Not started | Depends on persistence hooks and facade support |
| 10 | Simulator learning UI | Build a step-by-step, richly annotated UI that visualises algorithm execution (e.g., OCRA rounds) for education | Not started | Requires REST/UI workstreams and detailed algorithm instrumentation |

## Upcoming Milestones

1. Finalise credential domain design (Workstream 1) and land supporting unit tests.
2. Establish CLI surface for credential lifecycle management (Workstream 3).
3. Implement REST API skeleton with security considerations and generate OpenAPI docs (Workstream 4).
4. Design replay/verification workflows (Workstream 9) once persistence hardening lands.
5. Prototype educational simulator UI flows (Workstream 10) to complement human operators.

## Action Items & Follow-ups

- [ ] Create detailed feature plans for Workstreams 2–6 (Feature Plan 001 covers Workstream 1).
- [ ] Capture ADRs for crypto design decisions (key formats, hashing algorithms, etc.).
- [ ] Identify specification links for inclusion in docs once network lookups are permitted.
- [x] Bootstrap architecture knowledge map in `docs/4-architecture/knowledge-map.md`.
- [x] Publish OCRA capability matrix and telemetry contract in `docs/1-concepts` (T014).

Keep this roadmap synced with each significant decision or completion event.
