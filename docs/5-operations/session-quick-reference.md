# Session Quick Reference

Use this appendix to accelerate hand-offs and new-session spin-up. Update it whenever the standard workflow changes so every agent can copy/paste the same artefacts.

## Session Kickoff Checklist
- [ ] Run `git status -sb` to review branch, staged changes, and repo cleanliness.
- [ ] Confirm environment prerequisites: `JAVA_HOME` points to a Java 17 JDK and `git config core.hooksPath` returns `githooks`.
- [ ] Review current context: latest roadmap entry, active specification, feature plan, tasks checklist, and `docs/4-architecture/open-questions.md`.
- [ ] If the last build is stale or after syncing, run `./gradlew --no-daemon spotlessApply check` to ensure the baseline is green (capture or resolve any failures before proceeding).
- [ ] Before running long Gradle workflows (`spotlessApply check`, `qualityGate`, full commits), set CLI tool timeouts to ≥600 s so the run doesn’t terminate mid-pipeline.
- [ ] Check `docs/_current-session.md` for the active snapshot; refresh it with today’s status before you hand off.
- [ ] Confirm whether the user granted a compatibility exception; default is no fallbacks for any facade unless explicitly requested.

## Commit Protocol Reminder
- When the user says “commit” or “commit and push,” stage the entire repository (`git add -A`) and rely on the managed hooks to run the quality pipeline.
- Skim the staged changes under `docs/4-architecture/` so you know which specs/plans/tasks moved, then commit with a single-line conventional subject that summarises the change (no feature listings) and push as soon as the hooks succeed.

## Handoff Prompt Template
```
You’re resuming work on [project/workstream identifier]. Core context:

- Environment: repo at [path], Java 17 (`JAVA_HOME=[…]`), Gradle via `./gradlew --no-daemon …`, hooks installed at `githooks`. Follow docs/6-decisions/project-constitution.md and AGENTS.md.
- Current status: roadmap entry #[…], spec […], plan […], tasks […]. Last green build(s): [commands + date].
- Recent increments: [brief bullet list of the last completed increments, noting key files touched and outcomes].
- Pending scope: [next planned increments/tasks], including failing tests to stage, implementation goals, telemetry/observability requirements, and documentation updates.
- Git state: [branch], staged files [list or “clean”], outstanding TODOs [if any].
- Next steps you should take now: [ordered checklist, e.g., “1. Stage failing tests for … 2. Implement … 3. Update docs …”].
- Reminders: keep increments ≤10 minutes, update docs (spec/plan/tasks/roadmap), run `./gradlew --no-daemon spotlessApply check` before commits, capture telemetry/trust-anchor decisions (if relevant), and document open questions in `docs/4-architecture/open-questions.md`.
```
    
> Tip: Retros for new sessions should paste the filled template into the opening message so successors inherit complete context.

## Reminders
- Never document or implement fallback paths in specs, plans, or code without explicit user approval.

## Common Command Snippets
- Repository snapshot: `git status -sb`
- Summarise staged/local changes: `git diff --stat` (or `git diff --cached --stat` when reviewing staged work)
- Focused module test: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.YourTestClass"` (swap module/test as needed)
- Formatting + baseline verification: `./gradlew --no-daemon spotlessApply check`
- Full quality gate (when required): `./gradlew --no-daemon qualityGate`
