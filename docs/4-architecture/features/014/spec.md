# Feature 014 – Native Java API Facade

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/014/plan.md` |
| Linked tasks | `docs/4-architecture/features/014/tasks.md` |
| Roadmap entry | #14 – Native Java API Facade |

> Guardrail: This specification is the single normative source of truth for the Native Java API facade. High- and medium-impact questions about Java entry points, stability guarantees, or Javadoc publishing must be tracked in `docs/4-architecture/open-questions.md`, encoded here once resolved, and mirrored into ADRs (for example ADR-0007) when they materially affect architecture. Do not introduce per-feature “Clarifications” sections; use this spec plus ADRs instead.

## Overview
Define Native Java API usage as a first-class facade across the OpenAuth Simulator so external Java 17 applications can
drive protocol flows in-process without going through CLI/REST/UI, while keeping the project’s greenfield constraints and
spec-first workflow intact. Rather than a single monolithic SDK, each protocol feature (HOTP, TOTP, OCRA, FIDO2/WebAuthn,
EMV/CAP, EUDIW OpenID4VP) exposes its own Native Java entry points in `core` and/or `application`, following a shared
pattern for APIs, Javadoc, and `*-from-java` guides, plus matching per-protocol how-to guides for JMeter (Groovy) and Neoload
that demonstrate calculation-only usage from load-testing tools. This feature codifies that pattern, ties it to ADR-0007
(Native Java API Facade Strategy), and mandates incremental backlog work in Features 001, 002, 004, 005, and 006 to mirror
the existing OCRA Native Java API.

## Goals
- G-014-01 – Treat Native Java usage as a facade alongside CLI, REST, and operator UI, with clear documentation and
  stability expectations recorded in this spec and ADR-0007.
- G-014-02 – Define a repeatable pattern for per-protocol Native Java APIs (entry-point classes, DTOs, error handling,
  telemetry hooks, and Javadoc) that other features can adopt, including naming/placement rules in `core`/`application`
  and stability expectations for those entry points.
- G-014-03 – Ensure each protocol feature (HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP, EUDIW OpenID4VP) has explicit plan
  and tasks backlog items to (a) expose a Native Java API and (b) publish at least one `*-from-java` how-to guide.
- G-014-04 – Keep DeepWiki and docs/2-how-to aligned so the “Native Java API” surface is visible and discoverable for all
  protocols once their APIs are implemented.

## Non-Goals
- N-014-01 – Designing or shipping a single cross-protocol “Java SDK” module; any higher-level SDK must be defined under
  a future feature once per-protocol APIs exist.
- N-014-02 – Introducing new runtime behaviour in protocols beyond what existing specs already require; this feature
  governs access patterns and documentation for APIs, not new flows.
- N-014-03 – Guaranteeing long-term semantic stability beyond the constitution’s greenfield stance; Native Java APIs
  remain changeable via spec + ADR updates, but changes must be deliberate and documented.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-014-01 | Document Native Java API as a fourth facade alongside CLI, REST, and operator UI for all protocols. | README, docs/2-how-to, DeepWiki, and this spec consistently describe Native Java as a supported consumption surface across protocols. | Manual review of README, docs/2-how-to, `.devin/wiki.json`, and DeepWiki pages; `rg "Native Java API"` across docs shows consistent messaging. | Docs omit Native Java or describe it only for OCRA. | No new telemetry; relies on existing protocol events. | ADR-0007, Feature 010 spec. |
| FR-014-02 | Define a per-protocol Native Java API pattern that OCRA and future protocols follow. | Spec enumerates expectations for entry-point classes, DTOs, error/exception semantics, telemetry hooks, and stability guarantees (including package placement and naming rules); OCRA’s existing API is annotated as the reference; other features inherit this pattern in their specs/plans. | Spec sections and ADR-0007 reference the pattern; at least one additional protocol feature (e.g., HOTP or TOTP) documents a planned Native Java API increment referencing this spec. | Protocol features define ad-hoc Java entry points that diverge from the pattern. | Existing telemetry events remain unchanged; Java APIs reuse telemetry adapters where applicable. | ADR-0007, Feature 003 spec/plan/tasks. |
| FR-014-03 | Seed backlog items in Features 001, 002, 004, 005, and 006 to expose Native Java APIs and `*-from-java` guides. | Each referenced feature plan’s Follow-ups/Backlog section lists a Native Java API increment referencing this spec/ADR-0007; tasks files mention future `*-from-java` guides where appropriate. | Manual inspection of feature plans/tasks and roadmap; `_current-session.md` logs the planning change. | No cross-feature backlog exists; Native Java remains OCRA-only. | None. | Spec, ADR-0007. |
| FR-014-04 | Keep DeepWiki and how-to docs aligned with Native Java API governance. | `.devin/wiki.json` includes a Native Java API page and repo notes describing four surfaces; docs/2-how-to lists per-surface guides, including Native Java; DeepWiki renders a Native Java API page that points to per-protocol guides as they are added. | DeepWiki and docs are checked after each major Native Java API increment; discrepancies are recorded in Feature 014 plan/tasks. | DeepWiki omits or misrepresents Native Java usage, or docs drift away from implemented APIs. | None. | Feature 010 spec/plan, `.devin/wiki.json`. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-014-01 | Consistent API style | Reduce friction for Java consumers by aligning naming, packaging, and error-handling across protocol APIs. | Code review of new Native Java APIs; style guidelines captured in Feature 014 plan/tasks. | `core`, `application` modules; Java 17 language features. | ADR-0007. |
| NFR-014-02 | Documentation parity | Native Java APIs ship with how-to guides and, when feasible, Javadoc published in `docs/3-reference/`. | Presence of `*-from-java` how-to guides and a documented Javadoc generation command in Feature 014 plan/tasks. | Gradle Javadoc tasks, docs/3-reference. | Spec, ADR-0007. |
| NFR-014-03 | Governance & stability | Native Java entry points remain changeable under the greenfield constitution but may only change via spec/ADR updates and with docs/tests kept in sync. | Protocol specs/plans reference this feature when changing Native Java APIs; review of ADRs and `_current-session.md` logs shows deliberate changes. | Feature specs 001–006, ADR-0007, Feature 010. | Constitution, ADR-0007. |
| NFR-014-04 | Public Javadoc hygiene | Keep public-facing Javadoc (as published via `:core:javadoc`, `:application:javadoc`, and `:application:nativeJavaApiJavadoc`) free of internal roadmap identifiers (Feature/FR/NFR/T numbers); describe behaviour via protocol names, standards, and how-to docs instead. | Automated guard in `core-architecture-tests` scans aggregated Javadoc for forbidden identifiers; manual spot checks compare Javadoc to `docs/2-how-to/*-from-java.md`. | Gradle Javadoc tasks, core-architecture-tests module, docs/2-how-to. | Spec, ADR-0008. |

## UI / Interaction Mock-ups
This feature has no UI-facing changes; operator console behaviour remains governed by existing protocol features and
Feature 009. Remove or update this section if future increments introduce UI controls specifically for Native Java
configuration.

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-014-01 | Readers can discover and understand Native Java API usage for at least OCRA plus one additional protocol via docs/2-how-to and DeepWiki. |
| S-014-02 | Feature plans/tasks for 001, 002, 004, 005, and 006 contain explicit backlog entries to expose or refine Native Java APIs under this spec. |

## Test Strategy
- **Core:** For each protocol that adds or refines a Native Java API, add unit tests that call the public entry points
  directly and assert behaviour without going through CLI/REST/UI.
- **Application:** Where application services are used as Native Java seams, add tests that treat them as public APIs and
  verify telemetry/event behaviour.
- **REST/CLI/UI:** No direct changes; existing tests remain the source of truth for those facades, but Native Java flows
  should be able to reproduce REST/CLI/UI behaviour using shared fixtures.
- **UI (JS/Selenium):** No changes expected; rely on existing end-to-end coverage per protocol.
- **Docs/Contracts:** Treat `docs/2-how-to/*-from-java.md` guides as executable specifications; spot-check against current
  APIs and update when APIs change.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-014-01 | NativeJavaEntryPoint – abstract notion of a protocol-specific Java entry point (e.g., OcraCredentialFactory + OcraResponseCalculator, future HOTP/TOTP/FIDO2/EMV/EUDIW services). | core, application |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-014-01 | In-process Java | Native Java API entry points for each protocol, referenced from their respective feature specs/plans and `*-from-java` guides. | Not a single module; governed by this spec and ADR-0007. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-014-01 | n/a | Native Java APIs are consumed directly from Java, not via CLI. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-014-01 | (reuses existing protocol events) | Native Java APIs must reuse existing telemetry adapters / events rather than introducing facade-specific ones. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-014-01 | docs/test-vectors/** | Existing fixture catalogues used by Native Java examples in `*-from-java` guides. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-014-01 | n/a | No new UI states; Native Java remains an in-process facade. |

## Native Java API Pattern

This section defines what “Native Java API” means in this repository and how protocol features must expose it.

### Placement
- Primary entry points SHOULD live in the `application` module under protocol-specific packages (for example,
  `io.openauth.sim.application.hotp`, `...totp`, `...fido2`, `...emv.cap`, `...eudi.openid4vp`) and follow the
  established naming style `*ApplicationService` or `*SimulationService` for orchestration-style APIs.
- Low-level helpers in `core` MAY be treated as Native Java entry points when they encapsulate protocol behaviour
  directly (for example, `OcraCredentialFactory`, `OcraResponseCalculator`). When doing so, the governing feature spec
  must explicitly list those types as part of its Native Java surface.
- Protocol features must identify their Native Java entry points in their own specs under “Interface & Contract
  Catalogue” and reference FR-014-01..04 so the seam remains traceable.
- For HOTP (Feature 001), `io.openauth.sim.application.hotp.HotpEvaluationApplicationService` and its
  `EvaluationCommand` / `EvaluationResult` types act as the Native Java entry point once T-001-21/22 complete, with
  usage governed by this pattern and `docs/2-how-to/use-hotp-from-java.md`.
- For TOTP (Feature 002), `io.openauth.sim.application.totp.TotpEvaluationApplicationService` and its
  `EvaluationCommand` / `EvaluationResult` types act as the Native Java entry point once T-002-19/20 complete, with
  usage governed by this pattern and `docs/2-how-to/use-totp-from-java.md`.
- For FIDO2/WebAuthn (Feature 004), `io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService` and its
  `EvaluationCommand` / `EvaluationResult` types act as the Native Java entry point for assertion evaluation once
  T-004-08/09 complete, with usage governed by this pattern and `docs/2-how-to/use-fido2-from-java.md`.
- For EMV/CAP (Feature 005), `io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService` and its
  `EvaluationRequest` / `EvaluationResult` types act as the Native Java entry point once T-005-47/48 complete, with
  usage governed by this pattern and `docs/2-how-to/use-emv-cap-from-java.md`.
- For EUDIW OpenID4VP (Feature 006), `io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService`
  and `OpenId4VpValidationService` act as the Native Java entry points for wallet simulation and validation once
  T-006-28/29 complete, with usage governed by this pattern and `docs/2-how-to/use-eudiw-from-java.md`.

### Naming & style
- Entry-point classes SHOULD:
  - Be `final` (or otherwise clearly non-extensible) and use simple records or POJOs for request/response types.
  - Avoid exposing mutable internal state or persistence details; treat persistence as an injected collaborator where
    needed.
  - Use method names that mirror protocol operations (for example, `evaluate`, `replay`, `simulateWallet`, `validate`)
    rather than generic verbs.
- Checked exceptions SHOULD be avoided; validation and domain failures should use:
  - `IllegalArgumentException` or a small set of protocol-specific runtime exceptions with clear messages, and/or
  - return types that encode failure reasons when appropriate.
- Telemetry MUST be implemented via existing `TelemetryContracts` adapters rather than facade-specific loggers; Native
  Java APIs should either:
  - invoke the same telemetry paths used by CLI/REST/UI, or
  - document explicitly when no telemetry is emitted (for example, pure core math helpers).

### Stability expectations
- Native Java APIs remain subject to the project’s greenfield stance but must not change accidentally:
  - Any change to a documented entry point (signature, semantics) MUST go through an update to the owning feature spec
    and, for cross-cutting concerns, reference this feature and ADR-0007.
  - Associated `*-from-java` how-to guides and tests MUST be updated in the same increment.
- Protocol features should treat their Native Java entry points as façade seams in tests: add tests that exercise the
  public API end to end without reaching into internal collaborators.

### Javadoc & docs integration
- Entry-point classes and their public methods MUST include concise Javadoc that:
  - Summarises behaviour and inputs/outputs.
  - Cites publicly consumable references only (protocol/standard names, how-to guides, ADR-0007, etc.) and MUST NOT mention internal roadmap identifiers such as Feature numbers, FR/NFR IDs, or task IDs.
- Javadoc generation and publication follow this pattern:
  - Gradle remains the source of truth for Javadoc: `:core:javadoc` documents low-level helpers (for example, OCRA
    credential factories), while `:application:javadoc` documents Native Java entry points (`HotpEvaluationApplicationService`,
    `TotpEvaluationApplicationService`, `WebAuthnEvaluationApplicationService`, `EmvCapEvaluationApplicationService`,
    `OpenId4VpWalletSimulationService`, `OpenId4VpValidationService`, and future seams).
  - A future Feature 010 increment will own a dedicated aggregation task (for example,
    `:application:nativeJavaApiJavadoc`) that runs the relevant Javadoc tasks and exports either a zipped bundle or
    curated reference pages into `docs/3-reference/native-java-api/`.
  - Offline snapshots under `docs/3-reference/native-java-api/` MUST remain small and focused (indexes or curated
    summaries) rather than full HTML trees; the full Javadoc output stays in `build/docs/javadoc` artefacts.
- `docs/2-how-to/*-from-java.md` guides SHOULD reuse the same terminology and types as the Javadoc and avoid duplicating
  low-level details; they act as entry-level runbooks rather than full API references and SHOULD link to the Native Java
  API reference in `docs/3-reference/native-java-api/` once it is available. These guides remain the preferred location
  for referencing feature/task identifiers when needed because they are internal artefacts.
