# Feature 039 – EMV/CAP Simulation Services

_Status: In review_  
_Last updated: 2025-11-09 (Implementation Drift Gate recorded)_

## Overview
Introduce first-class EMV Chip Authentication Program (CAP) support that mirrors the reference calculator workflows while fitting the OpenAuth Simulator architecture. Scope now covers reusable core derivation utilities, application orchestration, REST and CLI facades, operator console integration, and MapDB-backed credential seeding so every surface can evaluate Identify/Respond/Sign flows with consistent telemetry and traces. Documentation across REST, CLI, and operator UI guides captures the extended fixture set delivered in T3908c/T3909 so operators can reproduce reference flows end-to-end.


## Goals
- Deliver end-to-end EMV/CAP evaluation and replay flows across core, application, REST, CLI, and operator UI with verbose traces.
- Provide MapDB seeding, fixtures, and telemetry redaction specific to EMV/CAP credentials.

## Non-Goals
- Does not simulate APDU/hardware-level exchanges—scope is application-layer derivation only.
- Does not add issuer provisioning or account management workflows.


## Clarifications
- 2025-11-01 – CLI parity will follow existing Picocli ergonomics: positional/flag inputs with optional `--output-json` mirroring the REST payload, plus an `--include-trace` toggle (owner decision).
- 2025-11-01 – Operator console already contains an EMV/CAP tab; we will activate it with an inline result card and rely on the global verbose trace toggle shared across protocols (owner decision).
- 2025-11-01 – EMV/CAP credentials must be persisted in MapDB alongside other protocols and support deterministic seeding commands akin to HOTP/TOTP/OCRA/FIDO2 (owner decision).
- 2025-11-01 – Hardware/token-level APDU workflows remain out of scope; the simulator only needs the application-layer derivation to model `GENERATE AC` outputs and OTP masking (owner decision).
- 2025-11-02 – Operator console result pane must use the shared two-column layout: the right column exposes only the OTP preview table (Counter/Δ/OTP) and status badge, while mask length, masked digits count, ATC, branch factor, height, and other diagnostics live in the verbose trace panel (owner directive).
- 2025-11-02 – Evaluate and Replay panels must no longer surface standalone “Resolved ICC payload”, “ICC payload override”, or “Terminal data” controls; verbose traces continue to expose the resolved ICC payload, and manual terminal/ICC overrides stay limited to REST/CLI facades (owner directive).
- 2025-11-02 – Operator UI transaction sections are removed entirely; Evaluate and Replay forms expose only the ICC template alongside issuer application data, while REST/CLI retain transaction override fields for advanced scenarios (owner directive).
- 2025-11-01 – Seed regression tests with the transcripted calculator vectors; specification documents additional input tuples so the user can supply more samples for broader coverage (owner directive).
- 2025-11-02 – Verbose trace output must funnel through the shared `VerboseTraceConsole` so copy/download controls and Selenium assertions match other protocols while the EMV-specific metrics remain exposed within the trace payload (owner directive).
- 2025-11-02 – Replay flows must mirror other protocols: Evaluate and Replay actions live side by side, Replay accepts operator-entered OTPs against stored or inline EMV/CAP credentials, and verbose tracing follows the same toggle semantics (owner directive).
- 2025-11-02 – Task T3915 must deliver replay documentation updates plus Jacoco branch coverage recovery, culminating in a successful full `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` run (owner directive).
- 2025-11-02 – Verbose traces must redact EMV master keys by surfacing only SHA-256 digests (formatted `sha256:<hex>`), matching the redaction pattern already used by HOTP/TOTP/OCRA verbose traces. Session keys remain visible to support troubleshooting (owner directive).
- 2025-11-02 – Evaluate flows across all facades (Operator console, REST, CLI) must allow selecting stored EMV/CAP credentials in addition to inline parameters, mirroring HOTP/TOTP/OCRA behaviour where stored presets prefill/drive evaluation while remaining editable until cleared (owner directive).
- 2025-11-03 – Operator console Evaluate tab must surface a "Choose evaluation mode" selector mirroring other protocols: radio buttons for stored credential vs. inline parameters, a single Evaluate CTA bound to the active selection, and no duplicate preset/inline buttons (owner directive).
- 2025-11-04 – Operator console Evaluate panel must render the "Choose evaluation mode" heading and radio selector immediately below the panel title, before stored credential presets or parameter inputs, matching the HOTP/TOTP/FIDO2 ordering (owner directive).
- 2025-11-05 – Selecting a sample vector while "Inline parameters" mode is active must keep the evaluation panel in inline mode (fields stay editable); do not auto-switch to stored credential mode (owner decision).
- 2025-11-05 – Selecting a sample vector while "Inline parameters" mode is active must keep the evaluation panel in inline mode (fields stay editable); do not auto-switch to stored credential mode (owner decision).
- 2025-11-05 – Inline evaluations triggered with a selected preset must still hydrate defaults from the stored credential (submit `credentialId` so missing fields fall back to persistence) while allowing inline overrides to replace individual parameters (owner decision).
- 2025-11-08 – Checkstyle `WhitespaceAround` rule may be disabled globally to unblock EMV template/JS refactors without scattered suppressions (owner decision; configuration updated in `config/checkstyle/checkstyle.xml`).
- 2025-11-08 – Remove the inline helper copy that previously stated "Identify mode does not accept customer inputs." along with the session-derivation hints on Evaluate/Replay; rely on field legends/labels instead (owner directive).
- 2025-11-06 – Stored credential mode in the operator console must hide ICC master key, CDOL1 payload, Issuer Proprietary Bitmap, ICC payload template, and Issuer Application Data inputs entirely; operators switch to inline mode to inspect or edit those values (owner directive).
- 2025-11-03 – Preview window offsets remain mandatory on the Evaluate form and corresponding REST/CLI requests so operators can adjust the neighboring OTP previews; align EMV controls/DTOs with the HOTP/TOTP/OCRA window schema (owner directive).
- 2025-11-08 – Remove the redundant helper sentence beneath the "Preview window offsets" controls on EMV/CAP panels so the heading mirrors the other protocols (owner directive).
- 2025-11-08 – Remove the evaluate-tab helper text "Inline evaluation uses the parameters entered above…"; mode toggles and preset selectors already convey the stored vs. inline workflow (owner directive).
- 2025-11-08 – Replay action bar spacing must match the Evaluate tab and other protocol panels by reusing the shared `stack-offset-top-lg` utility so the Provided OTP inputs never butt against the CTA (owner directive – codified as requirement R5.8).
- 2025-11-03 – Verbose trace payloads must surface ATC, branch factor, height, and mask length metadata alongside the masked digits overlay so operator diagnostics match other protocols (owner directive).
- 2025-11-04 – Operator console Replay mode toggle must list Inline parameters before Stored credential and surface succinct helper copy beside each option (“Manual replay with full CAP derivation inputs.” / “Replay a seeded preset without advancing ATC.”), mirroring other protocols without wrapping onto a second line (owner directive).
- 2025-11-04 – Evaluate and Replay dropdown labelling must match other protocols (“Load a sample vector” + “Select a sample”) while retaining EMV/CAP hinting about canonical parameters and ATC preservation (owner directive).
- 2025-11-04 – Evaluate and Replay sample vector controls must apply the shared `stack-offset-top-lg` spacing utility so their layout matches the HOTP/TOTP/FIDO2 panels (owner directive).
- 2025-11-04 – Evaluate sample vector and seeding controls must share the same block/spacing treatment as Replay: the preset selector sits directly under the mode toggle with no additional vertical gap, and the seed action/button lives within that block so spacing mirrors the Replay layout (owner directive).
- 2025-11-04 – Evaluate and Replay sample vector selectors must reuse the shared inline preset styling (label stacked above a full-width dark inline preset dropdown with seed actions and hints beneath) so the EMV panel matches HOTP/TOTP/FIDO2 ergonomics (owner directive).
- 2025-11-05 – Stored credential summaries supplied to any facade must never include raw ICC master keys, CDOL1 payloads, issuer proprietary bitmaps, ICC templates, or issuer application data. REST responses expose only SHA-256 digests and length metadata; operator UI panels render masked placeholders while keeping the underlying secret material server-side (owner directive).
- 2025-11-06 – Operator console Evaluate and Replay “Input from customer” section must group Challenge/Reference/Amount inputs beneath the mode radios, keeping fields visible at all times but only enabling Challenge for Respond and Reference/Amount for Sign. Identify mode leaves all three disabled (owner directive).
- 2025-11-08 – Input from customer fieldsets now present each mode radio and its related inputs on the same horizontal row (Identify shows disabled placeholders, Respond lines the Challenge input beside the radio, Sign lines Reference and Amount beside the radio). The fields remain a single shared set that stays mounted while toggling only `disabled` states; CSS handles the grid alignment (owner directive).
- 2025-11-06 – Card configuration section should present CDOL1 and IPB inputs in a stacked card configuration block with full-width text areas under their labels (owner directive).
- 2025-11-06 – Inline preset hydration must populate every overridable field (master key, CDOL1, IPB, ICC template, IAD, and mode-specific customer inputs) when a preset is selected so operators can see defaults before editing (owner directive).
- 2025-11-06 – Card configuration must include a sub-group labelled “Transaction” that stacks ICC payload template and Issuer Application Data with helper copy indicating “xxxx” is replaced by the ATC (owner directive).
- 2025-11-08 – Card configuration is an independent fieldset containing only the CDOL1 payload and Issuer Proprietary Bitmap inputs. The Transaction and Input from customer fieldsets must remain siblings (never nested inside Card configuration) so borders/legends stay distinct for each block (owner directive – captured as requirement R5.6).
- 2025-11-08 – Session key derivation fieldsets must present Branch factor (b) and Height (H) on the same horizontal row for both Evaluate and Replay panels so operators can compare the tree parameters side-by-side even when stored presets hide other secrets (owner directive).
- 2025-11-08 – ICC master key and Application Transaction Counter inputs must stretch edge-to-edge across their row inside the Session key derivation block (matching the other field widths) so no horizontal gutter remains between the fields (owner directive).

## Requirements

### R1 – Core EMV/CAP domain
1. Add a `core.emv.cap` package encapsulating:
   - Session key derivation per EMV 4.1 Book 2, Part III, Annex A1.3 using ICC master key, ATC, branch factor `b`, height `H`, and IV parameters.
   - CAP mode abstractions that validate input tuples for Identify (no challenge/reference), Respond (challenge), and Sign (challenge + reference + amount) flows.
   - Bitmask application driven by Issuer Proprietary Bitmap (IPB) and CDOL1 descriptors to extract digits from the Generate AC response.
2. Models must treat all external inputs as uppercase hexadecimal strings and emit detailed validation errors (length, parity, non-hex characters).
3. Provide deterministic functions returning:
   - derived session key (hex),
   - generated application cryptogram (`generateAc` result without tag-length),
   - masked numeric OTP (string preserving leading zeros, 8–12 digits depending on IPB),
   - metadata detailing which nibbles contributed to the OTP.
4. Share existing crypto primitives (e.g., 3DES MAC) where possible; do not introduce external dependencies without explicit approval.
5. Record redaction guidance so callers can scrub master keys (and other sensitive material) when emitting telemetry outside verbose traces.

### R2 – Application orchestration & telemetry
1. Introduce `EmvCapEvaluationApplicationService` (and supporting request/response records) that wrap the core domain utilities and enforce:
   - optional transaction overrides (terminal/ICC via REST/CLI only) and Issuer Application Data (IAD),
   - substitution of ATC into ICC payload segments marked `xxxx`,
   - mode-specific input validation and error reporting.
2. Emit telemetry via `TelemetryContracts`:
   - Event names `emv.cap.identify`, `emv.cap.respond`, `emv.cap.sign`.
   - Frames include sanitized metadata (mode, ATC, ipbMaskLength, maskedDigitsCount) without master/session keys or raw cryptograms.
3. Provide toggleable verbose trace assembly containing full derivation details and masked-digit overlays for downstream facades while redacting master keys (expose digest + byte length only) and leaving session keys visible.
4. Enrich the verbose trace content so every request surfaces the following provenance (single verbosity level shared across facades):
   - **Protocol context** – CAP profile/mode, EMV reference (default 4.3 Book 3 unless overridden), AC/CID type, and issuer policy identifiers so auditors can understand which variant generated the OTP.
   - **Key derivation transparency** – IMK family label (IMK-AC/SMI/SMC, etc.), derivation algorithm identifier, masked PAN/PSN (BIN + last digits plus `sha256:` digest), ATC, IV, master-key digest, and the resulting session key value per R2.3 visibility rules.
   - **CDOL/IAD decoding & validation** – ordered tag/length/source tables with byte offsets, resolved hex, and decoded CVR/IAD metadata so operators can verify every TLV that fed `GENERATE AC`.
   - **MAC transcript details** – MAC algorithm (3DES-CBC-MAC today), IV, padding rule, block-by-block notes, CID interpretation, and the raw Generate AC response so cryptogram derivations are reproducible.
   - **Decimalization & overlay proof** – declared decimalization table (derived from IPB), concatenated source string (e.g., `AC||ATC`), mask pattern, and per-digit overlay steps that lead to the final OTP digits.
5. Introduce fixtures under `docs/test-vectors/emv-cap/*.json` capturing canonical inputs/outputs, seeded with the transcript sample and placeholders for user-supplied vectors. Maintain coverage for:
   - Baseline identify/respond/sign flows (`identify-baseline`, `respond-baseline`, `sign-baseline`).
   - Additional identify variations spanning branch factor/height pairs (`identify-b2-h6`, `identify-b6-h10`).
   - Respond challenges covering short/long inputs (`respond-challenge4`, `respond-challenge8`).
   - Sign flows with low/high amounts (`sign-amount-0845`, `sign-amount-50375`).

#### R2.4 – Verbose trace schema requirements
All facades (application service, REST/CLI DTOs, verbose trace console, and replay flows) must emit the same JSON structure when `includeTrace=true`. The payload extends the summary fields already enumerated in R3 with a normative `provenance` object:

```json
"trace": {
  "masterKeySha256": "sha256:…",
  "sessionKey": "5EC8…",
  "atc": "00B4",
  "branchFactor": 6,
  "height": 10,
  "maskLength": 9,
  "previewWindow": { "backward": 0, "forward": 0 },
  "generateAcInput": { "terminal": "9F02…", "icc": "1000xxxxA500…" },
  "iccPayloadTemplate": "1000xxxxA500…",
  "iccPayloadResolved": "10000000A500…",
  "generateAcResult": "8000…",
  "bitmask": "....1F...",
  "maskedDigitsOverlay": "....14...",
  "issuerApplicationData": "06770A...",
  "provenance": {
    "protocolContext": {
      "profile": "CAP-IDENTIFY",
      "mode": "IDENTIFY",
      "emvVersion": "4.3 Book 3",
      "acType": "ARQC",
      "cid": "0x80",
      "issuerPolicyId": "retail-branch",
      "issuerPolicyNotes": "CAP-1, ISO-0 decimalization"
    },
    "keyDerivation": {
      "masterFamily": "IMK-AC",
      "derivationAlgorithm": "EMV-3DES-ATC-split",
      "masterKeyBytes": 16,
      "masterKeySha256": "sha256:…",
      "maskedPan": "492181••••••••1234",
      "maskedPanSha256": "sha256:…",
      "maskedPsn": "••01",
      "maskedPsnSha256": "sha256:…",
      "atc": "00B4",
      "iv": "0000000000000000",
      "sessionKey": "5EC8…",
      "sessionKeyBytes": 16
    },
    "cdolBreakdown": {
      "schemaItems": 8,
      "entries": [
        {
          "index": 0,
          "tag": "9F02",
          "length": 6,
          "source": "terminal",
          "offset": "[00..05]",
          "rawHex": "000000000000",
          "decoded": { "label": "Amount Authorised", "value": "0.00" }
        }
      ],
      "concatHex": "0000…"
    },
    "iadDecoding": {
      "rawHex": "06770A03A48000",
      "fields": [
        { "name": "cvr", "value": "06770A03" },
        { "name": "cdaSupported", "value": true }
      ]
    },
    "macTranscript": {
      "algorithm": "3DES-CBC-MAC (ISO9797-1 Alg 3)",
      "paddingRule": "ISO9797-1 Method 2",
      "iv": "0000000000000000",
      "blockCount": 11,
      "blocks": [
        { "index": 0, "input": "B0", "cipher": "…" },
        { "index": 10, "input": "B10", "cipher": "…" }
      ],
      "generateAcRaw": "8000…",
      "cidFlags": { "arqc": true, "advice": false, "tc": false, "aac": false }
    },
    "decimalizationOverlay": {
      "table": "ISO-0",
      "sourceHex": "00B47F32A79FDA9400B4",
      "sourceDecimal": "00541703287953009400B4",
      "maskPattern": "....1F...........FFFFF..........8...",
      "overlaySteps": [
        { "index": 4, "from": "1", "to": "1" },
        { "index": 5, "from": "7", "to": "4" }
      ],
      "otp": "140456438",
      "digits": 9
    }
  }
}
```

Key points:

- Every field above is mandatory when verbose tracing is enabled; optional arrays (`entries`, `fields`, `blocks`, `overlaySteps`) must appear even when empty so downstream tooling can rely on a consistent schema.
- Field formats are normative: digests follow `sha256:<64 hex>`; `sessionKey`/`sessionKeyBytes` map to 16- or 32-hex characters; `atc` is exactly four hex characters; `generateAcInput` strings are even-length uppercase hex; `iccPayloadTemplate` may contain literal `x` placeholders while `iccPayloadResolved` is fully substituted hex; `bitmask`/`maskedDigitsOverlay` mirror the mask notation already used in the operator UI (e.g., `.` passthrough, `F` forced). Failing tests must assert these formats.
- CLI JSON output MUST equal the REST representation byte-for-byte (ordering aside). Text-mode traces can pretty-print, but the underlying data source must still populate the JSON structure.
- Operator UI traces reuse the same JSON via `VerboseTraceConsole`. Rendered labels/sections must map one-to-one with the schema keys (`Protocol Context`, `Key Derivation`, `CDOL Breakdown`, `IAD Decoding`, `MAC Transcript`, `Decimalization Overlay`).
- Replay flows reuse the identical schema; mismatches add an `expectedOtp` field (already defined elsewhere) but may not omit provenance objects.
- Example objects in the spec and docs serve as golden fixtures; update `docs/test-vectors/emv-cap/*.json` when fields change to keep automated snapshots deterministic.
- Acceptance tests:
  1. `EmvCapEvaluationApplicationServiceTest` and `EmvCapReplayApplicationServiceTest` assert the full schema (including empty arrays) and enforce the format constraints.
  2. `EmvCapEvaluationEndpointTest`/`EmvCapReplayEndpointTest` capture MockMvc snapshots for `includeTrace=true` and keep OpenAPI snapshots in sync.
  3. `EmvCliEvaluateTest` and `EmvCliReplayTest` compare `--output-json` responses against the REST payload to guarantee parity.
  4. `rest-api/src/test/javascript/emv/console.test.js` plus `EmvCapOperatorUiSeleniumTest` assert that `VerboseTraceConsole` renders each provenance section with the expected labels/values.

### R3 – REST EMV/CAP evaluate endpoint
1. Add `POST /api/v1/emv/cap/evaluate` accepting JSON payload:
   ```json
   {
     "mode": "IDENTIFY" | "RESPOND" | "SIGN",
     "masterKey": "0123...",
     "atc": "00B4",
     "branchFactor": 4,
     "height": 8,
     "iv": "0000...",
     "cdol1": "9F02...",
     "issuerProprietaryBitmap": "00001F...",
     "customerInputs": {
       "challenge": "1234",
       "reference": "5678",
       "amount": "00123456"
     },
     "transactionData": {
       "terminal": "000000...",
       "icc": "1000xxxxA500..."
     },
     "issuerApplicationData": "06770A..."
   }
   ```
   - `customerInputs` properties are optional depending on mode.
   - `transactionData` is optional and limited to REST/CLI facades (operator UI omits manual overrides).
2. Response payload returns:
   ```json
   {
     "otp": "42511495",
     "maskLength": 8,
     "trace": {
       "masterKeySha256": "sha256:8A7F...",
     "sessionKey": "5EC8...",
     "atc": "00B4",
     "branchFactor": 6,
     "height": 10,
     "maskLength": 8,
     "previewWindow": { "backward": 0, "forward": 0 },
     "generateAcInput": { "terminal": "0000000000000000…", "icc": "1000xxxxA500…" },
     "iccPayloadTemplate": "1000xxxxA500…",
     "iccPayloadResolved": "10000000A500…",
     "generateAcResult": "8000...",
     "bitmask": "....1F...",
     "maskedDigitsOverlay": "....14...",
     "issuerApplicationData": "06770A...",
     "provenance": {
       "protocolContext": { "...": "..." },
       "keyDerivation": { "...": "..." },
       "cdolBreakdown": { "schemaItems": 8, "entries": [ ... ] },
       "iadDecoding": { "...": "..." },
         "macTranscript": { "...": "..." },
         "decimalizationOverlay": { "...": "..." }
       }
     },
     "telemetry": { ... sanitized frame metadata ... }
   }
   ```
3. Reject requests with validation errors using the existing problem-details contract; include precise field hints.
4. Respect the verbose trace toggle (default true) via request flag `includeTrace`; when false, omit sensitive fields from `trace`. When true, replace the master key with a SHA-256 digest while preserving overlay diagnostics and the derived session key value.
5. Add OpenAPI documentation and snapshot updates covering request schema, enumerations, sanitisation notes, and example responses.

### R4 – CLI EMV/CAP evaluate command
1. Extend the Picocli application with `emv cap evaluate` that accepts:
   - Required options: `--mode`, `--master-key`, `--atc`, `--branch-factor`, `--height`, `--iv`, `--cdol1`, `--issuer-proprietary-bitmap`.
   - Optional mode-specific inputs: `--challenge`, `--reference`, `--amount` (Sign mode requires all three, Respond requires `--challenge` only, Identify ignores them).
   - Optional overrides: `--terminal-data`, `--icc-template`, `--issuer-application-data`.
   - Flags: `--include-trace` (default true) and `--output-json` (pretty-prints the REST-equivalent response).
2. Command delegates to `EmvCapEvaluationApplicationService` and emits telemetry via new CLI adapters (`TelemetryContracts.emvCap*`) with IDs prefixed `cli-emv-cap-`.
3. Default (text) output includes OTP, mask length, sanitized telemetry fields, and—when `--include-trace`—a structured trace section matching REST field names.
4. JSON output must match the REST schema (`otp`, `maskLength`, `trace`, `telemetry`) so tooling can switch between facades without reformatting. The master key surfaces as a SHA-256 digest (`masterKeySha256`) with accompanying metadata; session keys remain in plaintext. Trace payloads also expose `atc`, `branchFactor`, `height`, `maskLength`, and preview window offsets so downstream consumers see the same diagnostics as the operator console.
5. Tests cover each mode, invalid parameter scenarios, includeTrace toggling, JSON parity, and telemetry sanitisation.

#### Reference verbose trace example
```
step.0: context
  profile               = CAP-Identify (issuer policy: retail-branch)
  emv.version           = 4.3 Book 3
  ac.type               = ARQC (CID=0x80)
  issuer.policy.notes   = "CAP-1, ISO-0 decimalization, mask 9-digit preview"

step.1: keys
  master.family         = IMK-AC
  master.sha256         = sha256:223E0A160AF9DA0A03E6DD2C4719C56F5D66A633CBE84E78AAA9F3735865522A
  pan.masked            = 492181••••••••1234 (sha256:5DE415C6A7B13E82D7FE6F410D4F9F1E4636A90BD0E03C03ADF4B7A12D5F7F58)
  psn.masked            = ••01 (sha256:97E9BE4AC7040CFF67871D395AAC6F6F3BE70A469AFB9B87F3130E9F042F02D1)
  atc                   = 0x00B4
  derivation.fn         = EMV-3DES-ATC-split (left/right halves)
  session.key           = 5EC8B98ABC8F9E7597647CBCB9A75402

step.2: cdol1
  schema.items          = 8 (validated: OK)
  [00..05] tag 9F02 len 6 src terminal      => 000000000000  (Amount Authorised = 0.00)
  [06..11] tag 9F03 len 6 src terminal      => 000000000000  (Amount Other = 0.00)
  [12..13] tag 9F1A len 2 src terminal      => 0276          (Country = NLD)
  [14..19] tag 95   len 5 src terminal      => 0000000000    (TVR = 00 00 00 00 00)
  [20..21] tag 5F2A len 2 src terminal      => 0978          (Currency = EUR)
  [22..25] tag 9A   len 3 src terminal      => 230908        (Date = 2023-09-08)
  [26..31] tag 9C   len 1 src terminal      => 21            (Transaction Type = 0x21)
  [32..33] tag 9F37 len 4 src terminal      => 12345678      (Unpredictable Number)
  concat.hex           = 00000000000000000000000002760000000009782309082112345678

step.3: generate_ac
  mac.algorithm         = 3DES-CBC-MAC (ISO9797-1 Alg 3)
  mac.iv                = 0000000000000000
  message.blocks        = 11 (padding applied)
  chain.block[0]        = DES3(0000000000000000 XOR B0)
  chain.block[10]       = DES3(chain.block[9] XOR B10)
  generateAc.raw        = 80 00 B4 7F 32 A7 9F DA 94 56 43 06 77 0A 03 A4 80 00
  cid.flags             = {b8=ARQC, b7=Advice=0, b6=TC=0, b5=AAC=0}
  ac.hex                = 00B47F32A79FDA94
  iad.raw               = 06770A03A48000
  iad.decode            = {cvr=06770A03, counters=0xA480, CDA supported=1, offline PIN=0}

step.4: masked_digits
  decimalization.table  = ISO-0
  source.hex            = AC||ATC = 00B47F32A79FDA94 || 00B4
  source.dec            = 00541703287953009400B4
  mask.pattern          = "....1F...........FFFFF..........8..."
  overlay.steps         = [
    {index:4,  from:'1', to:'1'},
    {index:5,  from:'7', to:'4'},
    {index:17, from:'3', to:'3'},
    {index:18, from:'0', to:'4'},
    {index:19, from:'0', to:'8'}
  ]
  otp                   = 140456438
  digits.count          = 9

step.5: outputs
  previewWindowBackward = 0
  previewWindowForward  = 0
  maskLength            = 9
  credential.source     = inline (id=emv-cap-identify-baseline)
```

### R5 – Operator UI EMV/CAP console panel
1. Activate the existing EMV/CAP tab in the operator console with:
   - Input groups for derivation parameters, customer inputs, ICC template, issuer application data, and mode selector radio buttons.
   - “Load a sample vector” dropdown mirroring other protocols (fed by MapDB persistence in R6).
   - Preview-window offset controls (`Backward`, `Forward`) matching the HOTP/TOTP/OCRA evaluation panels.
2. Rely exclusively on the global “Enable verbose tracing for the next request” control shared by all protocols; when unchecked, the trace pane remains hidden and the request omits the `trace` flag. Surface the shared warning copy (`Verbose traces expose raw secrets and intermediate buffers. Use only in trusted environments.`) beneath the global toggle.
3. Request layout mirrors HOTP/TOTP/OCRA: left column hosts the input form (including preview-window offsets that drive the evaluation result table), right column renders the result card. The result card surfaces only the OTP preview table (counter/Δ/OTP) and the status badge; all other metadata shifts to verbose trace.
4. Stored credential mode hides preset-owned secrets entirely: ICC master key, CDOL1 payload, Issuer Proprietary Bitmap, ICC payload template, and Issuer Application Data inputs disappear so operators only see editable fields. Apply `hidden`/`aria-hidden="true"` to the surrounding `.field-group` containers and mask wrappers so neither labels nor helper copy render while stored mode is active; switching back to inline mode removes those attributes and restores the full editable set.
5. Session key derivation fieldset mirrors the reference calculator: ICC master key + ATC occupy the first row, Branch factor (b) and Height (H) share the next horizontal row, and the IV spans the full width beneath them. The ICC master key **and** IV controls are single-line text inputs (no textareas) so the row stays compact without vertical scrolling; only the master key column hides in stored mode while the ATC and IV inputs remain visible. The inputs must each expand to the full width of their column without additional gutter spacing on the row, matching the sizing applied to branch/height. Branch/height inputs remain visible in stored mode (only secrets like the master key hide) so operators can audit the tree configuration at a glance. Selenium coverage asserts both rows keep their dedicated wrappers and width constraints.
6. **R5.6 – Card configuration isolation.** Card configuration remains an isolated fieldset (`.emv-card-block`) that contains only the CDOL1 payload and Issuer Proprietary Bitmap inputs. CDOL1 stays a textarea so multi-field payloads remain readable, while the Issuer Proprietary Bitmap uses a single-line text input because it never exceeds a few bytes. Transaction (`.emv-transaction-block`) and Input from customer (`.emv-customer-block`) fieldsets are adjacent siblings; CSS borders/spacing must ensure none of those sections render inside Card configuration even when stacked vertically. Selenium/JS tests should assert that each legend/container is a sibling node and that stored-mode masking only affects the CDOL1/IPB groups within the card block. The Transaction block keeps the ICC payload template as a textarea but switches Issuer Application Data to a single-line text input so short 16–32-byte blobs stay inline while still hiding entirely in stored mode.
7. **R5.7 – Input-from-customer row layout.** Each mode radio renders on its own grid row with the relevant customer inputs aligned to the right: Identify displays the disabled Challenge/Reference/Amount placeholders, Respond pairs the Challenge input on the same row as the Respond radio, and Sign shows Reference + Amount inputs beside the Sign radio. Challenge/Reference/Amount remain a single shared input set (no duplicates), permanently mounted in the DOM, and toggle only their `disabled` state when the operator switches modes. Stored credential mode must not hide these inputs—they simply stay disabled per mode. Console JS must preserve the existing mode toggle semantics, and Selenium + Node console tests should assert the grid row DOM structure so regressions are caught.
8. **R5.8 – Replay CTA spacing parity.** The Replay form’s action bar must reuse the same vertical spacing contract as HOTP/TOTP/OCRA and the EMV Evaluate tab: the CTA container applies the shared `stack-offset-top-lg` utility so the Provided OTP/preview controls always retain a visible gap before the button. Keep the `.emv-action-bar` wrapper (and its button) unchanged otherwise so console.js bindings remain stable. Guard this requirement with template-focused Node tests (asserting the class is present near `data-testid="emv-replay-submit"`) and Selenium coverage that ensures the replay CTA maintains the spacing utility class so regressions surface quickly.
7. Verbose trace collects every diagnostic detail previously shown on the result card (mask length, masked digits, ATC, branch factor, height) plus the active preview window offsets (`previewWindowBackward`, `previewWindowForward`). These sit alongside the SHA-256 digest of the master key (`masterKeySha256`), the derived session key, Generate AC inputs/result, bitmask overlay, masked digits overlay, issuer application data, and resolved ICC payload. Use accessible formatting (monospaced columns, scroll containers as needed).
8. Validation errors surface inline using the existing problem-details mapping with field-level annotations.
9. Selenium/JS tests exercise happy paths for each mode, includeTrace toggle, preset loading, error rendering, telemetry sanitisation of DOM nodes, and masked placeholder visibility/toggling.

### R6 – EMV/CAP credential persistence & seeding
1. Persist EMV/CAP credentials in MapDB via `CredentialStore`, storing master key, derivation parameters, issuer data, and transaction templates under deterministic identifiers.
2. Add an application seeding service that imports canonical EMV CAP fixtures (and optional user-supplied additions) and exposes:
   - REST endpoint `POST /api/v1/emv/cap/credentials/seed` returning added/total counts and credential identifiers.
   - CLI command `emv cap seed` with equivalent reporting.
3. Ensure seeding is idempotent and guard sensitive values with the same sanitisation rules applied elsewhere (no master/session keys in telemetry/logs). Credential directory responses expose only sanitized summaries (digests/length metadata) so downstream facades never transmit raw secret material.
4. Operator UI integrates stored credential selection (dropdown + autofill) and still loads sanitized summaries, but inline hydration requests fetch full preset defaults via `GET /api/v1/emv/cap/credentials/{credentialId}` (master key hex, CDOL1, issuer proprietary bitmap, ICC template, issuer application data, and stored customer inputs). Responses are cached per credential so inline Evaluate/Replay forms populate immediately while stored mode keeps secrets hidden.
5. Tests cover MapDB persistence flows, seeding idempotency, preset rendering, sanitized summary generation, and facade interactions with stored credentials.

### R7 – Application replay orchestration & telemetry
1. Introduce `EmvCapReplayApplicationService` that reuses the core derivation helpers to recompute OTP candidates across the configured preview window (backward/forward deltas) and validates operator-supplied OTPs for Identify, Respond, and Sign modes.
2. Support both stored credentials (via `CredentialStore`) and inline requests, preserving optional transaction payloads, issuer application data, and ATC substitution semantics from evaluation.
3. Accept preview-window overrides (backward/forward) matching evaluation defaults; inline requests default to `0/0` while stored credentials inherit the persisted offsets unless overridden.
4. Return replay results containing status, reason code, matched delta (when successful), sanitized telemetry frame, and optional verbose trace payload. Trace content mirrors evaluation diagnostics plus supplied OTP, comparison overlays, and mismatch highlights when validation fails.
5. Emit telemetry events `emv.cap.replay.identify`, `emv.cap.replay.respond`, and `emv.cap.replay.sign`, redacting master keys (no raw master key/session key data in telemetry) while including credential source, preview-window configuration, supplied OTP length, and match outcome.

### R8 – REST EMV/CAP replay endpoint
1. Add `POST /api/v1/emv/cap/replay` mirroring existing protocol replay schemas:
   - `credentialId` (optional) selects a stored credential; when omitted, the request must include inline parameters: `iccMasterKey`, `atc`, `branchFactor`, `height`, `initializationVector`, `cdol1Payload`, `issuerBitmap`, optional `transactionData`, and optional `issuerApplicationData`.
   - `mode` enumerates `IDENTIFY`, `RESPOND`, `SIGN` and drives optional inputs (`challenge`, `reference`, `amount`).
   - `otp` (required) captures the operator-supplied digits for comparison.
   - `driftBackward` / `driftForward` mirror the preview-window offsets surfaced in the Evaluate UI (default `0/0` inline; stored credentials inherit persisted offsets).
   - `includeTrace` toggles verbose trace emission (default true) and suppresses sensitive fields when false.
2. Response payload aligns with existing replay responses (`status`, `reasonCode`, `metadata`, `trace`); metadata includes credential source (`stored`/`inline`), credential identifier/reference, preview-window bounds, matched delta (when successful), sanitized issuer metadata, and telemetry identifier.
3. Validation errors must surface through the problem-details contract with explicit field-level messages for invalid OTP length/value, missing credential inputs, or mode-specific omissions.
4. Regenerate OpenAPI definitions and snapshots to cover the new endpoint, request schema, enums, examples, and error responses.

### R9 – CLI EMV/CAP replay command
1. Add `emv cap replay` supporting stored and inline credentials:
   - Stored replay requires `--credential-id` and `--otp` while allowing preview window overrides (`--search-backward`, `--search-forward`).
   - Inline replay accepts the same derivation flags as `emv cap evaluate` plus `--otp`, optional `--challenge`, `--reference`, `--amount`, and optional transaction/issuer payload overrides.
2. Provide `--include-trace` (default true) and `--output-json` switches; JSON output mirrors the REST replay schema, and text output highlights status, reason code, matched delta, sanitized telemetry, and—when traces are enabled—the verbose diagnostic payload.
3. Emit telemetry via adapters prefixed `cli-emv-cap-replay-*`, capturing credential source, preview-window configuration, supplied OTP length, and match outcome without disclosing sensitive buffers.
4. Unit tests cover stored/inline success, OTP mismatch, invalid parameter combinations, preview-window overrides, includeTrace toggling, and JSON/text parity.

### R10 – Operator UI EMV/CAP replay panel
1. Mirror the Evaluate/Replay tab pattern from other protocols: add a Replay tab containing stored credential dropdown, inline parameter form, preview-window controls, OTP input, and a `[ Replay CAP OTP ]` action button—verbose tracing remains governed by the global toggle.
2. Replay form must accept stored presets and allow inline overrides; selecting a preset pre-populates derivation fields while keeping them editable until cleared, matching the HOTP/TOTP behavior. Stored mode uses sanitized summaries (digests/length metadata) and keeps sensitive inputs non-interactive until inline overrides are requested.
3. Replay result card exposes match status badge, matched delta (when successful), and the shared OTP preview table; verbose trace (via `VerboseTraceConsole`) surfaces Generate AC inputs/result, masked overlays, issuer data, supplied OTP, comparison overlays, and mismatch diagnostics while redacting the master key (session keys remain visible).
4. Validation errors mirror the Evaluate experience; Selenium/JS coverage expands to stored/inline replay flows, mismatch outcomes, trace suppression governed by the global verbose toggle, and masked placeholder assertions.
5. Replay session key fieldset keeps Branch factor (b) and Height (H) paired on the same row beneath the master key + ATC inputs so operators can validate stored-tree parameters even when secrets stay hidden; Selenium tests must verify both inputs share the dedicated wrapper and remain visible in stored mode.

## UI / Interaction Mock-ups

These ASCII mock-ups capture the operator-console layout for the live EMV/CAP tab. They align with the console design language and inform UI implementation plus Selenium coverage.

### Evaluate Form & Result Layout (Identify mode shown)
```
┌──────────────────────────────────────────────────────────────────────────────┐
│ EMV/CAP evaluation                                                           │
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌───────────────────────────────┐   ┌──────────────────────────────────────┐ │
│ │ Choose evaluation mode        │   │ Evaluation result                    │ │
│ │  (• Inline parameters)         │  │  COUNTER   Δ    OTP                  │ │
│ │  ( ) Stored credential         │  │  180       0   42511495              │ │
│ │                                │  │ Status                      [SUCCESS]│ │
│ │ Stored credential (optional) │  │                                       │ │
│ │  Preset  [ identify-baseline▼]│  │                                       │ │
│ │                                │  │                                       │
│ │ Session key derivation         │  │                                       │
│ │  ICC Master Key (hex) [ … ]   ATC (hex) [00B4] │  │                                    │ │
│ │                                │  └────────────────────────────────────┘ │
│ │  Branch factor (b)  [ 4 ]     Height (H) [ 8 ] │                                        │
│ │  IV (hex)           [ … ]      │                                        │
│ │                                │                                        │
│ │ Card configuration             │                                        │
│ │  CDOL1 payload       [ … ]     │                                        │
│ │  Issuer bitmap       [ … ]     │                                        │
│ │                                │                                        │
│ │ Transaction                   │                                        │
│ │  Transaction data   [ … ]     │                                        │
│ │  Issuer App Data    [ … ]     │                                        │
│ │  “xxxx” → ATC hint            │                                        │
│ │                                │                                        │
│ │ Customer input (shared grid)   │                                        │
│ │  Identify  (•) Challenge [—] Reference [—] Amount [—] (all disabled)    │
│ │  Respond   ( ) Challenge [     ]                                       │
│ │  Sign      ( ) Reference [     ] Amount [     ]                        │
│ │  (Inputs stay mounted; mode toggles only their disabled state.)        │
│ │                                │                                        │
│ │ Preview window offsets         │                                        │
│ │  Backward [ 1 ]  Forward [ 1 ] │                                        │
│ │                                │                                        │
│ │ ICC template        [ … ]      │                                        │
│ │ Issuer App Data     [ … ]      │                                        │
│ │                                │                                        │
│ │ [ Evaluate CAP OTP ]                                                     │
│ └───────────────────────────────┘                                         │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Replay Form & Result Layout (Respond mode shown)
```
┌──────────────────────────────────────────────────────────────────────────────┐
│ EMV/CAP replay                                                               │
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────┐  ┌──────────────────────────────────────┐ │
│ │ Stored credential (optional)   │  │ Replay result                        │ │
│ │  Preset  [ respond-baseline▼]  │  │  COUNTER   Δ    OTP                  │ │
│ │                                │  │  180       0   42511495 (match)      │ │
│ │ Session key derivation         │  │ Status                      [SUCCESS]│ │
│ │  ICC Master Key (hex) [ … ]   ATC (hex) [00B4] │  │ Reason                 MATCH_FOUND   │ │
│ │                                │  └──────────────────────────────────────┘ │
│ │  Branch factor (b)  [ 4 ]     Height (H) [ 8 ] │                                           │
│ │  IV (hex)           [ … ]      │                                           │
│ │                                │                                           │
│ │ Card configuration             │                                           │
│ │  CDOL1 payload       [ … ]     │                                           │
│ │  Issuer bitmap       [ … ]     │                                           │
│ │                                │                                           │
│ │ Transaction                     │                                           │
│ │  Transaction data   [ … ]       │                                           │
│ │  Issuer App Data    [ … ]       │                                           │
│ │  “xxxx” → ATC hint              │                                           │
│ │                                │                                           │
│ │ Customer input (shared grid)      │                                        │
│ │  Identify  ( ) Challenge [—] Reference [—] Amount [—] (disabled)          │
│ │  Respond   (•) Challenge [1234]                                          │
│ │  Sign      ( ) Reference [     ] Amount [     ]                          │
│ │  (Single input set; no DOM duplication, only disabled toggles.)          │
│ │                                │                                           │
│ │ Provided OTP [ 42511495 ]      │                                           │
│ │ Preview window offsets         │                                           │
│ │  Backward [ 1 ]  Forward [ 1 ] │                                           │
│ │                                │                                           │
│ │ ICC template      [ … ]        │                                           │
│ │ Issuer App Data   [ … ]        │                                           │
│ │                                │                                           │
│ │ [ Replay CAP OTP ]             │                                           │
│ └────────────────────────────────┘                                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Verbose Trace Panel
```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Verbose trace                                                                │
│                                                                              │
│  Master key SHA-256:          sha256:2C1F0AA91A260BA1F5C8B8CDA6F0FBA7        │
│  Session key:                 5EC8B98ABC8F9E7597647CBCB9A75402               │
│  Generate AC input (terminal): 00000000000000000000000000008000…             │
│  Generate AC input (ICC):      100000B4A50006040000                          │
│  Generate AC result:           8000B47F32A79FDA94564306770A03A48000          │
│  Bitmask:                      ....1F...........FFFFF..........8...          │
│  Masked digits overlay:        ....14...........45643..........8...          │
│  Issuer Application Data:      06770A03A48000                                │
│  Mask length:                  8                                             │
│  Masked digits:                8                                             │
│  ATC:                          00B4                                          │
│  Branch factor:                4                                             │
│  Height:                       8                                             │
│                                                                              │
│  Copy trace button lives on the shared console toolbar (no per-panel controls).│
└──────────────────────────────────────────────────────────────────────────────┘
Legend: verbose trace remains optional; result metrics (mask length, ATC, etc.) are trace-only.
```

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S39-01 | Stored or inline Identify/Respond/Sign evaluations compute deterministic CAP OTP previews, telemetry, and verbose traces across core, application, REST, CLI, and operator UI facades. |
| S39-02 | Operator UI inline mode stays active when loading sample vectors, hydrates credential defaults on demand, and keeps inline inputs editable prior to submission. |
| S39-03 | Stored credential workflows hide ICC secrets in the console, expose digest/length placeholders in directory APIs, and only hydrate sensitive fields server-side for inline requests. |
| S39-04 | Credential seeding services/commands idempotently populate MapDB so stored Evaluate/Replay journeys share the curated EMV/CAP dataset without duplicating secrets. |
| S39-05 | Replay flows recompute preview windows, report match deltas, and surface mismatch diagnostics/telemetry for stored and inline submissions across every surface. |
| S39-06 | Replay controls expose backward/forward preview offsets, inline vs stored toggles, and validation errors (missing OTP, out-of-window inputs, invalid mode) consistently in REST, CLI, and UI. |
| S39-07 | Operator UI Evaluate/Replay panels follow the shared two-column layout with grouped session-derivation, card, transaction, and customer-input fieldsets plus consistent CTA spacing. |
| S39-08 | Verbose trace payloads include protocol context, key derivation, CDOL breakdown, IAD decoding, MAC transcript, and decimalization overlay rendered through the shared console on all facades. |
| S39-09 | Sample vector selectors share labels, spacing, and styling with other protocols so Evaluate/Replay dropdowns remain accessible regardless of inline/stored mode. |
| S39-10 | REST endpoints, Picocli commands, and documentation stay synchronized (evaluate, evaluate-stored, replay) with JSON/text parity, preview offset parameters, and telemetry redaction guidance. |

## Non-Goals
- Modelling full APDU reader lifecycles, terminal risk management, or PIN capture workflows.
- Introducing hardware integrations or vendor-specific token emulators.
- Altering existing HOTP/TOTP/OCRA/FIDO2 telemetry schemas beyond adding new adapters.
- Persisting sensitive trace data beyond the lifetime of a single request.

## Acceptance Criteria
1. Unit/property tests cover mode validation, session key derivation, cryptogram generation, bitmask digit extraction, replay comparisons (match + mismatch), and stored credential seeding.
2. Application-layer tests confirm telemetry redaction and verbose trace contents for inline and stored credentials across evaluation and replay flows.
3. REST integration tests exercise all modes for `POST /api/v1/emv/cap/evaluate` and `/replay`, stored credential usage, inline overrides, preview-window adjustments, includeTrace toggling, mismatch responses, and seeding endpoints; validation failures surface via problem-details payloads.
4. CLI tests ensure `emv cap evaluate`, `emv cap replay`, and `emv cap seed` honour option validation, text/JSON parity, telemetry sanitisation, preview-window overrides, mismatch handling, and includeTrace toggling.
5. Operator console tests (JS + Selenium) cover Evaluate and Replay tabs, preset loading, OTP/trace rendering, mismatch messaging, global verbose toggle behaviour, and accessibility expectations.
6. OpenAPI snapshot regenerated with evaluate, replay, and seeding endpoints; documentation aligns with sanitisation requirements and example payloads.
7. Fixture files capture the transcript vectors, additional replay cases, and stored credential metadata plus guidance for extending coverage.

## Test Strategy
- **Core**: Deterministic tests using transcript vector(s), verifying session key, generate AC result, OTP, masked overlay output per mode, and stored credential serialisation.
- **Application**: Mocked telemetry assertions ensuring sensitive fields remain redacted while verbose traces capture expected data (inline/stored) for both evaluate and replay flows, including mismatch diagnostics.
- **REST**: MockMvc tests for evaluate and replay endpoints (happy path, mismatch, validation), seeding endpoints, stored credential listings, preview-window overrides, and includeTrace toggling; contract tests validate JSON schema and OpenAPI snapshots.
- **CLI**: Picocli unit tests invoking `emv cap evaluate`, `emv cap replay`, and `emv cap seed`, covering text/JSON parity, telemetry IDs, failure messaging, preview-window overrides, and includeTrace toggles.
- **Operator UI**: JS unit tests and Selenium coverage validating Evaluate/Replay interactions, preset loading, OTP entry, global verbose toggle behaviour, mismatch messaging, DOM sanitisation, and error display.
- **Security**: Negative tests covering invalid hex length/parity, lower-case input normalisation, branch factor/height bounds, and sanitisation of persisted/seeding data.
- **Documentation**: Update `docs/2-how-to` catalogue with REST + CLI guidance, operator console instructions, and stored credential reference material.

## Appendix – Additional Vectors Needed
1. Identify mode baseline (provided transcript) – confirm OTP `42511495`.
2. Respond mode with non-empty challenge.
3. Sign mode with challenge, reference, and amount values.
4. Each mode repeated with and without transaction data to confirm optional flow.
5. Alternative branch factor/height pairs (if available) to exercise derivation matrix.

For each vector the user will supply:
- ICC master key (hex, 16 or 32 bytes).
- ATC (hex, 2 bytes).
- Branch factor `b` (integer).
- Height `H` (integer).
- IV (hex, 16 bytes).
- CDOL1 (hex).
- Issuer Proprietary Bitmap (hex).
- Customer inputs (challenge/reference/amount) per mode.
- Transaction data (terminal + ICC payload with ATC placeholders) when applicable.
- Issuer Application Data (hex).
- Expected session key, Generate AC result, bitmask overlay, and final OTP.
