# Runbook: Session Reset

Symptom:
- New chat session starts without prior conversation context.

Detection (Alerts/Queries):
- User opens with "Project status" or otherwise indicates a fresh session.

Immediate actions:
1. Read `AGENTS.md` to refresh global working agreements and the project constitution link.
2. Review `docs/4-architecture/roadmap.md` for current workstreams and milestones.
3. Inspect the active feature specification(s) in `docs/4-architecture/specs/`.
4. Inspect the corresponding feature plan(s) in `docs/4-architecture/feature-plan-*.md`.
5. Review the per-feature tasks in `docs/4-architecture/tasks/`.
6. Check `docs/4-architecture/open-questions.md` for unresolved clarifications.

Diagnosis tree:
- If open questions exist, prepare a clarification request before planning.
- If no open questions but feature plan tasks remain, select the highest-priority task (marked `☐`).
- If all plans are complete, coordinate with the user to queue new scope.

Remediation:
- Summarise project status back to the user (roadmap state, open questions, next suggested action).
- Request clarifications where needed and wait for responses before coding.
- Once direction is confirmed, ensure the analysis gate (`docs/5-operations/analysis-gate-checklist.md`) is satisfied, then proceed with planning/implementation per `AGENTS.md` guidelines.

Owner/On-call escalation:
- Escalate to the user when:
  * Scope is ambiguous or conflicting across documents.
  * Required approvals (dependencies, destructive commands) are not documented.

Post-incident notes:
- Update feature plans, roadmap, and open-questions log to reflect decisions made during the new session kickoff.
- Validate local tooling: ensure the shared pre-commit hook is installed (`git config core.hooksPath githooks`) because it now runs Gradle formatting, targeted module tests, secret scanning (gitleaks), commit linting (gitlint), and a binary/size guard on every staged change.
