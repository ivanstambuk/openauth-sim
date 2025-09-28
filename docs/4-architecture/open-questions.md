# Open Questions Log

Use this file to capture outstanding questions or ambiguities before starting work. Limit each feature to a maximum of five high-impact questions per the clarification gate. Delete a row as soon as its question is resolved and ensure the answer is captured in the referenced spec/plan/ADR before removal. Each entry should include:

- **Date** the question was raised
- **Context** (feature plan / file / task)
- **Question**
- **Status** (always `Open`; remove the row once clarified)
- **Resolution target** (note where the answer is documented when you remove the row)

When communicating options to the user—whether resolving an open question or proposing a general approach—enumerate them alphabetically (A, B, C …), include pros and cons for each, and call out the recommended choice with supporting rationale before requesting a decision.

| Date | Context | Question | Status | Resolution |
|------|---------|----------|--------|------------|
| 2025-09-28 | Feature 003 – R003 | Should the new controller validation tests assert the future 400/200 responses now (failing until R004) or mirror current 501/404 placeholders with TODOs? | Resolved | Feature 003 spec/plan (Option B – mirror current behavior, document TODO) |
