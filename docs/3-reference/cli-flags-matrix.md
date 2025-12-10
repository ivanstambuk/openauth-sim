# CLI Flags Matrix

_Status: Draft_
_Last updated: 2025-12-09_

JSON schemas for `--output-json` payloads now live in a single global registry at [docs/3-reference/cli/cli.schema.json](cli/cli.schema.json), with one definition per CLI JSON `event` (for example `cli.hotp.evaluate`, `cli.ocra.verify`, `cli.fido2.replay`). Historical per-command schema files under `docs/3-reference/cli/output-schemas` have been removed; the global registry is the only authoritative source.

Quick reference for the Picocli facades. Each table lists the mandatory vs optional flags, their defaults, and how JSON/trace output behaves. Use the per-protocol how-tos for end-to-end examples.

**Conventions**
- `--output-json` emits a single object; pair with `--verbose` (or `--include-trace` for EMV/CAP) to attach the verbose trace. Without it, traces print only in text mode.
- `--database` points at the shared MapDB store and defaults to `data/credentials.db`. Inline-only flows ignore it and use in-memory stores.
- Stored mode = flag or preset that resolves a descriptor; inline mode = secret/material supplied on the command line. Mixing stored and inline inputs fails validation.

## HOTP (use alongside [HOTP CLI guide](../2-how-to/use-hotp-cli-operations.md))
| Flag | Required when | Default | JSON / trace notes |
| --- | --- | --- | --- |
| `--database` | Stored commands (`import`, `list`, stored `evaluate`) | `data/credentials.db` | Not emitted. |
| `--credential-id` | Required for `import` and stored `evaluate`; omit for inline evaluate | — | Included in JSON responses for stored mode. |
| `--secret` | Required for `import` and inline `evaluate` | — | Never echoed in text/JSON. |
| `--counter` | Required for inline `evaluate`; optional on `import` | `0` on `import` | JSON shows previous/next counters. |
| `--digits` | Inline `evaluate` / `import` | `6` | Reflected in JSON. |
| `--algorithm` | Inline `evaluate` / `import` | `SHA1` | Upper-cased; reflected in JSON. |
| `--window-backward`, `--window-forward` | `evaluate` (both modes) | `0` | Drives preview rows; included in JSON. |
| `--metadata` | Inline `evaluate` / `import` | empty | Included in JSON. |
| `--verbose` | `evaluate` | `false` | Adds `trace` when combined with `--output-json`; prints trace in text. |
| `--output-json` | All commands | text output | Mirrors REST schema; add `--verbose` for trace field. |

## TOTP (use alongside [TOTP CLI guide](../2-how-to/use-totp-cli-operations.md))
| Flag | Required when | Default | JSON / trace notes |
| --- | --- | --- | --- |
| `--database` | Stored commands (`list`, stored `evaluate`) | `data/credentials.db` | Not emitted. |
| `--credential-id` | Stored `evaluate` | — | Included in JSON. |
| `--secret` / `--secret-base32` | Exactly one required for inline `evaluate` | — | Secret never echoed. Base32 is normalised to hex. |
| `--algorithm` | Inline `evaluate` | `SHA1` | Included in JSON. |
| `--digits` | Inline `evaluate` | `6` | Included in JSON. |
| `--step-seconds` | Inline `evaluate` | `30` | Included in JSON. |
| `--timestamp` | Optional evaluation time (stored or inline) | current clock | Included in JSON when supplied. |
| `--timestamp-override` | Optional authenticator override | — | Reflected in trace/JSON when present. |
| `--window-backward`, `--window-forward` | `evaluate` (both modes) | `0` | Controls preview window; included in JSON. |
| `--verbose` | `evaluate` | `false` | Adds `trace` with `--output-json`; prints trace in text. |
| `--output-json` | All commands | text output | Mirrors REST schema; add `--verbose` for trace field. |

## OCRA (use alongside [OCRA CLI guide](../2-how-to/use-ocra-cli-operations.md))
| Flag | Required when | Default | JSON / trace notes |
| --- | --- | --- | --- |
| `--database` | Stored commands (`import`, `list`, `delete`, stored `evaluate`/`verify`) | `data/credentials.db` | Not emitted. |
| `--credential-id` | Stored `evaluate` / `verify` | — | Included in JSON. |
| `--suite` | Inline `evaluate` / `verify` | — | Required inline; echoed in JSON. |
| `--secret` / `--secret-base32` | Inline `evaluate` / `verify` | — | Exactly one; never echoed. |
| `--challenge` / `--client-challenge` / `--server-challenge` | Required when the suite expects them | — | Passed through to JSON/trace. |
| `--session` / `--timestamp` / `--pin-hash` | When suite requires data/session/timestamp inputs | — | Included in JSON/trace when provided. |
| `--counter` | Required for counter-based suites (evaluate/verify) | — | Included in JSON/trace. |
| `--otp` | Required for `verify` | — | Not echoed; result fields show match/mismatch. |
| `--window-backward`, `--window-forward` | `evaluate` (stored/inline) | `0` | Controls preview list; included in JSON. |
| `--verbose` | `evaluate` / `verify` | `false` | Adds `trace` with `--output-json`; prints trace in text. |
| `--output-json` | All commands | text output | Mirrors REST schema for evaluate/verify/delete. |

## EMV/CAP (use alongside [EMV/CAP CLI guide](../2-how-to/use-emv-cap-cli-operations.md))
| Flag | Required when | Default | JSON / trace notes |
| --- | --- | --- | --- |
| `--database` | Stored commands (`seed`, stored `evaluate`, stored `replay`) | `data/credentials.db` | Not emitted. |
| `--credential-id` | Stored `evaluate` / `replay` | — | Included in JSON/telemetry. |
| `--mode` | Stored `evaluate` overrides; required for inline `evaluate` / `replay` | stored descriptor mode / `IDENTIFY` inline fallback | Included in JSON. |
| `--master-key`, `--atc`, `--branch-factor`, `--height`, `--iv`, `--cdol1`, `--issuer-proprietary-bitmap` | Required for inline `evaluate` and inline `replay` | — | Included in JSON; secrets stay sanitized. |
| `--challenge` | Required in RESPOND/SIGN | — | Included in JSON. |
| `--reference`, `--amount` | Required in SIGN | — | Included in JSON. |
| `--search-backward`, `--search-forward` | `replay` (stored/inline) | `0` | Included in JSON. |
| `--window-backward`, `--window-forward` | `evaluate` (stored/inline) | `0` | Included in JSON. |
| `--include-trace` | `evaluate`/`replay` | `true` | Controls verbose trace inclusion in text/JSON. |
| `--output-json` | `seed` / `evaluate` / `replay` | text output | Pretty-prints REST-equivalent payload; trace present when `--include-trace` is true. |

## FIDO2/WebAuthn (use alongside [FIDO2 CLI guide](../2-how-to/use-fido2-cli-operations.md))
| Flag | Required when | Default | JSON / trace notes |
| --- | --- | --- | --- |
| `--database` | Stored commands (evaluate/replay/seed/attest-replay/seed-attestations) | `data/credentials.db` | Not emitted. |
| `--preset-id` | Common for `evaluate`, `replay`, `attest`, `attest-replay`, `vectors` | — | Drives fixture selection; included in JSON. |
| `--credential-id` | Stored `evaluate` / stored `replay` | — | Included in JSON. |
| `--signature-counter` | Optional counter for `evaluate` | derived from current Unix seconds (uint32) when omitted | Reflected in JSON/telemetry with `counterDerived=true`. |
| `--algorithm` / `--private-key` / `--private-key-file` | Required for manual inline assertions/attestations when not using presets | — | Included in JSON; keys stay sanitized. |
| `--user-verification-required` | Inline assertions | `false` unless flag present without value (then `true`) | Included in JSON/telemetry. |
| `--verbose` | All subcommands | `false` | Adds `trace` field with `--output-json`; prints trace in text. |
| `--output-json` | `evaluate`, `replay`, `attest`, `attest-replay`, `seed-attestations`, `vectors` | text output | Emits single object; trace included when `--verbose` is set. |

## EUDIW OpenID4VP (use alongside [EUDIW CLI guide](../2-how-to/use-eudiw-cli-operations.md))
| Flag | Required when | Default | JSON / trace notes |
| --- | --- | --- | --- |
| `--profile` | All subcommands | `HAIP` | Included in JSON/telemetry. |
| `--response-mode` | `request create` | `DIRECT_POST_JWT` | Included in JSON. |
| `--dcql-preset` / `--dcql-json` | Exactly one required for `request create` | — | DCQL echoed in JSON/trace (masked). |
| `--include-qr` | `request create` | `false` | When true, JSON includes QR ASCII/URI. |
| `--wallet-preset` / inline `--inline-sdjwt` (+ optional `--disclosure` / `--kb-jwt`) | One required for `wallet simulate` | — | Payload echoed; secrets stay sanitized. |
| `--request-id` | `wallet simulate` / `validate` | — | Included in JSON. |
| `--verbose` | All subcommands | `false` | Adds trace maps to JSON when paired with `--output-json`. |
| `--output-json` | `request create`, `wallet simulate`, `validate`, `seed`, `vectors` | text output | Emits single object; traces only when `--verbose` is true. |
