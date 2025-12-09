# Open Questions Log

Use this file to capture every outstanding high- or medium-impact question or ambiguity before starting work. Delete a row as soon as its question is resolved and ensure the answer is captured in the referenced spec/plan/ADR before removal. Each entry should include:

> **Guardrail:** This table may only contain rows whose status is `Open`. If you resolve a question, remove the row immediately (record the answer in the referenced spec/plan/ADR). Before discussing an ambiguity with the user, add or update the corresponding row plus its entry in the "Question Details" section so the authoritative option breakdown lives here, then reference the question ID in conversation, restate the options inline (no “go open the file” detours), and keep specs/plans/tasks limited to that ID until it is resolved. Always order options by preference (Option A is the recommended path) and mark the recommendation explicitly so the decision path is obvious to reviewers.

- **Date** the question was raised
- **Context** (feature plan / file / task)
- **Question**
- **Status** (always `Open`; remove the row once clarified)
- **Resolution target** (note where the answer is documented when you remove the row)

When communicating options to the user—whether resolving an open question or proposing a general approach—enumerate them alphabetically (A, B, C …), include pros and cons for each, and call out the recommended choice with supporting rationale before requesting a decision.

<!-- Add new rows below with Status set to Open only. Remove the row once resolved. -->

| Date | Context | Question | Status | Resolution target |


## Question Details


### Q010-02 – Documentation Link Policy

**Context:** Feature 010 governs documentation & knowledge automation quality gates. Human-facing guides currently mention repo files using bare paths, and we need a rule that balances human readability with LLM/agent token footprint.

**Option A (Recommended) – Prose-only linking.** Convert every bare repo path that appears in prose, lists, or tables into path links while leaving fenced code blocks and multi-line shell/Java samples untouched so copy/paste workflows and LLM prompts remain stable. Minimal token increase because the link text duplicates the existing literal.

**Option B – Link everything.** Wrap every occurrence, including fenced code and inline command snippets, for perfect consistency. Risks: breaks copy/paste workflows, significantly inflates fenced-code tokens, and may confuse tooling that expects raw commands.

**Option C – Defer to future tooling.** Leave markdown untouched and rely on a future docs pipeline to auto-link paths. Avoids immediate churn but does not help current human readers and fails the “prose must link” objective.

**Decision needed:** Confirm Option A and record it as an NFR in the Feature 010 spec/plan so future docs use the same standard.

**Outcome (2025-11-16):** Approved Option A and captured it as NFR-010-06 in [docs/4-architecture/features/010/spec.md](docs/4-architecture/features/010/spec.md), added scope guidance in the Feature 010 plan, and scheduled sweep task T-010-08. Remove bare prose references by following that task.

### Q010-03 – Documentation Link Scope

**Context:** NFR-010-06 states that “human-facing Markdown prose” must link repo paths, but Feature 010 needs an explicit list so audits, automation, and handoffs stay consistent.

**Option A (Recommended)** – Treat every Markdown file intended for humans as in-scope: [README.md](README.md), [AGENTS.md](AGENTS.md), top-level docs directories (`docs/0-overview`…`docs/8-compliance`), feature specs/plans/tasks, runbooks, roadmap, knowledge map, and `_current-session.md`. Exclude generated artefacts (e.g., JSON-LD snippets) and machine-oriented appendices. Pros: single rule, no ambiguity; aligns with governance. Cons: initial sweep touches many files.

**Option B** – Limit scope to operator-facing guides plus the main README family (repo root + `docs/2-how-to`). Pros: smaller blast radius, focused on high-traffic guides; Cons: roadmap/spec/runbooks keep bare paths, reducing consistency for reviewers.

**Option C** – Phase the rollout: start with README + `docs/2-how-to`, then add other directories once lint automation exists. Pros: incremental effort; Cons: requires tracking exceptions and may drag on, creating confusion about compliance.

**Decision & Outcome (2025-11-16):** Adopted Option A—every human-facing Markdown file (README/AGENTS, `docs/0-overview`…8-compliance, feature specs/plans/tasks, runbooks, roadmap, knowledge map, `_current-session.md`, etc.) must apply the link rule while generated/machine artefacts stay out of scope. Captured in Feature 010 spec/plan/tasks (NFR-010-06 + T-010-08 scope).
