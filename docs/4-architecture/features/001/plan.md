# Feature Plan 001 – Core Credential Domain

_Linked specification:_ `docs/4-architecture/features/001/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Deliver an OCRA-focused credential domain inside `core` that models RFC 6287 descriptors, shared-secret handling, validation, persistence, and execution helpers.
- Keep downstream facades (application/CLI/REST/UI) aligned by exposing immutable descriptors, registry metadata, and deterministic diagnostics.
- Ensure RFC 6287 Appendix C + draft session vectors replay through `OcraResponseCalculator`, backed by property-based secret codecs and versioned persistence envelopes.
- Maintain documentation/telemetry parity: specs, plan, tasks, roadmap/knowledge map, and telemetry contracts stay in sync.

## Scope Alignment
- **In scope:**
  - Descriptor + registry modelling for OCRA suites, including metadata maps and unique naming.
  - Shared-secret canonicalisation utilities (`SecretMaterial`) plus validation/telemetry hooks.
  - Versioned persistence envelopes with upgrade hooks and ArchUnit boundaries.
  - Execution helper + CLI maintenance command wiring needed to validate session-aware suites.
  - Documentation/fixture updates required to explain the credential domain and session vectors.
- **Out of scope:**
  - HOTP/TOTP/FIDO2/EMV credential models or façade integrations (handled by other features).
  - REST/UI endpoints beyond referencing the shared registry (Feature 003 picks up that work).
  - Alternative persistence engines or runtime toggles for backward compatibility.

## Dependencies & Interfaces
- `core` module classes (`OcraCredentialDescriptor`, `SecretMaterial`, `OcraResponseCalculator`).
- Application-layer registry services that query the descriptors (shared with Feature 003 but defined here).
- MapDB-backed persistence store and versioned envelope schema.
- `TelemetryContracts` adapters for `core.ocra.validation`, `core.ocra.secret.validation`, and `core.ocra.execution` events.
- CLI maintenance harness leveraging the calculator for regression coverage.
- RFC 6287 + IETF draft vector datasets under `docs/test-vectors/ocra/`.

## Assumptions & Risks
- **Assumptions:**
  - MapDB shared cache remains the persistence backing during migration.
  - No additional external standards (beyond RFC 6287 + current draft) are required before renumbering.
  - CLI maintenance tooling is acceptable as the first consumer of the calculator (REST/UI follow later).
- **Risks / Mitigations:**
  - _Fixture drift:_ Mitigated by storing RFC/draft vectors in version control with provenance notes.
  - _Telemetry regressions:_ Use ArchUnit + telemetry contract tests to prevent missing fields.
  - _Secret leakage:_ Property-based tests and dedicated redaction assertions guard against exceptions emitting raw material.

## Implementation Drift Gate
- Execution evidence lives inside this plan and the Feature 001 tasks checklist.
- Gate outputs: mapping of FR/NFR to implemented increments, telemetry screenshots/logs, and fixture provenance references.
- Run the drift gate once `OcraResponseCalculator` + CLI helper landed; capture findings in this plan and log follow-ups if additional coverage is needed.

## Increment Map
1. **I1 – Clarification + descriptor foundations (T-001-01–T-001-03)**
   - _Goal:_ Close clarifications, seed roadmap/knowledge map touch points, and implement descriptor validation from staged tests.
   - _Preconditions:_ Spec + branch/scenario matrix drafted; property-based failing tests exist for descriptors.
   - _Steps:_
     - Update roadmap + knowledge map (`T-001-01`).
     - Write failing descriptor tests covering required fields/telemetry (`T-001-02`).
     - Implement descriptors, validation helpers, and diagnostics; rerun targeted tests + Spotless (`T-001-03`).
   - _Commands:_ `./gradlew --no-daemon :core:test --tests "*OcraCredentialDescriptorTest"`, `./gradlew --no-daemon spotlessApply`.
   - _Exit:_ Descriptor tests green, telemetry redaction confirmed, documentation updated.

2. **I2 – Secret canon + registry/persistence (T-001-04–T-001-05)**
   - _Goal:_ Canonicalise shared secrets and enable registry metadata + persistence envelopes with upgrade hooks.
   - _Preconditions:_ I1 complete; property-based codec tests staged.
   - _Steps:_
     - Implement codec + property-based tests (`T-001-04`).
     - Expose registry metadata + versioned envelopes; add upgrade tests (`T-001-05`).
     - Re-enable ArchUnit guardrails once packages stabilise.
   - _Commands:_ `./gradlew --no-daemon :core:test --tests "*SecretMaterialCodecTest"`, `./gradlew --no-daemon :core:test --tests "*CredentialEnvelopeUpgradeTest"`.
   - _Exit:_ Registry lookups deterministic; persistence upgrade tests green; ArchUnit rules enforced.

3. **I3 – Execution helper + CLI maintenance wiring (T-001-06–T-001-07)**
   - _Goal:_ Implement `OcraResponseCalculator`, wire session-aware helper into the CLI maintenance flows, and document provenance.
   - _Preconditions:_ I2 complete; placeholder compliance tests referencing RFC vectors exist.
   - _Steps:_
     - Implement calculator + RFC Appendix C/session compliance tests (`T-001-06`).
     - Hook the helper into `maintenance ocra`, add sanitized logging, and update docs with vector provenance (`T-001-07`).
   - _Commands:_ `./gradlew --no-daemon :core:test --tests "*OcraRfc6287ComplianceTest"`, `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`.
   - _Exit:_ Compliance tests + CLI suites green; telemetry frames recorded; docs updated.

4. **I4 – Fixture provenance + follow-up coverage (Future tasks)**
   - _Goal:_ Keep RFC/draft vectors authoritative, stage additional failure coverage, and prepare for downstream REST adoption.
   - _Preconditions:_ I3 complete; fixtures stored under `docs/test-vectors/ocra/`.
   - _Steps:_
     - Extend spec/tasks with additional fixture metadata.
     - Stage negative tests for missing session lengths and telemetry omissions.
     - Capture command outputs (`./gradlew spotlessApply check`) for the archive.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check` plus targeted module tests as needed.
   - _Exit:_ Fixtures documented, telemetry coverage proven, plan ready for REST/UI features.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-001-01 | I1 / T-001-02–T-001-03 | Descriptor immutability + validation diagnostics. |
| S-001-02 | I2 / T-001-04 | Shared-secret canonicalisation + telemetry. |
| S-001-03 | I2 / T-001-05 | Registry metadata + factory wiring. |
| S-001-04 | I2 / T-001-05 | Persistence envelope upgrades + ArchUnit guardrails. |
| S-001-05 | I3 / T-001-06–T-001-07 | Execution helper + CLI maintenance wiring + fixtures. |

## Analysis Gate
- 2025-09-27 – Checklist executed (PASS).
  - Spec FR/NFR tables + clarifications populated; open-questions log empty for this feature.
  - Plan references correct artefacts and ≤90-minute increments.
  - Tasks order tests before code; telemetry + persistence dependencies captured.
  - Command readiness verified via `./gradlew --no-daemon spotlessApply check`.

## Exit Criteria
- Specification, plan, tasks, and telemetry contracts stay in sync (no drift noted in the gate report).
- `./gradlew --no-daemon spotlessApply check` plus targeted module suites remain green.
- RFC 6287 Appendix C + session fixtures stored under `docs/test-vectors/ocra/` with provenance notes.
- `maintenance ocra` CLI helper documented and emits sanitized telemetry/verbose trace identifiers.
- Knowledge map + roadmap entries reference the completed OCRA domain work.

## Follow-ups / Backlog
1. Extend the specification/tasks to capture additional RFC/draft fixture metadata beyond Appendix C.
2. Stage placeholder tests asserting `UnsupportedOperationException` paths for future REST integration before wiring new helpers.
3. Document coverage notes + Gradle command outputs for the next iteration (especially once REST/UI adoption begins).
4. Evaluate additional failure branches (missing session input, invalid suite) and add regression tests.
5. Track downstream adoption workstreams (Feature 003+) to ensure they consume the canonical descriptor + registry APIs without drift.
