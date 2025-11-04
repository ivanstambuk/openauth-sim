# Analysis Gate Checklist

Use this checklist after a feature's specification, plan, and tasks exist but before implementation begins. After implementation, complete the Implementation Drift Gate section before the feature can be marked complete. Together these guardrails enforce the project constitution and keep specifications, plans, tasks, and code aligned.

## Inputs
- Feature specification (e.g., `docs/4-architecture/specs/feature-XXX-*.md`)
- Feature plan (e.g., `docs/4-architecture/feature-plan-XXX-*.md`)
- Feature tasks (e.g., `docs/4-architecture/tasks/feature-XXX-*.md`)
- Open questions log (`docs/4-architecture/open-questions.md`)
- Constitution (`docs/6-decisions/project-constitution.md`)
- Feature plan subsection reserved for the Implementation Drift Gate report (create if missing)

## Checklist
1. **Specification completeness**
   - [ ] Objectives, functional, and non-functional requirements are populated.
   - [ ] Clarifications section reflects the latest answers for every high- and medium-impact question logged for this feature.
   - [ ] UI-impacting work includes an ASCII mock-up in the spec (`docs/4-architecture/spec-guidelines/ui-ascii-mockups.md`).
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

## Implementation Drift Gate (Pre-Completion)
Run this section once all planned tasks are complete and the latest build is green.

1. **Preconditions**
   - [ ] Feature tasks are all marked complete (☐ → ☑) and associated specs/plans reflect the final implementation.
   - [ ] Latest `./gradlew spotlessApply check` (or narrower documented suite) has passed within this increment.
2. **Cross-artifact validation**
   - [ ] Every high- and medium-impact specification requirement maps to executable code/tests; cite spec sections against classes/tests in the drift report, and note any low-level coverage adjustments.
   - [ ] No implementation or tests lack an originating spec/plan task; undocumented work is captured as a follow-up task or spec addition.
   - [ ] Feature plan and tasks remain consistent with the shipped implementation (dependencies, acceptance criteria, sequencing).
3. **Divergence handling**
   - [ ] High- and medium-impact gaps or over-deliveries are logged as new entries in `docs/4-architecture/open-questions.md` for user direction.
   - [ ] Low-impact or low-level drift (typos, minor wording, formatting) is corrected directly before finalising the report; document the fix without escalating.
   - [ ] Follow-up tasks or spec updates are drafted for any outstanding divergences awaiting approval.
4. **Coverage confirmation**
   - [ ] Tests exist for each success, validation, and failure branch enumerated in the specification, and their latest run is green.
   - [ ] Any missing coverage is documented with explicit tasks and blockers.
5. **Report & retrospective**
   - [ ] Implementation Drift Gate report added to the feature plan, detailing findings, artefact links, and reviewer(s).
   - [ ] Lessons learned and reusable guidance captured for future features (e.g., updates to specs/runbooks/templates).
   - [ ] Stakeholders (product, technical, AI agent as applicable) have acknowledged the report outcome before completion.

## Output
Document the outcome in the relevant feature plan under a "Analysis Gate" subsection, including:
- Date/time of the review
- Checklist pass/fail notes
- Follow-up actions or remediation tasks

Only proceed to implementation when every checkbox is satisfied or deferred with explicit owner approval.

For the Implementation Drift Gate, append the completed checklist and report summary to the feature plan. Do not mark the feature complete until all high/medium-impact divergences are resolved through updated specs, approved tasks, or user sign-off.
