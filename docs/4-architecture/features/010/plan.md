# Feature 010 – Documentation & Knowledge Automation Plan

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Specification | `docs/4-architecture/features/010/spec.md` |
| Tasks checklist | `docs/4-architecture/features/010/tasks.md` |
| Roadmap entry | #10 – Documentation & Knowledge Automation |

## Vision & Success Criteria
Centralise every operator-facing guide, roadmap/knowledge-map reference, DeepWiki steering config (`.devin/wiki.json`), and
quality-automation workflow under one feature so all documentation and the aggregated `qualityGate` evolve together. Success
means the Java/CLI/REST guides, README, roadmap, knowledge map, `_current-session.md`, session log (docs/_current-session.md),
DeepWiki page outline, and GitHub Actions workflow share a single authoritative spec/plan/tasks set with deterministic
verification commands.

## Scope Alignment
- Maintain the operator documentation suite (Java/CLI/REST guides, README, roadmap/knowledge map references, `_current-session.md`, session quick reference) and DeepWiki steering config (`.devin/wiki.json`) inside Feature 010’s spec/plan/tasks so these artefacts evolve together.
- Keep roadmap, knowledge map, architecture graph, DeepWiki page outline, session log (docs/_current-session.md), and session quick reference aligned with documentation and automation changes.
- Maintain the aggregated `qualityGate` task plus its CI workflow, report locations, skip flags, and troubleshooting guides.
- Log every documentation/automation increment—including changes to `.devin/wiki.json`—inside `_current-session.md` with command history.

_Out of scope:_ shipping runtime simulator changes, expanding the quality gate to non-OCRA modules, or introducing new
publishing tooling.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| Docs templates (`docs/templates/*.md`) | Govern structure for specs/plans/tasks and operator guides. |
| Operator guides (`docs/2-how-to/*.md`) + README | Must reflect current simulator behaviour and telemetry expectations. |
| External dependency matrix (`docs/3-reference/external-dependencies-by-facade-and-scenario.md`) | Documents, per protocol/facade/flow/credential source, which major external dependencies (MapDB/Caffeine, Spring Boot/Thymeleaf/Springdoc, Picocli, etc.) are exercised so consumers understand stack composition even when using a single fat JAR. |
| Roadmap / knowledge map / session log (docs/_current-session.md) / `_current-session.md` | Need synchronized references and command logs per increment. |
| Gradle build logic (`qualityGate` task, Spotless, ArchUnit, Jacoco, PIT, SpotBugs, Checkstyle, gitleaks) | Provide the automation enforced by this feature. |
| GitHub Actions workflow (`.github/workflows/quality-gate.yml`) | Mirrors local gate execution and uploads reports. |

## Assumptions & Risks
- Documentation remains Markdown/ASCII; deviations require template updates.
- Developers run `./gradlew --no-daemon spotlessApply check` for every doc change and `./gradlew --no-daemon qualityGate` when automations change.
- Risk: forgetting to log commands in `_current-session.md` or session log (docs/_current-session.md) reduces auditability—mitigate via checklist items.
- Risk: PIT/Jacoco runtimes can exceed NFR limits if cache hints regress—monitor timings inside tasks/plan notes.

## Implementation Drift Gate

- Summary: Use this gate to ensure the docs/automation surface (operator guides, protocol reference pages, README, external-dependency reference, roadmap/knowledge map, session logs, DeepWiki steering, `qualityGate` and CI wiring) stays aligned with FR-010-01..12 and NFR-010-01..05, and that every change to documentation or automation is reflected consistently across artefacts and logged in `_current-session.md`.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] `docs/4-architecture/features/010/{spec,plan,tasks}.md` updated to the current date; all clarifications are encoded in normative sections (no ad-hoc “Clarifications” appendices).  
    - [ ] `docs/4-architecture/open-questions.md` has no `Open` entries for Feature 010.  
    - [ ] The following commands have been run in this increment and logged in `docs/_current-session.md`:  
      - `./gradlew --no-daemon spotlessApply check`  
      - `./gradlew --no-daemon qualityGate` (with `-Ppit.skip=true` only when explicitly allowed by the spec/plan).  

  - **Spec ↔ docs/automation mapping**
    - [ ] For FR-010-01..FR-010-12 and NFR-010-01..05, confirm the spec’s expectations are reflected in:  
      - Operator how-to guides (Java/CLI/REST, and any other documented surfaces).  
      - Protocol reference docs and diagrams under `docs/3-reference/protocols/` (HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP, EUDIW OpenID4VP).  
      - `README.md` and `docs/2-how-to/README.md` landing pages.  
      - External dependency matrix reference (`docs/3-reference/external-dependencies-by-facade-and-scenario.md`).  
      - Roadmap (`docs/4-architecture/roadmap.md`) and knowledge map (`docs/4-architecture/knowledge-map.md`).  
      - Session quick reference (`docs/5-operations/session-quick-reference.md`).  
      - Session log (`docs/_current-session.md`) and any related logs referenced in the spec.  
    - [ ] Ensure `.devin/wiki.json` mirrors the current docs structure and feature catalogue (including the Feature 010 role) and that DeepWiki descriptions match the spec’s view of docs/automation responsibilities.  

  - **Operator docs alignment**
    - [ ] Operator guides for Java/CLI/REST (and any other shipped guides) still:  
      - Match current module behaviour and telemetry expectations.  
      - Use examples that compile or are trivially fixable against the current code.  
      - Reference troubleshooting and quality-gate guidance where the spec requires it.  
    - [ ] README no longer references retired directories or legacy features, and its list of simulators, URLs, and how-to links matches the actual set of shipped features.  

  - **Automation & quality-gate alignment**
    - [ ] `qualityGate` definition in `build.gradle.kts` still matches the spec:  
      - Includes Spotless, Checkstyle, SpotBugs, ArchUnit, Jacoco aggregated coverage, PIT (subject to skip flags), and gitleaks.  
      - Uses thresholds and skip flags consistent with FR-010/NFR-010.  
    - [ ] `.github/workflows/ci.yml` invokes `./gradlew --no-daemon qualityGate` (or the documented equivalent) and uploads or references reports as described in the spec/plan.  
    - [ ] Any helper scripts or runbooks referenced in the spec (e.g., how to run `qualityGate`, how to bypass PIT when allowed) still exist and match the documented instructions.  

  - **Governance & logging**
    - [ ] Feature 011 hook/constitution requirements referenced from this spec are still honoured:  
      - `git config core.hooksPath` logs appear in `docs/_current-session.md`.  
      - Pre-commit and commit-msg hooks enforce Spotless, tests, secret scanning, and gitlint as described.  
    - [ ] Every documentation/automation increment has a corresponding entry in `docs/_current-session.md` that mentions:  
      - What changed (guides, roadmap/knowledge map, session quick reference, CI wiring, etc.).  
      - The commands executed (at least `spotlessApply check`, and `qualityGate` for automation changes).  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (e.g., guides contradicting behaviour, `qualityGate` behaviour diverging from docs, missing logging of key commands) is:  
      - Logged as an `Open` row in `docs/4-architecture/open-questions.md` for Feature 010.  
      - Captured as explicit tasks in `docs/4-architecture/features/010/tasks.md` (and, if cross-cutting, in other features’ plans).  
    - [ ] Low-impact drift (typos, minor link fixes, small doc mismatches) is corrected directly in docs/plan/tasks, with a brief note added to this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the date of the latest drift gate run, listing key commands executed and a short “matches vs gaps” summary plus remediation notes.  
    - [ ] `docs/_current-session.md` logs that the Feature 010 Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

### Drift Report – 2025-11-13
- **Scope review:** Spec/plan/tasks describe the steady-state documentation + knowledge automation scope (Java/CLI/REST guides, README, roadmap/knowledge map, session quick reference, `_current-session.md`, `qualityGate` automation). No legacy references remain and roadmap/knowledge-map entries point to Feature 010 as the authority (covers FR-010-01..10, NFR-010-01..05, Scenarios S-010-01..10).
- **Doc alignment:** Operator guides and README paths remain documented in the spec; roadmap, knowledge map, and session quick reference updates are logged in `_current-session.md`, satisfying FR-010-03/S-010-03. Templates + how-tos reference telemetry expectations and troubleshooting coverage (FR-010-01/02).
- **Automation alignment:** Aggregated `qualityGate`, ArchUnit, Jacoco, PIT, and lint suites remain captured in plan/spec with skip flags and CI parity guidance (FR-010-04..09, S-010-04..09). Documentation now highlights ongoing logging expectations (FR-010-10) without migration framing.
- **Verification commands:** `./gradlew --no-daemon spotlessApply check` (2025-11-13, 18 s, 96 tasks: 2 executed, 94 up-to-date) recorded in Feature 010 tasks and `_current-session.md`; command exercises Spotless/PMD/Checkstyle suites plus the console Node harness.


## Increment Map
1. **I1 – Documentation suite foundation** (Owner: Ivan, Status: Completed)  
   - Established the consolidated spec for Java/CLI/REST guides and README cross-links; FR-010-01/02 reference guide paths, telemetry expectations, and troubleshooting coverage.  
2. **I2 – Quality automation charter** (Owner: Ivan, Status: Completed)  
   - Captured the ArchUnit/Jacoco/PIT/`qualityGate` workflow plus CI troubleshooting docs (FR-010-04..09 + NFR updates).  
3. **I3 – Directory + template cleanup** (Owner: Ivan, Status: Completed)  
   - Ensured legacy documentation directories were retired after verifying coverage; command outputs remain logged in `_current-session.md`.  
4. **I4 – Documentation/automation verification gate** (Owner: Ivan, Status: Pending)  
   - Record the cross-feature summary, rerun `./gradlew --no-daemon spotlessApply check`, and schedule the final `qualityGate` once Features 011–013 finish their rewrites.

_Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate [-Ppit.skip=true]`, targeted ArchUnit/Jacoco/PIT/SpotBugs/Checkstyle runs, and `rg "Feature 010" docs/` to confirm cross-document references.

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-010-01 | Operator guides remain runnable with telemetry and troubleshooting coverage. | P3-I1 |
| S-010-02 | README/doc landing pages point to shipped capabilities only. | P3-I1 |
| S-010-03 | Roadmap/knowledge map/session log (docs/_current-session.md)/session quick reference stay synchronized and log commands. | P3-I1–P3-I4 |
| S-010-04 | `qualityGate` aggregates Spotless, Checkstyle, SpotBugs, ArchUnit, Jacoco, PIT, gitleaks. | P3-I2 |
| S-010-05 | ArchUnit rules enforce module boundaries. | P3-I2 |
| S-010-06 | Jacoco aggregated thresholds remain ≥90% line/branch. | P3-I2 |
| S-010-07 | PIT mutation score stays ≥85% with documented skip flag. | P3-I2 |
| S-010-08 | GitHub Actions workflow mirrors the gate and uploads reports. | P3-I2 |
| S-010-09 | Docs/runbooks explain how to run/remediate the gate. | P3-I2 |
| S-010-10 | `_current-session.md` + session log (docs/_current-session.md) log every documentation/automation increment. | P3-I3 |

## Analysis Gate
Run `docs/5-operations/analysis-gate-checklist.md` whenever major doc/automation scope changes land. Ensure roadmap, knowledge map, session log (docs/_current-session.md), and `_current-session.md` match the updated spec/plan/tasks before moving to implementation work.

## Exit Criteria
- Feature 010 spec/plan/tasks describe the operator doc suite and quality automation guardrails (no `legacy/` references).
- Roadmap, knowledge map, architecture graph, session log (docs/_current-session.md), session quick reference, and `_current-session.md` cite Feature 010 for documentation and automation work.
- `./gradlew --no-daemon spotlessApply check` recorded after each major doc/automation change; `qualityGate` rerun once Features 011–013 integrate.

## Follow-ups / Backlog
- Script knowledge-map regeneration to reduce manual edits (capture timing + instructions in Feature 010 tasks once designed).
- Evaluate adding Markdown lint to the managed hook after Batch P3 verification.
- Expand `qualityGate` coverage beyond the OCRA stack in a future feature once this migration stabilises.
- Coordinate with Feature 014 – Native Java API Facade for cross-protocol Native Java API entry points, Javadoc surfaces, and `*-from-java` guides per protocol; keep Feature 010 focused on documentation/automation while the cross-cutting spec governs runtime API stability and facade ownership. Feature 010 now also tracks the `:application:nativeJavaApiJavadoc` aggregation task as part of the documentation automation surface.

$chunk
