# How to Operate the OCRA CLI

This guide is for operators who manage the simulator from the command line. It covers every Picocli subcommand shipped with the `ocra` tool, including credential lifecycle, evaluation flows, and database maintenance. Outputs are sanitized so secrets never appear in logs.

## Prerequisites
- Java 17 JDK configured (`JAVA_HOME` must point to it per the project constitution).
- Repository cloned locally with Gradle available.
- Default MapDB database path (`data/ocra-credentials.db`) is writable. Override with `--database` when needed.

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
| `maintenance compact` | Run MapDB compaction to reclaim disk space |
| `maintenance verify` | Run integrity checks against the MapDB store |

Invoke commands via the Gradle helper:
```bash
./gradlew :cli:runOcraCli --args="[--database <path>] <command> [options]"
```
If `--database` is omitted, the CLI uses `data/ocra-credentials.db` (shared with REST/UI facades).

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

## 4. Delete a Credential
```bash
./gradlew :cli:runOcraCli --args="delete --credential-id operator-demo"
```
Successful deletions emit `reasonCode=deleted`. Running the command again yields `credential_not_found`.

## 5. Maintain the Database
Periodic maintenance keeps MapDB compact and healthy. Run these commands when rotating credentials or after large import batches.

### 5.1 Compaction
```bash
./gradlew :cli:runOcraCli --args="maintenance compact"
```
Outputs include `status=success` and compaction statistics (bytes reclaimed, elapsed time).

### 5.2 Verification
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
