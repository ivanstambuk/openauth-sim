# Feature 013 Tasks – Toolchain & Quality Platform

_Status:_ In review  
_Last updated:_ 2025-12-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-013-01 – EMV trace fixture sync automation (FR-013-05, S-013-04): add Gradle tasks in the `rest-api` module to verify and sync the EMV/CAP trace provenance fixture between the canonical docs path ([docs/test-vectors/emv-cap/trace-provenance-example.json](docs/test-vectors/emv-cap/trace-provenance-example.json)) and the REST module path ([rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json](rest-api/docs/test-vectors/emv-cap/trace-provenance-example.json)), wiring the verification task into the `:rest-api:test` / `:rest-api:check` pipeline.
  _Intent:_ Replace the manual dual-storage guardrail for `trace-provenance-example.json` with a small, reproducible automation hook so drift is detected and corrected via Gradle tasks instead of ad-hoc copy steps.
  _Verification commands:_
  - `./gradlew --no-daemon :rest-api:verifyEmvTraceProvenanceFixture`
  - `./gradlew --no-daemon :rest-api:syncEmvTraceProvenanceFixture`
  - `./gradlew --no-daemon :rest-api:test`

- [x] T-013-02 – Design MCP-based agent integration from existing OpenAPI specs (backlog – Option C, part 1) (FR-013-01/04, FR-010-13).  
  _Intent:_ Deliver the Option A design: specify the `tools/mcp-server` Gradle module, define the initial tool catalog (HOTP/TOTP/OCRA/EMV/FIDO2/EUDIW evaluate/simulate/validate flows plus fixture listing), document configuration (`mcp-config.yaml` base URL/API key/timeout), and add verification notes (reuse `:rest-api:test`, add `:tools-mcp-server:test`, Spotless) without implementing code yet.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check` (2025-11-16 – design snapshot recorded in Feature 013 plan/tasks)  
  _Notes:_ Plan now includes the module layout, tool catalogue, configuration, telemetry, security, and quality-hook expectations for `tools/mcp-server`; implementation task to follow once scheduled.

- [x] T-013-03 – Plan JSON-LD / Schema.org metadata generator (Option A decision, 2025-11-16) (FR-010-12/13).  
  _Intent:_ Design the Gradle-backed generator that emits both inline Markdown snippets (README, [ReadMe.LLM](ReadMe.LLM), docs) and a standalone JSON-LD bundle for future hosted docs so metadata stays consistent across surfaces, covering the required schema types (protocol simulators, modules, MCP proxy, Maven coordinates) without changing runtime behaviour yet.  
  _Verification commands:_  
  - `./gradlew --no-daemon generateJsonLd`  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ Implemented `GenerateJsonLdTask` in [build.gradle.kts](build.gradle.kts), created [docs/3-reference/json-ld/metadata.json](docs/3-reference/json-ld/metadata.json), refreshed README + session quick reference with usage instructions, and committed the generated snippets under ``docs/3-reference/json-ld/snippets`/*.jsonld`. The task now writes inline snippets plus [build/json-ld/openauth-sim.json](build/json-ld/openauth-sim.json) for hosted docs.

- [x] T-013-04 – Implement `tools/mcp-server` module and operator guidance (FR-013-01/04, FR-010-13, FR-011-02).  
  _Intent:_ Create the REST-backed MCP proxy module per the plan: add `tools/mcp-server` with Spotless/test wiring, implement the MCP tool handlers that forward to `/api/v1/**`, add `mcp-config.yaml` template + README/AGENTS run instructions, and ensure `qualityGate` runs `:tools/mcp-server:test` and `:tools/mcp-server:spotlessApply`.  
  _Verification commands:_  
  - `./gradlew --no-daemon :tools-mcp-server:test :rest-api:test` (covered via `qualityGate`)  
  - `./gradlew --no-daemon :tools-mcp-server:spotlessApply` (included in `qualityGate`)  
  - `./gradlew --no-daemon qualityGate` (automatically executes new module tests)  
  - `./gradlew --no-daemon spotlessApply check` (2025-11-16 – post-implementation verification)  
  _Notes:_ Added `tools/mcp-server` Gradle module, content-length framed MCP server, JSON utility, tool registry, config loader, and README/AGENTS run instructions. Tests cover config parsing, tool definitions, framer, JSON utilities, and end-to-end server plumbing via stubbed HTTP responses.

- [x] T-013-05 – MCP proxy feedback runbook (Option A, 2025-11-16) (FR-013-01/04, FR-010-13).  
  _Intent:_ Exercise every MCP tool against the local REST facade, capture transcripts plus configuration ergonomics, and log the findings under this plan/tasks so future catalog work is evidence-backed.  
  _Verification commands:_  
  - `./gradlew --no-daemon --init-script [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) runRestApi` (background for REST facade)  
  - `./gradlew --no-daemon :tools-mcp-server:installDist`  
  - `python - <<'PY' …` harness (minified JSON + Content-Length framing captured in [build/mcp-feedback.json](build/mcp-feedback.json); snippet recorded in the plan)  
  _Notes:_ All tools responded via MCP; key observations recorded in the plan (fixtures coverage, HOTP success, TOTP `otp_out_of_window` failure path, OCRA inline success, EMV/FIDO2/EUDIW parity). `fixtures.list` for OCRA currently returns `[]`, reinforcing the need for stored presets if persistence is enabled. The harness’ minified JSON requirement surfaced the server’s strict parser and is now documented in the MCP plan.

- [x] T-013-06 – Add `generateJsonLd` to `check`/`qualityGate` (Option A, 2025-11-16) (FR-010-12/13).  
  _Intent:_ Integrate the JSON-LD generator into `check`/`qualityGate` with an up-to-date guard so snippet drift fails CI without rewriting unchanged files; update README/session quick reference once automation is in place.  
  _Verification commands:_  
  - `./gradlew --no-daemon generateJsonLd check` (PASS – 45 s, triggered Spotless + Checkstyle across all modules with the new dependency wiring)  
  - `./gradlew --no-daemon qualityGate` (PASS – 19 s, confirmed `generateJsonLd` + reflectionScan + Spotless wiring execute in the gate)  
  _Notes:_ `GenerateJsonLdTask` now writes snippets/bundle only when content actually changes, and every Spotless task plus `check`/`qualityGate` depends on it. README and the session quick reference call out the automation while keeping the manual command documented for quick previews.

- [x] T-013-07 – Add `totp.helper.currentOtp` MCP tool + REST helper endpoint (Option A, 2025-11-16) (FR-010-13, FR-013-01, FR-002-04).  
  _Intent:_ Implement the TOTP preset helper endpoint + MCP wiring that returns the current OTP, timestamp, and drift metadata so agents can fetch valid payloads before calling `totp.evaluate`.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test --tests "*TotpCurrentOtpHelperServiceTest"`  
  - `./gradlew --no-daemon :rest-api:test --tests "*TotpHelperEndpointTest"`  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*RestToolRegistryTest"`  
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`  
  - `./gradlew --no-daemon generateJsonLd check` (PASS – ≥600 s timeout)  
  - `./gradlew --no-daemon qualityGate` (PASS – 6m42s, full suite)  
  _Notes:_ Added `TotpCurrentOtpHelperService`, the `/api/v1/totp/helper/current` endpoint, OpenAPI snapshots, README/AGENTS/MCP feedback updates, and the `totp.helper.currentOtp` tool in `RestToolRegistry`. Helper telemetry now flows under `totp.helper.lookup` with sanitized payloads so assistants can fetch valid codes before evaluations.

- [x] T-013-L1 – Legacy Coverage (Features 010/011/012/013/014/015 CLI harness, maintenance buffers, reflection policy).  
  _Intent:_ Ensure FR-013-01..06 and NFR-013-02/03 memorialize the CLI exit harness, maintenance CLI coverage buffer, reflection ban, and wrapper guidance migrated from the legacy features.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test --tests "*OcraCliLauncherTest"` (captured during the 2025-11-11 qualityGate run)  
  - `./gradlew --no-daemon qualityGate` (latest pass 2025-11-16)  
  - `./gradlew --no-daemon reflectionScan` (2025-11-16 – revalidated the reflection ban for the drift gate)  
  _Notes:_ `_current-session.md` logs both the historical 2025-11-11 CLI harness evidence and the 2025-11-16 reflectionScan rerun executed while closing the drift gate.

- [x] T-013-L2 – Legacy Coverage (Features 029/030/031 toolchain automation + quality gates).  
  _Intent:_ Confirm FR-013-07..09 and NFR-013-01/04/05 represent the PMD/SpotBugs orchestration, configuration-cache requirements, and CI parity inherited from these features.  
  _Verification commands:_  
  - `./gradlew --no-daemon pmdMain pmdTest` (covered via 2025-11-16 `qualityGate`)  
  - `./gradlew --no-daemon :core-ocra:spotbugsMain :core-ocra:pmdMain :core-ocra:pmdTest` (2025-11-16)  
  - `./gradlew --no-daemon qualityGate` (2025-11-16)  
  _Notes:_ `_current-session.md` captures the legacy 2025-11-11 command outputs and the 2025-11-16 module sweeps used to finalize the drift gate.

- [x] T-013-08 – Cross-facade scenario matrix + normalised contract (FR-013-11, S-013-08).  
  _Intent:_ Extract canonical scenarios per protocol from Features 001–006, define the shared scenario matrix (success/validation/failure), and introduce the test-only `CanonicalFacadeResult`/`ScenarioEnvironment` contracts.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test --tests "*CanonicalScenario*"` (once scaffolding lands)  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-09 – Shared scenarios + harness scaffolding (FR-013-11).  
  _Intent:_ Add `java-test-fixtures` to `application` and host canonical scenario factories plus facade harness utilities (Native Java/REST/CLI/standalone) with deterministic store/clock seeding.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-10 – HOTP/TOTP/OCRA cross-facade parity suites (FR-013-11).  
  _Intent:_ Implement parameterised contract tests that run the canonical scenarios through Native Java, REST, and CLI and assert parity for evaluate/replay + one failure branch each; resolve any discrepancies via spec updates first.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test --tests "*CrossFacade*"`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-11 – Extend parity to FIDO2/EMV/CAP/EUDIW + UI smoke parity (FR-013-11).  
  _Intent:_ Extend harnesses/scenarios to FIDO2/WebAuthn, EMV/CAP, and EUDIW OpenID4VP, and add tagged Selenium/JS smoke parity checks (1–2 scenarios per protocol) reusing existing flows.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test --tests "*CrossFacade*"`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-12 – Wire parity suites into `qualityGate` + remove temp sketch (FR-013-11).  
  _Intent:_ Add JUnit tags/Gradle wiring so cross-facade contract tests run under `check`/`qualityGate`, document commands/runtime deltas in plan/spec, and delete `docs/tmp/2-cross-facade-contract-tests-plan.md` once fully migrated.  
  _Verification commands:_  
  - `./gradlew --no-daemon qualityGate`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-13 – REST OpenAPI runtime schema validation (FR-013-12).  
  _Intent:_ Validate representative REST responses (success + error branches per protocol) against the `$ref`-linked OpenAPI component schemas in `docs/3-reference/rest-openapi.json` using a lightweight validator (no new deps).  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*Schema*"`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-14 – Required-field OpenAPI governance (FR-013-12).  
  _Intent:_ Mark REST response DTO fields as required (where always present) and ensure optional fields do not violate schema (omit nulls or mark nullable) so schema validation is meaningful; refresh OpenAPI snapshots via the snapshot test.  
  _Verification commands:_  
  - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"`  
  - `./gradlew --no-daemon :rest-api:test`  

- [x] T-013-15 – Schema contract Gradle entrypoints (FR-013-12).  
  _Intent:_ Add module-scoped Gradle test tasks for schema contract suites (REST/telemetry as applicable) and tag the relevant tests so developers can run them without the full module test suite.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:schemaContractTest`  
  - `./gradlew --no-daemon :application:test --tests "*Telemetry*"`  

- [x] T-013-16 – Remove temp sketch after migration (FR-013-12).  
  _Intent:_ Delete `docs/tmp/3-json-and-telemetry-governance-plan.md` once Feature 013 spec/plan/tasks fully encode its requirements and all enforcement tests are green.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-17 – Shared cross-facade contract harness utilities (FR-013-11).  
  _Intent:_ Reduce boilerplate in cross-facade parity suites by centralising scenario lookup, file-store seeding, Picocli execution, JSON envelope parsing, and per-protocol REST request builders so new parity suites are “data + assertions” rather than plumbing.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*CrossFacadeContractTest"`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-18 – Cross-facade adapter layer runner (FR-013-11).  
  _Intent:_ Refactor parity tests to use a shared runner (`CrossFacadeContractRunner`) plus per-protocol facade adapters so the suites stay compact and avoid repeating environment bootstrapping logic; keep protocol adapters within their packages to access package-private REST request DTOs.  
  _Verification commands:_  
  - `./gradlew --no-daemon :rest-api:test --tests "*CrossFacadeContractTest"`  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-19 – Gradle quality conventions extraction (NFR-013-04, FR-013-10).  
  _Intent:_ Extract the root `subprojects { ... }` quality configuration block (Checkstyle/PMD/SpotBugs/ErrorProne/Jacoco/PIT +
  dependency locking + resolution pinning + JUnit/Test defaults) into `gradle/quality-conventions.gradle` (applied via the
  wrapper `gradle/quality-conventions.gradle.kts`), then replace the inlined block with `subprojects { apply(from = …) }`
  so the root build stays compact without changing task semantics.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  - `./gradlew --no-daemon qualityGate`  
  - `./gradlew --no-daemon reflectionScan`

- [x] T-013-20 – Remove temporary Gradle plan after migration (FR-013-10).  
  _Intent:_ Delete `docs/tmp/4-gradle-quality-conventions-plan.md` once Feature 013 spec/plan/tasks encode the work and the
  quality gate is green.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-21 – Facade contract playbook (docs) (FR-013-10, FR-013-11).  
  _Intent:_ Author `docs/4-architecture/facade-contract-playbook.md` as the single cross-facade reference for how protocol
  features expose Native Java, CLI, REST, UI, MCP, and standalone seams (contracts, naming, telemetry, and enforcement).  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-22 – Facade contract playbook (spec/runbook links) (FR-013-10).  
  _Intent:_ Add minimal references from the relevant feature specs (001–006, 009, 012–015) plus governance docs (session
  quick reference / knowledge map / AGENTS) to the playbook so agents discover it during session kickoffs and facade work.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-23 – Remove temporary facade playbook plan after migration (FR-013-10).  
  _Intent:_ Delete `docs/tmp/5-facade-contract-playbook-plan.md` once Feature 013 docs encode the work and verification is
  green.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`

- [x] T-013-24 – Centralize facade boundary ArchUnit rules (FR-013-13, S-013-04).  
  _Intent:_ Add a single ArchUnit suite (`FacadeBoundariesArchitectureTest`) that defines canonical facade package
  constants plus reusable boundary rules (CLI↔REST bans, MCP↔REST bans, etc.), then refactor existing architecture tests
  to reuse the shared constants/rules.
  _Verification commands:_  
  - `./gradlew --no-daemon :core-architecture-tests:test`
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-12-13 – `./gradlew --no-daemon :core-architecture-tests:test` (PASS – facade boundary ArchUnit suite centralised)
- 2025-12-13 – `./gradlew --no-daemon spotlessApply check` (PASS – facade boundary ArchUnit suite centralised)
- 2025-12-12 – `./gradlew --no-daemon check` (PASS – cross-facade parity suites green; temp sketch removed; lockfiles refreshed)
- 2025-12-12 – `./gradlew --no-daemon :rest-api:test --tests "*CrossFacadeContractTest"` (PASS – HOTP/TOTP/OCRA/FIDO2/EMV/EUDIW parity; adapter layer runner refactor)
- 2025-12-12 – `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "*OpenApiSnapshotTest"` (PASS – refreshed REST OpenAPI snapshots after schema governance updates)
- 2025-12-12 – `./gradlew --no-daemon :rest-api:schemaContractTest` (PASS – tagged schema contract suite)
- 2025-12-12 – `./gradlew --no-daemon :rest-api:test` (PASS – schema/runtime validation and reasonCode parity checks)
- 2025-12-12 – `./gradlew --no-daemon spotlessApply check` (PASS – schema governance increment complete; temp sketch removed)
- 2025-12-12 – `./gradlew --no-daemon spotlessApply check` (PASS – extracted Gradle quality conventions into `gradle/quality-conventions.gradle` and applied via `gradle/quality-conventions.gradle.kts`)
- 2025-12-12 – `./gradlew --no-daemon qualityGate` (PASS – quality gate remains green after conventions extraction)
- 2025-12-12 – `./gradlew --no-daemon spotlessApply check` (PASS – removed `docs/tmp/4-gradle-quality-conventions-plan.md` after migration)
- 2025-12-12 – `./gradlew --no-daemon spotlessApply check` (PASS – published facade contract playbook, linked relevant specs/runbooks, and removed `docs/tmp/5-facade-contract-playbook-plan.md`)
- 2025-11-16 – `./gradlew --no-daemon spotlessApply check` (PASS – Option A MCP design captured in plan/tasks; no code shipped)
- 2025-11-15 – `./gradlew --no-daemon :rest-api:verifyEmvTraceProvenanceFixture` (PASS – typed task verifies fixture parity before every REST test/check run)
- 2025-11-15 – `./gradlew --no-daemon :rest-api:syncEmvTraceProvenanceFixture` (PASS – mirrors canonical docs fixture into `rest-api/docs` and logs copy locations)
- 2025-11-15 – `./gradlew --no-daemon :rest-api:test` (PASS – verify task executes ahead of the suite; rerun with 420 s timeout to accommodate Selenium + OpenAPI snapshots)
- 2025-11-15 – `./gradlew --no-daemon spotlessApply check` (PASS – workspace-wide formatting + verification sweep after build-logic updates)
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (toolchain drift gate; 11 s, 96 tasks: 2 executed, 94 up-to-date)
- 2025-11-13 – `./gradlew --no-daemon qualityGate` (toolchain closure gate; 9 s, 40 tasks: 1 executed, 39 up-to-date)
- 2025-11-11 – `rg "Feature 013" [docs/4-architecture/roadmap.md](docs/4-architecture/roadmap.md) [docs/4-architecture/knowledge-map.md](docs/4-architecture/knowledge-map.md)`
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`
- 2025-11-11 – `./gradlew --no-daemon qualityGate`

## Notes / TODOs
- Track Maintenance CLI coverage buffer restoration and spotbugs/pmd follow-ups once tooling work resumes.
- Capture wrapper upgrade cadence + warning-mode sweeps each time Gradle versions bump.
- Consider adding automation to block `legacyEmit` or router shim regressions.
