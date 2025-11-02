# How-to Guides (Draft)

Document deterministic runbooks for common tasks. Published topics:

1. [Operate the OCRA REST API](use-ocra-rest-operations.md)
2. [Use the HOTP operator UI](use-hotp-operator-ui.md)
3. [Use the TOTP operator UI](use-totp-operator-ui.md)
4. [Use the OCRA operator UI](use-ocra-operator-ui.md)
5. [Operate the OCRA CLI](use-ocra-cli-operations.md)
6. [Drive OCRA evaluations from Java applications](use-ocra-from-java.md)
7. [Generate OCRA test vectors](generate-ocra-test-vectors.md)
8. [Configure MapDB persistence profiles](configure-persistence-profiles.md)
9. [Benchmark OCRA verification latency](benchmark-ocra-verification.md)
10. [Embed the Protocol Info surface](embed-protocol-info-surface.md)
11. [Use the FIDO2/WebAuthn operator UI](use-fido2-operator-ui.md)
12. [Operate the FIDO2/WebAuthn REST API](use-fido2-rest-operations.md)
13. [Operate the EMV/CAP REST API](use-emv-cap-rest-operations.md)
14. [Use the EMV/CAP CLI](use-emv-cap-cli-operations.md)
15. [Use the EMV/CAP operator UI](use-emv-cap-operator-ui.md)
16. [Use the FIDO2/WebAuthn CLI](use-fido2-cli-operations.md)

Most REST/UI guides assume the simulator is running via:

```bash
./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi
```

Each guide should follow the template in `docs/templates/how-to-template.md` (TBD).
