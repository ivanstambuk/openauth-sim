# Implementation Roadmap

_Last updated: 2025-09-30_

This roadmap tracks the major workstreams required to reach a feature-complete OpenAuth Simulator. Update this file whenever scope or status changes so future sessions can pick up without replaying prior conversations.

## Guiding Principles

- Prioritise core cryptographic capabilities and deterministic emulator behaviour before user-facing interfaces.
- Maintain incremental delivery: break every workstream into self-contained changes taking &lt;= 10 minutes each, with a passing build after every step.
- Persist all context (plans, ADRs, follow-up actions) in `docs/` so sessions remain stateless.

## High-Level Workstreams

| # | Workstream | Goal | Status | Notes |
|---|------------|------|--------|-------|
| 1 | Core OCRA domain | Model OCRA credential descriptors with validation, issuance, and presentation helpers reused by downstream facades | Complete | Spec: [Feature 001](specs/feature-001-core-credential-domain.md), Plan: [Feature Plan 001](feature-plan-001-core-domain.md), Tasks: [Feature 001 Tasks](tasks/feature-001-core-credential-domain.md); Final verification 2025-09-28 (`./gradlew spotlessApply check` with ArchUnit guardrails active) |
| 2 | OCRA persistence & caching | Tune MapDB + Caffeine (encryption options, compaction, metrics) for OCRA credential storage | Complete | Spec: [Feature 002](specs/feature-002-persistence-hardening.md), Plan: [Feature Plan 002](feature-plan-002-persistence-hardening.md), Tasks: [Feature 002 Tasks](tasks/feature-002-persistence-hardening.md); Final benchmarks (2025-09-28) record writes ≈2.57k ops/s, reads ≈330k ops/s, P99 ≈0.02283 ms |
| 3 | OCRA CLI tooling (Picocli) | Provide command-line flows to import, list, delete, and evaluate OCRA credentials | Complete | Feature 005 delivered OCRA import/list/delete/evaluate CLI (R017–R020 completed 2025-09-28). Non-OCRA protocols will launch separate workstreams. |
| 4 | OCRA REST API (Spring Boot) | Expose OCRA evaluation over REST with OpenAPI documentation | Complete | Feature 003 delivered OCRA evaluation, Feature 004 added credential lookup + dual-mode requests; future non-OCRA endpoints tracked separately |
| 5 | OCRA operator UI | Build an operator UI consuming the OCRA REST endpoint for manual operations | Complete | Spec: [Feature 006](specs/feature-006-ocra-operator-ui.md) closed after R075 validation on 2025-09-30; UI + REST/Selenium suites green via `rest-api:test` |
| 6 | OCRA quality automation | Enforce OCRA-specific architecture boundaries, expand test/coverage checks, integrate CI badges | In progress | Spec: [Feature 008](specs/feature-008-ocra-quality-automation.md), Plan: [Feature Plan 008](feature-plan-008-ocra-quality-automation.md), Tasks: [Feature 008 Tasks](tasks/feature-008-ocra-quality-automation.md) |
| 7 | OCRA specification alignment | Document OCRA references and compliance notes | Complete | Feature 007 (specs/feature-007-operator-docs.md) shipped Java/CLI/REST operator guides and synced README on 2025-09-30 |
| 8 | OCRA replay & verification | Enable OCRA credential replay flows (OTP regeneration with supplied challenges/counters) | Not started | Depends on persistence hooks and facade support |
| 9 | OCRA learning UI | Build a step-by-step UI that visualises OCRA algorithm execution for education | Not started | Requires REST/UI workstreams and instrumentation |

## Upcoming Milestones

1. Spin up dedicated workstreams for non-OCRA credential protocols (CLI/REST/persistence) once scope is prioritised.
2. Design replay/verification workflows (Workstream 8) once persistence hardening lands.
3. Prototype educational simulator UI flows (Workstream 9) to complement human operators.

## Action Items & Follow-ups

- [ ] Create detailed feature plans for Workstreams 2–5 (Feature Plan 001 covers Workstream 1).
- [ ] Capture ADRs for crypto design decisions (key formats, hashing algorithms, etc.).
- [ ] Identify specification links for inclusion in docs once network lookups are permitted.
- [x] Bootstrap architecture knowledge map in `docs/4-architecture/knowledge-map.md`.
- [x] Publish OCRA capability matrix and telemetry contract in `docs/1-concepts` (T014).

Keep this roadmap synced with each significant decision or completion event.
