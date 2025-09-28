# CLI OCRA Telemetry Snapshot

Captured on 2025-09-28 using `./gradlew :cli:runOcraCli` commands with a scratch database at `build/tmp/cli-ocra/docs.db`.

```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db import --credential-id docs-token --suite OCRA-1:HOTP-SHA1-6:QN08 --secret 3132333435363738393031323334353637383930"
```
```
event=cli.ocra.import status=success reasonCode=created sanitized=true credentialId=docs-token suite=OCRA-1:HOTP-SHA1-6:QN08
```

```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db list"
```
```
event=cli.ocra.list status=success reasonCode=success sanitized=true count=1
credentialId=docs-token suite=OCRA-1:HOTP-SHA1-6:QN08 hasCounter=false hasPin=false hasDrift=false
```

```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db evaluate --credential-id docs-token --challenge 00000000"
```
```
event=cli.ocra.evaluate status=success reasonCode=success sanitized=true credentialId=docs-token suite=OCRA-1:HOTP-SHA1-6:QN08 otp=237653
```

```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db evaluate --suite OCRA-1:HOTP-SHA1-6:QN08 --secret 3132333435363738393031323334353637383930 --challenge 11111111"
```
```
event=cli.ocra.evaluate status=success reasonCode=success sanitized=true mode=inline suite=OCRA-1:HOTP-SHA1-6:QN08 otp=243178
```

```bash
./gradlew :cli:runOcraCli --args="--database build/tmp/cli-ocra/docs.db delete --credential-id docs-token"
```
```
event=cli.ocra.delete status=success reasonCode=deleted sanitized=true credentialId=docs-token
```

Use these samples to validate future telemetry changes—any additional field must keep `sanitized=true` when potential secret material is involved.
