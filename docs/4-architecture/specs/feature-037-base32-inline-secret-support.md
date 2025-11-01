# Feature 037 – Base32 Inline Secret Support

_Status: Complete_  
_Last updated: 2025-11-01_

## Overview
Streamline inline HOTP, TOTP, and OCRA flows by allowing operators to paste Base32-encoded secrets (for example, copied from `otpauth://` URIs). The simulator continues to persist and process hexadecimal secrets so existing persistence, telemetry, and fixtures remain intact.

## Clarifications
- 2025-10-31 – Protocol coverage extends to HOTP, TOTP, and OCRA inline flows across REST, CLI, and operator UI surfaces (user directive; Option A selected).
- 2025-10-31 – REST contracts retain `sharedSecretHex` while adding an explicit `sharedSecretBase32` field; exactly one value must be supplied per inline request (user directive; Option B selected).
- 2025-10-31 – CLI commands expose dedicated Base32 options alongside existing hex flags (user directive; Option A selected).
- 2025-11-01 – Operator UI shared-secret messaging keeps the conversion hint visible by default and replaces it with the active validation error (Option A selected).

## Requirements
### R1 – Shared secret encoding helpers
- Introduce a reusable utility (for example `SecretEncodings`) that normalises Base32 input, strips whitespace, validates the RFC 4648 alphabet, and converts it to uppercase hex strings.
- Reject mixed or invalid alphabets with field-specific error descriptors so REST services can surface precise reason codes.
- Provide redaction-safe helpers so telemetry never includes the Base32 secret.

### R2 – REST inline flows
- Extend inline request DTOs (`HotpInlineEvaluationRequest`, `HotpReplayRequest`, `TotpInlineEvaluationRequest`, `TotpReplayRequest`, `OcraEvaluationRequest`, `OcraVerificationInlineCredential`) with an optional `sharedSecretBase32` property.
- Validation fails when neither secret field nor both are provided, reusing existing reason-code patterns (`shared_secret_missing`, `shared_secret_conflict`) with updated hints.
- Convert Base32 input to hex before invoking application services so downstream persistence/telemetry stay unchanged.
- Regenerate OpenAPI snapshots and update REST integration tests to cover Base32 success and validation failure scenarios.

### R3 – CLI inline evaluations and replays
- Add `--shared-secret-base32` (protocol-specific variants as needed) for HOTP, TOTP, and OCRA inline commands; reject calls that provide both hex and Base32 values.
- Reuse the shared encoding helper to translate Base32 before building application service commands.
- Update CLI help text/examples and tests to demonstrate the new flag while ensuring telemetry still omits secret material.

### R4 – Operator console inline forms
- Replace the dual shared-secret textareas with a single textarea and a Hex/Base32 mode toggle for HOTP, TOTP, and OCRA inline evaluation/replay panels.
- Keep the displayed value synchronised with the toggle: flipping from one encoding to the other re-encodes the secret, and validation errors surface inline when re-encoding fails.
- Show contextual hints (for example, current encoding and character count) without exposing secret material. Surface only one message row beneath the textarea: it defaults to the conversion hint and swaps to the active validation error (with appropriate alert semantics) while conversion fails. Update Selenium coverage to assert hint defaulting, dynamic error rendering, and mode switching behaviours.

```
+--------------------------------------------------------------+
| Shared secret                                                |
|                                                              |
|  Mode:  [ Hex ] [*Base32 ]          Length:  64 chars        |
|                                                              |
|  ┌────────────────────────────────────────────────────────┐  |
|  │                                                        │  |
|  │  JBSWY3DPEB3W64TMMQ======                              │  |
|  │                                                        │  |
|  └────────────────────────────────────────────────────────┘  |
|                                                              |
|  ↔ Converts automatically when you flip the mode.           |
|  (Switches to ↯ Validation: {invalid_characters} when errors occur) |
+--------------------------------------------------------------+
```

### R5 – Documentation and governance updates
- Update HOTP/TOTP/OCRA operator how-to guides with Base32 instructions.
- Record the encoding helper relationship in the architecture knowledge map and adjust the analysis gate checklist if additional lint commands are introduced.

## Non-Goals
- Stored credential seeding workflows remain hex-based.
- No QR or otpauth URI generation in this feature.

## Acceptance Criteria
- All inline REST endpoints accept Base32 input and return 422 errors for missing or conflicting secrets.
- CLI integration tests demonstrate Base32 flag usage per protocol.
- Operator UI allows Base32 submission end-to-end in Selenium smoke tests without leaking secrets in telemetry, including mode-toggle coverage for the unified shared-secret textarea.
- `./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check` passes with refreshed documentation snapshots.

## Test Strategy
- Unit tests for the encoding helper covering valid/invalid Base32 input.
- REST controller/service tests for Base32 success and error cases across HOTP/TOTP/OCRA.
- Picocli-driven CLI tests asserting flag exclusivity and telemetry fields.
- Selenium/UI integration tests covering Base32 flows and validation messaging.
