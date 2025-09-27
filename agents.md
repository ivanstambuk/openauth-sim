# Agent Playbook

This repository is intentionally agent-driven. To keep collaboration predictable across sessions, follow these guardrails whenever an AI agent takes control:

1. **Stay incremental.** Deliver self-contained changes that land in &lt;10 minutes, run `spotlessApply check`, and commit with a conventional message. Decompose big tasks rather than landing monolithic diffs.
2. **Sync context to disk.** Update the appropriate files under `docs/` (overview, ADRs, runbooks, changelog) as you learn new facts or change behaviour so the next agent can resume without replaying the conversation.
3. **Tests are compulsory.** Never skip `JAVA_HOME="$PWD/.jdks/jdk-17.0.16+8" ./gradlew spotlessApply check`. If something must remain red temporarily, disable the failing test with a TODO and document the reason in an ADR or issue.
4. **No surprises.** Avoid destructive commands (e.g. `rm -rf`, `git reset --hard`) unless explicitly instructed. Never mutate files outside the repo without prior agreement.
5. **Flag anomalies.** If you notice unexpected local changes, missing assets, or security-sensitive material, stop and raise it in the conversation before proceeding.
6. **Document decisions.** Major architectural/protocol/storage choices require an ADR entry under `docs/6-decisions/`.
7. **Respect secrets.** Treat all credential material as synthetic; never paste real secrets or production data into the repo or transcripts.

Following this playbook keeps the project reproducible and auditable across asynchronous agent hand-offs.
