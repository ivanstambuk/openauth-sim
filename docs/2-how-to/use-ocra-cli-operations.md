# How to Operate the OCRA CLI

This guide is for operators who manage the simulator from the command line. It covers every Picocli subcommand shipped with the `ocra` tool, including credential lifecycle, evaluation flows, and database maintenance. Outputs are sanitized so secrets never appear in logs.

## Prerequisites
- Java 17 JDK configured (`JAVA_HOME` must point to it per the project constitution).
- Repository cloned locally with Gradle available.
- Ensure the default MapDB database path (`data/credentials.db`) is writable. Override with `--database` when needed; legacy filenames such as `data/ocra-credentials.db` are still detected automatically.

Warm up the CLI module once so dependencies compile:
```bash
./gradlew :cli:classes
```

## Command Summary
| Command | Purpose |
|---------|---------|
| `import` | Persist a credential descriptor (suite + secret or derived metadata) |
| `list` | Display sanitized credential summaries |
| `delete` | Remove a credential descriptor |
| `evaluate` | Generate an OTP using stored or inline credential data |
| `verify` | Replay and validate an operator-supplied OTP without mutating counters |
| `maintenance compact` | Run MapDB compaction to reclaim disk space |
| `maintenance verify` | Run integrity checks against the MapDB store |

Invoke commands via the Gradle helper:
```bash
./gradlew :cli:runOcraCli --args="[--database <path>] <command> [options]"
```
If `--database` is omitted, the CLI asks `CredentialStoreFactory` to resolve the path and defaults to `data/credentials.db` (shared with REST/UI facades). When only a legacy file exists, the resolver logs the fallback decision and continues using that path so upgrades remain non-breaking.

All commands emit structured telemetry lines (see `docs/3-reference/cli-ocra-telemetry-snapshot.md`) with `sanitized=true`.

## 1. Import a Credential Descriptor
Seed the database with a reusable credential:
```bash
./gradlew :cli:runOcraCli --args="import \
  --credential-id operator-demo \
  --suite OCRA-1:HOTP-SHA256-8:QA08-S064 \
  --secret 3132333435363738393031323334353637383930313233343536373839303132"
```
Success emits `reasonCode=created`. Use `--database` to target a different file.

## 2. List Stored Credentials
```bash
./gradlew :cli:runOcraCli --args="list"
```
The output shows sanitized metadata (suite, creation time, optional counter snapshots). Add `--verbose` for extended attributes.

## 3. Evaluate an OTP
**Stored credential mode** uses the descriptor from MapDB:
```bash
./gradlew :cli:runOcraCli --args="evaluate \
  --credential-id operator-demo \
  --challenge SESSION01 \
  --session-hex 00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
```

**Inline mode** sends suite + secret directly:
```bash
./gradlew :cli:runOcraCli --args="evaluate \
  --suite OCRA-1:HOTP-SHA256-8:QA08-S064 \
  --secret 3132333435363738393031323334353637383930313233343536373839303132 \
  --challenge SESSION01 \
  --session-hex 00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
```
Successful evaluations print the OTP and telemetry ID. If required parameters are missing, the CLI returns `reasonCode` values such as `session_required`, `counter_required`, or `credential_conflict`.

## 4. Verify Historical OTPs
Replays confirm whether a supplied OTP matches what the simulator would have produced previously. The verifier never mutates counters or session state, so you can run it repeatedly for audits.

### 4.1 Stored credential mode
Use descriptors already persisted in MapDB. Provide the OTP plus the context originally used to generate it.
```bash
./gradlew :cli:runOcraCli --args="verify \
  --credential-id operator-demo \
  --otp 17477202 \
  --challenge SESSION01 \
  --session-hex 00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
```
Exit code `0` signals a match, while `2` indicates `reasonCode=strict_mismatch`. Missing or malformed context returns exit code `64` with `reasonCode=validation_failure`.

### 4.2 Inline secret mode
Supply the suite and secret directly when no stored credential exists.
```bash
./gradlew :cli:runOcraCli --args="verify \
  --suite OCRA-1:HOTP-SHA256-8:QA08-S064 \
  --secret 3132333435363738393031323334353637383930313233343536373839303132 \
  --otp 17477202 \
  --challenge SESSION01 \
  --session-hex 00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
```
Inline verification shares exit codes with the stored path.

### 4.3 Audit interpretation
Every run emits `event=cli.ocra.verify` with hashed payloads (`otpHash`, `contextFingerprint`) so you can correlate findings without exposing secrets. Example lines live in `docs/3-reference/cli-ocra-telemetry-snapshot.md`. Capture the `telemetryId`, `credentialSource`, and `reasonCode` fields when recording audits—`match` confirms the OTP is legitimate, `strict_mismatch` proves an exact replay failed, and `validation_failure` means the operator-provided context was incomplete.

### 4.4 Failure scenarios
- Supply the wrong OTP to rehearse incident handling. The command exits with status `2` and prints `reasonCode=strict_mismatch`.
- If both `--credential-id` and `--secret` are set, you receive `reasonCode=credential_conflict`.
- Timestamp or counter drift is never corrected—re-submit with the precise historical values from your logs.


## 5. Delete a Credential
```bash
./gradlew :cli:runOcraCli --args="delete --credential-id operator-demo"
```
Successful deletions emit `reasonCode=deleted`. Running the command again yields `credential_not_found`.

## 6. Maintain the Database
Periodic maintenance keeps MapDB compact and healthy. Run these commands when rotating credentials or after large import batches.

### 6.1 Compaction
```bash
./gradlew :cli:runOcraCli --args="maintenance compact"
```
Outputs include `status=success` and compaction statistics (bytes reclaimed, elapsed time).

### 6.2 Verification
```bash
./gradlew :cli:runOcraCli --args="maintenance verify"
```
Produces an integrity report summarizing page scans and corruption checks. Failures surface `reasonCode=verification_failed`.

## Troubleshooting
- **`credential_conflict`** – Remove either stored or inline parameters so only one evaluation mode is active.
- **`credential_not_found`** – The ID is missing; run `list` with `--database` pointing at the expected file.
- **`validation_error`** – Check the returned `reasonCode` for the missing field, then retry.
- **`maintenance_failed`** – Review the structured output; compaction/verification errors include remediation hints in `details`.

## Related Guides
- [How to Drive OCRA Evaluations from Java Applications](use-ocra-from-java.md)
- REST operations guide (published separately) for HTTP-based workflows.
- [Configure persistence profiles](configure-persistence-profiles.md) when tuning MapDB for non-default environments.
