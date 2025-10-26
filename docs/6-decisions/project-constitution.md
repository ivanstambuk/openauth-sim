<!--
Sync Impact Report
Version: none -> 1.0.0
Modified Principles: n/a
Added Sections: Principles, Governance, Enforcement
Removed Sections: none
Templates requiring updates: docs/6-decisions/project-constitution.md ✅, AGENTS.md ✅, docs/5-operations/runbook-session-reset.md ✅, docs/4-architecture/feature-plan-001-core-domain.md ✅, docs/4-architecture/specs/feature-001-core-credential-domain.md ✅, docs/4-architecture/tasks/feature-001-core-credential-domain.md ✅, docs/5-operations/analysis-gate-checklist.md ✅
Follow-up TODOs: none
-->
# OpenAuth Simulator Constitution

## Metadata
- Constitution Version: 1.0.0
- Ratified On: 2025-09-27
- Last Amended On: 2025-10-19
- Maintainer: Ivan (project owner)

## Preamble
This constitution establishes the non-negotiable operating principles for the OpenAuth Simulator project. Specifications lead every change, AI agents execute within small, auditable increments, and the ecosystem remains reproducible for future maintainers. All contributors must follow these rules before planning, coding, or committing work.

## Principles
### Principle 1 – Specifications Lead Execution
- Author or update a feature specification before producing plans, tasks, or code.
- Treat the specification as the source of truth; implementation plans, tasks, and code must reference it explicitly.
- Store specifications under `docs/4-architecture/specs/` with traceable identifiers (e.g., Feature 001).

### Principle 2 – Clarification Gate
- Resolve ambiguous scope before planning by asking up to five high-impact questions per feature.
- Record open questions in `docs/4-architecture/open-questions.md`; do not plan or implement until blocking items are answered and captured in the specification.
- Document clarified answers in the specification under `## Clarifications`.

### Principle 3 – Test-First Quality Discipline
- Write or update executable tests (unit, integration, contract) before implementing behavior.
- During specification and planning, enumerate the success, validation, and failure branches and stage failing test cases for each path before implementation begins.
- Keep increments focused on straight-line logic by extracting validation/normalisation into small helpers that return simple enums/results, limiting new branching introduced per change.
- Run `./gradlew spotlessApply check` after every self-contained increment; a red build must be fixed or the failing test explicitly quarantined with a documented follow-up.
- Maintain architectural rules via ArchUnit and related automation when modules change.

### Principle 4 – Documentation Sync & Traceability
- Mirror every approved change across roadmap, feature plans, tasks, knowledge map, and runbooks as needed.
- Maintain per-feature `tasks.md` files that decompose work into ≤30 minute increments, reference spec requirements, and sequence tests before code.
- Log self-review notes and tool usage back into the relevant plan or runbook to preserve provenance.

### Principle 5 – Controlled Dependencies & Security
- Add or upgrade dependencies only with explicit owner approval and record the rationale in the feature plan.
- Keep secrets synthetic and test-only; production data or sensitive keys must never enter the repository.
- Follow least-destructive command practices; seek approval for high-risk actions even when automation is available.
- Backward compatibility across all facades (REST, CLI, UI, programmatic, and future additions) is intentionally unsupported. Implement fallback logic only when the user explicitly directs it for the current scope.

## Governance
- **Amendments:** Propose constitution changes via pull request referencing this document. Classify version bumps as MAJOR (principle removal or incompatible rewrite), MINOR (new principle or substantial expansion), or PATCH (clarification without semantic change).
- **Review cadence:** Reconfirm adherence during each session reset using `docs/5-operations/runbook-session-reset.md`.
- **Exception handling:** Temporary deviations require written approval in the relevant feature plan and must include a restoration plan.

## Enforcement
- `docs/5-operations/analysis-gate-checklist.md` must be executed once a spec, plan, and tasks exist to verify alignment before implementation.
- Commits failing constitutional checks may not merge; re-run analysis and remediation before continuing.
- Repeated violations trigger a governance review to determine whether additional principles or automation are required.
