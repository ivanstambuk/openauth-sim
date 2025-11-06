# Agent Playbook

_Project TL;DR: core cryptography lives in `core/`, interface modules (`cli/`, `rest-api/`, `ui/`) sit on top, and long-form docs reside under `docs/`._ Read the project constitution in `docs/6-decisions/project-constitution.md` before acting.

## Before You Code
- **Clarify ambiguity first.** Do not plan or implement until every requirement is understood. Ask the user, record unresolved items in `docs/4-architecture/open-questions.md`, and wait for answers. Capture accepted answers in the relevant specification under `## Clarifications`.
  - Whenever you present alternative approaches—whether for open questions or general solution proposals—precede each clarification with a numbered heading (1., 2., …) and enumerate options alphabetically (A, B, C …); include pros and cons for each option and state the recommended choice (with rationale) before requesting a decision.
- **Work in small steps.** Deliver self-contained changes that finish in ≤30 minutes, run `./gradlew spotlessApply check`, and commit with a conventional message.
- **Confirm prerequisites.** Ensure `JAVA_HOME` points to a Java 17 JDK before invoking Gradle or Git hooks.
- **Hook guard.** Verify `git config core.hooksPath githooks` before staging changes; reapply the setting after fresh clones or tool resets so the managed pre-commit hook and `commit-msg` gitlint gate both execute.
- **Prime the knowledge map.** Skim `docs/4-architecture/knowledge-map.md` and the up-to-date module snapshot in `docs/architecture-graph.json` before planning so new work reinforces the architectural relationships already captured there.

## Specification Pipeline
- Start every feature by updating or creating its specification in `docs/4-architecture/specs/`.
- For any new UI feature or modification, include an ASCII mock-up in the specification (see `docs/4-architecture/spec-guidelines/ui-ascii-mockups.md`).
- Capture every high-impact clarification question (and each medium-impact uncertainty) per feature; log them in `docs/4-architecture/open-questions.md` and record resolutions in the spec. Tidy lightweight ambiguities locally and note the adjustment in the governing spec/plan.
- Generate or refresh the feature plan (`docs/4-architecture/feature-plan-*.md`) only after the specification is current and clarifications resolved.
- Maintain a per-feature tasks checklist under `docs/4-architecture/tasks/` that mirrors the plan, orders tests before code, and keeps increments ≤30 minutes.
- When revising a specification, only document fallback or compatibility behaviour if the user explicitly asked for it; if instructions are unclear, pause and request confirmation instead of assuming a fallback.
- Run the analysis gate in `docs/5-operations/analysis-gate-checklist.md` once spec, plan, and tasks agree; address findings before implementation.

## Session Kickoff
- Follow `docs/5-operations/runbook-session-reset.md` whenever a chat session starts without prior context.
- Begin every fresh interaction by summarising roadmap status, feature plan progress, and open questions for the user.
- Request clarification on outstanding questions before planning or implementation; log any new questions immediately.

> Quick reference: See `docs/5-operations/session-quick-reference.md` for the Session Kickoff Checklist and handoff prompt template.
- Maintain `docs/_current-session.md` as the single live snapshot across active chats; always review/update it before closing a session.

## SDD Feedback Loops
- Specification-Driven Development (SDD) is the default cadence. Anchor every increment in an explicit specification, aligned with the [GitHub Spec Kit reference](https://github.com/github/spec-kit/blob/main/spec-driven.md).
- **Update specs before code.** For every task, refresh the relevant feature plan and note open questions; only move forward once the plan reflects the desired change.
- **Test-first cadence.** Write or extend executable specifications (unit, behaviour, or scenario tests) ahead of implementation, confirm they fail, and then drive code to green before refactoring.
- **Branch coverage upfront.** When outlining a feature, list the expected success, validation, and failure branches and add thin failing tests for each path before writing implementation code so coverage grows organically.
- **Reflection checkpoint.** After loops close, record lessons, coverage deltas, and follow-ups back into the feature plan or roadmap to keep the spec-driven history auditable.

## During Implementation
- **Sync context to disk.** Update the roadmap (`docs/4-architecture/roadmap.md`), feature specs, feature plans, and tasks documents as progress is made. Use ADRs only for final decisions.
- **No unapproved deletions.** Never delete files or directories—especially via recursive commands or when cleaning untracked items—unless the user has explicitly approved the exact paths in the current session. Features may be developed in parallel across sessions, so untracked files or directories can appear without warning; surface them for review instead of removing them.
- **Tests are compulsory.** Always run `./gradlew spotlessApply check`. If a test remains red, disable it with a TODO, note the reason, and capture the follow-up in the relevant plan. Commit messages must satisfy the repository `.gitlint` conventional-commit rules.
- **Formatter policy.** Spotless now uses Palantir Java Format 2.78.0 with a 120-character wrap; configure IDE formatters to match before pushing code changes.
- **Maintain the knowledge map.** Add, adjust, or prune entries in `docs/4-architecture/knowledge-map.md` whenever new modules, dependencies, or contracts appear.
- **Straight-line increments.** Keep each increment's control flow flat by delegating validation/normalisation into tiny pure helpers that return simple enums or result records, then compose them instead of introducing inline branching that inflates the branch count per change.
- **RCI self-review.** Before hand-off, review your own changes, rerun checks, and ensure documentation/test coverage matches the behaviour.
- **Lint checkpoints.** When introducing new helpers/utilities or editing files prone to style violations (records, DTOs, generated adapters), run the narrowest applicable lint target before the full pipeline (for example `./gradlew --no-daemon :application:checkstyleMain`). Note the command in the related plan/task so every agent repeats it.
- **Commit & push protocol.** Once an increment passes `./gradlew spotlessApply check`, proactively commit and push unless the user has asked you to pause. Stage the entire repository (`git add -A`) and obtain a Conventional Commit message from an automated assistant that (a) satisfies the repository’s gitlint rules and (b) records documentation/spec impact whenever code and docs change together (use a `Spec impact:` line in the body). Apply the returned message verbatim, run the managed hooks, and push to the tracked remote immediately. When the user explicitly requests a commit (“commit” or “commit and push”), follow the same steps without asking for additional confirmation. The repository includes `./tools/codex-commit-review.sh` as the default helper, but equivalent tooling is acceptable if it honours the same contract.
  - Pre-commit hooks and the managed quality pipeline routinely take longer than two minutes. When invoking `git commit` (or any command that triggers that pipeline) via the CLI tool, always specify `timeout_ms >= 300000` so the process has enough time to finish cleanly.
- **Dependencies.** **Never add or upgrade libraries without explicit user approval.** When granted, document the rationale in the feature plan. Dependabot opens weekly update PRs—treat them as scoped requests that still require owner approval before merging.
- **No surprises.** Avoid destructive commands (e.g., `rm -rf`, `git reset --hard`) unless the user requests them. Stay within the repository sandbox.
- **No reflection.** Do not introduce Java reflection in production or test sources. When existing code requires access to collaborators, expose package-private seams or dedicated test fixtures instead. Guardrails live under Feature 011 (`docs/4-architecture/specs/feature-011-reflection-policy-hardening.md`) and every increment must keep `./gradlew reflectionScan` and the ArchUnit suite green.

## Guardrails & Governance
- **Module boundaries.** Treat `core/` as the source of truth for cryptography; facades (`cli/`, `rest-api/`, `ui/`) must not mutate its internals without an approved plan. Delegate OCRA orchestration through the shared `application` module and acquire MapDB stores via `infra-persistence`’s `CredentialStoreFactory` instead of touching builders directly.
- **Backward-compat stance.** Treat every interface (REST, CLI, UI/HTML+JS, programmatic APIs, and any future facades) as greenfield. Do not add fallbacks, shims, or legacy checks unless the user explicitly instructs you to do so for the current task.
- **Telemetry contract.** Emit operational events through `application.telemetry.TelemetryContracts` adapters so CLI/REST/UI logging stays sanitised and architecture tests remain green; avoid bespoke loggers unless the specification grants an exemption.
- **Intent logging.** Capture prompt summaries, command sequences, and rationale in the active feature plan or an appendix referenced from it so downstream reviewers know how the change was produced.
- **Escalation policy.** Propose risky refactors, persistence changes, or dependency updates to the user before touching code—record approvals in the relevant plan.

## Tracking & Documentation
- **Implementation plans.** Keep high-level plans in `docs/4-architecture/roadmap.md`, specifications in `docs/4-architecture/specs/`, feature plans alongside them, and per-feature tasks under `docs/4-architecture/tasks/`. Remove plans once work is complete.
- **Open questions.** Log open questions in `docs/4-architecture/open-questions.md` and mark them resolved when answered.
- **Decisions.** Record only confirmed architectural decisions as ADRs under `docs/6-decisions/`.
- **Local overrides.** If a subdirectory requires different instructions, add an `AGENTS.md` there and reference it from the roadmap/feature plan.
- **Quality gates.** Track upcoming additions for contract tests, mutation analysis, and security/“red-team prompt” suites in the plans until automated jobs exist.

## After Completing Work
- Treat “completing work” as finishing any self-contained increment (≤30 minutes) within an active feature. The checklist below fires after every increment that ends with a passing build.
- Verify `./gradlew spotlessApply check` passes.
- Update/close entries in `docs/4-architecture/open-questions.md`.
- Remove or mark feature plans as complete when the work ships.
- Summarise any lasting decisions in the appropriate ADR (if applicable).
- Publish prompt and tool usage notes alongside the feature plan update so future agents understand how the iteration unfolded.
- Every proactively triggered or user-requested commit must stage the entire repository, describe all deltas in the message, and push before starting the next task.

## Security & Secrets
- Keep credential data synthetic; hard-coded secrets are acceptable for tests only. Do not leak user data or modify files outside the repository.

Following this playbook keeps the project reproducible and auditable across asynchronous agent hand-offs.
