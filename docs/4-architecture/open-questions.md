# Open Questions Log

Use this file to capture outstanding questions or ambiguities before starting work. Limit each feature to a maximum of five high-impact questions per the clarification gate. Delete a row as soon as its question is resolved and ensure the answer is captured in the referenced spec/plan/ADR before removal. Each entry should include:

- **Date** the question was raised
- **Context** (feature plan / file / task)
- **Question**
- **Status** (always `Open`; remove the row once clarified)
- **Resolution target** (note where the answer is documented when you remove the row)

When communicating options to the user—whether resolving an open question or proposing a general approach—enumerate them alphabetically (A, B, C …), include pros and cons for each, and call out the recommended choice with supporting rationale before requesting a decision.

| Date | Context | Question | Status | Resolution |
| 2025-10-01 | Feature 009 – OCRA Replay & Verification | Which facades must expose the replay/verification capability (CLI, REST API, operator UI)? | Open | Spec `feature-009-ocra-replay-verification.md` |
| 2025-10-01 | Feature 009 – OCRA Replay & Verification | What contextual inputs must accompany the operator-supplied OTP for verification (e.g., suite parameters, counter/timestamp, credential identifier)? | Open | Spec `feature-009-ocra-replay-verification.md` |
| 2025-10-01 | Feature 009 – OCRA Replay & Verification | What evidence needs to be produced for non-repudiation (audit log only, signed receipt, persisted verification record)? | Open | Spec `feature-009-ocra-replay-verification.md` |
| 2025-10-01 | Feature 009 – OCRA Replay & Verification | Should the verification use stored credentials exclusively, allow inline secrets, or both? | Open | Spec `feature-009-ocra-replay-verification.md` |
| 2025-10-01 | Feature 009 – OCRA Replay & Verification | Are tolerances (time drift, counter resynchronisation) allowed when validating OTP replays, and if so how should failures be classified? | Open | Spec `feature-009-ocra-replay-verification.md` |
