# How to Drive HOTP Evaluations from Neoload via the Native Java API

This guide shows how to call the Native Java HOTP facade from NeoLoad using JavaScript actions that talk directly to the
Native Java seam (`HotpEvaluationApplicationService`). You do not need NeoLoad-specific Java utilities; scripts call the
Java APIs via `Packages.*`.

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- NeoLoad’s embedded JRE must also be Java 17 or newer, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check `<neoload-install>/jre/bin/java -version` rather than relying on `JAVA_HOME`.
- OpenAuth Simulator JARs on the NeoLoad classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`,
  and `infra-persistence`) via `<project>/lib/jslib`.

## 1. JavaScript action for inline HOTP

```javascript
// NeoLoad JavaScript action: generate HOTP from an inline secret
var HotpService = Packages.io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
var InlineCommand =
        Packages.io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand.Inline;
var TelemetryStatus =
        Packages.io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus;
var HotpHashAlgorithm = Packages.io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
var CredentialStoreFactory = Packages.io.openauth.sim.infra.persistence.CredentialStoreFactory;

// Fail-fast guard for required inputs (treat "<NOT FOUND>" as missing)
function requirePresent(value, label) {
    var normalized = (value == null) ? "" : value.trim();
    if (normalized.length === 0 || normalized === "<NOT FOUND>") {
        var msg = "Missing required input: " + label;
        logger.error(msg);
        context.fail(msg);
        return null;
    }
    return value;
}

var secretHex = requirePresent(
        context.variableManager.getValue("hotpSecretHex"),
        "NeoLoad variable 'hotpSecretHex'");
if (secretHex === null) {
    return;
}
var counterRaw = requirePresent(
        context.variableManager.getValue("hotpCounter"),
        "NeoLoad variable 'hotpCounter'");
if (counterRaw === null) {
    return;
}
var digitsRaw = requirePresent(
        context.variableManager.getValue("hotpDigits"),
        "NeoLoad variable 'hotpDigits'");
if (digitsRaw === null) {
    return;
}

var counter = java.lang.Long.parseLong(counterRaw);
var digits = java.lang.Integer.parseInt(digitsRaw);

var store = CredentialStoreFactory.openInMemoryStore();
var service = new HotpService(store);

var inlineCommand = new InlineCommand(
        secretHex,
        HotpHashAlgorithm.SHA1, // or SHA256/SHA512
        digits,
        counter,
        java.util.Map.of(),
        0,
        0);

var result = service.evaluate(inlineCommand);
if (!TelemetryStatus.SUCCESS.equals(result.telemetry().status())) {
    throw "Inline HOTP evaluation failed: " + String(result.telemetry().reason());
}

var otp = result.otp();
context.variableManager.setValue("hotpInlineOtp", otp);
logger.debug("Generated inline HOTP (counter=" + counter + "): " + otp);
```

## 2. JavaScript action for stored HOTP

```javascript
// NeoLoad JavaScript action: generate HOTP for a stored credential
var HotpService = Packages.io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
var StoredCommand =
        Packages.io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand.Stored;
var TelemetryStatus =
        Packages.io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus;
var CredentialStoreFactory = Packages.io.openauth.sim.infra.persistence.CredentialStoreFactory;

// Inputs (must be provided via NeoLoad variables)
var credentialId = requirePresent(
        context.variableManager.getValue("hotpStoredCredentialId"),
        "NeoLoad variable 'hotpStoredCredentialId'");
if (credentialId === null) {
    return;
}

var databasePath = requirePresent(
        context.variableManager.getValue("hotpDatabasePath"),
        "NeoLoad variable 'hotpDatabasePath'");
if (databasePath === null) {
    return;
}

var resolvedPath = CredentialStoreFactory.resolveDatabasePath(databasePath, "credentials.db");
var store = CredentialStoreFactory.openFileStore(resolvedPath);
var service = new HotpService(store);

var storedCommand = new StoredCommand(credentialId, 0, 0);
var storedResult = service.evaluate(storedCommand);
if (!TelemetryStatus.SUCCESS.equals(storedResult.telemetry().status())) {
    throw "Stored HOTP evaluation failed: " + String(storedResult.telemetry().reason());
}

var storedOtp = storedResult.otp();
context.variableManager.setValue("hotpStoredOtp", storedOtp);
logger.debug("Stored HOTP for " + credentialId + ": " + storedOtp);
```

These two actions mirror the inline and stored HOTP flows from the JMeter and Java guides, keeping NeoLoad focused on
pure OTP calculation with no replay semantics.
