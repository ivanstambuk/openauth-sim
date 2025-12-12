# Session Quick Reference

Use this appendix to accelerate hand-offs and new-session spin-up. Update it whenever the standard workflow changes so every agent can copy/paste the same artefacts.

## Session Kickoff Checklist
- [ ] Run `git status -sb` to review branch, staged changes, and repo cleanliness.
- [ ] Confirm environment prerequisites: `JAVA_HOME` points to a Java 17 JDK and `git config core.hooksPath` returns `githooks`; log the command/output in `_current-session.md` so Feature 011 traceability stays intact.
- [ ] Review current context: latest roadmap entry, active specification, feature plan, tasks checklist, and [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md).
- [ ] Confirm that the active feature spec already encodes known decisions directly in its requirements/NFR/behaviour/telemetry sections (no per-feature `## Clarifications` appendices).
- [ ] If new clarifications arise, record them in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md), pause planning until answers are agreed, then update the spec sections and mark the questions as resolved with links to those sections; create or reference an ADR for architectural or other high‑impact decisions.
- [ ] Console/UI changes now live under Feature 009 – consult ``docs/4-architecture/features/009`/{spec,plan,tasks}.md` (legacy operator-console features moved under `legacy/<old-id>/`).
- [ ] Documentation/how-to automation (Feature 010), governance/runbooks/hooks (Feature 011), core cryptography & persistence docs (Feature 012), and toolchain/quality automation (Feature 013) each have consolidated spec/plan/tasks under ``docs/4-architecture/features`/<NNN>/`; check those folders when triaging Batch P3 work. Feature 011 specifically documents FR-011-01..08/NFR-011-01..05, including the required hook guard + spotless logging commands.
- [ ] If scope introduces or modifies UI, confirm the spec includes an ASCII mock-up ([docs/4-architecture/spec-guidelines/ui-ascii-mockups.md](docs/4-architecture/spec-guidelines/ui-ascii-mockups.md)).
- [ ] If the last build is stale or after syncing, run `./gradlew --no-daemon spotlessApply check` to ensure the baseline is green (capture or resolve any failures before proceeding).
- [ ] When changing Gradle dependencies (approved): run `./gradlew --no-daemon --write-locks :<module>:check` (or `:<module>:pmdTest`) right after the edit so `gradle.lockfile` drift is resolved early and doesn’t surface late during `spotlessApply check`.
- [ ] Before running long Gradle workflows (`spotlessApply check`, `qualityGate`, full commits), set CLI tool timeouts to ≥600 s so the run doesn’t terminate mid-pipeline.
- [ ] Check [docs/_current-session.md](docs/_current-session.md) for the active snapshot; refresh it with today’s status and update the `## Next suggested actions` section before you hand off.
- [ ] Confirm whether the user granted a compatibility exception; default is no fallbacks for any facade unless explicitly requested.

## Commit Protocol Reminder
- When the user says “commit” or “commit and push,” assistants prepare the commit while the user runs the commands. Stage (or explicitly list) the relevant files, verify `./gradlew --no-daemon spotlessApply check` has passed, and gather the staged diff for review.
- Run [./tools/codex-commit-review.sh](./tools/codex-commit-review.sh) (or equivalent) to obtain a gitlint-compliant Conventional Commit message. When code and docs change together, include a `Spec impact:` line that explicitly lists the impacted artefacts (spec/plan/tasks/ADR paths). Commit messages must not contain semicolons; if a body needs multiple lines, compose it using multiple `-m` flags. Do **not** use yes/no flags. Then output copy/paste-ready fenced-code-block `git commit …` and `git push …` commands (with any required timeouts noted). The operator executes those commands locally unless they explicitly delegate execution.

## Handoff Prompt Template
```
You’re resuming work on [project/workstream identifier]. Core context:

- Environment: repo at [path], Java 17 (`JAVA_HOME=[…]`), Gradle via `./gradlew --no-daemon …`, hooks installed at `githooks`. Follow docs/6-decisions/project-constitution.md and AGENTS.md.
- Current status: roadmap entry #[…], spec […], plan […], tasks […]. Last green build(s): [commands + date].
- Recent increments: [brief bullet list of the last completed increments, noting key files touched and outcomes].
- Pending scope: [next planned increments/tasks], including failing tests to stage, implementation goals, telemetry/observability requirements, and documentation updates (keep this aligned with the `## Next suggested actions` section in `docs/_current-session.md`).
- Git state: [branch], staged files [list or “clean”], outstanding TODOs [if any].
- Next steps you should take now: [ordered checklist, e.g., “1. Stage failing tests for … 2. Implement … 3. Update docs …”].
- Reminders: keep planned tasks/increments ≤90 minutes by organising work into logical slices (execution may run longer if needed), update docs (spec/plan/tasks/roadmap), run `./gradlew --no-daemon spotlessApply check` before commits, capture telemetry/trust-anchor decisions (if relevant), and document open questions in `docs/4-architecture/open-questions.md`.
```
    
> Tip: Retros for new sessions should paste the filled template into the opening message so successors inherit complete context.

## Reminders
- Specification-Driven Development governs every change: verify the spec is current, stage failing tests, then execute the plan.
- Never document or implement fallback paths in specs, plans, or code without explicit user approval.

## Common Command Snippets
- Repository snapshot: `git status -sb`
- Summarise staged/local changes: `git diff --stat` (or `git diff --cached --stat` when reviewing staged work)
- Focused module test: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.YourTestClass"` (swap module/test as needed)
- Formatting + baseline verification: `./gradlew --no-daemon spotlessApply check`
- Full quality gate (when required): `./gradlew --no-daemon qualityGate`
- JSON-LD snippets + bundle: `./gradlew --no-daemon generateJsonLd` (task now runs automatically inside `check`/`qualityGate`, but manual runs update ``docs/3-reference/json-ld/snippets`/*.jsonld` and [build/json-ld/openauth-sim.json](build/json-ld/openauth-sim.json) without invoking the rest of the pipeline.)
