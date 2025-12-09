# Use the TOTP CLI

_Status: Draft_  
_Last updated: 2025-12-09_

The `totp` Picocli entry point lists stored TOTP credentials and validates OTPs in stored or inline mode. Output can be the default key=value telemetry line (with a preview table) or a single JSON object when you pass `--output-json`. Pair `--output-json` with `--verbose` to embed the verbose trace. Quick-reference defaults live in the [CLI flags matrix](../3-reference/cli-flags-matrix.md).

## Prerequisites
- Java 17 (`JAVA_HOME` must point to a JDK 17 install).
- Standalone thin JAR `openauth-sim-standalone-<version>.jar`.
- Stored mode reads MapDB at [data/credentials.db](data/credentials.db) unless overridden with `--database`; inline mode uses an in-memory store and does not touch MapDB.

## Command Summary
| Command | Purpose | Output formats |
|---------|---------|----------------|
| `list` | Show sanitized stored credential summaries | Default key=value lines; `--output-json` returns an array matching the REST directory |
| `evaluate` | Validate a stored credential (`--credential-id`) or inline parameters (omit `--credential-id`) at a given timestamp | Default key=value telemetry line + preview table; `--output-json` returns a single object; `--verbose` adds a `trace` field to JSON and prints the trace in text mode |

Invoke via the standalone JAR:
```bash
java -jar openauth-sim-standalone-<version>.jar totp [--database <path>] <command> [options]
```

## List stored credentials
```bash
java -jar openauth-sim-standalone-<version>.jar totp list --output-json
```
- Default output: one line per credential (`credentialId`, `algorithm`, `digits`, `stepSeconds`, drift windows).
- JSON output: `data.credentials[]` array aligned with the REST directory response.

## Evaluate a TOTP
### Stored credential mode
```bash
java -jar openauth-sim-standalone-<version>.jar totp evaluate \
  --credential-id demo-totp \
  --timestamp 1700000000 \
  --window-forward 1 \
  --verbose \
  --output-json
```
- Default output: telemetry line (success/invalid/error) then a preview table; verbose trace printed when `--verbose` is set.
- `--output-json`: single object with `data` fields such as `credentialReference`, `credentialId`, `valid`, `matchedSkewSteps`, `algorithm`, `digits`, `stepSeconds`, `driftBackwardSteps`, `driftForwardSteps`, `otp` (when valid), `previews[]`, and optional `trace` when `--verbose` is present.
- JSON schema for `--output-json`: [docs/3-reference/cli/output-schemas/totp-evaluate.schema.json](../3-reference/cli/output-schemas/totp-evaluate.schema.json)

### Inline mode
```bash
java -jar openauth-sim-standalone-<version>.jar totp evaluate \
  --secret-base32 JBSWY3DPEHPK3PXP \
  --algorithm SHA1 \
  --digits 6 \
  --step-seconds 30 \
  --timestamp 1700000000 \
  --window-backward 0 \
  --window-forward 0 \
  --output-json
```
- Provide exactly one of `--secret` (hex) or `--secret-base32`; omit `--credential-id` for inline mode.
- Output formatting matches stored mode (key=value by default; JSON with `--output-json`). Use `--verbose` to include the verbose trace.
- Optional `--timestamp-override` lets you simulate authenticator clock skew; the override is reflected in verbose traces and JSON.

## Troubleshooting
- Quick failure drill (JSON): passing both stored and inline inputs triggers `credential_conflict`.  
  ```bash
  java -jar openauth-sim-standalone-<version>.jar totp evaluate \
    --credential-id demo-totp \
    --secret-base32 JBSWY3DPEHPK3PXP \
    --output-json
  ```
  Output (truncated): `{"event":"cli.totp.evaluate","status":"invalid","reasonCode":"credential_conflict","sanitized":true,"data":{"reason":"stored and inline parameters cannot be combined"}}` with exit code `64`.
- `validation_error`: missing or conflicting secrets (`--secret` vs `--secret-base32`), inline secrets provided together with `--credential-id`, or bad hex/Base32 input.
- `unexpected_error`: any other failure; exit code is non-zero and the message is sanitized in both text and JSON output.

## Related guides
- [Drive TOTP evaluations from Java applications](use-totp-from-java.md)
- [Use the TOTP operator UI](use-totp-operator-ui.md)
- [Configure MapDB persistence profiles](configure-persistence-profiles.md)
