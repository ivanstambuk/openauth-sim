# Analysis Gate Checklist

Use this checklist after a feature's specification, plan, and tasks exist but before implementation begins. It enforces the project constitution and keeps specifications, plans, and tasks aligned.

## Inputs
- Feature specification (e.g., `docs/4-architecture/specs/feature-XXX-*.md`)
- Feature plan (e.g., `docs/4-architecture/feature-plan-XXX-*.md`)
- Feature tasks (e.g., `docs/4-architecture/tasks/feature-XXX-*.md`)
- Open questions log (`docs/4-architecture/open-questions.md`)
- Constitution (`docs/6-decisions/project-constitution.md`)

## Checklist
1. **Specification completeness**
   - [ ] Objectives, functional, and non-functional requirements are populated.
   - [ ] Clarifications section reflects most recent answers (≤5 high-impact questions per feature).
2. **Open questions review**
   - [ ] No blocking `Open` entries remain for this feature. If any exist, pause and obtain clarification.
3. **Plan alignment**
   - [ ] Feature plan references the correct specification and tasks files.
   - [ ] Dependencies and success criteria match the specification wording.
4. **Tasks coverage**
   - [ ] Every functional requirement maps to at least one task.
   - [ ] Tasks sequence tests before implementation and keep increments ≤30 minutes.
   - [ ] Planned tests enumerate the success, validation, and failure branches with failing cases queued before implementation begins.
5. **Constitution compliance**
   - [ ] No planned work violates principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
   - [ ] Planned increments minimise new control-flow complexity by extracting validation/normalisation into small helpers, keeping each change nearly straight-line.
6. **Tooling readiness**
   - [ ] Commands (`./gradlew spotlessApply check`) documented for the feature plan or runbook.
   - [ ] SpotBugs dead-state detectors (Feature 015) noted, including the module command used to validate `URF/UWF/UUF/NP` findings.
   - [ ] Analysis results recorded in the feature plan (copy this checklist with pass/fail notes).

## Output
Document the outcome in the relevant feature plan under a "Analysis Gate" subsection, including:
- Date/time of the review
- Checklist pass/fail notes
- Follow-up actions or remediation tasks

Only proceed to implementation when every checkbox is satisfied or deferred with explicit owner approval.
