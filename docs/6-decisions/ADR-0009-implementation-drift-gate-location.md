# ADR-0009: Implementation Drift Gate Report Location

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 003 ([docs/4-architecture/features/003/spec.md](docs/4-architecture/features/003/spec.md)), Feature 006 ([docs/4-architecture/features/006/spec.md](docs/4-architecture/features/006/spec.md)), Feature 010 ([docs/4-architecture/features/010/spec.md](docs/4-architecture/features/010/spec.md)), Feature 014 ([docs/4-architecture/features/014/spec.md](docs/4-architecture/features/014/spec.md))
- **Related open questions:** none

## Context

The project constitution (Principle 6 – Implementation Drift Gate) requires a structured review for each feature to ensure
that specifications, plans, tasks, and code/tests/docs stay aligned before a feature is marked complete. The checklist in
[docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) describes this gate in general terms, but does not prescribe where the
resulting drift reports must live.

Recent work on Feature 014 (Native Java API Facade) introduced:

- A feature-specific drift gate section under [docs/4-architecture/features/014/plan.md](docs/4-architecture/features/014/plan.md) (“Drift Gate – 2025-11-15
  (Native Java + Javadoc)” with an explicit agent checklist).
- A brief log entry in [docs/_current-session.md](docs/_current-session.md) noting that the gate was run and pointing back to the plan.

This matches how Specification-Driven Development (SDD) is organised in this repository:

- `spec.md` – normative behaviour/requirements for the feature.
- `plan.md` – execution strategy, increments, verification commands, and retrospectives.
- `tasks.md` – actionable checklist.

However, the constitution and analysis-gate checklist leave room for interpretation: drift reports could live inside
`plan.md`, in standalone `drift-report-XXX.md` files, or under a shared ``docs/5-operations`/` index. For AI agents and
humans to find and maintain these reports reliably, we need a consistent, explicit location.

## Decision

Implementation Drift Gate reports will be stored **inside each feature’s `plan.md`**, not in separate markdown files:

- Every feature that runs an Implementation Drift Gate MUST:
  - Add a dated subsection under the **“Implementation Drift Gate”** heading of
    ``docs/4-architecture/features`/<NNN>/plan.md` (for example,
    “Drift Gate – 2025-11-15 (Native Java + Javadoc)”).
  - Include within that subsection:
    - A short human-readable summary of the gate (scope, outcome).
    - A structured, checklist-style block that agents can follow on future runs (commands to execute, artefacts to
      inspect, and expected pass/fail conditions).
- The global session log ([docs/_current-session.md](docs/_current-session.md)) SHOULD record:
  - That a drift gate was run for the feature (with date).
  - A pointer back to the feature plan section as the source of truth for details.

We explicitly avoid introducing stand-alone per-feature drift report files at this time. Instead:

- The feature plan remains the single execution history document per feature (increments, gates, verification logs).
- [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) continues to describe the general gate process and may link to examples
  in feature plans (such as Feature 014) but does not host individual reports.

## Consequences

### Positive

- **Single place to look per feature.** Reviewers and agents know that all execution history—including drift reports and
  checklists—lives in ``docs/4-architecture/features`/<NNN>/plan.md`, reducing the risk of scattered or stale documents.
- **Agent-friendly structure.** Housing the gate checklists directly in the plan keeps them next to increments and tasks,
  making it easy for AI agents to discover and execute the required commands when re-running a gate.
- **Lower documentation overhead.** Avoiding separate `drift-report-*.md` files keeps the SDD artefact set small:
  spec + plan + tasks + session log, with gate reports treated as part of the plan rather than a parallel hierarchy.
- **Consistent provenance.** Drift gate outcomes are co-located with the plan’s existing verification logs, making it
  simpler to understand how a feature evolved and was validated over time.

### Negative

- **Plan files become longer.** For large features with multiple drift gate runs, `plan.md` can accumulate substantial
  history, making it longer to scan. Careful use of dated subsections and concise summaries is needed to preserve
  readability.
- **Cross-feature overviews require an index.** Because reports stay embedded in plans, anyone wanting a global view of
  all drift gates must either search across plans or build a separate index document; this ADR does not define such an
  index yet.

## Alternatives Considered

- **A – Standalone drift report files per feature (`drift-report-XXX.md`)**  
  - *Pros:*  
    - Keeps `plan.md` smaller; reports can include extensive detail without bloating the plan.  
    - Easy to link to a single “report” file from the constitution or operations docs.  
  - *Cons:*  
    - Splits execution history across multiple documents (plan vs drift report).  
    - Increases the risk of divergence between `plan.md` and the drift report.  
    - Harder for agents to know which file is authoritative without extra conventions.  
  - *Outcome:* Rejected in favour of keeping execution history per feature in one place.

- **B – Centralised drift report index under ``docs/5-operations`/`**  
  - *Pros:*  
    - Provides a single overview of all gates; useful for audits and governance reviews.  
  - *Cons:*  
    - Still requires per-feature detail somewhere; if the index also hosted full reports, it would duplicate or move
      content away from feature plans.  
  - *Outcome:* Deferred. An index may be introduced later, but individual reports will remain in feature plans.

- **C – Embed drift gate results in `tasks.md` instead of `plan.md`**  
  - *Pros:*  
    - Keeps verification notes close to task checkboxes.  
  - *Cons:*  
    - Tasks files are meant to be concise checklists; embedding full reports there would blur responsibility between
      plan and tasks.  
  - *Outcome:* Rejected; tasks remain a summary checklist, while plans hold narrative and verification details.

## Security / Privacy Impact

- This decision does not change runtime behaviour or data flows; it only affects documentation layout.  
- Keeping drift reports in feature plans helps ensure that security-relevant drift (for example, missing telemetry,
  changed trust boundaries) is documented where the rest of the feature’s operational notes live, reducing the chance of
  missing important context during reviews.

## Operational Impact

- **For contributors and agents:**  
  - When running an Implementation Drift Gate, they must update the relevant feature’s `plan.md` under the
    “Implementation Drift Gate” heading and note the run in [docs/_current-session.md](docs/_current-session.md).  
  - They should follow the feature-specific checklist in the plan (e.g., Feature 014’s Native Java + Javadoc gate) rather
    than inventing ad-hoc verification steps.
- **For reviewers:**  
  - Reviewing a feature for completion involves checking: `spec.md`, `plan.md` (including the drift gate section),
    `tasks.md`, and the latest CI run; no additional drift-report files need to be tracked.  
- **For tooling:**  
  - Future automation that parses drift gate status can treat ``docs/4-architecture/features`/*/plan.md` as the canonical
    location, searching for “Implementation Drift Gate” headings and dated subsections.

## Links

- Related spec sections:  
  - [docs/4-architecture/features/010/spec.md](docs/4-architecture/features/010/spec.md) (Documentation & Knowledge Automation – governance for docs/quality).  
  - [docs/4-architecture/features/014/spec.md](docs/4-architecture/features/014/spec.md) (Native Java API Facade – cross-cutting facade governance).  
- Related ADRs:  
  - ADR-0003 – Governance Workflow and Drift Gates.  
  - ADR-0004 – Documentation & Aggregated Quality Gate Workflow.  
  - ADR-0008 – Native Java Javadoc CI Strategy.  
- Related operations docs:  
  - [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) (global analysis and drift-gate checklist).  

