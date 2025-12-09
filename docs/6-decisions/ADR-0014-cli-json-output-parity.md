# ADR-0014 – CLI JSON Output Parity

| Status | Proposed |
|--------|----------|
| Date | 2025-12-09 |
| Owners | Ivan (project owner) |
| Related features | 001 (HOTP), 002 (TOTP), 003 (OCRA), 004 (FIDO2/WebAuthn), 005 (EMV/CAP), 006 (EUDIW), 015 (CLI/MCP facade) |

## Context

CLI commands currently default to human-readable text lines and, for some protocols, optional verbose tables/traces. Only a subset (EMV/CAP, EUDIW) provide `--output-json`, leaving automation clients to scrape text for HOTP/TOTP/OCRA/FIDO2 flows. We need a uniform, machine-consumable JSON output mode across all authentication protocols and commands (evaluate, replay/verify, seed/import, list, etc.) while preserving existing text defaults for human operators.

## Decision

- Every CLI command across all authentication protocols MUST support an `--output-json` flag.
- When `--output-json` is present, the command prints a single JSON object to stdout and nothing else. The schema includes at minimum: `event`, `status`, `reasonCode`, and the protocol-specific payload (`otp`, assertion, trace, etc.). Verbose traces (when `--verbose` is also set) are embedded in the JSON as structured fields.
- Exit codes remain authoritative for success/failure and are unchanged.
- Default (no flag) behaviour stays as the current human-readable text output.
- Documentation (specs, plans, tasks, how-to guides) must reflect the new flag for every protocol.

## Consequences

- Specs for Features 001–006 and 015 must list `--output-json` as a required CLI flag with JSON schema expectations.
- New tasks are added to each feature to implement and test JSON output parity.
- CI/CLI tests need coverage for `--output-json` across all commands, including the standalone thin jar.
- Existing consumers that parse the first line can continue unchanged; automation can switch to JSON for stability.

## Alternatives Considered

- Text-only plus external parsers: rejected (fragile, inconsistent across commands).
- Per-command bespoke JSON flags: rejected (inconsistent UX).

## Notes

- Implementation is deferred to future increments; this ADR records the mandate and scope.
