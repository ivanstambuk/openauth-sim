# ADR-0004: Documentation & Aggregated Quality Gate Workflow

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 010 (`docs/4-architecture/features/010/spec.md`), Feature 011 (`docs/4-architecture/features/011/spec.md`)
- **Related open questions:** (none currently)

## Context

OpenAuth Simulator relies on operator documentation and a shared quality gate to keep the OCRA stack understandable and safe
to change. Before Feature 010, documentation lived in several guides and README sections with uneven coverage, and quality
automation (ArchUnit, Jacoco, PIT, Spotless, SpotBugs, Checkstyle, gitleaks) ran via separate Gradle tasks or CI workflows.

The project constitution (Principles 1–6) requires Specification-Driven Development, Clarification Gates, and Implementation
Drift Gates, but it did not define a single, aggregated “quality gate” or a canonical documentation workflow for the OCRA
surface. Feature 010 must provide a repeatable path for:
- Authoring and updating the Java/CLI/REST how-to guides and README.
- Keeping roadmap, knowledge map, architecture graph, session references, and `_current-session.md` aligned with doc/automation changes.
- Running a single aggregated `qualityGate` locally and in CI.

## Decision

Adopt Feature 010 as the authoritative owner of documentation and quality gate workflow for the OCRA stack:
- Documentation workflow:
  - Operator-facing guides live under `docs/2-how-to/` (`use-ocra-from-java`, `use-ocra-cli-operations`,
    `use-ocra-rest-operations`) and are referenced from `README.md` and roadmap/knowledge map entries.
  - Documentation changes must be driven from the Feature 010 spec/plan/tasks and logged in `_current-session.md` with
    commands executed and deltas summarised.
  - Roadmap, knowledge map, architecture graph, session quick reference, and session logs remain synchronised with
    documentation scope changes.
- Aggregated quality gate:
  - Define `./gradlew --no-daemon qualityGate` as the canonical entry point that runs Spotless, Checkstyle, SpotBugs,
    ArchUnit, Jacoco aggregation (≥90% line/branch), PIT (≥85% mutation score), and gitleaks in a single command.
  - Allow local PIT triage via `-Ppit.skip=true`, while CI must run the full gate by default.
  - Require GitHub Actions workflows to invoke `qualityGate` with matching flags and upload Jacoco/PIT/quality reports.

Feature 010’s spec/plan/tasks are the source of truth for this workflow and reference this ADR. Feature 011 provides
governance around hooks, logging, and constitution alignment but does not supersede Feature 010’s ownership of the docs +
quality gate workflow.

## Consequences

### Positive
- Single, predictable process for updating OCRA documentation, with clear locations and verification commands.
- Aggregated quality gate reduces the risk of missing a required suite or running automation inconsistently between local
  environments and CI.
- `_current-session.md` entries and session logs provide an audit trail for doc/automation changes, supporting drift gates.

### Negative
- Running the full `qualityGate` can be expensive; contributors must use caching and optional PIT skip flags to keep
  runtimes within acceptable bounds.
- Documentation and automation changes require coordination across multiple artefacts (spec/plan/tasks, guides, roadmap,
  knowledge map, session quick reference, CI workflow).

## Alternatives Considered

- **A – Separate tasks for each quality tool**
  - Pros: Fine-grained control over what runs when.
  - Cons: Easy to forget a suite; encourages inconsistent checks between developers and CI; contradicts the desire for a
    single quality gate entry point.
- **B – CI-only quality gate with ad-hoc local runs**
  - Pros: Less local runtime overhead.
  - Cons: Pushes failures to CI; makes SDD and drift gates harder to execute locally; weakens developer feedback loops.
- **C – Aggregated qualityGate owned by Feature 010 (chosen)**
  - Pros: Aligns local and CI behaviour; integrates with documentation and knowledge automation; matches the constitution’s
    emphasis on reproducible gates.

## Security / Privacy Impact

- The quality gate includes gitleaks and other static checks that reduce the risk of committing secrets or unsafe patterns.
- Documentation clarifies telemetry expectations and redaction behaviour for OCRA flows, guiding operators to interpret logs
  without exposing secrets.

## Operational Impact

- Operators and maintainers must:
  - Use Feature 010 spec/plan/tasks when updating documentation or adjusting the quality gate.
  - Run `./gradlew --no-daemon spotlessApply check` for doc changes and `./gradlew --no-daemon qualityGate` after automation
    changes, logging results in `_current-session.md`.
  - Ensure GitHub Actions workflows keep `qualityGate` wiring in sync with local expectations and upload reports to the
    documented locations.

## Links

- Related spec sections: `docs/4-architecture/features/010/spec.md#overview`, `#goals`, `#functional-requirements`,
  `#documentation-deliverables`
- Related ADRs: ADR-0002 (Governance Formatter and Managed Hooks), ADR-0003 (Governance Workflow and Drift Gates)
- Related issues / PRs: (to be linked from future documentation/automation updates)

