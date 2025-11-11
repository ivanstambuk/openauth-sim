# Feature 008 Plan – EUDIW SIOPv2 Wallet Simulator

| Field | Value |
|-------|-------|
| Status | Placeholder |
| Last updated | 2025-11-11 |
| Linked specification | `docs/4-architecture/features/008/spec.md` |
| Linked tasks | `docs/4-architecture/features/008/tasks.md` |

## Vision
Ship a deterministic wallet simulator that handles SIOPv2 authorization requests, composes HAIP-compliant presentations,
and exposes operator-friendly surfaces across CLI/REST/UI.

## Scope & Dependencies
- Shares fixtures, telemetry, and Trusted Authority metadata with Features 006/007.
- Relies on existing OpenID4VP verifier endpoints for validation.
- No persistence beyond transient wallet sessions.

## Increment Map (≤90 min)
1. Authorization parsing + consent summary scaffolding.
2. Deterministic presentation builders (SD-JWT + mdoc) wired to fixtures.
3. CLI/REST endpoints and Picocli commands for wallet operations.
4. Operator console consent UI + Selenium coverage.
5. Documentation/telemetry updates + verification log.

## Assumptions & Risks
- HAIP profile remains stable; track spec updates in `_current-session.md`.
- Fixture overlap with Feature 006 requires coordination to avoid duplication.

## Quality Gates
- `./gradlew --no-daemon spotlessApply check`
- `./gradlew --no-daemon :application:test`
- `./gradlew --no-daemon :rest-api:test --tests "*Eudiw*"`
- `./gradlew --no-daemon :ui:test`
- Selenium + Node consent UI harnesses when JS changes.

## Scenario Tracking
| Scenario ID | Description | Status |
|-------------|-------------|--------|
| S-008-01 | Authorization parsing + consent | Pending |
| S-008-02 | Presentation composition | Pending |
| S-008-03 | CLI/REST/UI parity | Pending |
| S-008-04 | Documentation + telemetry | Pending |

## Renumbering Note
Legacy Feature 008 (OCRA quality automation) artifacts moved to
`docs/4-architecture/features/new-010/legacy/008/`; the new Feature 008 placeholder focuses on EUDIW wallet simulation.
