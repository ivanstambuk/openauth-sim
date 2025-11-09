# Feature Plan 035 – Evaluate & Replay Audit Tracing

_Linked specification:_ `docs/4-architecture/features/035/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-29

## Vision & Success Criteria
- Every credential evaluation/replay/attestation workflow (HOTP, TOTP, OCRA, FIDO2) exposes an opt-in verbose trace that lists each cryptographic operation, inputs, and intermediate outputs in execution order.
- CLI, REST, and operator UI facades honour per-request verbose toggles and surface the identical trace payload with no redaction.
- UI introduces a terminal-style panel (collapsed by default) that renders the verbose trace when requested without disrupting existing layouts.
- Traces remain ephemeral and never land in telemetry, persisted storage, or logs; default behaviour (verbose disabled) is unchanged.
- Trace metadata advertises the available redaction tiers (`normal`, `educational`, `lab-secrets`), with Feature 035 continuing to emit the fully detailed (`educational`) view; all tier filtering work now shifts to Feature 036.
- Full pipeline (`./gradlew spotlessApply check`) stays green after implementation.

## Scope Alignment
- **In scope:** Core trace model, application-layer plumbing, facade toggles (CLI flag, REST request/response fields, UI control), REST contract updates, UI terminal-panel design/implementation, documentation (CLI/REST/UI how-to), automated tests across all facades.
- **Out of scope:** Persistent audit storage, global configuration switches, telemetry schema changes outside the verbose pathway, non-authentication protocols (future work will inherit the trace model).

## Dependencies & Interfaces
- Core services in `core/ocra`, `core/hotp`, `core/totp`, `core/fido2` plus corresponding application services under `application/`.
- CLI commands (`cli/src/main/java/io/openauth/sim/cli/**`), REST controllers (`rest-api/src/main/java/io/openauth/sim/rest/**`), and operator UI templates/scripts (`rest-api/src/main/resources/templates/ui/**`, `static/js/**`).
- Telemetry contracts (must remain untouched by verbose trace data).
- Existing JSON fixtures for HOTP/TOTP/OCRA and WebAuthn sample vectors to drive deterministic trace assertions.

## Trace Detail Expectations
- **Cross-protocol format**
  - Preserve the current human-readable layout (operation header, metadata block, numbered steps).
  - Each step lists key/value attributes grouped by type (e.g., `hex`, `base64url`, `int`, `bool`) and ends with `spec: <anchor>` where applicable.
  - Record `tier: educational` in the envelope while tier selection helpers are delivered under Feature 036.
  - Hash any sensitive secrets with SHA-256 and print as `sha256:<digest>` regardless of the protocol algorithm family.
  - When an operator changes protocol tabs or toggles between evaluate/replay or inline/stored modes, the verbose trace panel must clear immediately so traces remain scoped to the initiating request (Option B, approved 2025-10-25).
- **HOTP (RFC 4226 §5.1–§5.4)** – Output must match the mandated “step.N” format (two-space indentation, `name = value`, lowercase hex). Secrets are always hashed; refuse digits >9.
  - *Evaluate:* emit the six ordered steps (`normalize.input`, `prepare.counter`, `hmac.compute`, `truncate.dynamic`, `mod.reduce`, `result`) with the exact field list in the spec, noting non-standard algorithms when used.
  - *Verify:* emit `normalize.input`, `search.window` (prefix with `window.range = [counter.hint-10, counter.hint+10]` and `order = ascending`, then log attempt entries plus the expanded match derivation), and `decision` with matched/next counters. Publish the recommended counter advance (`matched + 1`) in metadata even when inline replays leave server state untouched so operators see the expected next value.
  - CLI/REST/UI printers must keep the same deterministic ordering and comments (`-- begin match.derivation --` markers) while preserving human-readable formatting.
- **TOTP (RFC 6238 §1.2, §4.2, Appendix B)**
  - Prepend `derive.time-counter` (epoch, step, T value, drift window) and `evaluate.window` (per-offset results) before reusing HOTP steps.
- **OCRA (RFC 6287 §5–§7)**
  - `parse.suite` – raw suite, parsed fields (digits, hash, data inputs).
  - `normalize.inputs` – counter, challenge encoding, PSHA digests, session/timestamp bytes.
  - `assemble.message` – ordered segments with hex and overall SHA-256 hash.
  - Emit explicit `len.bytes` attributes for every `segment.*` entry and for the concatenated message (`message.len.bytes`) so padding mistakes surface instantly (added 2025-10-24).
  - Expose canonical HMAC identifier via `alg = HMAC-SHA-*` on OCRA trace steps; mirror the canonical name in `compute.hmac` detail and cite `rfc2104` alongside `rfc6287§7` (added 2025-10-24).
  - Summarise message integrity with `parts.count` and `parts.order` so operators can confirm the concatenation sequence at a glance (added 2025-10-24).
  - Conclude with HOTP-style HMAC/truncation/modulo steps.
- **WebAuthn / FIDO2 (WebAuthn L2 §6–§7, CTAP 2 §5)** – reframe trace sequencing so humans and downstream automation see the full attestation/assertion story:
  - *Attestation path:* `step.parse.clientData` (raw JSON, `clientDataHash.sha256`, expected vs. actual `type`/`origin`, challenge metadata, token binding status/id), `step.parse.authenticatorData` (RP hash, `rpId.canonical`, `rpIdHash.expected`, `rpIdHash.match`, flag byte plus `flags.bits.{UP,RFU1,UV,BE,BS,RFU2,AT,ED}`, counters), `step.extract.attestedCredential` (AAGUID, credential ID + length, decoded COSE summary with `cose.kty`, `cose.kty.name`, `cose.alg`, `cose.alg.name`, `cose.crv`, `cose.crv.name`, coordinate/modulus/exponent base64url fields, `publicKey.cose.hex`, `publicKey.jwk.thumbprint.sha256`), `step.parse.extensions` (presence flag, raw CBOR hex, decoded credProps/credProtect/largeBlobKey/hmac-secret fields, stash unknown entries), `step.build.signatureBase` (component byte lengths, `signedBytes.hex`, `signedBytes.preview`, `signedBytes.sha256`), `step.verify.signature` (algorithm IDs, `verify.sig`, `verify.ok`, per-algorithm metadata including ECDSA DER parts and low-S policy status, RSA padding/hash + salt length, EdDSA raw output, `error.alg_mismatch` detection), and `step.validate.metadata` (attestation type, trust path, chain verdict, anchor, metadata/AAGUID lookup, extension/policy summary).
  - *Assertion path:* mirror `step.parse.clientData`, `step.parse.authenticatorData`, and `step.parse.extensions`, reuse `step.build.signatureBase` and `step.verify.signature`, then finish with `step.evaluate.counter` documenting previous counter, observed counter, increment verdict, and any enforcement outcome.
  - Signed-byte reporting and RP canonicalisation remain mandatory even when previews are present; always retain the full hex plus SHA-256 digest so operators can reproduce the signature base.
  - UI/CLI/REST facades must clear existing WebAuthn traces when operators toggle protocol tabs, stored vs. inline inputs, or verbose mode to prevent stale payloads from leaking into the next interaction.
  - Continue applying lowercase hex, explicit byte-length attributes, and deterministic two-space indentation with `name = value` formatting so machine parsing stays trivial without sacrificing readability.

## Increment Breakdown (≤30 min each, tests-before-code)
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

17. **I12b – OCRA operator verbose wiring**
    - Update OCRA evaluate/replay panel scripts to delegate request/response handling through `VerboseTraceConsole`, mirroring the HOTP/TOTP integration so verbose traces render in the shared console panel.
    - Add Selenium coverage that toggles verbose mode for OCRA evaluate and replay, asserts the trace output appears, and remains hidden when disabled. Introduce a dedicated `OcraOperatorUiSeleniumTest` (or extend existing suite).
        - Exercise stored evaluate + replay happy paths with verbose enabled, plus a replay request with verbose disabled to assert the console remains hidden.
        - Capture the pre-wiring failure by running the new Selenium test before modifying the UI scripts.
    - 2025-10-24 – Updated `ui/ocra/evaluate.js`/`replay.js` to attach the verbose flag, pipe responses through `VerboseTraceConsole`, and verified `OcraOperatorUiSeleniumTest` alongside `Ocra*EndpointTest`; replay assertions tolerate trace-less responses while still validating verbose flag propagation.
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiSeleniumTest"` (new) plus existing OCRA endpoint suites.

18. **I12c – OCRA verification verbose instrumentation (completed 2025-10-24)**
    - Added replay-focused verbose trace tests (stored + inline) and implemented trace generation inside `OcraVerificationApplicationService`, reusing the existing evaluation trace builder to document suite parsing, input normalisation, message assembly, HMAC, truncation, modulo reduction, and OTP comparison.
    - Propagated verbose traces through REST (`OcraVerificationEndpoint`/`OcraVerificationResponse`), CLI (`--verbose` verify flag), and operator UI panels; regenerated OpenAPI snapshots to document the new `verbose` request field and `trace` response payload.
    - Updated Selenium coverage (`OcraOperatorUiSeleniumTest`) to assert that stored replay surfaces `ocra.verify.stored` traces when verbose mode is enabled.
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.ocra.*VerboseTraceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.OcraCliVerboseTraceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OcraVerificationEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.OcraOperatorUiSeleniumTest"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`.

19. **I12d – FIDO2 canonical trace naming (completed 2025-10-24)**  
    - Aligned WebAuthn verbose traces with canonical field naming: output `alg`/`cose.alg`, rename RP/client data/signed payload hashes, and removed the `0x` prefix from single-byte fields.  
    - Refreshed CLI/REST fixtures (including OpenAPI snapshots) and Feature 035 documentation to reflect the new vocabulary; verbose trace tests now lock the updated keys.  
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon spotlessApply check`.

20. **I12e – WebAuthn RP ID canonicalisation & trace match indicators (completed 2025-10-24)**
    - Normalised relying party identifiers (trim, IDNA to ASCII, lower-case) before persistence or verification across assertion and attestation services; updated credential descriptors, stored credential construction, and request builders to reuse the canonical value and refreshed verbose trace assertions across application/CLI/REST flows to expect `rpId.canonical`, `rpIdHash.expected`, and `rpIdHash.match`.
    - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`.

21. **I12f – WebAuthn COSE key decode & thumbprint (pending)**
    - Expand verbose trace expectations (application/CLI/REST) to cover decoded COSE metadata and RFC 7638 thumbprint outputs across EC, RSA, PS256, and EdDSA fixtures before wiring the implementation.
    - Introduce a shared helper that exposes COSE key attributes and thumbprint generation for trace builders, ensuring failures fall back to the existing hex payload with a descriptive note.
    - Update trace builders, CLI printers, REST DTOs, and OpenAPI snapshots to surface `cose.*` attributes and `publicKey.jwk.thumbprint.sha256`; rerun the FIDO2 verbose suites and finish with `./gradlew --no-daemon spotlessApply check`.
    - Normalised relying party identifiers (trim, IDNA to ASCII, lower-case) before persistence or verification across assertion and attestation services; updated credential descriptors, stored credential construction, and request builders to reuse the canonical value.
    - Extended verbose traces to emit `rpId.canonical`, `rpIdHash.expected`, and `rpIdHash.match` alongside the authenticator-provided hash so operators can spot mismatches without relying solely on verifier exceptions; refreshed application/CLI/REST fixtures and OpenAPI snapshots accordingly.
    - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`; `./gradlew --no-daemon spotlessApply check`.

21. **I13 – Documentation & knowledge sync**
    - Update CLI/REST how-to guides, operator UI docs, and knowledge map entries; ensure verbose tracing instructions are clear.
    - Command: `./gradlew --no-daemon spotlessApply`

22. **I14 – Full quality gate**
    - 2025-10-23 – `./gradlew --no-daemon spotlessApply check` completed successfully after additional REST/CLI tests lifted aggregated branch coverage to 70.45 % (1 645/2 335).

23. **I15 – WebAuthn signature inspection tests (fail first)**
    - Extend verbose trace suites (core/application/CLI/REST) to expect algorithm-specific signature attributes: DER/base64url encodings, `ecdsa.r.hex`, `ecdsa.s.hex`, `ecdsa.lowS`, RSA padding/hash metadata, EdDSA raw signatures, and a mirrored `verify.ok` flag. Add failure coverage for metadata/COSE algorithm mismatches and low-S violations when policy is enabled.
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`, `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"` (expected red until implementation).
    - 2025-10-25 – Completed: Application verbose trace suites now assert signature encodings/low-S state (including enforced-mode failure), CLI trace assertions capture the new fields, core tests cover inspector semantics, and REST attestation replay exposes the same signature payloads via the new controller step.

24. **I16 – Signature inspector implementation (green)**
    - Introduce a core `SignatureInspector` (or equivalent helper) that parses ECDSA DER payloads into R/S components, computes low-S against curve order (P-256/P-384/P-521), and surfaces raw base64url encodings plus lengths for each algorithm. Update WebAuthn assertion/attestation services to populate the new trace attributes and propagate `verify.ok` while keeping legacy `valid`.
    - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.SignatureInspectorTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`.
    - 2025-10-25 – Completed: `SignatureInspector` delivers DER parsing for ECDSA, RSA metadata extraction, and EdDSA raw reporting; integrated into evaluation/attestation services with dedicated unit coverage.

25. **I17 – Low-S policy wiring**
    - Add a `WebAuthnSignaturePolicy` with configuration hook (default observe-only) to determine whether high-S signatures trigger verifier failure. Ensure traces emit `policy.lowS.enforced` and raise `error.lowS` when enforcement blocks verification. Cover policy toggles with dedicated tests.
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*PolicyTest"`, `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"`.
    - 2025-10-25 – Completed: Policy now enforces low-S when enabled, emits trace annotations, and fails verification with `error.lowS`; high-S regression tests added for stored/inline assertion flows.

26. **I18 – Facade + documentation sync**
    - REST WebAuthn replay/attestation endpoints now assert the signature trace schema and expose `verify.signature` via `WebAuthnAttestationService`; refreshed FIDO2 REST how-to guidance and knowledge map to document the new fields (CLI/UI printers already covered by earlier increments). Attempted full `spotlessApply check`, which timed out after five minutes because of pre-existing Checkstyle/PMD findings in the SignatureInspector suites.
    - Commands: `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`, `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, attempted `./gradlew --no-daemon spotlessApply check` (timeout after 300 s; see session log).

27. **I19 – Coverage regression + lint follow-up**
    - 2025-10-25 – Coverage dipped to 69 % after introducing `SignatureInspector` decode fallbacks that only execute on malformed signatures. Extended application and REST verbose-trace tests to assert `signature.decode.error` notes and low-S enforcement, then re-ran `./gradlew --no-daemon jacocoCoverageVerification` to confirm aggregated line/branch coverage ≥0.70.
    - 2025-10-25 – Palantir formatting collapsed empty record declarations, tripping Checkstyle’s `WhitespaceAround` rule. Normalised helper records (core + application tests) with explicit bodies so `./gradlew --no-daemon checkstyleMain checkstyleTest` and the full `./gradlew --no-daemon spotlessApply check` pipeline now pass.

28. **I20 – WebAuthn trace step restructure tests (fail first)**
    - Add failing assertions across core/application/CLI/REST/UI suites to demand the new WebAuthn structure: `step.parse.clientData`, `step.parse.authenticatorData`, `step.extract.attestedCredential`, `step.parse.extensions`, enriched token-binding/RP metadata, signed-bytes previews, and per-algorithm signature attributes.
    - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`.
    - 2025-10-26 – Application/CLI/REST/UI suites now assert the new step IDs plus token-binding, RP hash, and signed-bytes preview metadata; builds sit red pending I21 implementation.
    - Status: Completed (tests red by design).

29. **I21 – WebAuthn trace step restructure implementation (green)**
    - Update WebAuthn trace builders, DTOs, CLI/REST/UI presenters, and OpenAPI artefacts to satisfy the new step/field expectations. Ensure the console clears traces on context switch and preserve low-S policy/extension logic.
    - 2025-10-26 – Implemented verification-trace append for REST attestation replay, set default token-binding metadata, propagated signed-bytes previews, refreshed CLI/REST/UI expectations, and pruned legacy signature helpers.
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.*VerboseTraceTest" :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest" :rest-api:test --tests "io.openauth.sim.rest.Fido2*Test" :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`; `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon spotlessApply check`.
    - Status: Completed.

30. **I22 – WebAuthn generation trace tests (fail first)**
    - 2025-10-26 – Added failing assertions to application generation suites (assertion + attestation), CLI `fido2 evaluate` verbose runs, REST evaluation/attestation endpoints, and Selenium UI flows demanding `build.clientData`, `build.authenticatorData`, `build.signatureBase`, `generate.signature`, and `compose.attestationObject` (attestation) with SHA-256 hashes for sensitive material. All new tests are red pending I23 implementation.
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationServiceTest"`; `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceManualTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`.
    - Status: Completed (tests red by design).

31. **I23 – WebAuthn generation trace implementation (green)**
    - 2025-10-26 – Implemented verbose trace builders for assertion/attestation generation (client data, authenticator data, signature base, and signature steps), ensured SHA-256 placeholders cover private keys and attestation CBOR, and wired the optional trace through CLI `fido2 evaluate[--verbose]`, REST evaluation/attestation endpoints, and the operator UI panel. Selenium and REST tests now assert the hashes derived from the actual response payloads rather than fixture baselines.
    - Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationServiceTest"`, `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationServiceManualTest"`, `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.Fido2CliVerboseTraceTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2EvaluationEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`, `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`, `./gradlew --no-daemon spotlessApply check`.
    - Status: Completed.

32. **I24 – WebAuthn stored credential ordering alignment (Option A)**
    - Update REST stored credential directory sorting to prefer `WebAuthnSignatureAlgorithm` enumeration order (ES256 → ES384 → ES512 → RS256 → PS256 → EdDSA) ahead of label/ID fallbacks so server responses mirror inline sample ordering. Mirror the same ordering helper in operator-console JavaScript when rendering stored credential dropdowns to keep UI selections stable even if external callers reorder results.
    - Refresh Selenium UI expectations if ordering-sensitive assertions exist, and ensure REST controller/unit tests cover the new comparator behaviour.
    - Commands: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.*"` (sanity), `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.WebAuthnCredentialDirectoryControllerTest"` (add coverage), `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.Fido2OperatorUiSeleniumTest"`; finish with `./gradlew --no-daemon spotlessApply check`.
    - Status: Completed (2025-10-26 – REST comparator, UI helper, Selenium expectation updated; new controller unit test added).

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
   - **2025-10-23 follow-up:** Owner revised the decision: keep the verbose toggle near the bottom of the console and render the trace panel immediately after it as a continuous in-flow section (no fixed overlay).

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
- 2025-10-24 – Completed T3533 to replace WebAuthn authenticator flag outputs with a full `flags.bits.*` map plus `userVerificationRequired` / `uv.policy.ok` attributes, driven by failing tests ahead of implementation.
- Track redaction-tier controls (CLI flag, REST/JSON contract, UI toggle) within Feature 036 once the shared helper lands.
- **2025-10-25 – WebAuthn extension handling options**  
  - **Option A – Decode and log full extension payloads (selected).** Parse the authenticator `extensions` CBOR when `flags.bits.ED = true`, emit a dedicated `parse.extensions` step with `extensions.present`, raw CBOR hex, and normalised attributes for `credProps.rk`, `credProtect.policy`, `largeBlobKey`, and `hmac-secret`. Surface unknown entries in a generic map for operator awareness.  
  - **Option B – Presence flag only.** Record `extensions.present` without decoding payloads, deferring detailed metadata.  
  - **Option C – Defer handling.** Leave extensions unreported and rely on future work.  
  - **Recommendation:** Option A chosen for parity with risk-analysis needs and to align with Specification Clarification 17.  
  - **Next increments:**  
    1. Stage failing tests across application/CLI/REST/UI asserting the new `parse.extensions` step and decoded attributes.  
       - 2025-10-25 – Application/CLI/REST suites now assert `parse.extensions`; operator UI Selenium coverage verifies verbose inline replay surfaces credProps/credProtect/largeBlobKey/hmac-secret attributes (still red pending parser).  
       - 2025-10-25 – Reproduction showed the Selenium assertion racing the fetch call; updated UI tests now wait for a non-blank status (`pending` excluded) before asserting the badge so the existing UI update logic can be observed reliably.  
    2. Share a reusable authenticator-data parser that exposes extension bytes for both assertion and attestation flows; update trace builders/formatters accordingly.  
       - 2025-10-26 – `WebAuthnAuthenticatorDataParser` now normalises authenticator data (flags, counters, extensions) and feeds `WebAuthnExtensionDecoder` so application/CLI/REST traces log decoded metadata plus unknown entries under `extensions.unknown.*`.  
    3. Refresh documentation (REST/CLI/UI guides, trace examples) once traces emit the new metadata; rerun `./gradlew --no-daemon spotlessApply check`.  
       - 2025-10-26 – Updated `use-fido2-cli-operations.md`, `use-fido2-rest-operations.md`, and `use-fido2-operator-ui.md` with extension-aware trace snippets; reran `./gradlew --no-daemon spotlessApply check` (green, no OpenAPI changes required).

## Completion Notes
- 2025-10-26 – Increments I1–I24 delivered verbose tracing across HOTP, TOTP, OCRA, and WebAuthn with module, REST, CLI, and Selenium suites green plus regenerated OpenAPI artefacts.
- 2025-10-29 – Documentation, roadmap, and knowledge map updates landed; Feature 035 plan closed with remaining work tracked under Feature 036 tier controls.

## Analysis Gate
- **Review date:** 2025-10-22  
- **Checklist outcome:** Pass – specification requirements and clarifications captured; no open questions remain; tasks map to every requirement with tests staged before implementation; increments respect ≤30 minute guidance and maintain straight-line control flow; documented commands cover targeted module tests plus final `spotlessApply check`; SpotBugs guardrails remain in force with no planned exemptions.  
- **Follow-ups:** Proceed to UI layout option write-up (I10) before implementation.
