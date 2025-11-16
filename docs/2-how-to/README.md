# How-to Guides (Draft)

Document deterministic runbooks for common tasks across all four consumption surfaces: Native Java API, REST API, operator console UI, and CLI tools. Published topics:

## Table of Contents

- [Native Java API](#native-java-api)
- [Native Java API from JMeter](#native-java-api-from-jmeter)
- [Native Java API from Neoload](#native-java-api-from-neoload)
- [REST API guides](#rest-api-guides)
- [Operator console UI guides](#operator-console-ui-guides)
- [CLI guides](#cli-guides)
- [Supporting tools & operations](#supporting-tools--operations)

### Native Java API
- [Drive EMV/CAP evaluations from Java applications](use-emv-cap-from-java.md)
- [Drive WebAuthn assertion evaluations from Java applications](use-fido2-from-java.md)
- [Drive TOTP evaluations from Java applications](use-totp-from-java.md)
- [Drive HOTP evaluations from Java applications](use-hotp-from-java.md)
- [Drive OCRA evaluations from Java applications](use-ocra-from-java.md)
- [Drive EUDIW OpenID4VP flows from Java applications](use-eudiw-from-java.md)

### Native Java API from JMeter
- [Drive HOTP evaluations from JMeter](use-hotp-from-jmeter.md)
- [Drive TOTP evaluations from JMeter](use-totp-from-jmeter.md)
- [Drive OCRA evaluations from JMeter](use-ocra-from-jmeter.md)
- [Drive WebAuthn assertion evaluations from JMeter](use-fido2-from-jmeter.md)
- [Drive EMV/CAP evaluations from JMeter](use-emv-cap-from-jmeter.md)
- [Drive EUDIW OpenID4VP flows from JMeter](use-eudiw-from-jmeter.md)

### Native Java API from Neoload
- [Drive HOTP evaluations from Neoload](use-hotp-from-neoload.md)
- [Drive TOTP evaluations from Neoload](use-totp-from-neoload.md)
- [Drive OCRA evaluations from Neoload](use-ocra-from-neoload.md)
- [Drive WebAuthn assertion evaluations from Neoload](use-fido2-from-neoload.md)
- [Drive EMV/CAP evaluations from Neoload](use-emv-cap-from-neoload.md)
- [Drive EUDIW OpenID4VP flows from Neoload](use-eudiw-from-neoload.md)

### REST API guides
- [Operate the OCRA REST API](use-ocra-rest-operations.md)
- [Operate the FIDO2/WebAuthn REST API](use-fido2-rest-operations.md)
- [Operate the EMV/CAP REST API](use-emv-cap-rest-operations.md)

### Operator console UI guides
- [Use the HOTP operator UI](use-hotp-operator-ui.md)
- [Use the TOTP operator UI](use-totp-operator-ui.md)
- [Use the OCRA operator UI](use-ocra-operator-ui.md)
- [Use the FIDO2/WebAuthn operator UI](use-fido2-operator-ui.md)
- [Use the EMV/CAP operator UI](use-emv-cap-operator-ui.md)

### CLI guides
- [Operate the OCRA CLI](use-ocra-cli-operations.md)
- [Use the FIDO2/WebAuthn CLI](use-fido2-cli-operations.md)
- [Use the EMV/CAP CLI](use-emv-cap-cli-operations.md)

### Supporting tools & operations
- [Generate OCRA test vectors](generate-ocra-test-vectors.md)
- [Configure MapDB persistence profiles](configure-persistence-profiles.md)
- [Benchmark OCRA verification latency](benchmark-ocra-verification.md)
- [Embed the Protocol Info surface](embed-protocol-info-surface.md)

Most REST/UI guides assume the simulator is running via:

```bash
./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi
```

Each guide should follow the template in `docs/templates/how-to-template.md` (TBD).
