# Feature 036 – Verbose Trace Tier Controls

_Status: Proposed_  
_Last updated: 2025-10-25_

## Overview
Standardise verbose trace redaction tiers across all authentication protocols (HOTP, TOTP, OCRA, FIDO2) so operators can select between a “normal” operational view and progressively richer “educational” / “lab-secrets” diagnostics without duplicating filtering logic. The feature introduces a shared tier filter, applies consistent attribute tagging inside every trace builder, and harmonises documentation and tests so future protocols inherit the same behaviour automatically.

## Goals
- Provide a single tier-aware filtering helper that trace builders invoke to include or suppress attributes based on the requested detail level.
- Retain the existing full-detail (`educational`) experience by default while adding a deterministic `normal` view that exposes only high-level metadata and outcomes per protocol.
- Ensure `lab-secrets` can optionally surface additional experimental data (for example, dynamic truncation internals) without inflating production code paths.
- Update CLI, REST, and operator UI tests/fixtures to exercise all three tiers, guaranteeing consistent payloads across facades.
- Document the per-protocol tier matrices so operators know which fields appear at each level and engineers can extend the model safely.

## Non-goals
- Shipping user-facing toggles for tier selection (to be handled by a follow-up UX feature once helper plumbing is complete).
- Persisting traces or introducing telemetry redaction policies beyond the verbose trace pathway.
- Backporting the tier helper to legacy trace implementations predating Feature 035 (all current traces already leverage the shared model).

## Clarifications
1. 2025-10-25 – The `normal` tier must emit final outcomes plus a curated set of non-secret metadata (algorithms, counters, drift windows, relying-party identifiers, signature counters) while suppressing raw secrets, derived byte arrays, and truncation internals. (`educational` retains today’s detail; `lab-secrets` may extend it.)  
2. 2025-10-25 – FIDO2 traces may expose full COSE public-key metadata (algorithm identifiers, curve names, public coordinates/modulus, RFC 7638 thumbprints) in the `normal` tier; only secret or private-key material remains restricted to higher tiers.

## Success Metrics
- Tier helper API adopted by every existing trace builder with no duplicate per-protocol filtering logic.
- CLI/REST/UI snapshots for HOTP/TOTP/OCRA/FIDO2 pass when asserting tier-specific inclusions/exclusions.
- `./gradlew spotlessApply check` remains green after wiring tier gating.
- Documentation (spec, plan, knowledge map, operator how-tos) enumerates tier behaviour for each protocol.

## Deliverables
- `core` module: tier-aware trace attribute tagging utilities, expanded tests covering filtering combinations, and documentation for future protocol authors.
- `application` module: updated verbose trace builders that tag attributes with their minimum tier and invoke the shared filter before returning payloads.
- `cli` module: printer updates plus tiered acceptance tests to validate textual output.
- `rest-api` module: DTO adjustments and OpenAPI snapshots representing tier-dependent payloads.
- Operator UI: deterministic fixtures showing tier toggling effects (even if the UI control ships later).
- Documentation updates across Feature 035/036 materials, how-to guides, and knowledge map entries.

## Risks & Mitigations
- **Risk:** Inconsistent tagging across protocols causes missing attributes in higher tiers.  
  **Mitigation:** Add cross-protocol contract tests that assert tier matrices for each trace builder and exercise `educational`/`lab-secrets` parity.
- **Risk:** Tier filtering slows verbose requests noticeably.  
  **Mitigation:** Keep filtering operations linear on attribute counts and benchmark with representative traces before rollout.
- **Risk:** Confusion around future UI toggles.  
  **Mitigation:** Document dependency on forthcoming UX feature in roadmap/plan and warn operators that only API-level tier parameters are available initially.

## Dependencies & Follow-ups
- Requires Feature 035 trace model and builders (already in place).
- UI/CLI toggle surfaces will be scoped via a separate feature once filtering proves out.
- Mutation/coverage tooling should be updated to ensure tier tagging paths remain exercised as part of the test-first cadence.

## Appendix – Tier Attribute Matrix (Initial Draft)
- **HOTP/TOTP:**  
  - `normal`: outcome OTP (masked where necessary), algorithm names, counter values, drift window metadata.  
  - `educational`: adds dynamic truncation bytes, HMAC digest slices, per-step intermediate buffers.  
  - `lab-secrets`: may include additional experimental diagnostics (e.g., alternate truncation strategies) documented per test.
- **OCRA:**  
  - `normal`: request metadata, suite parameters, validation decisions.  
  - `educational`: message assembly segments, per-segment hex dumps, dynamic truncation fields.  
  - `lab-secrets`: optional extra instrumentation such as alternate challenge encodings or draft suite comparisons.
- **FIDO2:**  
  - `normal`: relying-party canonical identifiers, signature counters, COSE public-key metadata, verification verdicts.  
  - `educational`: authentication/attestation buffer dumps, hashed secrets, COSE hex payloads.  
  - `lab-secrets`: extended previews (e.g., signed byte windows) useful for lab analysis.
