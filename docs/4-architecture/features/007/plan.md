# Feature Plan 007 – Operator-Facing Documentation Suite

_Linked specification:_ `docs/4-architecture/features/007/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-10

## Vision & Success Criteria
- Give operators end-to-end guidance for Java, CLI, and REST OCRA workflows without reading implementation code.
- Keep `README.md`, roadmap, and knowledge map synchronized with delivered capabilities, including Swagger UI discovery.
- Ensure every documented example references deterministic fixtures, sanitized telemetry, and the shared credential store defaults so operators can reproduce results reliably.

## Scope Alignment
- **In scope:** Java integration how-to, CLI operations guide, REST operations guide (including Swagger UI), README refresh, roadmap/knowledge-map/session log updates.
- **Out of scope:** Introducing new simulator features, commands, or endpoints; non-OCRA protocol documentation; documentation automation/publishing pipeline changes.

## Dependencies & Interfaces
- Relies on current CLI command surface (`runOcraCli`) and REST endpoints (`/api/v1/ocra/*`).
- References core helpers (`OcraCredentialFactory`, `OcraResponseCalculator`) and deterministic fixture catalogues in `docs/test-vectors/ocra/`.
- Governance artefacts: roadmap, knowledge map, migration plan, `_current-session.md`.

## Assumptions & Risks
- **Assumptions:** Latest CLI/REST behaviour is validated before documenting; credential store default remains `data/credentials.db`; telemetry events stay stable.
- **Risks / Mitigations:**
  - Stale knowledge could misdocument commands/endpoints → cross-check with CLI help + OpenAPI snapshot before publishing.
  - README messaging drift → peer review ensures statements align with roadmap.
  - Template mismatch → run doc lint + spotless as part of every increment.

## Implementation Drift Gate
- Trigger once T-007-01–T-007-06 finish and `./gradlew --no-daemon spotlessApply check` is green.
- Evidence package lives in this plan’s appendix: map FR/NFR IDs to doc sections, attach verification log entries, and note CLI/REST sample outputs used during validation.
- Confirm roadmap + knowledge map entries reference the published guides; capture screenshots or snippets if instructions change formatting.

## Increment Map
1. **I1 – Clarify scope & prep outlines** _(T-007-01)_
   - _Goal:_ Align spec/plan/questions, document outline per guide.
   - _Preconditions:_ Feature 007 spec drafted; migration templates chosen.
   - _Steps:_ Close clarifications, update roadmap/knowledge map references, capture doc skeletons.
   - _Commands:_ `rg -n "Feature 007" docs/4-architecture/roadmap.md`.
   - _Exit:_ Open questions cleared; outlines stored alongside tasks. ✅ 2025-09-30.
2. **I2 – Java operator guide** _(T-007-02)_
   - _Goal:_ Publish `docs/2-how-to/use-ocra-from-java.md` with runnable samples.
   - _Preconditions:_ Outline + fixture references ready.
   - _Steps:_ Author content, validate code snippets, capture troubleshooting notes.
   - _Commands:_ `rg -n "Java operator" docs/2-how-to`, `markdownlint docs/2-how-to/use-ocra-from-java.md`.
   - _Exit:_ Guide merged, references added. ✅ 2025-09-30.
3. **I3 – CLI operations guide** _(T-007-03)_
   - _Goal:_ Document import/list/delete/evaluate/maintenance commands.
   - _Steps:_ Capture command syntax, sample outputs, telemetry notes.
   - _Commands:_ `rg -n "ocra" docs/2-how-to/use-ocra-cli-operations.md`.
   - _Exit:_ Guide complete with troubleshooting section. ✅ 2025-09-30.
4. **I4 – REST operations guide** _(T-007-04)_
   - _Goal:_ Replace evaluation-only doc with full REST coverage + Swagger UI flow.
   - _Commands:_ `rg -n "Swagger" docs/2-how-to/use-ocra-rest-operations.md`, `./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshot*"` (optional snapshot validation).
   - _Exit:_ New guide published, legacy doc removed. ✅ 2025-09-30.
5. **I5 – README refresh** _(T-007-05)_
   - _Goal:_ Align README with shipped capabilities and link to guides.
   - _Commands:_ `rg -n "Swagger" README.md`, `markdownlint README.md`.
   - _Exit:_ README references new guides, no future-work placeholders. ✅ 2025-09-30.
6. **I6 – Final verification & logging** _(T-007-06)_
   - _Goal:_ Run spotless/check, capture verification log, and record completion in governance docs.
   - _Commands:_ `./gradlew --no-daemon spotlessApply check`.
   - _Exit:_ Verification log updated, knowledge map/roadmap/migration plan reflect completion. ✅ 2025-09-30.

## Scenario Tracking
| Scenario ID | Increment / Task reference | Notes |
|-------------|---------------------------|-------|
| S-007-01 | I2 / T-007-02 | Java operator how-to walkthrough. |
| S-007-02 | I3 / T-007-03 | CLI guide covering import/list/delete/evaluate/maintenance. |
| S-007-03 | I4 / T-007-04 | REST guide including Swagger UI and troubleshooting. |
| S-007-04 | I5 / T-007-05 | README refresh referencing shipped capabilities. |
| S-007-05 | I1 & I6 / T-007-01, T-007-06 | Cross-links + governance logs updated. |

## Analysis Gate (2025-09-30)
- ✅ Specification populated with objectives, requirements, clarifications.
- ✅ Open questions cleared in `docs/4-architecture/open-questions.md`.
- ✅ Plan + tasks align with scope and governance artefacts.
- ✅ Tasks kept ≤30 minutes with tests/verification sequenced first.
- ✅ Tooling readiness captured (`./gradlew spotlessApply check`, optional markdownlint, REST snapshot verification).

## Exit Criteria
- Java, CLI, REST how-to guides published and cross-linked.
- README reflects shipped capabilities and Swagger UI entry point.
- Roadmap, knowledge map, migration plan, and `_current-session.md` describe the documentation suite.
- `./gradlew --no-daemon spotlessApply check` completes successfully after doc edits.

## Follow-ups / Backlog
- None; future documentation expansions will land under their respective feature IDs when prioritized.
