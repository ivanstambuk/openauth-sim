# Feature <NNN> – <Descriptive Name>

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | YYYY-MM-DD |
| Owners | <Name(s)> |
| Linked plan | `docs/4-architecture/features/<NNN>/plan.md` |
| Linked tasks | `docs/4-architecture/features/<NNN>/tasks.md` |
| Roadmap entry | #<workstream number> |

## Overview
Summarise the problem, affected modules (core/application/CLI/REST/UI), and the user impact in 2–3 sentences. Call out any constitutional constraints (spec-first, telemetry, persistence) that drive this work.

## Clarifications
- YYYY-MM-DD – Question/decision summary (owner decision).

Log every high- or medium-impact answer here. Remove the row from `docs/4-architecture/open-questions.md` once recorded.

## Goals
List the concrete outcomes this feature must deliver (behavioural, quality, telemetry, documentation).

## Non-Goals
Call out adjacent topics that remain out of scope so the implementation doesn’t drift.

## Requirements
Document one section per requirement (R1, R2, …). Each section should include the success, validation, and failure paths so tests can be staged before implementation.

### R1 – <Title>
- **Success path:** Required behaviour when inputs are valid.
- **Validation path:** Input validation logic, errors, or warnings.
- **Failure path:** How the system responds to downstream faults.
- **Telemetry & traces:** Event names, redaction rules, verbose trace changes.
- **Traceability:** Tests (unit/integration/UI) that will cover this requirement.

### R2 – <Title>
<Repeat as needed.>

## UI / Interaction Mock-ups (required for UI-facing work)
Embed ASCII sketches illustrating layouts or state changes. Reference the guideline in `docs/4-architecture/spec-guidelines/ui-ascii-mockups.md` when completing this section. Remove it if the feature has no UI impact.

```
<ASCII mock-up>
```

## Branch & Scenario Matrix
Assign each scenario a stable identifier (e.g., `S<feature>-<nn>`) so feature plans, tasks, and tests can reference it without editing the spec. Keep entries high-level; all implementation status tracking happens in the plan/tasks.

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S123-01 | Describe behaviour |

## Test Strategy
Describe how each layer gains coverage. Mention failing tests that must be staged before implementation.
- **Core:** …
- **Application:** …
- **REST:** …
- **CLI:** …
- **UI (JS/Selenium):** …
- **Docs/Contracts:** OpenAPI, telemetry snapshots, etc.

## Telemetry & Observability
Detail event names, required fields, redaction rules, and verbose-trace additions so all facades stay in sync.

## Documentation Deliverables
Enumerate roadmap/knowledge-map/how-to/ADR updates triggered by this feature.

## Fixtures & Sample Data
List any fixture files that must be added or updated (e.g., `docs/test-vectors/<protocol>/…`).

## Appendix (Optional)
Include supporting notes, payload examples, or references that help future agents understand the context.
