# Open Questions Log

Use this file to capture outstanding questions or ambiguities before starting work. Limit each feature to a maximum of five high-impact questions per the clarification gate. Delete a row as soon as its question is resolved and ensure the answer is captured in the referenced spec/plan/ADR before removal. Each entry should include:

- **Date** the question was raised
- **Context** (feature plan / file / task)
- **Question**
- **Status** (always `Open`; remove the row once clarified)
- **Resolution target** (note where the answer is documented when you remove the row)

When communicating options to the user—whether resolving an open question or proposing a general approach—enumerate them alphabetically (A, B, C …), include pros and cons for each, and call out the recommended choice with supporting rationale before requesting a decision.

| Date | Context | Question | Status | Resolution |
| 2025-10-17 | Feature 026 – lint follow-up | How should we resolve the outstanding Checkstyle/PMD violations: strictly by updating source/tests to comply, or may we adjust the rule configurations if justified? | Resolved | Spec `feature-026-fido2-attestation-support.md` (2025-10-17) – Option A confirmed: fix code to satisfy existing rules. |
| 2025-10-17 | Feature 026 – attestation UI results | Should the Evaluate result panel continue showing Attestation ID / Format / Signing mode, or can we rely on the input summary on the left? | Resolved | Spec `feature-026-fido2-attestation-support.md` (2025-10-17) – remove redundant metadata from result card. |
| 2025-10-17 | Feature 026 – manual generation inputs | For Manual mode, do we require an explicit AAGUID or default a deterministic synthetic per format? | Resolved | Option B approved: default deterministic per format with optional override. Spec updated 2025-10-17 under “Clarifications – Manual Mode Decisions”. |
| 2025-10-17 | Feature 026 – custom-root chain length | For Manual + CUSTOM_ROOT signing, enforce chain length ≥1 or root+leaf minimum? | Resolved | Option B approved: require ≥1 certificate. Spec updated 2025-10-17. |
| 2025-10-17 | Feature 026 – UI affordance | Should we add a visible “Copy preset ID” link next to the preset selector? | Resolved | Option A approved: add copy link. Spec updated 2025-10-17. |
| 2025-10-17 | Feature 026 – CLI parity | Should CLI accept `--input-source=manual` mirroring REST? | Resolved | Option A approved: support CLI parity. Spec updated 2025-10-17. |
| 2025-10-17 | Feature 026 – algorithm inference | In Manual mode, infer algorithm from key or require an explicit field? | Resolved | Option B approved: infer from credential key; error if undecidable. Spec updated 2025-10-17. |
| 2025-10-18 | Feature 026 – manual credential ID override | Should Manual generation auto-generate credential IDs, allow operator overrides, and how should overrides be validated? | Resolved | Spec `feature-026-fido2-attestation-support.md` (2025-10-18) – server generates random Base64URL IDs; optional override accepts Base64URL, rejects malformed values, blanks fall back to random. |
| 2025-10-18 | Feature 026 – preset key representation | When surfacing attestation preset key material in UI/CLI/REST, should we present compact single-line JWK JSON or a pretty-printed multi-line variant? | Resolved | Spec `feature-026-fido2-attestation-support.md` (2025-10-18) – Option B confirmed: pretty-printed multi-line JWK payloads. |
| 2025-10-18 | Feature 026 – legacy Base64URL inputs | Do we need to continue accepting legacy Base64URL attestation private-key inputs for backwards compatibility, or can we reject them now that JWK/PEM support is in place? | Resolved | Spec `feature-026-fido2-attestation-support.md` (2025-10-18) – Option B confirmed: drop Base64URL-only inputs; require JWK or PEM/PKCS#8. |
| 2025-10-18 | Feature 026 – attestation result payload | Should we continue emitting `attestationId` in REST/CLI/UI JSON responses, or restrict it to internal telemetry only? | Resolved | Spec `feature-026-fido2-attestation-support.md` (2025-10-18) – drop from user-facing JSON, retain for telemetry. |
| 2025-10-18 | Feature 027 – unified credential store naming | Which inclusive default filename should all facades adopt for the shared credential store? | Resolved | Spec `feature-027-unified-credential-store.md` (2025-10-18) – use `credentials.db` with legacy fallback. |

## Feature 026 – Pending Question Details (2025-10-17)

1. Manual AAGUID defaulting vs explicit input
   - Options:
     - A) Require explicit AAGUID input in Manual mode.
     - B) Default a deterministic synthetic AAGUID per selected format, allow optional override.
   - Pros/Cons:
     - A) Precise and realistic, but increases UI/REST/CLI friction and validation paths.
     - B) Smooth defaults and deterministic cross-layer behavior; needs clear docs labeling.
   - Recommended: B (deterministic default with optional override).

2. Manual + CUSTOM_ROOT: minimum certificate chain length
   - Options:
     - A) Enforce root+leaf minimum (≥2 certificates).
     - B) Enforce chain length ≥1 (at least one PEM certificate required).
   - Pros/Cons:
     - A) Encourages realistic chains; more validation surface.
     - B) Simpler validation; supports single-root tests.
   - Recommended: B (≥1 certificate required).

3. UI affordance: “Copy preset ID” next to preset selector
   - Options: A) Add the copy link; B) Skip for now.
   - Pros/Cons:
     - A) Low effort; improves debugging and CLI/REST parity workflows.
     - B) Slightly simpler UI; worse discoverability.
   - Recommended: A (add copy link).

4. CLI parity for Manual mode
   - Options: A) Support `--input-source=manual` mirroring REST; B) Defer.
   - Pros/Cons:
     - A) Consistent UX across facades; clearer docs/examples.
     - B) Less work now; inconsistent UX.
   - Recommended: A (support CLI parity).

5. Manual algorithm selection: infer vs explicit
   - Options: A) Require explicit `algorithm` field; B) Infer from credential key.
   - Pros/Cons:
     - A) No guesswork, but redundant input and more validation.
     - B) Simpler inputs; aligns with verifier behavior; error if undecidable.
   - Recommended: B (infer from key).
