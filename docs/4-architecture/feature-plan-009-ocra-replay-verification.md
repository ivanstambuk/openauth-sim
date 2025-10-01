# Feature Plan 009 – OCRA Replay & Verification

_Status: Draft_
_Last updated: 2025-10-01_

## Objective
Enable operators and automated systems to verify previously generated OCRA one-time passwords (OTPs) against stored or inline credentials without regenerating new values. The feature must provide strict verification (no tolerance windows), expose CLI and REST interfaces, and emit audit-ready telemetry for non-repudiation.

Reference specification: `docs/4-architecture/specs/feature-009-ocra-replay-verification.md`.

## Success Criteria
- New CLI command verifies OTPs using immutable replay logic with no counter/session mutation.
- REST endpoint accepts OTPs plus full suite context, returning deterministic success/failure responses.
- Verification supports both persisted credentials (by identifier) and inline secrets with identical telemetry metadata.
- Telemetry entries capture operator principal, credential source, and hashed OTP payload for audit reconstruction.
- Verification flows remain within 150 ms P95 (stored) / 200 ms P95 (inline) on the documented benchmark environment.
- `./gradlew qualityGate` passes with added replay tests across core, CLI, and REST modules.

## Proposed Increments
- R901 – Prime knowledge map and roadmap notes; ensure open questions cleared. ☑ (2025-10-01 – roadmap entry updated, knowledge map note added)
- R902 – Run analysis gate checklist for Feature 009; record outcome here. ☐
- R903 – Draft CLI interface design (command syntax, options, telemetry mapping); capture in spec/plan addendum. ☐
- R904 – Draft REST contract (request/response JSON, validation errors) and update OpenAPI doc. ☐
- R905 – Identify telemetry fields and logging strategy for verification; add to docs. ☐
- R906 – Update knowledge map with new verification relationships once design solidifies. ☐
- R907 – Add failing core tests for replay engine (strict validation, inline vs stored) to drive implementation. ☐
- R908 – Add failing CLI integration tests covering success/failure scenarios. ☐
- R909 – Add failing REST integration tests (controller + service) covering success/failure. ☐
- R910 – Implement core verification logic ensuring immutability and strict matching. ☐
- R911 – Implement CLI command wiring and telemetry. ☐
- R912 – Implement REST endpoint, DTOs, and validation. ☐
- R913 – Ensure telemetry/logging includes required audit fields; update docs. ☐
- R914 – Add performance benchmark or documented measurements for P95 targets. ☐
- R915 – Update docs/how-to guides describing replay usage and audit interpretation. ☐
- R916 – Run `./gradlew qualityGate`, capture metrics, and record closure notes. ☐

## Checklist Before Implementation
- [ ] Specification clarifications resolved (see Clarifications section).
- [ ] Open questions log clear for Feature 009.
- [ ] Feature plan references correct specification and aligns success criteria.
- [ ] Tasks map to functional requirements with ≤10 minute increments prioritising tests first.
- [ ] Planned work respects constitution principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
- [ ] Tooling readiness noted with expected commands (`./gradlew qualityGate`, module-specific tasks).

## Analysis Gate (TBD)
Pending completion of R901–R905. Record checklist results once complete.

### Tooling Readiness – `./gradlew qualityGate`
- Expected runtime: ~2 minutes with warm caches when PIT runs; `-Ppit.skip=true` available for local iteration.
- Key tasks: `:core:test`, `:cli:test`, `:rest-api:test`, `mutationTest`, `jacocoAggregatedReport`, new replay tests, telemetry assertions.

## Notes
- 2025-10-01 – Draft plan created; awaiting analysis gate completion before implementation begins.
- 2025-10-01 – R901 complete: roadmap now lists Feature 009 in planning with spec/plan links; knowledge map references upcoming replay verification scope.

Update this plan after each increment.
