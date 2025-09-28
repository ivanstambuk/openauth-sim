# Feature 005 – CLI OCRA Operations Specification

_Status: Draft_
_Last updated: 2025-09-28_

## Overview
Extend the CLI facade so operators can manage and evaluate OCRA credentials end-to-end from the command line. The tool should support importing descriptors into the persistence layer, listing/querying stored OCRA credentials, deleting them, and computing OTPs using either stored descriptors (by name) or inline secret material. Behaviour, validation, and telemetry should mirror the REST facade where applicable.

## Clarifications
- 2025-09-28 – CLI will reuse the existing `CredentialStore` and `OcraCredentialPersistenceAdapter` to persist descriptors; no new storage mechanism is introduced.
- 2025-09-28 – Evaluation command must accept either `--credential-id` (lookup stored descriptor) or inline secret options (`--suite`, `--shared-secret`, etc.), enforcing mutual exclusivity like the REST API.
- 2025-09-28 – Output remains terminal friendly (stdout/stderr) with structured log lines for telemetry parity; no JSON serialization required for CLI responses.
- 2025-09-28 – CLI subcommands will be implemented with Picocli; telemetry-style output follows the `event=cli.ocra.* status=<...> sanitized=true` pattern to mirror REST logging semantics.
- 2025-09-28 – Authentication, rate limiting, or multi-user coordination are out of scope; CLI is assumed to run in a trusted operator context.

## Objectives & Success Criteria
- Provide commands to create/import OCRA credentials (from fixtures or arguments) and persist them via `CredentialStore`.
- Allow listing and inspecting stored OCRA descriptors (name, suite, optional counter/PIN metadata) without exposing secret values.
- Support evaluation that reuses stored descriptors or inline parameters, returning OTPs identical to the REST endpoint.
- Align validation/error messages and reason codes with existing REST behaviour where sensible; telemetry/logging must remain sanitized.

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| CLI-OCRA-001 | Add `cli ocra import` (or equivalent) to persist OCRA descriptors from provided suite/secret/counter/PIN data. | Running the command stores the descriptor and it appears in subsequent list queries. |
| CLI-OCRA-002 | Add `cli ocra list` / `show` to enumerate descriptors with redacted metadata. | Command outputs stored credential names, suites, and metadata without revealing secrets. |
| CLI-OCRA-003 | Add `cli ocra delete <name>` to remove a stored descriptor. | Command reports success/failure; descriptor removed from persistence. |
| CLI-OCRA-004 | Enhance evaluation command to accept either `--credential-id` (lookup) or inline secret parameters, mirroring REST validation (`credential_conflict`, `credential_missing`, etc.). | Evaluating the same suite via CLI returns OTPs matching REST/core; invalid combinations produce descriptive errors. |
| CLI-OCRA-005 | Surface telemetry/logging for CLI operations consistent with constitution rules (no secrets, include reason codes). | Unit/integration tests capture logs verifying absence of sensitive data and presence of reason codes. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| CLI-NFR-001 | Developer ergonomics | Commands accept human-friendly arguments, provide usage help, and exit with appropriate codes. |
| CLI-NFR-002 | Observability | Logging aligns with existing telemetry format (event names, reason codes, sanitized flags). |
| CLI-NFR-003 | Compatibility | Java 17 CLI application; no additional dependencies without approval. |

## CLI UX Outline
- `ocra import --name <id> --suite <suite> --secret <hex> [--counter <n>] [--pin-hash <hex>] [--drift <seconds>]`
- `ocra list` (optionally `--verbose`)
- `ocra show --name <id>` (optional detailed view)
- `ocra delete --name <id>`
- `ocra evaluate (--credential-id <id> | --suite <suite> --secret <hex>) [--challenge ...] [--session ...] [--timestamp ...]`

## Test Strategy
- **Unit tests** for CLI argument parsing, mutual exclusivity checks, and persistence interactions using in-memory stores.
- **Integration tests** (Picocli test harness) executing `ocra import/list/delete/evaluate` commands end-to-end, validating both credential-id lookup and inline secret modes with redacted output expectations.
- Reuse existing core fixtures to assert OTP results match RFC vectors.

## Dependencies & Out of Scope
- Leverage Picocli (already in project) and existing `MaintenanceCli` infrastructure.
- No new credential types/protocols beyond OCRA for this feature.
- REST or UI updates are out of scope; future facades will follow separate specs.

Update this specification as additional clarifications arise.
