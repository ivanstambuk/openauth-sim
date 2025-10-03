# Implementation Roadmap

_Last updated: 2025-10-01_

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
| 6 | OCRA quality automation | Enforce OCRA-specific architecture boundaries, expand test/coverage checks, integrate CI badges | Complete | Spec: [Feature 008](specs/feature-008-ocra-quality-automation.md), Plan: [Feature Plan 008](feature-plan-008-ocra-quality-automation.md), Tasks: [Feature 008 Tasks](tasks/feature-008-ocra-quality-automation.md); Quality gate revalidated 2025-10-01 (Jacoco 97.05 % line / 90.24 % branch, PIT 91.83 %) |
| 7 | OCRA specification alignment | Document OCRA references and compliance notes | Complete | Feature 007 (specs/feature-007-operator-docs.md) shipped Java/CLI/REST operator guides and synced README on 2025-09-30 |
| 8 | OCRA replay & verification | Enable OCRA credential replay flows (OTP verification with supplied OTP + context) | Complete | Spec: [Feature 009](specs/feature-009-ocra-replay-verification.md), Plan: [Feature Plan 009](feature-plan-009-ocra-replay-verification.md), Tasks: [Feature 009 Tasks](tasks/feature-009-ocra-replay-verification.md); Delivered 2025-10-01 with `./gradlew qualityGate` closure (Jacoco 97.31 % line / 90.08 % branch, PIT 91 %) |
| 9 | OCRA learning UI | Build a step-by-step UI that visualises OCRA algorithm execution for education | Not started | Requires REST/UI workstreams and instrumentation |
| 10 | Reflection policy hardening | Eradicate reflection usage, add quality guardrails, update contributor guidance | Complete | Spec: [Feature 011](specs/feature-011-reflection-policy-hardening.md), Plan: [Feature Plan 011](feature-plan-011-reflection-policy-hardening.md), Tasks: [Feature 011 Tasks](tasks/feature-011-reflection-policy-hardening.md); Closed 2025-10-02 after `reflectionScan` + `qualityGate` reruns validated no-reflection guardrails |
| 11 | Java 17 language enhancements | Adopt sealed hierarchies and text blocks across OCRA CLI/REST internals | Complete | Spec: [Feature 013](specs/feature-013-java17-enhancements.md), Plan: [Feature Plan 013](feature-plan-013-java17-enhancements.md), Tasks: [Feature 013 Tasks](tasks/feature-013-java17-enhancements.md); Closed 2025-10-01 with `qualityGate` validation |
| 12 | Architecture harmonization | Share OCRA orchestration, persistence provisioning, telemetry, and DTO seams across facades while preparing protocol-specific modules | Complete | Spec: [Feature 014](specs/feature-014-architecture-harmonization.md), Plan: [Feature Plan 014](feature-plan-014-architecture-harmonization.md), Tasks: [Feature 014 Tasks](tasks/feature-014-architecture-harmonization.md); Closed 2025-10-02 after R1408 documentation sync + quality gate rerun |

## Upcoming Milestones

1. Spin up dedicated workstreams for non-OCRA credential protocols (CLI/REST/persistence) once scope is prioritised.
2. Confirm the next high-priority facade effort (non-OCRA protocols vs Workstream 9 learning UI) through the clarification gate and update roadmap/scope documents.
3. Prototype educational simulator UI flows (Workstream 9) to complement human operators.
4. Complete Feature 011 increments (reflection removal + guardrails) and roll the policy into standard operating instructions.

## Action Items & Follow-ups

- [x] Create detailed feature plans for Workstreams 2–5 (Feature Plan 001 covers Workstream 1). Completed 2025-09-30.
- [ ] Confirm next priority (non-OCRA protocol expansion vs learning UI) via clarification gate and log outcomes in `docs/4-architecture/open-questions.md`.
- [ ] Draft specification and feature plan for the selected post-Feature 009 workstream before implementation begins.
- [ ] Capture ADRs for crypto design decisions (key formats, hashing algorithms, etc.).
- [ ] Identify specification links for inclusion in docs once network lookups are permitted.
- [x] Produce Maintenance CLI coverage hotspot analysis (Feature 012) to safeguard the ≥0.90 Jacoco buffer for Maintenance CLI. Completed 2025-10-01.
- [x] Bootstrap architecture knowledge map in `docs/4-architecture/knowledge-map.md`.
- [x] Publish OCRA capability matrix and telemetry contract in `docs/1-concepts` (T014).

Keep this roadmap synced with each significant decision or completion event.
