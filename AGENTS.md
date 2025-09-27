# Agent Playbook

_Project TL;DR: core cryptography lives in `core/`, interface modules (`cli/`, `rest-api/`, `ui/`) sit on top, and long-form docs reside under `docs/`._

## Before You Code
- **Clarify ambiguity first.** Do not plan or implement until every requirement is understood. Ask the user, record unresolved items in `docs/4-architecture/open-questions.md`, and wait for answers.
- **Work in small steps.** Deliver self-contained changes that finish in ≤10 minutes, run `./gradlew spotlessApply check`, and commit with a conventional message.
- **Confirm prerequisites.** Ensure `JAVA_HOME` points to a Java 17 JDK before invoking Gradle or Git hooks.

## During Implementation
- **Sync context to disk.** Update the roadmap (`docs/4-architecture/roadmap.md`) and feature plans (`docs/4-architecture/feature-plan-*.md`) as progress is made. Use ADRs only for final decisions.
- **Tests are compulsory.** Always run `./gradlew spotlessApply check`. If a test remains red, disable it with a TODO, note the reason, and capture the follow-up in the relevant plan.
- **RCI self-review.** Before hand-off, review your own changes, rerun checks, and ensure documentation/test coverage matches the behaviour.
- **Dependencies.** **Never add or upgrade libraries without explicit user approval.** When granted, document the rationale in the feature plan.
- **No surprises.** Avoid destructive commands (e.g., `rm -rf`, `git reset --hard`) unless the user requests them. Stay within the repository sandbox.

## Tracking & Documentation
- **Implementation plans.** Keep high-level plans in `docs/4-architecture/roadmap.md` and feature-specific plans alongside them. Remove plans once work is complete.
- **Open questions.** Log open questions in `docs/4-architecture/open-questions.md` and mark them resolved when answered.
- **Decisions.** Record only confirmed architectural decisions as ADRs under `docs/6-decisions/`.
- **Local overrides.** If a subdirectory requires different instructions, add an `AGENTS.md` there and reference it from the roadmap/feature plan.

## After Completing Work
- Verify `./gradlew spotlessApply check` passes.
- Update/close entries in `docs/4-architecture/open-questions.md`.
- Remove or mark feature plans as complete when the work ships.
- Summarise any lasting decisions in the appropriate ADR (if applicable).

## Security & Secrets
- Keep credential data synthetic; hard-coded secrets are acceptable for tests only. Do not leak user data or modify files outside the repository.

Following this playbook keeps the project reproducible and auditable across asynchronous agent hand-offs.
