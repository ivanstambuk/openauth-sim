# Feature Plan 036 – Verbose Trace Tier Controls

_Linked specification:_ `docs/4-architecture/features/036/spec.md`  
_Status:_ Draft  
_Last updated:_ 2025-10-25

## Vision & Success Criteria
- Provide a shared tier-filtering pipeline that applies consistently across HOTP, TOTP, OCRA, and FIDO2 verbose traces.
- Ensure `normal` tier responses expose only the approved metadata (per spec clarifications) while `educational` and `lab-secrets` tiers retain current detail without duplication.
- Keep CLI, REST, and UI trace outputs in sync for each tier, with deterministic fixtures and OpenAPI snapshots for verification.
- Document the tier matrix so future protocols inherit the tagging discipline automatically.
- Maintain a green baseline via `./gradlew --no-daemon spotlessApply check`.

## Scope Alignment
- **In scope:** Core tier helper, attribute tagging in all existing verbose trace builders, facade-level tests (CLI/REST/UI) covering tier permutations, documentation updates (spec/plan/tasks/how-to, knowledge map), and OpenAPI snapshot refreshes.
- **Out of scope:** Operator-facing toggle controls (CLI flags, REST request parameters, UI switches) and telemetry redaction policies; those will follow in a later UX-focused feature once gating is ready.

## Dependencies & Interfaces
- Builds on the trace model introduced in Feature 035; no additional persistence/telemetry changes expected.
- Touches `core` (`io.openauth.sim.core.trace`), application services for HOTP/TOTP/OCRA/FIDO2, CLI printers, REST DTOs/controllers, and operator UI console rendering.
- Pre-commit hook now blocks commits when resolved entries remain in `docs/4-architecture/open-questions.md`; keep that file clean while staging clarifications.

## Clarifications (carried from spec)
1. `normal` tier retains verdicts plus non-secret metadata (algorithms, counters, drift windows, RP identifiers, signature counters) while suppressing raw secrets and intermediate byte buffers.
2. FIDO2 traces may expose COSE public-key metadata—including base64url coordinates and RFC 7638 thumbprints—in the `normal` tier; only private material remains restricted.

## Implementation Plan
1. **T3601 – Tier helper specification tests (red)**
   - Add failing unit tests in `core` describing the tier catalogue (`normal`, `educational`, `lab-secrets`) and attribute-tagging expectations (e.g., `TraceAttributeTierTest`).
   - Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.TraceAttributeTierTest"`
2. **T3602 – Tier helper implementation (green)**
   - Implement tier-aware filtering utilities (`TraceAttributeTier`, `VerboseTraceFilter`) and update the trace model to carry minimum-tier metadata per attribute.
   - Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.*Tier*"`
3. **T3603 – Protocol tagging rollout (red → green per protocol)**
   - Stage failing application tests asserting tier behaviour for HOTP/TOTP/OCRA/FIDO2 verbose traces, then tag attributes with the appropriate minimum tier and re-run targeted suites.
   - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTierTest"`; replicate for CLI (`:cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`) and REST (`:rest-api:test --tests "io.openauth.sim.rest.*VerboseTraceTest"`).
4. **T3604 – Facade contract updates**
   - Refresh CLI output snapshots, REST DTO/OpenAPI fixtures, and operator UI fixtures to validate tier-dependent payloads; ensure Selenium traces respect the new helper.
   - Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`; UI Selenium as needed.
5. **T3605 – Documentation & knowledge sync**
   - Update Feature 035/036 specs, how-to guides, knowledge map, and roadmap references to reflect the shared helper; capture lessons in the session snapshot.
   - Command: `./gradlew --no-daemon spotlessApply check`

## Risks & Mitigations
- **Risk:** Forgetting to tag new attributes results in inconsistent tiers.  
  **Mitigation:** Add regression tests that assert tier matrices per protocol; review diff outputs for each attribute change.
- **Risk:** Tier filtering slows verbose trace generation.  
  **Mitigation:** Keep tagging metadata lightweight (enums + primitive flags) and benchmark CLI/REST responses post-implementation.
- **Risk:** OpenAPI snapshots balloon due to multiple tier examples.  
  **Mitigation:** Use focused fixtures (one per tier) and reuse existing schemas with descriptive annotations.

## Follow-ups
- Once helper stabilises, schedule a UX feature to expose tier selection controls across CLI/REST/UI.
- Consider adding mutation testing for tier filtering to ensure redactions remain covered.

## Analysis Gate
- To be completed after T3605; ensure spec, plan, and tasks align and that failing tests exist before each implementation increment.
