# Feature Plan 036 - Verbose Trace Tier Controls

_Linked specification:_ `docs/4-architecture/features/036/spec.md`  
_Status:_ Draft_  
_Last updated:_ 2025-11-11

## Vision & Success Criteria
- Provide a reusable trace-tier helper that keeps verbose payloads deterministic across HOTP/TOTP/OCRA/FIDO2 (S-036-01/S-036-02).
- Demonstrate facade parity: REST, CLI, and UI honour the same tier selection while masking secrets (S-036-03).
- Keep documentation, telemetry, and governance artefacts aligned so future protocols inherit the model automatically (S-036-04).
- Exit only when `./gradlew spotlessApply check` (including tier-aware tests) is green and captured inside the verification log (S-036-05).

## Scope Alignment
- **In scope:** TraceTier helper + validation, HOTP/TOTP/OCRA/FIDO2 builder tagging, CLI/REST/UI propagation, telemetry events, fixtures, roadmap/migration/current-session updates, and the quality/analysis gate evidence required for template migration.
- **Out of scope:** UI toggle controls for tier selection, trace persistence/storage policy changes, pre-Feature-035 trace implementations, and any dependency upgrades beyond what the helper requires.

## Dependencies & Interfaces
- Feature 035 verbose trace contract and builder code paths.
- Telemetry adapters emitting `telemetry.trace.filtered` and `telemetry.trace.invalid_tier`.
- Operator console verbose trace dock (receives tiered payloads even without UI toggle controls).
- Template sweep directive (docs/templates/*) and migration tracker.

## Assumptions & Risks
- **Assumptions:** Java 17 + Gradle 8.10 toolchain available; Feature 035 builders already expose attribute metadata; telemetry adapters can be extended without breaking schema snapshots.
- **Risks / Mitigations:**
  - Helper introduces performance regressions -> capture micro-bench timing inside core tests before rollout.
  - Tier tagging gaps in legacy builders -> enforce fixture-driven coverage plus mutation tests during I2.
  - Doc drift -> tie every increment to roadmap/migration/current-session updates and rerun analysis gate before implementation begins.

## Implementation Drift Gate
- Trigger once T-036-01 through T-036-05 finish with a green Gradle build.
- Evidence to archive inside this plan''s appendix:
  - Mapping of FR/NFR IDs to code/tests (link to helper class, builder suites, CLI/REST snapshots).
  - JSON diff showing each tier for HOTP and FIDO2 plus CLI text/output captures.
  - `telemetry.trace.filtered` sample payload proving tier metadata is emitted.
  - Coverage delta from `jacocoTestReport` demonstrating builder and helper tests.
- Sign-off criteria: roadmap + knowledge map entries updated, tasks checklist completed, no open questions.

## Increment Map
0. **I0 - Governance sync & artefact refresh (<=30 min)**
   - _Goal:_ Record clarifications, align roadmap entry, refresh `docs/_current-session.md`, and confirm migration tracker state.
   - _Preconditions:_ Feature 036 spec updated to new template.
   - _Steps:_ Update roadmap/migration/current-session scaffolding, ensure open-questions log remains empty.
   - _Commands:_ Documentation only.
   - _Exit:_ Directive recorded across roadmap + migration plan.
1. **I1 - Tier helper specification tests (T-036-01, S-036-01)**
   - _Goal:_ Add failing tests describing `TraceTier`, tagging contracts, and validation behaviour.
   - _Preconditions:_ Spec clarifies tier matrix + telemetry expectations.
   - _Steps:_ Create red tests in `core` for tier comparison, masking, invalid tiers; capture fixture expectations in spec appendix.
   - _Commands:_ `./gradlew --no-daemon :core:test --tests "*TraceTier*"`.
   - _Exit:_ Tests red with descriptive assertions logged in tasks file.
2. **I2 - Trace builder adoption (T-036-02, S-036-02)**
   - _Goal:_ Tag HOTP/TOTP/OCRA/FIDO2 attributes, wire helper, and update fixtures.
   - _Preconditions:_ I1 tests exist; helper contract approved.
   - _Steps:_ Implement helper, apply tags, refresh fixtures/OpenAPI snapshots.
   - _Commands:_ `./gradlew --no-daemon :application:test :rest-api:test :cli:test`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Builders green with deterministic fixtures.
3. **I3 - Facade parity & snapshots (T-036-03, S-036-03)**
   - _Goal:_ Ensure REST query parameters, CLI flags, and UI configuration propagate tier selection; refresh snapshots/Selenium assertions.
   - _Preconditions:_ Helper + builders wired.
   - _Steps:_ Update CLI flag plumbing, REST query parameter parsing, UI payload renderers, plus snapshots.
   - _Commands:_ `./gradlew --no-daemon :rest-api:test :cli:test :ui:test`, `./gradlew --no-daemon :rest-api:openapiSnapshot` (if required).
   - _Exit:_ Contract + CLI/UI tests green with new tier fixtures.
4. **I4 - Documentation & telemetry governance (T-036-04, S-036-04)**
   - _Goal:_ Emit telemetry events, capture sample payloads, and update spec/plan/how-to/knowledge map entries describing governance + UX dependency.
   - _Preconditions:_ Facades emit tiered payloads.
   - _Steps:_ Implement telemetry adapters, update docs, refresh roadmap/migration/current-session.
   - _Commands:_ `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Docs + telemetry events aligned; verification log updated.
5. **I5 - Quality gate closure (T-036-05, S-036-05)**
   - _Goal:_ Rerun the full Gradle gate, refresh verification logs, and archive drift-gate evidence.
   - _Preconditions:_ Documentation synced, telemetry events live.
   - _Steps:_ Run full Gradle gate, capture coverage deltas, archive evidence, prep commit instructions.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :core:test :application:test :rest-api:test :cli:test :ui:test`.
   - _Exit:_ Full green build + drift gate evidence ready for review.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-036-01 | I1 / T-036-01 | Core helper behaviour + validation coverage. |
| S-036-02 | I2 / T-036-02 | Builder tagging + fixture updates. |
| S-036-03 | I3 / T-036-03 | CLI/REST/UI parity & snapshots. |
| S-036-04 | I4 / T-036-04 | Documentation + telemetry alignment. |
| S-036-05 | I5 / T-036-05 | Quality/analysis gate evidence + verification log. |

## Analysis Gate
- Status: Pending (run after I4 completes).
- Checklist: spec/plan/tasks alignment, open questions resolved, telemetry snapshot captured, roadmap/current-session/migration entries updated, verification log points to latest Gradle command.

## Exit Criteria
- FR-036-01..05 and NFR-036-01..03 satisfied with traceability to code/tests.
- `./gradlew --no-daemon spotlessApply check` plus targeted module suites green and recorded in verification log.
- Telemetry fixtures + documentation updated (roadmap, knowledge map, how-to guides, migration plan, current session).
- Implementation Drift Gate appendix populated and reviewed.

## Follow-ups / Backlog
- UX feature to expose end-user tier toggles.
- Mutation testing for tier paths once helper stabilises.
- Potential extension to future protocols (EUDIW, WebAuthn multi-device) tracked via roadmap entry once template sweep finishes.
