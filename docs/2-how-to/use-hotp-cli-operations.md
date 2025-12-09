# Use the HOTP CLI

_Status: Draft_  
_Last updated: 2025-12-09_

The `hotp` Picocli commands cover credential import/listing and OTP generation in a single entry point. Output can be the default key=value telemetry line (with an OTP preview table) or a single JSON object when you pass `--output-json`. Pair `--output-json` with `--verbose` to embed the verbose trace in the response.

## Prerequisites
- Java 17 (`JAVA_HOME` must point to a JDK 17 install).
- Standalone thin JAR `openauth-sim-standalone-<version>.jar` (bundles Picocli + fixtures).
- Stored mode uses MapDB at [data/credentials.db](data/credentials.db) unless overridden with `--database`; inline mode runs against the in-memory EphemeralCredentialStore and does not touch MapDB.

## Command Summary
| Command | Purpose | Output formats |
|---------|---------|----------------|
| `import` | Persist a credential descriptor (secret, digits, counter, algorithm) | Default key=value telemetry line; `--output-json` emits a single JSON object |
| `list` | Show sanitized credential summaries | Default key=value per credential; `--output-json` returns an array of descriptors |
| `evaluate` | Generate an OTP from a stored credential (`--credential-id`) or inline parameters (omit `--credential-id`) | Default key=value telemetry line + preview table + `generatedOtp`; `--output-json` returns a single object; add `--verbose` to include a `trace` field |

Invoke via the standalone JAR:
```bash
java -jar openauth-sim-standalone-<version>.jar hotp [--database <path>] <command> [options]
```

## Import a credential
```bash
java -jar openauth-sim-standalone-<version>.jar hotp import \
  --credential-id operator-demo \
  --secret 3132333435363738393031323334353637383930 \
  --digits 6 \
  --counter 0 \
  --algorithm SHA1 \
  --output-json
```
- Default output: `event=cli.hotp.import status=success credentialId=operator-demo algorithm=SHA1 digits=6 counter=0`.
- `--output-json`: single object with `event/status/reasonCode/telemetryId/sanitized` plus `data` containing the stored attributes.

## List stored credentials
```bash
java -jar openauth-sim-standalone-<version>.jar hotp list --output-json
```
- Default output: one key=value line per credential (`credentialId`, `algorithm`, `digits`, counters, drift windows).
- JSON output: `data.credentials[]` array mirroring the REST directory payload.

## Evaluate an OTP
### Stored credential mode
```bash
java -jar openauth-sim-standalone-<version>.jar hotp evaluate \
  --credential-id operator-demo \
  --window-forward 1 \
  --verbose \
  --output-json
```
- Default output: telemetry line (success/invalid/error) followed by a preview table and `generatedOtp=<value>`; verbose trace printed after the table when `--verbose` is set.
- `--output-json`: single object with `data` fields such as `credentialReference`, `credentialId`, `algorithm`, `digits`, `previousCounter/nextCounter`, `otp`, `previews[]`, and optional `trace` when `--verbose` is present.

### Inline mode
```bash
java -jar openauth-sim-standalone-<version>.jar hotp evaluate \
  --secret 3132333435363738393031323334353637383930 \
  --counter 0 \
  --algorithm SHA1 \
  --digits 6 \
  --window-backward 0 \
  --window-forward 0 \
  --output-json
```
- Inline mode requires `--secret` and `--counter`; omit `--credential-id`.
- Output formatting matches stored mode (key=value + preview table by default; JSON when `--output-json` is supplied).
- Use `--verbose` alongside `--output-json` to embed the verbose trace (`trace` field) in the JSON payload.

## Troubleshooting
- `credential_conflict`: both stored and inline inputs supplied—drop either `--credential-id` or the inline secret/counter.
- `validation_error`: missing inline parameters (`--secret`, `--counter`) or bad hex; JSON output includes `reason` when available.
- `unexpected_error`: unexpected failure; exit code is non-zero and the message is sanitized in both text and JSON forms.

## Related guides
- [Drive HOTP evaluations from Java applications](use-hotp-from-java.md)
- [Use the HOTP operator UI](use-hotp-operator-ui.md)
- [Configure MapDB persistence profiles](configure-persistence-profiles.md)
