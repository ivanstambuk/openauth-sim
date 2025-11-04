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
- Last Amended On: 2025-11-04
- Maintainer: Ivan (project owner)

## Preamble
This constitution establishes the non-negotiable operating principles for the OpenAuth Simulator project. Specifications lead every change, AI agents execute within small, auditable increments, and the ecosystem remains reproducible for future maintainers. All contributors must follow these rules before planning, coding, or committing work.

## Principles
### Principle 1 – Specifications Lead Execution
- Author or update a feature specification before producing plans, tasks, or code.
- Treat the specification as the source of truth; implementation plans, tasks, and code must reference it explicitly.
- Store specifications under `docs/4-architecture/specs/` with traceable identifiers (e.g., Feature 001).

### Principle 2 – Clarification Gate
- Resolve ambiguous scope before planning by asking up to five high-impact questions per feature; escalate medium-impact uncertainties the same way, but tidy low-level or lightweight ambiguities directly and note the fixes in the governing spec/plan.
- Record high- and medium-impact open questions in `docs/4-architecture/open-questions.md`; do not plan or implement until the user answers them and the specification captures the resolution.
- Document clarified answers in the specification under `## Clarifications`, including any low-level adjustments handled without escalation.

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
- For any UI-facing feature or modification, include ASCII mock-ups directly in the governing specification per `docs/4-architecture/spec-guidelines/ui-ascii-mockups.md`.

### Principle 5 – Controlled Dependencies & Security
- Add or upgrade dependencies only with explicit owner approval and record the rationale in the feature plan.
- Keep secrets synthetic and test-only; production data or sensitive keys must never enter the repository.
- Follow least-destructive command practices; seek approval for high-risk actions even when automation is available.
- Backward compatibility across all facades (REST, CLI, UI, programmatic, and future additions) is intentionally unsupported. Implement fallback logic only when the user explicitly directs it for the current scope.

### Principle 6 – Implementation Drift Gate
- Before a feature can be marked complete, run an Implementation Drift Gate once all planned tasks are complete and tests are green.
- Cross-check the approved specification, feature plan, tasks checklist, and code/tests to confirm every spec requirement has a corresponding implementation and that no implementation ships without documented intent.
- Verify high-impact and medium-impact requirements explicitly trace from specification to implementation and tests; ensure low-level details remain consistent with the governing artefacts.
- Produce a drift report (attach it to the governing feature plan) summarising matches, gaps, and speculative work; reference exact spec sections and code paths so reviewers can trace decisions.
- Record every high- or medium-impact divergence as an open question in `docs/4-architecture/open-questions.md` for user direction; remediate lightweight or low-level drift yourself (typos, formatting, minor wording) and capture the adjustments in the drift report without escalating.
- Verify executable coverage alignment by confirming each spec branch has failing tests staged before implementation and green tests afterwards; call out any missing coverage as follow-up tasks.
- Document lessons and reusable guidance surfaced during the gate so downstream features inherit the updated practices.

## Governance
- **Amendments:** Propose constitution changes via pull request referencing this document. Classify version bumps as MAJOR (principle removal or incompatible rewrite), MINOR (new principle or substantial expansion), or PATCH (clarification without semantic change).
- **Review cadence:** Reconfirm adherence during each session reset using `docs/5-operations/runbook-session-reset.md`.
- **Exception handling:** Temporary deviations require written approval in the relevant feature plan and must include a restoration plan.

## Enforcement
- `docs/5-operations/analysis-gate-checklist.md` must be executed once a spec, plan, and tasks exist to verify alignment before implementation.
- The Implementation Drift Gate report must demonstrate zero unresolved high- or medium-impact divergences before a feature is marked complete; pending issues require recorded follow-up tasks or specification updates approved by the user.
- Commits failing constitutional checks may not merge; re-run analysis and remediation before continuing.
- Repeated violations trigger a governance review to determine whether additional principles or automation are required.
