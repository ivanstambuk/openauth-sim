# How to Manage OCRA Credentials via CLI

This guide walks operators through the new Picocli-based `ocra` commands for importing, listing, deleting, and evaluating OCRA credentials against the local MapDB store. The flows mirror the REST facade behaviour while keeping outputs sanitized for terminal use.

## Prerequisites
- Java 17 JDK available (`JAVA_HOME` must point to it per the project constitution).
- Build the CLI module at least once so runtime classes are on disk:
  ```bash
  ./gradlew :cli:classes
  ```
- Choose a writable database path. Examples below use `build/tmp/cli-ocra/docs.db`.

## Run the CLI
Use the helper task defined in `cli/build.gradle.kts` to invoke the Picocli entrypoint:
```bash
./gradlew :cli:runOcraCli --args="--database <dbPath> <subcommand> [options]"
```
All commands emit structured telemetry lines (see `docs/3-reference/cli-ocra-telemetry-snapshot.md`) with `sanitized=true` to guarantee secrets never leak.

## 1. Import a Credential Descriptor
Persist a descriptor into MapDB:
```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db \
  import --credential-id docs-token \
         --suite OCRA-1:HOTP-SHA1-6:QN08 \
         --secret 3132333435363738393031323334353637383930"
```
Expected telemetry:
```
event=cli.ocra.import status=success reasonCode=created sanitized=true credentialId=docs-token suite=OCRA-1:HOTP-SHA1-6:QN08
```

## 2. List Stored Credentials
```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db list"
```
Lists redacted metadata plus per-item lines. Use `--verbose` to include metadata keys.

## 3. Evaluate Using a Stored Credential
```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db \
  evaluate --credential-id docs-token --challenge 00000000"
```
The CLI pulls the descriptor, validates required inputs, and prints the OTP alongside `reasonCode=success`.

## 4. Evaluate Inline Secrets Instead
Provide suite + secret directly when no stored credential is available:
```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db \
  evaluate --suite OCRA-1:HOTP-SHA1-6:QN08 \
           --secret 3132333435363738393031323334353637383930 \
           --challenge 11111111"
```
Inline runs add `mode=inline` to the telemetry so monitoring can distinguish them from stored credentials.

## 5. Delete a Credential
```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db delete --credential-id docs-token"
```
Successful deletions report `reasonCode=deleted`. Subsequent lookups will raise `credential_not_found`.

## Troubleshooting
- **`credential_conflict`** – Remove either `--credential-id` or `--secret`/`--suite` so only one mode is selected.
- **`validation_error`** – Input failed suite-specific rules (e.g., missing challenge/session). The message remains sanitized; re-run with corrected values.
- **`credential_not_found`** – The requested ID is absent in the database. Run `list` to inspect current entries.

Keep the telemetry snapshot up to date after behavioural changes by re-running the commands above and updating `docs/3-reference/cli-ocra-telemetry-snapshot.md`.
