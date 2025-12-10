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
- When `--output-json` is present, the command prints a single JSON object to stdout and nothing else. The schema always uses a shared event envelope with top-level fields `event`, `status`, `reasonCode`, `telemetryId`, `sanitized`, and a required `data` object that carries the protocol-specific payload (`otp`, assertion, trace, etc.). Verbose traces (when `--verbose` is also set) are embedded in `data` as structured fields.
- Exit codes remain authoritative for success/failure and are unchanged.
- Default (no flag) behaviour stays as the current human-readable text output.
- Documentation (specs, plans, tasks, how-to guides) must reflect the new flag for every protocol.
- For **verify/replay**-style events, envelope `status` is constrained to `success`, `invalid`, or `error`; domain outcomes such as `match`, `strict_mismatch`, `otp_mismatch`, or `signature_invalid` belong in the envelope `reasonCode` (and, when present, `data.reasonCode`). This increment applies the rule to `cli.ocra.verify`, `cli.emv.cap.replay`, and FIDO2 replay/attest-replay commands; the remaining replay-style events will be aligned in later slices if any emerge.
- For **OCRA CLI events** (`cli.ocra.import`, `cli.ocra.list`, `cli.ocra.delete`, `cli.ocra.evaluate`, and `cli.ocra.verify`), envelope `reasonCode` MUST be drawn from a closed, per-event enum documented in `docs/3-reference/cli/cli.schema.json`; where `data.reasonCode` is present, it MUST mirror the envelope value. Human-readable diagnostics continue to live in `data.reason` (or RFC 7807-style problem-details objects) rather than overloading `reasonCode`.
- For **HOTP, TOTP, EMV/CAP, and FIDO2 CLI events**, envelope `reasonCode` MUST also be drawn from closed, per-event enums in `docs/3-reference/cli/cli.schema.json`, reflecting the existing telemetry/adaptor reason codes (`generated`, `match`, `invalid_input`, `credential_not_found`, `validation_error`, `unexpected_error`, and protocol-specific variants such as `private_key_invalid`, `attestation_private_key_required`, `invalid_request`, etc.). Where `data.reasonCode` is present (for example, replay/evaluate outputs), it MUST mirror the envelope value. Free-form explanation strings belong in `data.reason` or problem-details payloads, not in `reasonCode`.
- **EUDIW CLI events** (`cli.eudiw.request.create`, `cli.eudiw.wallet.simulate`, `cli.eudiw.validate`) surface RFC 7807 problem details; their envelope `reasonCode` carries the problem `type` or a short error code (for example, `invalid_request`, the `invalid_scope` type URI), and is intentionally left open-ended to match the underlying OpenID4VP specification. Validation for these events focuses on the `data` payload shape and problem-details structure rather than a closed `reasonCode` enum.

## Consequences

- Specs for Features 001–006 and 015 must list `--output-json` as a required CLI flag with JSON schema expectations.
- New tasks are added to each feature to implement and test JSON output parity.
- CI/CLI tests need coverage for `--output-json` across all commands, including the standalone thin jar.
- Existing consumers that parse the first line can continue unchanged; automation can switch to JSON for stability.
- CLI JSON-output schemas for all events are maintained in a single global registry at `docs/3-reference/cli/cli.schema.json`, with one definition per CLI `event` (for example, `cli.hotp.evaluate`, `cli.ocra.verify`, `cli.fido2.replay`); any per-command schema files under `docs/3-reference/cli/output-schemas` are legacy references only.

## Alternatives Considered

- Text-only plus external parsers: rejected (fragile, inconsistent across commands).
- Per-command bespoke JSON flags: rejected (inconsistent UX).

## Notes

- Implementation is deferred to future increments; this ADR records the mandate and scope.

## Schema registry guardrails

- The OpenCLI-style `commands` tree and the `definitions` map inside `docs/3-reference/cli/cli.schema.json` are both normative.
- Every `commands[].metadata[].value.event` value MUST have a matching `definitions` entry keyed by the same event name.
- The CLI test suite enforces this relationship so miswired or stale command metadata cannot drift silently.

### CLI schema registry format

- `docs/3-reference/cli/cli.schema.json` is authored against the OpenCLI meta-schema (`$schema: "https://opencli.org/draft.json"`).
- Per-event JSON Schemas live under `definitions[...]` (and, where present, `commands[].metadata[].value.schema`) and declare `$schema: "http://json-schema.org/draft-07/schema#"` at the fragment level.
- Only those embedded Draft-07 fragments are intended for generic JSON Schema tooling; the document as a whole is an OpenCLI descriptor, not a standalone Draft-07 schema.
- Repo tooling (CLI tests, MCP, future consumers) should parse the file as JSON and extract `definitions[...]` when performing JSON Schema validation.
