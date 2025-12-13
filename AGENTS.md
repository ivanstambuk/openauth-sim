# Agent Playbook

_Project TL;DR: core cryptography lives in `core/`, interface modules (`cli/`, `rest-api/`, `ui/`) sit on top, and long-form docs reside under `docs/`._ Read the project constitution in [docs/6-decisions/project-constitution.md](docs/6-decisions/project-constitution.md) before acting.

For AI coding assistants and agents, start with:
- [ReadMe.LLM](ReadMe.LLM) – LLM-oriented overview of protocols, Native Java entry points, and usage patterns.
- [llms.txt](llms.txt) – manifest of high-signal specs under `docs/4-architecture/features` for context windows.
- [docs/4-architecture/facade-contract-playbook.md](docs/4-architecture/facade-contract-playbook.md) – cross-facade contract conventions and enforcement gates.
- This `AGENTS.md` – governance, workflows, and repository guardrails.

## Agent-Facing Repo Map
- **Modules**
  - `core/` – protocol primitives, crypto, and fixtures (HOTP, TOTP, OCRA, FIDO2, EMV/CAP, EUDIW helpers). Treat as internal unless a spec explicitly blesses a type.
  - `application/` – orchestration + **Native Java API seams**. Preferred entry points for generated Java code:
    - HOTP – `io.openauth.sim.application.hotp.HotpEvaluationApplicationService`
    - TOTP – `io.openauth.sim.application.totp.TotpEvaluationApplicationService`
    - OCRA – `io.openauth.sim.application.ocra.OcraEvaluationApplicationService`
    - EMV/CAP – `io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService`
    - FIDO2/WebAuthn – `io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService`
    - EUDIW OpenID4VP – `io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService` and `OpenId4VpValidationService`
  - `infra-persistence/` – `CredentialStoreFactory` for MapDB-backed or in-memory `CredentialStore` instances; acquire stores here instead of constructing MapDB builders directly.
  - `cli/` – Picocli entry points (HOTP/TOTP/OCRA/FIDO2/EMV/EUDIW commands). Safe to extend once specs/plans/tasks describe the new commands.
  - `rest-api/` – Spring Boot REST facade and OpenAPI snapshots.
  - `ui/` – operator console HTML/JS; coordinate UI changes with the owning feature (for example Feature 009, Feature 006).
- ``tools/mcp-server`/` – REST-backed Model Context Protocol (MCP) proxy. Agents run it alongside `:rest-api:bootRun` so MCP-capable clients can invoke simulator endpoints via the tools listed in Feature 013 (hotp/totp/ocra/emv/fido2/eudiw + fixtures). The MCP `tools/list` catalogue now returns JSON Schema payloads plus per-tool prompt hints and version metadata, and the tool set also includes `totp.helper.currentOtp`, which retrieves the active OTP/metadata for a stored credential before calling `totp.evaluate`. See the commands below for instructions.
  - `docs/` – constitution, roadmap, specs/plans/tasks, protocol reference pages, and how-to guides (including ``docs/2-how-to`/*-from-java.md`).
- **Build and test commands (canonical)**
  - Full formatting + verification: `./gradlew --no-daemon spotlessApply check`
  - Docs-only fast lane (format + link checks): `./tools/docs-verify.sh` (still run the full gate before merge)
  - Focused Native Java checks (when editing entry points only): `./gradlew --no-daemon :application:test :core:test`
  - REST/CLI/UI integration checks (when changing facades): `./gradlew --no-daemon :rest-api:test :cli:test :ui:test`
  - MCP proxy end-to-end check: `./gradlew --no-daemon :tools-mcp-server:test :rest-api:test :tools-mcp-server:spotlessApply qualityGate`
  - Run sequence for agents: first start the REST API (`./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi`), then launch the MCP proxy with `./gradlew --no-daemon :tools-mcp-server:run --args="--config ~/.config/openauth-sim/mcp-config.yaml"`.
- **Guardrails for agents**
  - Prefer the Native Java entry points above and the ``docs/2-how-to`/*-from-java.md` guides over low-level helpers.
  - Do not change public API signatures or semantics without updating the owning feature spec/plan/tasks (especially Feature 014 for Native Java).
  - Treat [llms.txt](llms.txt) as the authoritative list of high-signal specs under `docs/4-architecture/features` when building context windows.

## Before You Code
- **Clarify ambiguity first.** Do not plan or implement until every requirement is understood. Ask the user, record unresolved items in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md), and wait for answers. Capture accepted answers by updating the relevant specification’s requirements/NFR/behaviour/telemetry sections so the spec remains the single source of truth for behaviour.
  - **No-direct-question rule:** Never ask the user for clarification, approval, or a decision in chat until the matching open question is logged as a row in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md). Treat violations as blockers—stop work, add the missing entry, then resume the conversation by referencing that question ID. **Low-ambiguity fast path:** purely mechanical/logistical questions (for example timeouts, which Gradle task to run, file paths, reproduction commands, or whether to run a long verification now) are exempt and may be asked directly; if the answer would affect requirements, behaviour, telemetry, security, module boundaries, or other high-/medium-impact choices, log it first.
  - Whenever you present alternative approaches—whether for open questions or general solution proposals—first capture or update the entry in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) (single summary row per question), then present the options **inline in chat** using the Decision Card format from [docs/4-architecture/spec-guidelines/open-questions-format.md](docs/4-architecture/spec-guidelines/open-questions-format.md). Encode the final decision directly in the governing spec/plan/ADR. Do not keep historical option breakdowns in `open-questions.md`; once a question is resolved and recorded in specs/ADRs, remove its row from the log. Always order options by preference in your reply (Option A is the recommended path, Option B the next-best, etc.) so the user sees our best advice first. Keep specifications/plans/tasks limited to that ID until the question is resolved.
- **Work in small steps.** During planning, break every change into logical, self-contained tasks that are expected to complete within ≤90 minutes. Execution can take longer if required; the goal is to plan manageable increments, run `./gradlew spotlessApply check`, and commit with a conventional message for each finished slice.
- **Confirm prerequisites.** Ensure `JAVA_HOME` points to a Java 17 JDK before invoking Gradle or Git hooks.
- **Hook guard.** Verify `git config core.hooksPath githooks` before staging changes; reapply the setting after fresh clones or tool resets so the managed pre-commit hook and `commit-msg` gitlint gate both execute. Feature 011 (``docs/4-architecture/features/011`/{spec,plan,tasks}.md`) is the canonical reference for FR-011-01..08/NFR-011-01..05—when you run the guard commands (`git config core.hooksPath`, [githooks/pre-commit](githooks/pre-commit), [githooks/commit-msg](githooks/commit-msg)) capture the output in `_current-session.md` as that feature requires.
- **Prime the knowledge map.** Skim [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md) and the up-to-date module snapshot in [docs/architecture-graph.json](docs/architecture-graph.json) before planning so new work reinforces the architectural relationships already captured there.
- **Template usage.** Author new specifications, feature plans, and task checklists using [docs/templates/feature-spec-template.md](docs/templates/feature-spec-template.md), [docs/templates/feature-plan-template.md](docs/templates/feature-plan-template.md), and [docs/templates/feature-tasks-template.md](docs/templates/feature-tasks-template.md) so structure, metadata, and verification notes stay uniform across features.
- **ADR context.** Before planning or implementation, skim ADRs under ``docs/6-decisions`/` whose related-features/specs entries reference the active feature ID so high-impact clarifications and architectural decisions are treated as required context alongside the roadmap, spec, plan, tasks, and knowledge map.
 - **No ad-hoc manual edits.** Treat all repository changes—including JSON schemas, OpenAPI snapshots, fixtures, and configuration—as outputs of the SDD pipeline. Do not propose or perform hand-edits that bypass feature specs, plans, tasks, and automated/tests-backed flows; the global CLI schema at [docs/3-reference/cli/cli.schema.json](docs/3-reference/cli/cli.schema.json) is the authoritative home for all CLI `--output-json` events.

## Specification Pipeline
- Start every feature by updating or creating its specification at ``docs/4-architecture/features`/<NNN>/spec.md`.
- For any new UI feature or modification, include an ASCII mock-up in the specification (see [docs/4-architecture/spec-guidelines/ui-ascii-mockups.md](docs/4-architecture/spec-guidelines/ui-ascii-mockups.md)).
- Capture every high-impact clarification question (and each medium-impact uncertainty) per feature; log them in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) and, once resolved, record the outcome directly in the spec (requirements, NFR, behaviour/UI, telemetry/policy sections). For architecturally significant clarifications (cross-feature/module boundaries, security/telemetry strategy, major NFR trade-offs), create or update an ADR under ``docs/6-decisions`/` using [docs/templates/adr-template.md](docs/templates/adr-template.md) after updating the spec, then mark the corresponding open-questions row as resolved with links to the spec sections and ADR ID. Tidy lightweight ambiguities locally and note the adjustment in the governing spec/plan.
- Generate or refresh the feature plan (``docs/4-architecture/features`/<NNN>/plan.md`) only after the specification is current and high-/medium-impact clarifications are resolved and recorded in the spec (plus ADRs where required).
- Maintain a per-feature tasks checklist at ``docs/4-architecture/features`/<NNN>/tasks.md` that mirrors the plan, orders tests before code, and keeps planned increments ≤90 minutes by preferring finer-grained entries and documenting sub-steps when something nears the limit.
- When revising a specification, only document fallback or compatibility behaviour if the user explicitly asked for it; if instructions are unclear, pause and request confirmation instead of assuming a fallback.
- Run the analysis gate in [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) once spec, plan, and tasks agree; address findings before implementation.
- Treat legacy per-feature `## Clarifications` sections as removed; do not reintroduce them. Resolved clarifications must be folded into the spec’s normative sections (requirements, NFR, behaviour/UI, telemetry/policy), with history captured via [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md), ADRs under ``docs/6-decisions`/`, and session/plan logs.

## Session Kickoff
- Follow [docs/5-operations/runbook-session-reset.md](docs/5-operations/runbook-session-reset.md) whenever a chat session starts without prior context.
- Summarise roadmap status, feature plan progress, and open questions **only** when the user asks for project/status context (for example “project status”, “read project context”) or when performing a session reset.
- Request clarification on outstanding questions before planning or implementation; log any new questions immediately.

> Quick reference: See [docs/5-operations/session-quick-reference.md](docs/5-operations/session-quick-reference.md) for the Session Kickoff Checklist and handoff prompt template.
- Maintain [docs/_current-session.md](docs/_current-session.md) as the single live snapshot across active chats; always review/update it before closing a session.
- Feature ownership quick cues (Batch P3): Feature 009 now covers operator console/UI docs, Feature 010 owns documentation & knowledge automation, Feature 011 governs AGENTS/runbooks/hooks, Feature 012 centralises core cryptography & persistence docs, and Feature 013 aggregates toolchain/quality automation guidance.

## SDD Feedback Loops
- Specification-Driven Development (SDD) is the default cadence. Anchor every increment in an explicit specification, aligned with the [GitHub Spec Kit reference](https://github.com/github/spec-kit/blob/main/spec-driven.md).
- **Update specs before code.** For every task, refresh the relevant feature plan and note open questions; only move forward once the plan reflects the desired change.
- **Test-first cadence.** Write or extend executable specifications (unit, behaviour, or scenario tests) ahead of implementation, confirm they fail, and then drive code to green before refactoring.
- **Branch coverage upfront.** When outlining a feature, list the expected success, validation, and failure branches and add thin failing tests for each path before writing implementation code so coverage grows organically.
- **Reflection checkpoint.** After loops close, record lessons, coverage deltas, and follow-ups back into the feature plan or roadmap to keep the spec-driven history auditable.

## During Implementation
- **Sync context to disk.** Update the roadmap ([docs/4-architecture/roadmap.md](docs/4-architecture/roadmap.md)), feature specs, feature plans, and tasks documents as progress is made. Use ADRs only for final decisions.
- **No unapproved deletions.** Never delete files or directories—especially via recursive commands or when cleaning untracked items—unless the user has explicitly approved the exact paths in the current session. Features may be developed in parallel across sessions, so untracked files or directories can appear without warning; surface them for review instead of removing them.
- **Tests are compulsory.** Always run `./gradlew spotlessApply check`. If a test remains red, disable it with a TODO, note the reason, and capture the follow-up in the relevant plan. Commit messages must satisfy the repository [.gitlint](.gitlint) conventional-commit rules.
- **Formatter policy.** Spotless now uses Palantir Java Format 2.78.0 with a 120-character wrap; configure IDE formatters to match before pushing code changes.
- **Maintain the knowledge map.** Add, adjust, or prune entries in [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md) whenever new modules, dependencies, or contracts appear.
- **Straight-line increments.** Keep each increment's control flow flat by delegating validation/normalisation into tiny pure helpers that return simple enums or result records, then compose them instead of introducing inline branching that inflates the branch count per change.
- **RCI self-review.** Before hand-off, review your own changes, rerun checks, and ensure documentation/test coverage matches the behaviour.
- **Lint checkpoints.** When introducing new helpers/utilities or editing files prone to style violations (records, DTOs, generated adapters), run the narrowest applicable lint target before the full pipeline (for example `./gradlew --no-daemon :application:checkstyleMain`). Note the command in the related plan/task so every agent repeats it.
- **Commit & push protocol.** Once an increment passes `./gradlew spotlessApply check`, prepare the commit for the operator instead of executing it yourself. Stage the requested files (or list the exact paths that must be staged), run [./tools/codex-commit-review.sh](./tools/codex-commit-review.sh) (or equivalent) to obtain a Conventional Commit message that satisfies gitlint and includes a `Spec impact:` line whenever docs and code change together. Commit messages must not contain semicolons; if a body needs multiple lines, emit them using multiple `-m` flags. When presenting Git commands to the operator, always render each command as its own fenced code block for copy/paste. Then present the staged summary plus copy/paste-ready `git commit …` and `git push …` commands (with timeout guidance). The human operator runs those commands locally unless they explicitly delegate execution back to you.
  - Pre-commit hooks and the managed quality pipeline routinely take longer than two minutes. When invoking `git commit` (or any command that triggers that pipeline) via the CLI tool, always specify `timeout_ms >= 300000` so the process has enough time to finish cleanly.
- **Dependencies.** **Never add or upgrade libraries without explicit user approval.** When granted, document the rationale in the feature plan. Dependabot opens weekly update PRs—treat them as scoped requests that still require owner approval before merging.
- **Dependency locks (Gradle).** When dependency changes are approved and you add/upgrade a library, refresh lockfiles *immediately* so failures surface early (PMD aux-classpath + other resolving tasks read locks). Prefer the narrowest lock refresh for the touched module:
  - `./gradlew --no-daemon --write-locks :<module>:check` (broad but reliable), or
  - `./gradlew --no-daemon --write-locks :<module>:pmdTest` (narrower; still updates PMD aux classpath locks).
  Commit `gradle.lockfile` (and `settings-gradle.lockfile` when it changes) alongside the Gradle dependency diff.
- **No surprises.** Avoid destructive commands (e.g., `rm -rf`, `git reset --hard`) unless the user requests them. Stay within the repository sandbox.
- **No reflection.** Do not introduce Java reflection in production or test sources. When existing code requires access to collaborators, expose package-private seams or dedicated test fixtures instead. Guardrails live under Feature 011 ([docs/4-architecture/features/011/spec.md](docs/4-architecture/features/011/spec.md)) and every increment must keep `./gradlew reflectionScan` and the ArchUnit suite green.

## Guardrails & Governance
- **Module boundaries.** Treat `core/` as the source of truth for cryptography; facades (`cli/`, `rest-api/`, `ui/`) must not mutate its internals without an approved plan. Delegate OCRA orchestration through the shared `application` module and acquire MapDB stores via `infra-persistence`’s `CredentialStoreFactory` instead of touching builders directly.
- **Backward-compat stance.** Treat every interface (REST, CLI, UI/HTML+JS, programmatic APIs, and any future facades) as greenfield. Do not add fallbacks, shims, or legacy checks unless the user explicitly instructs you to do so for the current task.
- **Telemetry contract.** Emit operational events through `application.telemetry.TelemetryContracts` adapters so CLI/REST/UI logging stays sanitised and architecture tests remain green; avoid bespoke loggers unless the specification grants an exemption.
- **Intent logging.** Capture prompt summaries, command sequences, and rationale in the active feature plan or an appendix referenced from it so downstream reviewers know how the change was produced.
- **Escalation policy.** Propose risky refactors, persistence changes, or dependency updates to the user before touching code—record approvals in the relevant plan.

## Tracking & Documentation
- **Implementation plans.** Keep high-level plans in [docs/4-architecture/roadmap.md](docs/4-architecture/roadmap.md), store each feature’s spec/plan/tasks inside ``docs/4-architecture/features`/<NNN>/`, and remove plans once work is complete.
- **Open questions.** Log high- and medium-impact open questions in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) and remove each row as soon as it is resolved, ensuring the answer is captured first in the governing spec’s normative sections and, for high-impact clarifications, in an ADR.
- **Decisions.** Record only confirmed architectural decisions and architecturally significant clarifications as ADRs under ``docs/6-decisions`/`, using [docs/templates/adr-template.md](docs/templates/adr-template.md), and reference those ADR IDs from the relevant spec sections and (when applicable) the open-questions log.
- **Local overrides.** If a subdirectory requires different instructions, add an [AGENTS.md](AGENTS.md) there and reference it from the roadmap/feature plan.
- **Quality gates.** Track upcoming additions for contract tests, mutation analysis, and security/“red-team prompt” suites in the plans until automated jobs exist.

## After Completing Work
- Treat “completing work” as finishing any self-contained increment that was scoped during planning to fit within ≤90 minutes, even if actual execution takes longer. The checklist below fires after every increment that ends with a passing build.
- Verify `./gradlew spotlessApply check` passes.
- Update/close entries in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md).
- Remove or mark feature plans as complete when the work ships.
- Summarise any lasting decisions in the appropriate ADR (if applicable).
- Publish prompt and tool usage notes alongside the feature plan update so future agents understand how the iteration unfolded.
- Every proactively triggered or user-requested commit must stage the entire repository, describe all deltas in the message, and push before starting the next task.

## Security & Secrets
- Keep credential data synthetic; hard-coded secrets are acceptable for tests only. Do not leak user data or modify files outside the repository.

Following this playbook keeps the project reproducible and auditable across asynchronous agent hand-offs.
