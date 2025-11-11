# Feature 037 - Base32 Inline Secret Support

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-11 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/037/plan.md` |
| Linked tasks | `docs/4-architecture/features/037/tasks.md` |
| Roadmap entry | #37 |

## Overview
Streamline inline HOTP, TOTP, and OCRA flows by allowing operators to paste Base32 encoded secrets (for example, copied from `otpauth://` URIs). The simulator continues to persist and process hexadecimal secrets so existing persistence, telemetry, and fixtures remain intact. The change ships a shared encoding helper, updates every facade, and aligns documentation so Base32 usage is self service.

## Clarifications
- 2025-10-31 - Protocol coverage extends to HOTP, TOTP, and OCRA inline flows across REST, CLI, and operator UI surfaces (user directive; Option A selected).
- 2025-10-31 - REST contracts retain `sharedSecretHex` while adding an explicit `sharedSecretBase32` field; exactly one value must be supplied per inline request (user directive; Option B selected).
- 2025-10-31 - CLI commands expose dedicated Base32 options alongside existing hex flags (user directive; Option A selected).
- 2025-11-01 - Operator UI shared secret messaging keeps the conversion hint visible by default and replaces it with the active validation error (Option A selected).

## Goals
- Accept Base32 encoded secrets across HOTP, TOTP, and OCRA inline flows without changing downstream persistence or telemetry contracts.
- Reuse a single encoding helper so REST, CLI, and UI surfaces stay in sync and redaction safe.
- Document Base32 usage (how tos, knowledge map, runbooks) so future protocol work inherits the pattern automatically.

## Non-Goals
- Stored credential seeding workflows remain hex based.
- No QR or `otpauth://` URI generation.
- Non OTP protocols (FIDO2, EMV, EUDIW) remain unchanged.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry and traces | Source |
|----|-------------|--------------|-----------------|--------------|----------------------|--------|
| FR-037-01 | Provide a reusable `SecretEncodings` helper that normalises Base32 strings, strips whitespace, validates the RFC 4648 alphabet, and converts to uppercase hex. | Helper returns uppercase hex along with masking hints; downstream callers pass the hex value to existing services. | Unit tests cover valid input, lowercase characters, padding, and invalid symbols; helper throws descriptive validation errors. | Helper rejects invalid alphabets and returns field scoped errors; request handling falls back to existing validation responses. | No new telemetry fields; helper ensures Base32 inputs are never logged or emitted. | Owner directive 2025-10-31. |
| FR-037-02 | Extend HOTP, TOTP, and OCRA inline REST DTOs/services with optional `sharedSecretBase32` fields while keeping mutual exclusivity with `sharedSecretHex`. | Clients send exactly one secret representation; REST services convert Base32 to hex before calling application services and reuse existing telemetry/persistence flows. | Validation fails when neither or both fields are supplied; tests assert `shared_secret_missing`, `shared_secret_conflict`, and `shared_secret_base32_invalid` reason codes. | Controllers return HTTP 422 with detailed field metadata; telemetry records sanitized errors only. | OpenAPI snapshot documents the new field and exclusivity notes. | Owner directive 2025-10-31. |
| FR-037-03 | Add `--shared-secret-base32` (protocol specific variants) to HOTP, TOTP, and OCRA CLI commands and reuse the helper before dispatching application calls. | CLI accepts Base32 or hex flags (mutually exclusive) and forwards converted hex to services; help text shows new options. | Picocli validation ensures at least one representation is set; tests assert mutually exclusive inputs and error codes. | CLI exits with code 3 and prints validation guidance if input is missing or invalid. | CLI telemetry payloads remain hex only because CLI never emits the Base32 string. | Owner directive 2025-10-31. |
| FR-037-04 | Replace dual shared secret textareas in the operator UI with a single textarea plus Hex/Base32 toggle, syncing values through shared JS helpers. | Toggle keeps both encodings in sync, displays Base32 mode hints, and submits whichever representation the user selected; Selenium verifies round trips. | UI validation surfaces inline errors (invalid characters, conflict) and swaps the hint text to the active error message. | Invalid Base32 input highlights the shared textarea, surfaces the error copy, and blocks form submission until corrected. | Verbose traces remain unchanged; telemetry receives only the converted hex string. | Owner directive 2025-11-01. |
| FR-037-05 | Update how to guides, knowledge map, and analysis gate notes with Base32 instructions and helper relationships. | Documentation explains Base32 usage across REST/CLI/UI along with the shared helper dependency. | Analysis gate confirms docs reference Base32 and no outstanding questions remain. | Feature cannot be marked complete if docs drift from implementation or telemetry requirements. | Knowledge map references the helper link between client surfaces and persistence. | Constitution Principle 4. |

## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-037-01 | Secrets remain hex at rest so persistence schemas and telemetry remain deterministic. | Avoid schema churn and keep replay fixtures valid. | Database records and telemetry frames always contain hex strings; regression tests assert conversion occurs before persistence. | Inline application services, persistence adapters. | Owner directive 2025-10-31. |
| NFR-037-02 | Base32 conversion must be deterministic and case insensitive. | Operators copy Base32 payloads from multiple tooling sources. | Helper round trip tests compare expected hex output for mixed case and padded input. | `SecretEncodings` helper. | Owner directive 2025-10-31. |
| NFR-037-03 | No Base32 value may appear in telemetry, logs, or fixtures outside targeted tests. | Maintain redaction guarantees. | Telemetry assertions and log sanitizers verify only hex strings leave the helper; Selenium traces confirm UI never sends both fields. | Application telemetry adapters, UI JS converters. | Constitution Principle 4. |

## UI / Interaction Mock ups
The unified shared secret textarea keeps a single input and swaps modes via toggle buttons.

```
+--------------------------------------------------------------+
| HOTP Inline Evaluation                                      |
|                                                              |
|  Shared secret                                               |
|  Mode:  [ Hex ] [*Base32 ]          Length:  64 chars        |
|                                                              |
|  +--------------------------------------------------------+  |
|  |                                                        |  |
|  |  JBSWY3DPEB3W64TMMQ======                              |  |
|  |                                                        |  |
|  +--------------------------------------------------------+  |
|                                                              |
|  <-> Converts automatically when you flip the mode.          |
|  (Switches to ! Validation: {invalid_characters} on errors)  |
+--------------------------------------------------------------+
```

## Branch and Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-037-01 | Shared encoding helper normalises Base32 secrets, detects invalid alphabets, converts to uppercase hex, and keeps telemetry hex only. |
| S-037-02 | REST HOTP/TOTP/OCRA inline endpoints accept either hex or Base32 (mutually exclusive), convert to hex before processing, and surface precise validation errors. |
| S-037-03 | CLI HOTP/TOTP/OCRA commands expose Base32 options alongside hex flags, enforce exclusivity, and reuse the helper before invoking application services. |
| S-037-04 | Operator UI provides a single shared secret textarea with Hex/Base32 toggle, keeps values synchronised, and updates validation or hints inline without leaking secrets. |
| S-037-05 | Documentation, knowledge map, and governance notes describe Base32 usage, conversion hints, and follow up requirements. |

## Test Strategy
- **Core:** `SecretEncodingsTest` covering valid, mixed case, and invalid Base32 payloads plus telemetry masking assertions (S-037-01).
- **Application/REST:** Inline DTO/service tests for HOTP/TOTP/OCRA verifying exclusivity, conversion, and 422 responses; OpenAPI snapshot captures new field metadata (S-037-02).
- **CLI:** Picocli integration tests asserting Base32 flag usage, exclusivity, and exit codes (S-037-03).
- **UI (JS/Selenium):** Shared secret textarea toggle tests plus Selenium smoke coverage for Base32 success and validation errors (S-037-04).
- **Docs/Contracts:** How to guides, knowledge map, and analysis gate checklist updated with Base32 references (S-037-05).

## Interface and Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-037-01 | `InlineSecretInput` record that holds mutually exclusive `sharedSecretHex` and `sharedSecretBase32` values. | rest-api, application |
| DO-037-02 | `SecretEncodings` helper that converts Base32 strings into uppercase hex with masking hints. | core, application |
| DO-037-03 | `SharedSecretFieldModel` used by operator UI panels to keep the textarea toggle in sync. | rest-api, ui |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-037-01 | REST POST `/api/v1/hotp/inline/evaluate` (and replay) | Accepts `sharedSecretBase32` or `sharedSecretHex`, converts to hex, forwards to HOTP services. | Mirrors existing inline DTO schema with exclusivity validation. |
| API-037-02 | REST POST `/api/v1/totp/inline/evaluate` and `/replay` | Same exclusivity rules; conversion occurs before application call. | Returns 422 with field level errors when validation fails. |
| API-037-03 | REST POST `/api/v1/ocra/evaluate` and `/verify` | Supports Base32 input for inline credential sections. | Shares InlineSecretInput helper to avoid duplication. |
| API-037-04 | Application service `InlineSecretService` | Central entry point that enforces exclusivity and conversion before persistence. | Emits sanitised validation errors and telemetry hooks. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-037-01 | `hotp inline-evaluate --shared-secret-base32=<value>` | Accepts Base32 input, converts to hex, enforces exclusivity with `--shared-secret-hex`. |
| CLI-037-02 | `totp inline-evaluate --shared-secret-base32=<value>` | Mirrors HOTP behaviour for TOTP evaluate/replay commands. |
| CLI-037-03 | `ocra evaluate --shared-secret-base32=<value>` | Applies helper conversion before invoking application OCRA services. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|------------|---------------------------|
| TE-037-01 | `otp.inline.evaluate` / `otp.inline.replay` | Fields `protocol`, `result`, `reasonCode`; helper ensures only hex secrets are logged and Base32 input never appears. |

### Fixtures and Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-037-01 | REST integration test payloads referencing `sharedSecretBase32` (see `rest-api/src/test/java/.../TotpEvaluationEndpointTest`) | Demonstrate Base32 success and validation failures for inline flows. |
| FX-037-02 | CLI integration test snapshots under `cli/src/test/resources/**/inline-base32*.txt` | Document expected CLI output when Base32 flags are used or rejected. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|----------------------------|
| UI-037-01 | Shared secret textarea (Hex mode) | Default state; placeholder shows hex guidance and length counter for hex input. |
| UI-037-02 | Shared secret textarea (Base32 mode) | Toggle flips to Base32, hint text switches, and helper converts existing hex value. |
| UI-037-03 | Validation error state | Invalid Base32 characters swap the hint to error copy and highlight the textarea until corrected. |

## Telemetry and Observability
- No new telemetry events are introduced; `telemetry.trace` and OTP evaluation events continue to carry protocol, result, and reason codes without secret material.
- Helper level masking guarantees Base32 strings never hit structured logs or telemetry sinks, and validation errors surface field names only (no secret values).
- CLI and REST controllers log only the sanitised validation summary when exclusivity checks fail.

## Documentation Deliverables
- Update HOTP/TOTP/OCRA how to guides with Base32 entry instructions and troubleshooting notes.
- Refresh `docs/4-architecture/knowledge-map.md` to note the shared encoding helper used by REST/CLI/UI.
- Record Base32 requirements and commands in `docs/_current-session.md` plus the roadmap entry when template migrations occur.
- Confirm analysis gate checklist references Base32 coverage when evaluating inline secrets.

## Fixtures and Sample Data
- Inline REST and CLI tests already carry Base32 examples; keep them in sync with helper behaviour when fixtures evolve.
- Selenium suites reuse existing synthetic OTP secrets and now also cover Base32 toggles; no persistent fixtures are added.

## Spec DSL
```yaml
domain_objects:
  - id: DO-037-01
    name: InlineSecretInput
    fields:
      - name: sharedSecretHex
        type: string
      - name: sharedSecretBase32
        type: string
        optional: true
  - id: DO-037-02
    name: SecretEncodings
    operations:
      - name: toHex
        params: [base32Value]
        returns: string
        errors: [invalid_alphabet, empty_secret]
routes:
  - id: API-037-01
    method: POST
    path: /api/v1/{protocol}/inline/evaluate
    body:
      sharedSecretHex: string
      sharedSecretBase32: string?
    constraints:
      - exactly_one_of: [sharedSecretHex, sharedSecretBase32]
cli_commands:
  - id: CLI-037-01
    command: hotp inline-evaluate --shared-secret-base32=<value>
    exclusivity: [--shared-secret-hex]
  - id: CLI-037-02
    command: totp inline-evaluate --shared-secret-base32=<value>
telemetry_events:
  - id: TE-037-01
    event: otp.inline.evaluate
    fields:
      - protocol
      - result
      - reasonCode
    redaction: secret_values_masked
ui_states:
  - id: UI-037-01
    description: Shared secret textarea shows hex hint and length counter.
  - id: UI-037-02
    description: Toggle switches to Base32 mode, converts existing value, and updates hint text.
```

## Appendix
- Base32 helper continues to emit uppercase hex strings regardless of input casing.
- CLI examples show `--shared-secret-hex=31323334` vs `--shared-secret-base32=JBSWY3DPEB3W64TMMQ======`; only one flag is legal per invocation.
- Operator UI default hint text reads "Paste Base32 or switch back to Hex" until validation errors override it.
