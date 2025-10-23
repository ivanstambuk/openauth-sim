# Feature Plan 035 – Evaluate & Replay Audit Tracing

_Linked specification:_ `docs/4-architecture/specs/feature-035-evaluate-replay-audit-tracing.md`  
_Status:_ Draft  
_Last updated:_ 2025-10-22

## Vision & Success Criteria
- Every credential evaluation/replay/attestation workflow (HOTP, TOTP, OCRA, FIDO2) exposes an opt-in verbose trace that lists each cryptographic operation, inputs, and intermediate outputs in execution order.
- CLI, REST, and operator UI facades honour per-request verbose toggles and surface the identical trace payload with no redaction.
- UI introduces a terminal-style panel (collapsed by default) that renders the verbose trace when requested without disrupting existing layouts.
- Traces remain ephemeral and never land in telemetry, persisted storage, or logs; default behaviour (verbose disabled) is unchanged.
- Full pipeline (`./gradlew spotlessApply check`) stays green after implementation.

## Scope Alignment
- **In scope:** Core trace model, application-layer plumbing, facade toggles (CLI flag, REST request/response fields, UI control), REST contract updates, UI terminal-panel design/implementation, documentation (CLI/REST/UI how-to), automated tests across all facades.
- **Out of scope:** Persistent audit storage, global configuration switches, telemetry schema changes outside the verbose pathway, non-authentication protocols (future work will inherit the trace model).

## Dependencies & Interfaces
- Core services in `core/ocra`, `core/hotp`, `core/totp`, `core/fido2` plus corresponding application services under `application/`.
- CLI commands (`cli/src/main/java/io/openauth/sim/cli/**`), REST controllers (`rest-api/src/main/java/io/openauth/sim/rest/**`), and operator UI templates/scripts (`rest-api/src/main/resources/templates/ui/**`, `static/js/**`).
- Telemetry contracts (must remain untouched by verbose trace data).
- Existing JSON fixtures for HOTP/TOTP/OCRA and WebAuthn sample vectors to drive deterministic trace assertions.

## Increment Breakdown (≤10 min each, tests-before-code)
1. **I1 – Baseline evaluation map**
   - Catalogue injection points for HOTP/TOTP/OCRA/FIDO2 evaluate/replay/attest services; note where traces should attach.
   - Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*EvaluationApplicationServiceTest"`

2. **I2 – Trace model spec tests (fail first)**
   - Author unit tests describing the desired trace structure (ordered steps, labels, payload typing) under `core`.
   - Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.TraceModelTest"` (expected to fail until implementation).

3. **I3 – Implement trace model**
   - Introduce immutable trace data structures and builders that satisfy I2 tests; ensure extensibility for future protocols.
   - Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.TraceModelTest"`
   - 2025-10-22: `VerboseTrace` (with nested `TraceStep` builder) implemented; test suite now green.

4. **I4 – Application verbose plumbing tests (fail first)**
   - Extend application-layer tests per protocol to expect populated traces when verbose is requested and absence otherwise.
   - Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTest"` (failing until wiring exists).
   - 2025-10-22: Added HOTP stored/inline verbose trace expectations (now passing after HOTP wiring).
   - 2025-10-22: Enabled TOTP, OCRA, WebAuthn assertion, and WebAuthn attestation verbose trace tests covering success and failure branches.

5. **I5 – Application verbose plumbing implementation**
   - Wire verbose flags through application services, populating trace steps from core helpers; keep default paths unchanged.
   - Command: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTest"`
   - 2025-10-22: HOTP evaluation now emits verbose traces (stored + inline) with passing tests; remaining protocols pending.
   - 2025-10-22: TOTP, OCRA, WebAuthn assertion evaluation, and WebAuthn attestation now emit verbose traces with deterministic step coverage.

6. **I6 – CLI verbose flag tests (fail first)**
   - Add Picocli tests ensuring `--verbose` emits trace output after standard result text, covering all relevant commands.
   - Command: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"` (initially red).

7. **I7 – CLI verbose implementation**
   - Implement flag parsing and trace rendering with clear section delimiters; keep exit codes untouched.
   - Command: `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`

8. **I8a – REST contract tests (TOTP slice, fail first)**
   - Extend TOTP evaluation/replay controller tests to accept a verbose boolean and assert the response `trace` payload structure (success + validation/error paths).
   - Command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Totp*VerboseTraceTest"` (expected red).
   - 2025-10-22 – Owner selected Option B for phased delivery: TOTP REST coverage lands first to harden the pattern before expanding to other protocols.
   - 2025-10-22 – Completed: added `TotpEvaluationEndpointTest`/`TotpReplayEndpointTest` coverage before wiring implementation.

9. **I8b – REST contract tests (HOTP slice, fail first)**
   - Clone the TOTP MockMvc coverage for HOTP stored/inline evaluate + replay, asserting trace payload presence for success and validation errors.
   - Command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.hotp.*"` (initially red on new expectations).
   - 2025-10-22 – Added stored HOTP verbose trace assertions across evaluation/replay tests to drive implementation.

10. **I9b – REST verbose implementation & schema sync (HOTP slice)**
   - Wire verbose boolean through HOTP DTOs/controllers/services, emit `VerboseTracePayload` for success/error responses, and refresh OpenAPI snapshots.
   - Commands: `./gradlew :rest-api:test --tests "io.openauth.sim.rest.hotp.*"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.
   - 2025-10-22 – HOTP evaluate/replay endpoints now mirror the TOTP trace contract; OpenAPI YAML/JSON updated.



9. **I9a – REST implementation & schema sync (TOTP slice)**
   - Propagate verbose flag through TOTP controllers/services, serialise trace payloads, refresh OpenAPI snapshots, and update targeted docs/tests.
   - Command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Totp*VerboseTraceTest"`
   - Execute immediately after I8a so the build returns to green before starting the next protocol slice.
   - 2025-10-22 – Completed: TOTP evaluate/replay REST surfaces `trace` payloads, OpenAPI snapshots regenerated, full `:rest-api:test` green.

10. **I8b – REST contract tests (HOTP slice, fail first)**
    - 2025-10-22 – Added HOTP stored/replay verbose trace expectations (`HotpEvaluationEndpointTest`/`HotpReplayEndpointTest`) covering success + validation/error flows.
    - Command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.hotp.*"` (initially red before implementation).

11. **I9b – REST implementation & schema sync (HOTP slice)**
    - 2025-10-22 – Propagated verbose flags through HOTP DTOs/controllers/services, surfaced `VerboseTracePayload` in success and error responses, and refreshed `docs/3-reference/rest-openapi.(json|yaml)` via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.
    - Command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.hotp.*"` (now green).

12. **I8c/I9c – OCRA slice**
    - 2025-10-23 – Regenerated OpenAPI artefacts after documenting `VerboseTracePayload`; OCRA evaluate endpoint tests now assert `trace.operation` for stored and inline requests and pass with the refreshed schema (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Ocra*EndpointTest"`).

13. **I8d/I9d – WebAuthn slice**
    - 2025-10-23 – Revalidated WebAuthn evaluation, replay, and attestation endpoints with verbose mode enabled, refreshed snapshots via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, and confirmed the endpoint suites stay green (`./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*EndpointTest"`).

14. **I10 – UI layout proposals**
    - Produce two UI layout prototypes (e.g., bottom dock vs side drawer) documented within this plan under “UI Layout Decision”; include pros/cons and recommended option for owner approval.
    - Command: `n/a` (documentation change); attach mock references or describe CSS hooks.

15. **I11 – UI verbose toggle tests (fail first)**
    - Extend Selenium suite to request verbose mode and expect trace payload rendered in the approved layout; confirm absence when disabled.
    - Command: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*VerboseTraceSeleniumTest"` (initially red).

16. **I12 – UI implementation**
    - Implement toggle, REST integration, and terminal-style panel with scroll/copy affordances adhering to the chosen layout.
    - Command: targeted Selenium suites from I11 + `./gradlew --no-daemon :rest-api:test`

17. **I13 – Documentation & knowledge sync**
    - Update CLI/REST how-to guides, operator UI docs, and knowledge map entries; ensure verbose tracing instructions are clear.
    - Command: `./gradlew --no-daemon spotlessApply`

18. **I14 – Full quality gate**
    - 2025-10-23 – `./gradlew --no-daemon spotlessApply check` completed successfully after additional REST/CLI tests lifted aggregated branch coverage to 70.45 % (1 645/2 335).

## UI Layout Decision

1. Trace panel placement (2025-10-22)

   - **Option A – Bottom dock terminal panel**
     - Pros: Mirrors common console layout; utilises full page width for long trace lines; minimal horizontal scroll; pairs naturally with existing result card; easy to collapse into a fixed-height region without shifting form fields sideways; can animate slide-up to reinforce “terminal output” metaphor.
     - Cons: Reduces vertical space for the form/result card when expanded; may require sticky positioning to avoid pushing content below the fold on smaller screens; needs responsive breakpoints to cap height on short viewports.

   - **Option B – Side drawer terminal panel**
     - Pros: Preserves vertical form space; allows simultaneous view of the entire form and trace; drawer can scale independently for tablet/desktop layouts; easier to pin in place for multi-column comparisons.
     - Cons: Constrains trace width leading to wrapped lines for long buffers/JSON; competes with existing right-column help content; requires more intrusive responsive logic (e.g., collapsing on narrow screens); may feel less “terminal-like” compared to a bottom dock.

   - **Recommended choice:** Option A – Bottom dock terminal panel, because it keeps trace content legible across protocols that emit long base64/hex strings and aligns with operator expectations for console-style output. Side drawer remains viable if preserving vertical form space proves critical.

   - **Owner decision:** Option A approved (2025-10-22).

## Evaluation Injection Map (T3501 – 2025-10-22)
- **HOTP evaluate (`HotpEvaluationApplicationService`)** – Stored path loads `StoredCredential` then calls `HotpGenerator.generate`, persists the new counter via `persistCounter`, and emits telemetry; inline path normalises metadata and validates counter before generating OTP. Verbose trace hook should wrap descriptor resolution, generator invocation, counter bump, and persistence operations.
- **HOTP replay (`HotpReplayApplicationService`)** – Stored path resolves descriptor/counter and delegates to `HotpValidator.verify`; inline path constructs an inline descriptor with `HotpDescriptor.create`. Trace steps should capture descriptor construction, validator input (counter, OTP), match result, and mismatch reason mapping.
- **TOTP evaluate (`TotpEvaluationApplicationService`)** – Stored path deserialises via `TotpCredentialPersistenceAdapter`, optionally generates OTP (`TotpGenerator.generate`) or verifies (`TotpValidator.verify`); inline path builds descriptor with `TotpDescriptor.create`. Trace coverage must include descriptor hydration, generator inputs (algorithm/digits/step/instant), validator drift calculations, and matched skew steps.
- **TOTP replay (`TotpReplayApplicationService`)** – Delegates to evaluation service (`TotpEvaluationApplicationService`) with pre-populated commands; tracing should reuse evaluation hooks while noting replay-specific metadata (stored vs inline, candidate OTP).
- **OCRA evaluate (`OcraEvaluationApplicationService`)** – Normalises command, resolves descriptor (stored via `CredentialResolver` or inline via `OcraCredentialFactory`), validates challenge/session/timestamp, builds `OcraExecutionContext`, and calls `OcraResponseCalculator.generate`. Trace should document request normalisation, each validation (including hex checks), context assembly, and calculator output.
- **WebAuthn assertion evaluate (`WebAuthnEvaluationApplicationService`)** – Stored path deserialises `WebAuthnCredentialDescriptor` then calls `WebAuthnAssertionVerifier.verify`; inline path builds a `WebAuthnStoredCredential` before invoking the verifier. Trace needs to surface credential resolution, COSE parsing outcomes, verifier inputs (client/authenticator data, challenge, counters), and verification result/error mapping.
- **WebAuthn assertion replay (`WebAuthnReplayApplicationService`)** – Wraps evaluation service with command adapters; adopt evaluation trace output while appending replay metadata (source, payload cloning).
- **WebAuthn attestation verify/replay (`WebAuthnAttestationVerificationApplicationService`, `WebAuthnAttestationReplayApplicationService`)** – `WebAuthnAttestationVerificationApplicationService` delegates to `WebAuthnAttestationServiceSupport.process`, which orchestrates trust-anchor selection, attestation-object parsing, and credential extraction. Replay service reuses verification results while layering supplemental fields. Trace should capture format selection, trust anchor resolution, attestation parsing/verification steps, credential extraction, and outcome status.
- **Baseline check** – `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*EvaluationApplicationServiceTest"` executed 2025-10-22 to lock current behaviour prior to verbose instrumentation.
## Risks & Mitigations
- **Risk:** Verbose tracing leaks into telemetry/logs by accident.  
  **Mitigation:** Add regression assertions ensuring telemetry payloads remain unchanged; document guardrails in code comments.

- **Risk:** Trace generation significantly slows evaluation.  
  **Mitigation:** Keep trace construction lazy/conditional; benchmark via targeted tests before rollout.

- **Risk:** UI panel disrupts existing layout.  
  **Mitigation:** Provide multiple layout proposals, validate with Selenium visual assertions, and keep panel collapsed by default.

## Follow-ups
- Revisit potential trace exporters (files/web sockets) under a future feature if persistent audit history is requested.
- Consider formatting helpers (diff highlighting, grouping) once baseline tracing stabilises.

## Analysis Gate
- **Review date:** 2025-10-22  
- **Checklist outcome:** Pass – specification requirements and clarifications captured; no open questions remain; tasks map to every requirement with tests staged before implementation; increments respect ≤10 minute guidance and maintain straight-line control flow; documented commands cover targeted module tests plus final `spotlessApply check`; SpotBugs guardrails remain in force with no planned exemptions.  
- **Follow-ups:** Proceed to UI layout option write-up (I10) before implementation.
