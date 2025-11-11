# Feature 029 – PMD Rule Hardening

| Field | Value |
|-------|-------|
| Status | Planned |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/029/plan.md` |
| Linked tasks | `docs/4-architecture/features/029/tasks.md` |
| Roadmap entry | #29 |

## Overview
Expand the PMD ruleset to surface high-signal findings in core and service layers without overwhelming fluent APIs, builders, or adapters. The work enables a curated set of error-prone, best-practice, and design rules, scopes Law-of-Demeter enforcement to domain/service packages, and documents the remediation workflow so future contributors understand how to interpret violations.

## Clarifications
- 2025-10-19 – Treat `LawOfDemeter` as a locality heuristic: enable it for domain/service layers, whitelist fluent APIs/builders/DTO adapters via `<exclude-pattern>` resources, and track the whitelist work under this feature (owner directive).
- 2025-10-19 – Upgrade PMD to the latest supported 7.x release (currently 7.17.0) before enabling additional rules so we can baseline behaviour and address migration fallout in-feature (owner directive).
- 2025-10-19 – Enable `NonSerializableClass` for core domain + application service modules while excluding DTO/builder/adaptor classes elsewhere; remediation should make domain/service classes intentionally serializable or document why not (Option B approved).
- 2025-10-19 – Evaluate `category/java/bestpractices.xml/NonExhaustiveSwitch` within this governance effort before deciding on enforcement; experimentation must remain traceable within Feature 029 (Option A approved).
- 2025-10-19 – Run the initial `NonExhaustiveSwitch` dry run against the entire codebase to capture violations before deciding on scope (Option A approved).

## Goals
- Enable the targeted PMD error-prone, best-practice, and design rules in `config/pmd/ruleset.xml`.
- Introduce a Law-of-Demeter whitelist resource and keep enforcement focused on core/service layers.
- Remediate surfaced violations with minimal exclusions and document every intentional whitelist entry.
- Keep `./gradlew --no-daemon spotlessApply check` (including PMD) green throughout rollout.
- Update contributor guidance so PMD expectations remain visible for future agents.

## Non-Goals
- Adding new static-analysis tools (SpotBugs, ESLint, etc.) beyond documentation touch-ups.
- Refactoring architecture purely to satisfy rules without tangible quality gains.
- Introducing build-time dependencies beyond the PMD 7 upgrade already sanctioned.

## Functional Requirements

### FR29-01 – PMD 7 baseline
- **Requirement:** Upgrade PMD to the latest 7.x release and capture migration fallout before rule expansion.
- **Success path:** `pmdMain`/`pmdTest` run successfully on all modules after the upgrade; baseline reports archived.
- **Validation path:** Dry-run `pmdMain pmdTest` to list new warnings; document findings in plan/tasks.
- **Failure path:** CI fails with upgrade issues; feature cannot proceed until addressed.

### FR29-02 – Ruleset expansion
- **Requirement:** Enable the enumerated rules (error-prone, best practices, design, `NonExhaustiveSwitch`, `NonSerializableClass`) in `config/pmd/ruleset.xml`.
- **Success path:** Rules execute across modules, producing actionable findings; configuration stored under version control.
- **Validation path:** Dry-run results logged with module + rule IDs; plan references remediation order.
- **Failure path:** Build fails due to misconfigured rule files or missing resources.

### FR29-03 – Law-of-Demeter scoping
- **Requirement:** Create `config/pmd/law-of-demeter-excludes.txt` (or similar) and limit enforcement to domain/service packages with documented exclusions.
- **Success path:** PMD reports violations only for intended packages; fluent APIs remain whitelisted.
- **Validation path:** Tests confirm whitelist works; tasks record every new glob and rationale.
- **Failure path:** False positives flood reports; work pauses until scoping corrected.

### FR29-04 – Remediation
- **Requirement:** Address violations in core, application, infra, and facade modules, adding tests where behaviour changes.
- **Success path:** Targeted modules rebuild cleanly; intentional exclusions documented.
- **Validation path:** Module-specific tests plus `pmdMain pmdTest` run green; notes capture follow-ups.
- **Failure path:** Remaining violations block `spotlessApply check` and the feature cannot close.

### FR29-05 – Governance & documentation
- **Requirement:** Update AGENTS/runbooks/knowledge map with PMD workflows, and log telemetry (if available) for rule adoption.
- **Success path:** Docs outline how to interpret violations and extend the whitelist; roadmap entry updated.
- **Validation path:** Analysis gate confirms artefacts align; tasks checklist completed.
- **Failure path:** Spec/plan/tasks drift or lack governance notes, preventing exit.

## Non-Functional Requirements
- **NFR29-01 – Developer ergonomics:** Provide clear remediation/whitelist guidance to keep developer friction low.
- **NFR29-02 – Build stability:** PMD tasks must add ≤2 minutes to CI and run deterministically with Gradle build cache.
- **NFR29-03 – Auditability:** Every new exclude pattern or rule toggle documented with timestamp and rationale.

## UI / Interaction Mock-ups
- Not applicable; work surfaces through contributor docs/CLI commands rather than UI screens.

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S29-01 | Upgrade PMD to the latest 7.x release and capture baseline migration fallout. |
| S29-02 | Expand the PMD ruleset (error-prone, best practices, design, NonExhaustiveSwitch) and record baseline results. |
| S29-03 | Scope Law-of-Demeter enforcement with whitelist resources and targeted exclude patterns. |
| S29-04 | Remediate domain-layer violations surfaced by the new rules. |
| S29-05 | Remediate service/facade violations with justified excludes. |
| S29-06 | Update documentation/runbooks and rerun spotless/PMD gates to close the feature. |

## Test Strategy
- `./gradlew --no-daemon pmdMain pmdTest`
- `./gradlew --no-daemon spotlessApply check`
- Targeted module suites (`:core:test`, `:application:test`, `:rest-api:test`, `:cli:test`, `:ui:test`) whenever remediation touches code.
- Optional `./gradlew --no-daemon :infra-persistence:test` if persistence adapters change.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-029-01 | `PmdRuleConfiguration` (Gradle + XML config describing enabled rule sets). | buildSrc, root |
| DO-029-02 | `LawOfDemeterWhitelist` resource listing glob patterns for fluent APIs/builders. | config/pmd |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-029-01 | Gradle PMD plugin | Consumes `config/pmd/ruleset.xml` and optional exclude resources. | Runs via `pmdMain`/`pmdTest`. |

### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-029-01 | `./gradlew --no-daemon pmdMain pmdTest` | Executes PMD with expanded rules, failing build on violations. |
| CLI-029-02 | `./gradlew --no-daemon spotlessApply check` | Ensures PMD and formatting changes integrate cleanly. |

### Telemetry Events
| ID | Event name | Fields / Redaction summary |
|----|------------|----------------------------|
| TE-029-01 | `quality.pmd.run` | `rulesetVersion`, `violationCount`, `modules`. Optional future enhancement; log through build analytics if enabled. |

### Fixtures & Sample Data
| ID | Path | Description |
|----|------|-------------|
| FX-029-01 | `config/pmd/ruleset.xml` | Authoritative list of enabled PMD rules. |
| FX-029-02 | `config/pmd/law-of-demeter-excludes.txt` | Glob patterns for fluent-interface exclusions. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|----------------------------|
| UI-029-01 | Contributor docs quality section | Readers learn how to run PMD + interpret results after migration. |

## Spec DSL
```yaml
ruleset:
  version: 7.17.0
  rules:
    - category: errorprone
      name: AvoidCatchingNPE
    - category: errorprone
      name: AssignmentInOperand
    - category: bestpractices
      name: LawOfDemeter
      options:
        exclude_patterns_file: config/pmd/law-of-demeter-excludes.txt
    - category: bestpractices
      name: NonExhaustiveSwitch
    - category: design
      name: DataClass
whitelist:
  - pattern: "*/**/dto/**"
    rationale: Fluent DTO builders
```

## Appendix
- Baseline reports stored under `*/build/reports/pmd/` after PMD 7 upgrade.
- Track intentional excludes in `docs/4-architecture/features/029/tasks.md` under each remediation task.
