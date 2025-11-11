# Feature 005 – CLI OCRA Operations

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/005/plan.md` |
| Linked tasks | `docs/4-architecture/features/005/tasks.md` |
| Roadmap entry | #5 |

## Overview
Bring the CLI facade to parity with the REST OCRA experience by adding commands to import, list/show, delete, and evaluate credentials. Evaluation must support both credential-id lookup (persisted descriptors) and inline secrets, matching REST validation, telemetry, and redaction semantics.

## Clarifications
- 2025-09-28 – CLI reuses the existing `CredentialStore` and persistence adapters; no new storage technology required.
- 2025-09-28 – `ocra evaluate` accepts either `--credential-id` or inline suite/secret options, enforcing mutual exclusivity just like the REST API.
- 2025-09-28 – CLI output remains terminal-friendly text with structured log lines (`event=cli.ocra.* status=... sanitized=true`) for telemetry parity; no JSON output required.
- 2025-09-28 – Commands are Picocli subcommands under the existing CLI entry point; authentication/rate limiting remains out of scope because CLI runs in a trusted operator context.

## Goals
- Persist OCRA descriptors via CLI and inspect them without revealing secrets.
- Delete stored descriptors when no longer needed.
- Evaluate OTPs using either stored descriptors (by name/ID) or inline parameters, producing the same results as the REST/core calculators.
- Mirror REST validation/telemetry reason codes to keep operator tooling consistent.

## Non-Goals
- Adding replay/audit workflows (tracked elsewhere).
- Modifying REST/UI behaviour beyond parity.
- Introducing new credential types.

## Functional Requirements

### FR1 – Import stored descriptors (CLI-OCRA-001, S05-01)
- **Requirement:** Provide `ocra import` (or equivalent) to persist descriptors from supplied suite/secret/counter/PIN data.
- **Success path:** Command stores the descriptor and prints sanitized confirmation; descriptor appears in `ocra list`.
- **Validation path:** Missing required options produce usage errors and `event=cli.ocra.import status=FAILED reasonCode=<...>` log lines.
- **Failure path:** Persistence failures surface sanitized error messages and non-zero exit codes.
- **Telemetry & traces:** `cli.ocra.import` events include suite, hasPin, hasCounter, sanitized flag.
- **Source:** Original FR table + clarifications.

### FR2 – List/show descriptors (CLI-OCRA-002, S05-02)
- **Requirement:** Provide `ocra list` / `ocra show --name <id>` to enumerate stored descriptors with redacted metadata.
- **Success path:** Commands display credential name, suite, optional metadata (counter/PIN markers) without revealing secret bytes.
- **Validation path:** Listing works even when store is empty; show for missing name returns descriptive error.
- **Failure path:** Persistence issues reported with sanitized messages and telemetry.
- **Telemetry & traces:** `cli.ocra.list` / `cli.ocra.show` events include counts and reason codes for missing entries.
- **Source:** Spec + scenario matrix.

### FR3 – Delete descriptors (CLI-OCRA-003, S05-03)
- **Requirement:** Provide `ocra delete --name <id>` to remove descriptors, reporting success or missing credential.
- **Success path:** Deleted descriptor no longer appears in listings; command exits 0.
- **Validation path:** Missing descriptors return `reasonCode=credential_not_found` and exit with non-zero status.
- **Failure path:** Persistence errors logged/surfaced with sanitized data.
- **Telemetry & traces:** `cli.ocra.delete` events track `status` and reason codes.
- **Source:** Spec FR table + scenario matrix.

### FR4 – Evaluate stored/inline descriptors (CLI-OCRA-004, S05-04)
- **Requirement:** Provide `ocra evaluate` supporting either `--credential-id <id>` lookup or inline suite/secret inputs with mutual exclusivity.
- **Success path:** OTPs match REST/core results; command prints sanitized output including OTP and telemetry ID.
- **Validation path:** Conflicting or missing mode arguments return `credential_conflict`/`credential_missing`; invalid inline data reuses REST checks.
- **Failure path:** Lookup failures emit `credential_not_found`; other validation errors propagate reason codes.
- **Telemetry & traces:** `cli.ocra.evaluate` events include `hasCredentialReference`, reasonCode, sanitized flag.
- **Source:** Spec FR table + clarifications.

### FR5 – Telemetry/logging parity (CLI-OCRA-005, S05-05)
- **Requirement:** All CLI commands emit redacted telemetry/log output matching constitution rules (event name, status, reasonCode, sanitized).
- **Success path:** Log capture tests confirm secrets are absent and reason codes recorded.
- **Validation path:** Commands exit non-zero when telemetry would report failures.
- **Failure path:** Missing telemetry fields fails tests.
- **Telemetry & traces:** `cli.ocra.*` events documented in knowledge map/how-to.
- **Source:** Spec FR table + telemetry expectations.

## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Source |
|----|-------------|--------|-------------|--------|
| CLI-NFR-001 | Commands accept human-friendly arguments, print usage, and exit deterministically. | Operator ergonomics | Picocli tests verifying usage/help output and exit codes. | Spec table |
| CLI-NFR-002 | Logging follows telemetry format with sanitized fields and reason codes. | Observability | Log capture tests for `event=cli.ocra.* sanitized=true`. | Spec table |
| CLI-NFR-003 | Remain Java 17-compatible; no new dependencies. | Compatibility | `./gradlew :cli:test spotlessApply check`. | Spec table |

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S05-01 | `ocra import` persists descriptors via CredentialStore and prints sanitized confirmation. |
| S05-02 | `ocra list/show` enumerate descriptors with redacted metadata. |
| S05-03 | `ocra delete` removes targeted descriptor and reports missing credentials gracefully. |
| S05-04 | `ocra evaluate` supports stored or inline inputs with mutual exclusivity, returning OTPs identical to REST/core. |
| S05-05 | All CLI commands emit sanitized telemetry/logging with deterministic exit codes. |

## Test Strategy
- **Unit tests:** Picocli argument parsing, mutual exclusivity enforcement, telemetry logging, and persistence helpers.
- **Integration tests:** Picocli command tests running `ocra import/list/delete/evaluate` end-to-end using in-memory persistence.
- **Fixture reuse:** Leverage RFC 6287 vectors to assert OTP parity with REST/core flows.

## Interface & Contract Catalogue

### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-005-01 | `OcraCliImportCommand` options (name, suite, secret, counter, PIN hash, drift). | cli |
| DO-005-02 | `OcraCliEvaluateCommand` options for stored vs inline modes. | cli |
| DO-005-03 | `OcraCliTelemetryEvent` payload fields (event, status, reasonCode, sanitized). | cli |

### CLI Commands
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-005-01 | `ocra import` | Persist descriptors from provided arguments/fixtures. |
| CLI-005-02 | `ocra list` / `ocra show` | Enumerate stored descriptors (redacted). |
| CLI-005-03 | `ocra delete --name <id>` | Remove a stored descriptor. |
| CLI-005-04 | `ocra evaluate (--credential-id <id> | --suite ... --secret ...)` | Evaluate OTPs via stored or inline mode. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-005-01 | `cli.ocra.import` | `credentialName`, `suite`, `hasCounter`, `hasPin`, `status`, `reasonCode`, `sanitized=true`. |
| TE-005-02 | `cli.ocra.evaluate` | `hasCredentialReference`, `suite`, `status`, `reasonCode`, `sanitized=true`. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-005-01 | `cli/src/test/resources/fixtures/ocra/*.json` | Sample descriptors for import tests. |
| FX-005-02 | `docs/test-vectors/ocra/rfc-6287/*.json` | RFC vectors reused for OTP verification. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| — | Not applicable | CLI-only feature. |

## Telemetry & Observability
CLI commands log via structured `event=cli.ocra.*` lines mirroring REST telemetry (status, reasonCode, sanitized flag). Knowledge map and how-to guides reference these events so operators can script around exit codes + telemetry output.

## Documentation Deliverables
- `docs/2-how-to/use-cli-ocra-operations.md` (or relevant section) updated with command usage and telemetry expectations.
- Roadmap/knowledge map entries referencing CLI/REST parity.

## Fixtures & Sample Data
- Import fixtures and RFC vectors stored alongside tests; telemetry samples archived after verification.

## Spec DSL
```
domain_objects:
  - id: DO-005-01
    name: OcraCliImportCommand
    fields:
      - name: name
        type: string
      - name: suite
        type: string
      - name: sharedSecretHex
        type: string
  - id: DO-005-02
    name: OcraCliEvaluateCommand
    fields:
      - name: credentialId
        type: string
        constraints: optional, mutually exclusive with inline options
      - name: inlineOptions
        type: object
        constraints: required when credentialId absent
commands:
  - id: CLI-005-04
    command: ocra evaluate
    description: Evaluate OTPs via stored or inline mode
telemetry_events:
  - id: TE-005-02
    event: cli.ocra.evaluate
    fields:
      - name: hasCredentialReference
        redaction: none (boolean)
fixtures:
  - id: FX-005-02
    path: docs/test-vectors/ocra/rfc-6287/*.json
```

## Appendix
- `docs/4-architecture/features/005/plan.md`
- `docs/4-architecture/features/005/tasks.md`
- `docs/2-how-to/use-cli-ocra-operations.md`
