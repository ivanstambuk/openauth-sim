# Feature 013 – Toolchain & Quality Platform Plan

_Linked specification:_ [docs/4-architecture/features/013/spec.md](docs/4-architecture/features/013/spec.md)  
_Linked tasks:_ [docs/4-architecture/features/013/tasks.md](docs/4-architecture/features/013/tasks.md)  
_Status:_ In review  
_Last updated:_ 2025-12-12  
_Owners:_ Ivan (project owner)  
_Roadmap entry:_ #13 – Toolchain & Quality Platform

## Vision & Success Criteria
Maintain a single plan for every toolchain/quality guardrail—CLI exit harnesses, Maintenance CLI coverage buffers, anti-
reflection policy, Java 17 refactors, architecture harmonization, SpotBugs/PMD enforcement, Gradle upgrades, and removal of
legacy entry points—so future increments know which commands to run and which backlog items remain.

## Scope Alignment
- Keep CLI exit harness, Maintenance CLI coverage, reflection policy, Java 17 refactor, architecture harmonization, SpotBugs/PMD enforcement, and Gradle upgrade guidance current inside this spec/plan/tasks set.
- List the commands that must run before/after toolchain changes (`qualityGate`, module tests, jacocoAggregatedReport,
  reflectionScan, spotbugsMain, pmdMain pmdTest, Gradle wrapper/warning sweeps) and log them in `_current-session.md`.
- Synchronize roadmap, knowledge map, architecture graph, session log ([docs/_current-session.md](docs/_current-session.md)), and `_current-session.md` entries with the
  refreshed toolchain ownership.
- Coordinate with Feature 015 (MCP Agent Facade) and Feature 010 (JSON-LD documentation automation) so the aggregated
  `qualityGate` and schema/telemetry drift tests remain consistent; Feature 013 owns governance automation, not MCP or
  documentation feature delivery.

_Out of scope:_ Shipping new protocol runtime behaviour; this plan focuses on schema/telemetry governance tests, OpenAPI
required-field annotations, and Gradle entrypoints that keep existing behaviours aligned.

## JSON & Telemetry Schema Governance (Plan 003 migration)

This increment migrates `docs/tmp/3-json-and-telemetry-governance-plan.md` into the governing Feature 013 artefacts and
implements the missing enforcement pieces:

- **CLI JSON schema** is enforced by existing `--output-json` tests using `docs/3-reference/cli/cli.schema.json`.
- **REST OpenAPI snapshots** remain the canonical REST contract (`docs/3-reference/rest-openapi.{json,yaml}`) and are
  enforced via snapshot tests plus runtime response shape validation against the `$ref`-linked component schemas.
- **Telemetry reasonCode parity** remains guarded via existing CLI/OpenAPI parity tests; this increment adds baseline
  schema validation so payload drift is caught even when enums remain unchanged.
- **MCP schema governance** continues under Feature 015 (tool catalogue JSON Schema + helper flows). Feature 013 only
  ensures the shared gates and drift tests remain wired into `check`/`qualityGate`.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| [config/spotbugs/dead-state-include.xml](config/spotbugs/dead-state-include.xml), [config/pmd/ruleset.xml](config/pmd/ruleset.xml), `config/pmd/law-of-demeter-excludes.txt` | Shared static-analysis config referenced by the spec. |
| [gradle/wrapper/gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties), [gradle/libs.versions.toml](gradle/libs.versions.toml) | Record Gradle 9/pugin versions. |
| [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md), [docs/5-operations/session-quick-reference.md](docs/5-operations/session-quick-reference.md), [AGENTS.md](AGENTS.md) | Reference reflection policy, sealed hierarchies, and static-analysis expectations. |
| `_current-session.md`, roadmap, knowledge map, architecture graph | Receive updated toolchain ownership + command logs. |

## Assumptions & Risks
- Commands listed in this plan remain invocable (Gradle wrapper present, Node tests available).
- Risk: failure to capture outstanding TODOs (e.g., PMD Law-of-Demeter exclusions, Maintenance CLI coverage buffer follow-ups)
  would hide future work—mitigate by scanning each legacy plan/tasks while updating this plan.
- Risk: forgetting to log wrapper updates/command outputs reduces auditability; tasks checklist enforces logging.

## Implementation Drift Gate

- Summary: Use this gate to ensure that toolchain and quality automation (Spotless, Checkstyle, SpotBugs, PMD, Jacoco, PIT, reflectionScan, wrapper/Gradle versioning, CLI exit harnesses) remain aligned with FR-013-01..10 and NFR-013-01..05, and that all verification commands and wrapper upgrades are properly logged.
- **Status:** Completed 2025-11-16 (see T-013-L1/L2). Commands executed for this run: `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`, `./gradlew --no-daemon reflectionScan`, and `./gradlew --no-daemon :core-ocra:spotbugsMain :core-ocra:pmdMain :core-ocra:pmdTest` (all logged in [docs/_current-session.md](docs/_current-session.md)). Future runs should continue following the checklist below.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] ``docs/4-architecture/features/013`/{spec,plan,tasks}.md` updated to the current date; all clarifications encoded in normative sections.  
    - [ ] [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) has no `Open` entries for Feature 013.  
    - [ ] The following commands have been run in this increment and logged in [docs/_current-session.md](docs/_current-session.md):  
      - `./gradlew --no-daemon spotlessApply check`  
      - `./gradlew --no-daemon qualityGate` (with documented skip flags if used).  
      - `./gradlew --no-daemon reflectionScan` (when reflection policies change).  
      - `./gradlew --no-daemon :core-ocra:spotbugsMain :core-ocra:pmdMain pmdTest` or equivalent module-specific SpotBugs/PMD runs when rules change.  

  - **Spec ↔ toolchain mapping**
    - [ ] Confirm FR-013 and NFR-013 requirements are reflected in:  
      - Static-analysis configs (`config/spotbugs/*.xml`, `config/pmd/*.xml`).  
      - [build.gradle.kts](build.gradle.kts) toolchain wiring (Spotless, Checkstyle, PMD, SpotBugs, Jacoco, PIT, reflectionScan, jacocoAggregatedReport, jacocoCoverageVerification, mutationTest, qualityGate).  
      - Gradle wrapper properties and [gradle/libs.versions.toml](gradle/libs.versions.toml) versions.  
      - Docs ([AGENTS.md](AGENTS.md), [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md), [docs/5-operations/session-quick-reference.md](docs/5-operations/session-quick-reference.md)) describing how to run/interpret the toolchain.  

  - **Quality gates & static analysis**
    - [ ] Verify `qualityGate` still depends on the expected tasks (Spotless, `check`, reflectionScan) and that `check` depends on architecture tests and aggregated Jacoco verification as documented.  
    - [ ] Ensure Jacoco thresholds and PIT settings match FR/NFR expectations (line/branch coverage and mutation score).  
    - [ ] Confirm SpotBugs/PMD are applied to intended modules with the documented filters and suppression etiquette.  

  - **CLI exit harness & maintenance tasks**
    - [ ] Check that CLI exit harnesses and Maintenance CLI coverage buffers described in the spec remain present and wired into the quality pipeline where required.  
    - [ ] Verify any helper tasks introduced by this feature (e.g., EMV trace fixture sync in `rest-api`) still align with their owning features (e.g., Feature 005) and operate as documented.  

  - **Wrapper and plugin upgrades**
    - [ ] Confirm Gradle wrapper and plugin versions match what the spec/plan describe and that upgrade steps (including warning-mode and configuration-cache validation) are still documented and repeatable.  

## MCP / Agent Integration Backlog (T-013-02/T-013-03)

Per Q013-01 (resolved 2025-11-16), Feature 013 will pursue **Option A – REST-backed MCP proxy**. The upcoming `tools/mcp-server` module will expose MCP tools (`hotp.evaluate`, `totp.evaluate`, `ocra.evaluate`, `emv.cap.evaluate`, `fido2.assertion.evaluate`, `eudiw.wallet.simulate`, `eudiw.presentation.validate`, `fixtures.list`) that forward requests to the existing `/api/v1/**` REST endpoints using the published OpenAPI contracts. The server stays stateless (base URL/API key/timeout read from `mcp-config.yaml`), depends only on the JDK HTTP client plus our REST/OpenAPI snapshots, and participates in `qualityGate` via `:tools-mcp-server:test` + Spotless/check tasks once implemented. No additional Native Java seams or persistence wiring are introduced for MCP; any future expansion must continue to reuse the REST facade unless a new specification says otherwise.

### MCP proxy design snapshot (2025-11-16)
- **Module layout**
  - New Gradle module `tools/mcp-server` (Java 17 application) that depends on `rest-api`’s OpenAPI snapshots and `docs/3-reference/rest-openapi.{json,yaml}` for schema validation only; runtime calls the deployed `rest-api` service over HTTPS using `java.net.http.HttpClient` (no additional libraries).
  - Packaging mirrors other tooling modules (`application`, `cli`) with `:tools-mcp-server:spotlessApply`, `:tools-mcp-server:test`, and `:tools-mcp-server:check` wired into `qualityGate` once the module exists.

- **Tool catalogue**
  - HOTP/TOTP/OCRA/FIDO2/EMV/EUDIW evaluator tools map 1:1 with existing REST endpoints; each MCP tool declares the OpenAPI schema path used for validation (e.g., `/api/v1/hotp/evaluate#requestBody`).
  - `fixtures.list` wraps the REST fixture endpoints so agents can inspect presets before invoking evaluate/simulate operations.
  - Errors propagate as MCP failures that embed the REST RFC 7807 payload (`type`, `title`, `status`, `detail`, `instance`). Success responses are streamed verbatim.

- **Configuration & auth**
  - `mcp-config.yaml` (resolved from `$XDG_CONFIG_HOME/openauth-sim/`) provides: `baseUrl`, optional `apiKey` header, per-tool timeout overrides, and default TLS verification flags (default on; self-signed certs require explicit opt-out noted in docs).
  - Server remains stateless; no credential caching or persistence beyond in-memory HTTP session state. Operators can run it locally alongside `./gradlew --no-daemon :rest-api:bootRun` or point at a deployed REST instance.

- **Telemetry & logging**
  - MCP server logs each tool invocation (tool name, REST path, status code, duration) to stdout using the existing toolchain logging format. Detailed protocol telemetry continues to flow through REST since the proxy forwards requests unmodified.
  - Any MCP-specific metrics (for example, tool latency histograms) will be documented in Feature 013 once implementation proceeds, but no new telemetry adapters are required because REST already emits the canonical events.

- **Quality hooks**
  - Design requires adding `:tools-mcp-server:test` (unit tests covering request/response marshalling, problem-details propagation, config parsing) and including it in `qualityGate` and `spotlessApply` once the module exists.
  - Contract coverage: add light tests that replay representative OpenAPI samples to ensure tool schemas stay aligned with the REST contracts (reusing the published JSON/YAML snapshots).

- **Security considerations**
  - No credentials are stored; MCP server supports optional API key header injection (documented in the future configuration guide) and requires TLS by default.
  - Because the server never touches persistence directly, it inherits REST’s validation and rate limiting; future enhancements (e.g., per-tool concurrency limits) can be layered on top without touching simulator internals.

- **Next increments**
- T-013-04 (new) will create the `tools/mcp-server` module, add the Spotless/Test wiring into `qualityGate`, and document operator usage in README/AGENTS (see tasks checklist). Target command suite: `./gradlew --no-daemon :tools-mcp-server:test :rest-api:test :tools-mcp-server:spotlessApply qualityGate` plus `./gradlew --no-daemon spotlessApply check`.
- T-013-03 remains the discoverability follow-up (JSON-LD/Schema.org metadata) once the MCP server ships, now explicitly scoped to **Option A – a Gradle-backed generator** that produces both inline Markdown snippets (README/ReadMe.LLM/docs) and a future hosted JSON-LD bundle so metadata stays in sync across publishing surfaces (decision recorded 2025-11-16).
- **Feedback loop (Option A, 2025-11-16):** run the structured MCP tool feedback playbook—exercise every tool against the local REST facade, capture transcripts/config pain points, and log the findings under this plan/tasks before expanding the tool catalog or Native Java seams.

### MCP tool feedback run – 2025-11-16
- **Method:** Started the REST facade (`./gradlew --no-daemon --init-script [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) runRestApi`) and executed the install-dist binary (`[tools/mcp-server/build/install/tools-mcp-server/bin/tools-mcp-server](tools/mcp-server/build/install/tools-mcp-server/bin/tools-mcp-server) --config ~/.config/openauth-sim/mcp-config.yaml`). A small Python harness emitted MCP JSON-RPC frames (minified JSON, Content-Length framing) covering every tool and stored the transcripts in [build/mcp-feedback.json](build/mcp-feedback.json) for reference.
- **Fixtures discovery (`fixtures.list`):**
  - `hotp`, `totp`, `emv`, and `fido2` returned the expected preset catalogues (ids/labels mirror the REST/UI dropdowns).  
  - `ocra` currently reports an empty array because persistence seeds only exist in CLI automation; note for future backlog that we either need stored presets or clearer inline guidance.
- **Evaluation tools:**
  - `hotp.evaluate` succeeded (`HTTP 200`) using `credentialId=ui-hotp-demo`, returning OTP `755224` and the preview delta block.  
  - `totp.evaluate` returned `HTTP 422 (otp_out_of_window)` when invoked with a dummy OTP/timestamp, confirming the REST error metadata (timestamp drift + digits) travels through MCP unchanged. This highlights the need for helper tooling or inline OTP hints when operating the tool interactively.  
  - `ocra.evaluate` succeeded using the inline sample from `use-ocra-rest-operations.md`, yielding OTP `17477202`.  
  - `emv.cap.evaluate` produced OTP `42511495` with the mask/trace payload intact; long field names survived Content-Length framing without truncation.  
  - `fido2.assertion.evaluate` returned `status=generated` and the full assertion envelope when targeting the `packed-es256` preset.
- **EUDIW flows:**  
  - `eudiw.wallet.simulate` and `eudiw.presentation.validate` both returned `status=SUCCESS` using the `pid-haip-baseline` presets, including Trusted Authority matches and telemetry IDs (`oid4vp.wallet.responded`, `oid4vp.response.validated`). Response bodies easily exceeded 4 KB without impacting framing.
- **Pain points / follow-ups:**  
  - Payload arguments must be passed as raw JSON strings (`payload` property). The client harness enforces minified JSON to satisfy the server’s strict parser; document this requirement for future tooling.  
  - Lack of stored OCRA presets means the MCP path is inline-only today; consider seeding CLI-loaded credentials when Feature 003 revisits persistence.  
  - TOTP and other verification flows would benefit from helper commands (for example, fetching the current OTP from the preset) so agents don’t guess timestamps. Capture these as separate backlog entries once we prioritise additional tooling.

### MCP helper command backlog – Option A selected (2025-11-16)
- **Decision:** Adopt Option A from Q013-05 – build a helper command that returns the current OTP/diagnostics for a preset (starting with `totp.evaluate`) so MCP clients can fetch a valid code before invoking the main evaluation tool.
- **Intent & scope:**
  - Surface a lightweight REST endpoint (and associated CLI/MCP tool) that exposes the latest OTP, drift window, and timestamp metadata for a selected credential (`ui-totp-demo`, etc.).
  - Reuse the existing `TotpEvaluationApplicationService` to compute the OTP, but tag the response as “helper” so telemetry distinguishes these lookups from full evaluations.
  - Extend the MCP registry with a `totp.helper.currentOtp` tool that returns a payload compatible with interactive agents; document JSON samples in README/AGENTS + [build/mcp-feedback.json](build/mcp-feedback.json) once tests land.
- **Constraints/risks:** Ensure helper endpoints redact secrets (shared key never leaves the simulator) and enforce the same Trusted Authority policies as the main flows when applicable. Tests must cover success path plus “unknown preset” validation errors before wiring the MCP tool.
- **Verification expectations:**
  - Stage failing tests in `:application` and `:rest-api` that assert helper responses, telemetry events, and RFC7807 errors.
  - Add MCP contract tests (and a feedback transcript update) proving the new tool returns a valid OTP + timestamp bundle.
  - Re-run `./gradlew --no-daemon generateJsonLd check` and `./gradlew --no-daemon qualityGate` after implementation, logging both in `_current-session.md`.
- **Implementation status (2025-11-16):** `/api/v1/totp/helper/current`, `TotpCurrentOtpHelperService`, README/AGENTS guidance, OpenAPI snapshots, MCP registry updates, and the new transcript entry shipped via T-013-07; telemetry frames now emit under `totp.helper.lookup`.

### JSON-LD / Schema.org generator outline (T-013-03)
- **Inputs & scope:**  
  - Source metadata drawn from existing documentation artefacts: README (surface overview + consumption paths), [ReadMe.LLM](ReadMe.LLM) (LLM entry points), [llms.txt](llms.txt) (high-signal docs), Feature 010 protocol references, and the MCP proxy docs added in README/AGENTS.  
  - Entities to describe: simulator protocols (HOTP, TOTP, OCRA, EMV/CAP, FIDO2/WebAuthn, EUDIW OpenID4VP), tooling modules (`tools/mcp-server`, CLI/REST/UI facades), documentation surfaces (operator guides, how-to guides), and distribution metadata (Gradle coordinates placeholder until Maven publishing lands). Schema types likely include `SoftwareApplication`, `HowTo`, and `TechArticle`; the generator must be able to emit multiple types per run.
- **Gradle task design:**  
  - Add a lightweight `:docs:generateJsonLd` (working name) task that ingests a structured YAML/JSON source file describing the entities, then writes two outputs:  
    1. Inline snippets (one per Markdown target) saved under ``docs/3-reference/json-ld`/*.jsonld` for manual embedding in README/ReadMe.LLM/docs.  
    2. A consolidated bundle (for future hosting) under [build/json-ld/openauth-sim.json](build/json-ld/openauth-sim.json).  
  - Task should have no external dependencies—pure Jackson/JDK JSON—so it fits within existing build constraints.
- **Workflow expectations:**  
  - Document how to embed the generated snippets into README/ReadMe.LLM (for example, include instructions in [docs/5-operations/session-quick-reference.md](docs/5-operations/session-quick-reference.md) or README).  
  - Require `./gradlew --no-daemon spotlessApply check` after updating snippets, and add `:docs:generateJsonLd` invocation + outputs to `_current-session.md` when run.  
  - Keep automation optional for now (no CI step), but capture follow-up to integrate into qualityGate once the hosted-site plan exists.
- **Telemetry / governance:**  
  - No runtime telemetry changes; this is documentation tooling only.  
  - Feature 010 remains the spec owner for how the metadata is consumed; Feature 013 tracks the generator implementation and verification commands.
  - **Implementation status (2025-11-16):** Metadata source + snippets live under ``docs/3-reference/json-ld`/`, the Gradle `generateJsonLd` task publishes both inline snippets and [build/json-ld/openauth-sim.json](build/json-ld/openauth-sim.json), and README / session quick reference now describe how to run it. Verification commands: `./gradlew --no-daemon generateJsonLd`, `./gradlew --no-daemon spotlessApply check`.
  - **Automation status (2025-11-16 – T-013-06):** `generateJsonLd` now participates in `check`, `qualityGate`, and all Spotless tasks, with a write-if-changed guard that prevents needless rewrites and surfaces snippet drift during CI. README and the session quick reference both note that the generator runs automatically while remaining manually invokable for quick previews. Verification commands: `./gradlew --no-daemon generateJsonLd check`, `./gradlew --no-daemon qualityGate`.
    - Ensure any wrapper or plugin upgrades are logged in [docs/_current-session.md](docs/_current-session.md) with commands and outcomes.  

  - **Drift capture & remediation**
    - [ ] Any high-/medium-impact drift (e.g., missing or outdated qualityGate components, reflectionScan not enforced as documented, coverage thresholds silently reduced) is:  
      - Logged as an `Open` entry in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) for Feature 013.  
      - Captured as explicit tasks in [docs/4-architecture/features/013/tasks.md](docs/4-architecture/features/013/tasks.md).  
    - [ ] Low-impact drift (typos, stale command snippets, minor option mismatches) is corrected directly, with a short note added in this section or the plan’s verification log.  

  - **Gate output**
    - [ ] This section is updated with the date of the latest drift gate run, listing key commands executed and a concise “matches vs gaps” summary plus remediation notes.  
    - [ ] [docs/_current-session.md](docs/_current-session.md) logs that the Feature 013 Implementation Drift Gate was executed (date, commands, and reference to this plan section).  

### Drift Report – 2025-11-13
- **Scope review:** Spec/plan/tasks document CLI exit harness coverage, Maintenance CLI buffers, reflection ban, Java 17 refactors, architecture harmonization, SpotBugs/PMD enforcement, Gradle upgrades, and legacy entry removal without migration framing. Roadmap + knowledge map cite Feature 013 for toolchain ownership.
- **Command alignment:** Required verification commands (`spotlessApply check`, `qualityGate`, `reflectionScan`, `spotbugsMain`, `pmdMain pmdTest`, Gradle warning sweeps) are listed with logging expectations. Wrapper upgrade steps remain captured.
- **Audit logging:** `_current-session.md` tracks the latest spotless/qualityGate executions (2025-11-13) along with earlier hook guard entries; plan/tasks reference the same commands.
- **Verification commands:** `./gradlew --no-daemon spotlessApply check` (11 s, 96 tasks: 2 executed, 94 up-to-date) and `./gradlew --no-daemon qualityGate` (9 s, 40 tasks: 1 executed, 39 up-to-date) recorded for this drift report.

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-013-01 | CLI exit harness + Maintenance coverage buffer documented with commands. | P3-I1 |
| S-013-02 | Reflection policy + sealed hierarchies captured with verification steps. | P3-I1 |
| S-013-03 | Architecture harmonization + Java 17 refactors described with ArchUnit/qualityGate requirements. | P3-I1 |
| S-013-04 | SpotBugs/PMD enforcement + suppression etiquette recorded. | P3-I1 |
| S-013-05 | Gradle wrapper/plugin upgrade steps captured with warning-mode + configuration-cache commands. | P3-I1 |
| S-013-06 | Legacy entry-point removal documented; telemetry/router references updated. | P3-I1 |
| S-013-07 | Roadmap/knowledge map/session log ([docs/_current-session.md](docs/_current-session.md))/session logs reflect toolchain ownership. | P3-I4 |
| S-013-08 | Cross-facade contract tests enforce parity across implemented facades. | I013-CF-* |

## Analysis Gate
After P3-I4 completes (cross-doc sync + logging), run [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md), capturing the
commands executed and any outstanding TODOs in `_current-session.md` and this plan.

- 2025-11-11 – Analysis gate rerun after roadmap/knowledge map/architecture graph/session log ([docs/_current-session.md](docs/_current-session.md)) updates referencing Feature 013.
  - Spec/plan/tasks alignment confirmed; outstanding backlog items remain tracked under Follow-ups.
  - Logged commands: `git config core.hooksPath`, `tmp_index=$(mktemp); GIT_INDEX_FILE=$tmp_index git read-tree --empty; GIT_INDEX_FILE=$tmp_index ./githooks/pre-commit` (dry-run), `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon qualityGate`.
- `_current-session.md` documents the outcomes plus the initial malformed `spotlessApply check,workdir:` command that was rerun without the stray argument.

## Follow-ups / Backlog
- Rerun the queued Phase 2 verification commands (`./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon qualityGate`) once Features 009–013 freeze, then log timings + outcomes in `_current-session.md` and the session log ([docs/_current-session.md](docs/_current-session.md)).
- Track Maintenance CLI coverage buffer restoration (≥0.90) and Jacoco hotspot updates.
- Monitor PMD Law-of-Demeter whitelist health + backlog of NonExhaustiveSwitch remediation.
- Capture Gradle wrapper/plugin upgrade cadence for future releases.
- Consider automation to ensure `legacyEmit`/router shims never reappear (simple `rg` guard in quality pipeline).
- Add a lightweight fixture-sync helper for EMV/CAP trace provenance: Gradle tasks in `rest-api` to verify and sync `trace-provenance-example.json` between `docs/` and ``rest-api/docs`/` (see Feature 005 T-005-19..T-005-23, T-005-67 for context).
- Implement cross-facade contract tests initiative (see Active Increment below) and record qualityGate runtime deltas per NFR-013-01.

## Active Increment – EMV/CAP provenance fixture sync

1. **I013-EMV-1 – EMV trace fixture sync tasks (delivered 2025-11-15)**  
   - Introduced dedicated typed Gradle tasks (`VerifyEmvTraceProvenanceFixture`, `SyncEmvTraceProvenanceFixture`) inside the `rest-api` module so the EMV/CAP trace provenance fixture stays mirrored between `docs/` and ``rest-api/docs`/`.  
   - Tasks:  
     - `:rest-api:verifyEmvTraceProvenanceFixture` – checks that [docs/test-vectors/emv-cap/trace-provenance-example.json](docs/test-vectors/emv-cap/trace-provenance-example.json) and [rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json](rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json) remain byte-identical and produces a targeted remediation hint.  
     - `:rest-api:syncEmvTraceProvenanceFixture` – copies the canonical fixture from `docs/` into ``rest-api/docs`/`, overwriting the module copy to restore parity.  
   - Integration: `verifyEmvTraceProvenanceFixture` now sits on every `Test` task plus the `check` lifecycle goal so both `:rest-api:test` and `:rest-api:check` catch fixture drift automatically while remaining configuration-cache friendly.  
   - Verification commands (2025-11-15):  
     - `./gradlew --no-daemon :rest-api:verifyEmvTraceProvenanceFixture` (PASS – configuration cache stored)  
     - `./gradlew --no-daemon :rest-api:syncEmvTraceProvenanceFixture` (PASS – logged copy path + configuration cache stored)  
     - `./gradlew --no-daemon :rest-api:test` (PASS – verify task executed before tests)  
     - `./gradlew --no-daemon spotlessApply check` (PASS – workspace-wide quality gate after build-logic updates)  
   - Feature linkage: keeps the dual-fixture requirement documented in Feature 005 (T-005-19..T-005-23, T-005-67) enforceable via tooling instead of manual copy steps.

## Active Increment – Cross-facade contract tests

> Goal: define canonical scenarios per protocol and enforce parity across Native Java (`application`), CLI, REST, operator UI, and standalone facades. MCP parity is explicitly deferred until Feature 015 closes its drift gate.

1. **I013-CF-1 – Scenario matrix + normalised contract (delivered 2025-12-12)**
   - Catalogue canonical scenarios per protocol by mapping FR/Scenario IDs in Features 001–006 to an explicit matrix (success/validation/failure branches).
   - Define a test-only normalised result record (`CanonicalFacadeResult`) capturing: `success`, `reasonCode`, core payload fields (OTP/counter/epochSeconds/etc), `includeTrace` presence, and `telemetryId`.
   - Define a deterministic `ScenarioEnvironment` (fixed `Clock`, seeded in-memory `CredentialStore`, preset IDs) to guarantee stable parity runs across facades.

2. **I013-CF-2 – Shared scenarios + harness scaffolding (delivered 2025-12-12)**
   - Host shared scenario definitions and harness utilities in `application` test fixtures (`java-test-fixtures`), reusing existing `core` fixtures.
   - Add factories that load scenarios from `docs/test-vectors/**` and `data/` without duplicating vectors.
   - Add unit tests ensuring scenario definitions match core fixture expectations.

3. **I013-CF-3 – HOTP/TOTP/OCRA parity suites (delivered 2025-12-12)**
   - Implement facade harnesses:
     - Native Java: direct `*EvaluationApplicationService` calls.
     - REST: in-process Spring Boot tests (`RANDOM_PORT`) + normalisation.
     - CLI: in-process Picocli execution with `--output-json` parsing.
     - Standalone: validate CLI/REST parity via the same harnesses (no separate seam unless new behaviour appears).
   - Add parameterised cross-facade contract tests for HOTP/TOTP/OCRA (evaluate + replay + one failure branch each).
   - Resolve any parity gaps via spec updates first, then facade convergence.

4. **I013-CF-4 – FIDO2/EMV/CAP/EUDIW + UI parity (delivered 2025-12-12)**
   - Extend harnesses and scenarios to FIDO2/WebAuthn, EMV/CAP, and EUDIW OpenID4VP.
   - Add minimal UI parity checks (1–2 canonical scenarios per protocol) reusing existing Selenium flows; tag tests to keep default build fast.

5. **I013-CF-5 – Quality gate wiring + docs (delivered 2025-12-12)**
   - Wire contract tests into `check` and `qualityGate` via JUnit tags (e.g., `@Tag("crossFacadeContract")`) and document commands in this plan/tasks and Feature 013 spec.
   - Added `:rest-api:crossFacadeContractTest` to run tagged suites directly.
   - Record runtime deltas in `_current-session.md` per NFR-013-01.
   - Remove `docs/tmp/2-cross-facade-contract-tests-plan.md` once this increment set ships and all content is preserved in specs/plan/tasks.

## Active Increment – Gradle quality conventions

> Goal: extract shared subproject quality wiring (Checkstyle/PMD/SpotBugs/ErrorProne/Jacoco/PIT defaults, dependency locking,
> resolution strategy pinning, and JUnit/Test defaults) out of the root build file into a reusable convention script while
> keeping all task semantics unchanged.

1. **I013-GQC-1 – Convention script extraction (delivered 2025-12-12)**
   - Move the root `subprojects { ... }` quality configuration block into `gradle/quality-conventions.gradle` and keep
     `gradle/quality-conventions.gradle.kts` as a thin wrapper (Kotlin applied scripts cannot compile against third-party
     Gradle plugin types without build-logic classpath wiring).
   - Keep global orchestration tasks in the root build (`architectureTest`, aggregated Jacoco, `mutationTest`,
     `reflectionScan`, `qualityGate`) and keep their wiring unchanged.

2. **I013-GQC-2 – Root wiring refactor (delivered 2025-12-12)**
   - Replace the inlined root `subprojects` quality block with `subprojects { apply(from = …) }` (wrapping into the
     Groovy convention script via `gradle/quality-conventions.gradle.kts`).
   - Acceptance criteria: `./gradlew --no-daemon spotlessApply check qualityGate reflectionScan` stays green without changes
     to task names, required task dependencies, or emitted reports.

3. **I013-GQC-3 – Audit trail + temp plan cleanup (delivered 2025-12-12)**
   - Record the verification commands in [docs/_current-session.md](docs/_current-session.md) (FR-013-10).
   - Delete `docs/tmp/4-gradle-quality-conventions-plan.md` after the governing Feature 013 artefacts fully encode the work.
