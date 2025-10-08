# Open Questions Log

Use this file to capture outstanding questions or ambiguities before starting work. Limit each feature to a maximum of five high-impact questions per the clarification gate. Delete a row as soon as its question is resolved and ensure the answer is captured in the referenced spec/plan/ADR before removal. Each entry should include:

- **Date** the question was raised
- **Context** (feature plan / file / task)
- **Question**
- **Status** (always `Open`; remove the row once clarified)
- **Resolution target** (note where the answer is documented when you remove the row)

When communicating options to the user—whether resolving an open question or proposing a general approach—enumerate them alphabetically (A, B, C …), include pros and cons for each, and call out the recommended choice with supporting rationale before requesting a decision.

| Date | Context | Question | Status | Resolution |
| 2025-10-08 | Workstream 15 – TOTP operator support | Please confirm target wiring order/apis for introducing TOTP evaluation commands/endpoints (CLI + REST) now that the application service exists. Should we mirror HOTP flows directly or stage separate failing tests first? | Resolved | docs/4-architecture/specs/feature-023-totp-operator-support.md |
