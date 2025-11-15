# ADR-0003: Governance Workflow and Drift Gates

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 011 (`docs/4-architecture/features/011/spec.md`), Constitution (`docs/6-decisions/project-constitution.md`)
- **Related open questions:** (none currently)

## Context

The project constitution defines Specification-Driven Development (SDD) as the core workflow (Principles 1–6), including
the Clarification Gate (Principle 2) and the Implementation Drift Gate (Principle 6). Before Feature 011, the practical
governance workflow—how specs, plans, tasks, hooks, and quality gates interact—was implied across multiple documents and
hooks rather than captured as a single, auditable contract.

To keep governance reproducible and verifiable, we need a standard loop that:
- Starts from updated specifications and clarifications.
- Drives plans/tasks and test-first execution.
- Runs managed hooks and quality gates.
- Captures drift findings and command logs in shared artefacts (`_current-session.md`, session logs, feature plans).

Feature 011 must encode this workflow and ensure it remains aligned with the constitution.

## Decision

Adopt a governance workflow centred on Feature 011 and the constitution:
- Treat Feature 011 as the executable owner of constitution Principles 2 (Clarification Gate) and 6 (Implementation Drift Gate)
  for governance artefacts (AGENTS, runbooks, constitution, analysis gate checklist, session logs).
- For each governance increment:
  1. Update the relevant spec sections first (Feature 011 spec, constitution, AGENTS/runbooks) to reflect resolved
     clarifications and policy changes.
  2. Refresh the Feature 011 plan/tasks to describe intended work, tests, and verification commands.
  3. Run the Clarification Gate by ensuring high/medium-impact questions are captured in `docs/4-architecture/open-questions.md`
     and resolved answers are encoded back into spec sections.
  4. Execute tests and quality gates (including `./gradlew --no-daemon spotlessApply check` and `qualityGate` when needed).
  5. Run the Implementation Drift Gate, cross-checking spec/plan/tasks/code/hooks/docs and recording findings in the Feature 011 plan.
  6. Log all governance commands and outcomes in `_current-session.md` and session logs (hook guard, hook runs, Gradle tasks).
- Use the analysis gate checklist (`docs/5-operations/analysis-gate-checklist.md`) as the operational wrapper around this
  loop for governance changes.

Feature 011’s spec/plan/tasks describe this loop explicitly and link back to this ADR and the constitution.

## Consequences

### Positive
- Single, repeatable governance loop for all contributors, grounded in the constitution and Feature 011.
- Clarifications and drift findings always resolve back into spec sections first, preventing split truth between docs and code.
- `_current-session.md` and session logs provide a durable audit trail of governance actions and verification commands.
- Analysis and drift gates become concrete practices rather than abstract principles.

### Negative
- Governance increments require more explicit bookkeeping (spec/plan/tasks updates, drift reports, log entries) than ad-hoc edits.
- Additional ceremony may feel heavy for very small changes, though it improves traceability and future audits.

## Alternatives Considered

- **A – Rely on constitution text only**
  - Pros: Fewer artefacts; less up-front documentation.
  - Cons: Leaves practical governance behaviour (hooks, logs, gates) implicit; increases drift between theory and practice.
- **B – Feature-local governance without central ownership**
  - Pros: Features tailor governance to local needs.
  - Cons: Fragments governance; contributors must rediscover patterns; contradicts constitution guidance and Feature 011’s
    role as governance owner.
- **C – Central governance workflow owned by Feature 011 (chosen)**
  - Pros: Aligns constitution, Feature 011 spec/plan/tasks, and runbooks; makes gates auditable and repeatable.
  - Cons: Requires discipline to keep Feature 011 documentation current with practice.

## Security / Privacy Impact

- Governance workflow itself does not change data handling, but:
  - Ensures that security-related clarifications (e.g., telemetry redaction, hook-based gitleaks checks) are captured in
    specs and validated via gates.
  - Encourages consistent logging of security-related commands (gitleaks, quality gates) in `_current-session.md`.

## Operational Impact

- Operators and maintainers must:
  - Use Analysis and Implementation Drift Gates for governance changes and log executions.
  - Keep Feature 011’s plan/tasks updated with verification commands and drift reports.
  - Ensure session reset/runbook procedures reference this workflow when new agents join or sessions restart.
- The workflow clarifies when to run `git config core.hooksPath`, `githooks/pre-commit`, `githooks/commit-msg`,
  `./gradlew --no-daemon spotlessApply check`, and `qualityGate` during governance work.

## Links

- Related spec sections: `docs/4-architecture/features/011/spec.md#overview`, `#goals`, `#functional-requirements`,
  `#non-functional-requirements`, `#test-strategy`
- Related ADRs: ADR-0002 (Governance Formatter and Managed Hooks)
- Related issues / PRs: (to be linked from future governance updates)

