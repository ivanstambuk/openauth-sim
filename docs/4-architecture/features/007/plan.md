# Feature 007 Plan – EUDIW ISO/IEC 18013-5 (mdoc PID) Simulator

| Field | Value |
|-------|-------|
| Status | Placeholder |
| Last updated | 2025-11-11 |
| Linked specification | `docs/4-architecture/features/007/spec.md` |
| Linked tasks | `docs/4-architecture/features/007/tasks.md` |

## Vision
Deliver deterministic mdoc PID generation + validation tooling that slots into the EUDIW/OpenID4VP stack so operators can
exercise HAIP-compliant workflows without external wallets.

## Scope & Assumptions
- Reuse Feature 006 fixtures + telemetry conventions.
- Store presets in the shared credential repository; no new persistence schema.
- CLI/REST/UI must reach parity in a single slice (Spec-as-Source directive).

## Increment Map (≤90 minutes per entry)
1. Fixture ingestion + DeviceResponse generator scaffolding.
2. Verifier validation harness (signatures, namespace policies, Trusted Authority filters).
3. CLI + REST command/controllers wiring (stored/preset/manual modes).
4. Operator console panels + Selenium coverage.
5. Documentation + telemetry gauntlet.

## Dependencies & Risks
- Requires EUDIW fixture catalogue coordination with Feature 006.
- HAIP spec updates may adjust encryption or namespace requirements; track releases in `_current-session.md`.

## Quality & Tooling Gates
- `./gradlew --no-daemon spotlessApply check`
- `./gradlew --no-daemon :core:test :application:test`
- `./gradlew --no-daemon :rest-api:test --tests "*Eudiw*"`
- `./gradlew --no-daemon :ui:test`
- Selenium + Node harness for console assets when UI surfaces change.

## Scenario Tracking
| Scenario ID | Description | Status |
|-------------|-------------|--------|
| S-007-01 | Deterministic DeviceResponse generation | Pending |
| S-007-02 | Verifier validation (success/failure paths) | Pending |
| S-007-03 | CLI/REST/UI parity for stored/preset/manual modes | Pending |
| S-007-04 | Documentation & telemetry coverage | Pending |

## Analysis Gate
- Confirm spec completeness before implementation.
- Record open questions in `docs/4-architecture/open-questions.md`.

## Implementation Drift Gate
- Before closing the feature, verify every FR/NFR is satisfied and documented; attach drift report to this plan.

## Exit Criteria
- CLI/REST/UI + docs/fixtures/telemetry all reference Feature 007.
- Verification commands recorded in `_current-session.md` with timestamps.

## Renumbering Note
Legacy operator documentation content now lives under `docs/4-architecture/features/new-010/legacy/007/` pending the
Feature 010 (Documentation & Knowledge Automation) consolidation.
