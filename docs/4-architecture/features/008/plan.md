# Feature 008 Plan – EUDIW SIOPv2 Wallet Simulator

_Linked specification:_ `docs/4-architecture/features/008/spec.md`  
_Linked tasks:_ `docs/4-architecture/features/008/tasks.md`  
_Status:_ Placeholder  
_Last updated:_ 2025-11-11

## Vision & Success Criteria
Ship a deterministic wallet simulator that handles SIOPv2 authorization requests, composes HAIP-compliant presentations,
and exposes operator-friendly surfaces across CLI/REST/UI.

## Scope Alignment
- **In scope:** Deterministic SIOPv2 authorization parsing, consent UX, presentation builders (SD-JWT + mdoc), and CLI/REST/UI flows sharing fixtures with Features 006/007.
- **Out of scope:** Persistent wallet state, issuance/OpenID4VCI workflows, or custom Trusted Authority expansion beyond what Feature 006 already provides.

## Dependencies & Interfaces
- Shares fixtures, telemetry, and Trusted Authority metadata with Features 006/007.
- Relies on existing OpenID4VP verifier endpoints for validation; wallet simulator reuses those services for replaying presentations.

## Assumptions & Risks
- **Assumptions:**
  - HAIP profile remains stable; track spec updates in `_current-session.md`.
  - Fixture overlap with Feature 006 remains intentional; no duplicate maintenance locations.
- **Risks / Mitigations:**
  - **Spec churn:** If HAIP/SIOPv2 clarifications land mid-iteration, pause increments until Feature 006 processes them.
  - **Tooling drift:** Keep CLI/REST/UI verification commands aligned with Feature 006 to avoid diverging consent flows.

## Implementation Drift Gate
- **Status:** Pending – run once all placeholder increments (authorization parsing through docs telemetry) are complete and full Gradle verification is green.
- **Evidence package:** Map every FR/NFR + scenario to code/tests, capture telemetry snapshots for consent/presentation payloads, and record Jacoco/SpotBugs outputs before closing the feature.
- **Required commands:** `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`, `./gradlew --no-daemon spotbugsMain spotbugsTest`, Selenium/Node consent harnesses, and any OpenAPI snapshot updates.

## Increment Map
_Target duration: ≤90 minutes per entry._
1. Authorization parsing + consent summary scaffolding.
2. Deterministic presentation builders (SD-JWT + mdoc) wired to fixtures.
3. CLI/REST endpoints and Picocli commands for wallet operations.
4. Operator console consent UI + Selenium coverage.
5. Documentation/telemetry updates + verification log.

_Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon :rest-api:test --tests "*Eudiw*"`, `./gradlew --no-daemon :ui:test`, and Selenium/Node consent harnesses when UI assets change.


## Scenario Tracking
| Scenario ID | Description | Status |
|-------------|-------------|--------|
| S-008-01 | Authorization parsing + consent | Pending |
| S-008-02 | Presentation composition | Pending |
| S-008-03 | CLI/REST/UI parity | Pending |
| S-008-04 | Documentation + telemetry | Pending |

## Analysis Gate
- Confirm the placeholder specification (Feature 008) is complete before implementation begins and log any open questions in `docs/4-architecture/open-questions.md`.
- Re-run the checklist once consent UI increments are scoped so the plan/tasks/spec stay in sync.

## Exit Criteria
- CLI/REST/UI wallet flows cover authorization parsing, consent UI, and deterministic SD-JWT/mdoc presentations.
- Telemetry + verbose traces align with Feature 006 conventions; verification commands recorded in `_current-session.md`.
- Documentation (how-tos, roadmap, knowledge map) references Feature 008 and the consolidated numbering.

## Follow-ups / Backlog
- Legacy Feature 008 (OCRA quality automation) artifacts now live under `docs/4-architecture/features/new-010/legacy/008/`; migrate any remaining references once Feature 010 finalises the documentation hub.
- Track consent UI usability studies + wallet persistence experiments as future increments once the placeholder graduates.
