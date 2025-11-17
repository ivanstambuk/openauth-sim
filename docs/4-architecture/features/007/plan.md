# Feature 007 Plan – EUDIW ISO/IEC 18013-5 (mdoc PID) Simulator

_Linked specification:_ [docs/4-architecture/features/007/spec.md](docs/4-architecture/features/007/spec.md)  
_Linked tasks:_ [docs/4-architecture/features/007/tasks.md](docs/4-architecture/features/007/tasks.md)  
_Status:_ Placeholder  
_Last updated:_ 2025-11-11

## Vision & Success Criteria
Deliver deterministic mdoc PID generation + validation tooling that slots into the EUDIW/OpenID4VP stack so operators can
exercise HAIP-compliant workflows without external wallets.

## Scope Alignment
- **In scope:** Reuse Feature 006 fixtures/telemetry, keep presets in the shared credential repository, and deliver CLI/REST/UI parity in one planning slice per Spec-as-Source.
- **Out of scope:** New persistence schema changes, issuance/DC-API flows, or bespoke wallet UX beyond the mdoc PID simulator described in the spec.

## Dependencies & Interfaces
- Coordination with Feature 006 for fixture catalogue updates and telemetry contract changes.
- Shared credential repository + operator console assets inherited from the OpenID4VP stack.

## Assumptions & Risks
- **Assumptions:**
  - HAIP profile clarifications are captured upstream (Feature 006) before this simulator executes.
  - Fixture overlap with Feature 006 remains intentional; no duplicate maintenance locations are introduced.
- **Risks / Mitigations:**
  - **HAIP spec churn:** Track ETSI/DCQL updates inside `_current-session.md`; pause increments if encryption/namespace requirements shift.
  - **Tooling drift:** Keep CLI/REST/UI verification commands aligned with Feature 006 to avoid divergent harness behaviour.

## Implementation Drift Gate

- Summary: This feature is still a placeholder. When it becomes active and approaches completion, use this gate to ensure mdoc PID simulator behaviour (fixtures, validation harness, CLI/REST/UI flows, telemetry/docs) stays aligned with FR-007/NFR-007 and with upstream EUDIW conventions from Feature 006.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] ``docs/4-architecture/features/007`/{spec,plan,tasks}.md` updated from placeholder to active status with full FR/NFR/Scenario coverage.  
    - [ ] [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) has no `Open` entries for Feature 007.  
    - [ ] The following commands have been run in the increment and logged in [docs/_current-session.md](docs/_current-session.md):  
      - `./gradlew --no-daemon :core:test :application:test :rest-api:test :ui:test spotlessApply check` (with EUDIW/mdoc-focused tests enabled).  

  - **Spec ↔ code/test mapping (once implemented)**
    - [ ] For each FR/NFR/Scenario in the future Feature 007 spec, identify implementing classes in core/application/REST/CLI/UI and the tests that cover them.  
    - [ ] Ensure Scenario Tracking in this plan lists Scenario IDs with increment/task and code/test pointers.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift discovered during the gate is logged as `Open` in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) and captured as tasks in [docs/4-architecture/features/007/tasks.md](docs/4-architecture/features/007/tasks.md).  
    - [ ] Low-impact drift is fixed directly in spec/plan/tasks/docs, with a short note added to this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the gate run date, key commands, and “matches vs gaps” summary.  
    - [ ] [docs/_current-session.md](docs/_current-session.md) logs that the Feature 007 Implementation Drift Gate was executed (date, commands, reference back to this section).  


## Increment Map
_Target duration: ≤90 minutes per entry._
1. Fixture ingestion + DeviceResponse generator scaffolding.
2. Verifier validation harness (signatures, namespace policies, Trusted Authority filters).
3. CLI + REST command/controllers wiring (stored/preset/manual modes).
4. Operator console panels + Selenium coverage.
5. Documentation + telemetry gauntlet.

_Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :core:test :application:test`, `./gradlew --no-daemon :rest-api:test --tests "*Eudiw*"`, `./gradlew --no-daemon :ui:test`, plus Selenium/Node harness runs whenever UI assets change.

## Scenario Tracking
| Scenario ID | Description | Status |
|-------------|-------------|--------|
| S-007-01 | Deterministic DeviceResponse generation | Pending |
| S-007-02 | Verifier validation (success/failure paths) | Pending |
| S-007-03 | CLI/REST/UI parity for stored/preset/manual modes | Pending |
| S-007-04 | Documentation & telemetry coverage | Pending |

## Analysis Gate
- Confirm spec completeness before implementation.
- Record open questions in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md).

## Exit Criteria
- CLI/REST/UI + docs/fixtures/telemetry all reference Feature 007.
- Verification commands recorded in `_current-session.md` with timestamps.

## Follow-ups / Backlog
- Document fixture/telemetry deltas in Feature 006 before cloning work so both simulators stay in sync.
- Legacy operator documentation content now lives under `docs/4-architecture/features/new-010/legacy/007/` pending the Feature 010 (Documentation & Knowledge Automation) consolidation; migrate any remaining references once Feature 010 finalises the docs hub.
