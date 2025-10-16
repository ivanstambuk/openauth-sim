# Runbook: Session Reset

Symptom:
- New chat session starts without prior conversation context.

Detection (Alerts/Queries):
- User opens with "Project status" or otherwise indicates a fresh session.

Immediate actions:
1. Read `AGENTS.md` to refresh global working agreements and the project constitution link.
2. Run `git config core.hooksPath` and ensure it returns `githooks`; set it if unset so the managed pre-commit hook executes.
3. Review `docs/4-architecture/roadmap.md` for current workstreams and milestones.
4. Inspect the active feature specification(s) in `docs/4-architecture/specs/`.
5. Inspect the corresponding feature plan(s) in `docs/4-architecture/feature-plan-*.md`.
6. Review the per-feature tasks in `docs/4-architecture/tasks/`.
7. Check `docs/4-architecture/open-questions.md` for unresolved clarifications.
8. Consult `docs/_current-session.md` for the latest workstream snapshot; update it as you discover new status.

Diagnosis tree:
- If open questions exist, prepare a clarification request before planning.
- If no open questions but feature plan tasks remain, select the highest-priority task (marked `☐`).
- If all plans are complete, coordinate with the user to queue new scope.

Remediation:
- Summarise project status back to the user (roadmap state, open questions, next suggested action).
- Request clarifications where needed and wait for responses before coding.
- Once direction is confirmed, ensure the analysis gate (`docs/5-operations/analysis-gate-checklist.md`) is satisfied, then proceed with planning/implementation per `AGENTS.md` guidelines.

## Handoff Prompt Template
Copy/paste the template in `docs/5-operations/session-quick-reference.md` when opening a new chat so the next agent inherits the full context quickly. Replace bracketed sections with the current details.

Owner/On-call escalation:
- Escalate to the user when:
  * Scope is ambiguous or conflicting across documents.
  * Required approvals (dependencies, destructive commands) are not documented.

Post-incident notes:
- Update feature plans, roadmap, and open-questions log to reflect decisions made during the new session kickoff.
- Validate local tooling: ensure the shared pre-commit hook is installed (`git config core.hooksPath githooks`) because it now runs Gradle formatting, targeted module tests, secret scanning (gitleaks), and a binary/size guard on every staged change. Confirm the companion `commit-msg` hook is present and that gitlint picks up the repo `.gitlint` conventional-commit policy.
- Reconfirm the commit/push protocol: after each passing increment, proactively stage the entire repository with `git add -A`, craft a conventional message summarising the full diff, run the managed hooks, and push immediately; follow the exact same steps when the user explicitly requests a commit.
- When committing (or running any command that triggers the managed pre-commit pipeline), set a generous CLI timeout so the Gradle checks finish. Use `timeout_ms >= 300000` when calling `git commit` to avoid premature termination.
