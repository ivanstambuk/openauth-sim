# How to Drive TOTP Evaluations from Neoload via the Native Java API

This guide shows how to call the Native Java TOTP facade from NeoLoad using JavaScript actions that talk directly to the
Native Java seam (`TotpEvaluationApplicationService`). You do not need NeoLoad-specific Java utilities; scripts call the
Java APIs via `Packages.*`.

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- NeoLoad’s embedded JRE must also be Java 17 or newer, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check `<neoload-install>/jre/bin/java -version` rather than relying on `JAVA_HOME`.
- OpenAuth Simulator JARs on the NeoLoad classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`, and `infra-persistence`) via `<project>/lib/jslib`.

## 1. JavaScript action for inline TOTP

```javascript
// NeoLoad JavaScript action: generate TOTP from an inline secret
var TotpService = Packages.io.openauth.sim.application.totp.TotpEvaluationApplicationService;
var InlineCommand =
        Packages.io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand.Inline;
var TelemetryStatus =
        Packages.io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
var TotpDriftWindow = Packages.io.openauth.sim.core.otp.totp.TotpDriftWindow;
var TotpHashAlgorithm = Packages.io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
var CredentialStoreFactory = Packages.io.openauth.sim.infra.persistence.CredentialStoreFactory;
var Instant = Packages.java.time.Instant;
var Duration = Packages.java.time.Duration;
var Optional = Packages.java.util.Optional;

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
        context.variableManager.getValue("totpSecretHex"),
        "NeoLoad variable 'totpSecretHex'");
if (secretHex === null) {
    return;
}
var digitsRaw = requirePresent(
        context.variableManager.getValue("totpDigits"),
        "NeoLoad variable 'totpDigits'");
if (digitsRaw === null) {
    return;
}
var stepSecondsRaw = requirePresent(
        context.variableManager.getValue("totpStepSeconds"),
        "NeoLoad variable 'totpStepSeconds'");
if (stepSecondsRaw === null) {
    return;
}

var digits = parseInt(digitsRaw, 10);
var stepSeconds = java.lang.Long.parseLong(stepSecondsRaw);
var evaluationInstant = Instant.now();

// Create an in-memory CredentialStore and TOTP service
var store = CredentialStoreFactory.openInMemoryStore();
var service = new TotpService(store);

// Empty OTP string => generate a TOTP value instead of validating one
var drift = TotpDriftWindow.of(0, 0);
var inlineCommand = new InlineCommand(
        secretHex,
        TotpHashAlgorithm.SHA1,
        digits,
        Duration.ofSeconds(stepSeconds),
        "", // empty OTP => generate
        drift,
        evaluationInstant,
        Optional.empty());

var result = service.evaluate(inlineCommand);
if (!TelemetryStatus.SUCCESS.equals(result.telemetry().status())) {
    throw "Inline TOTP generation failed: " + String(result.telemetry().reason());
}

var otp = result.otp();
context.variableManager.setValue("totpOtp", otp);
logger.debug("Generated TOTP at " + evaluationInstant.toString() + ": " + otp);
```

## 2. JavaScript action for stored TOTP

```javascript
// NeoLoad JavaScript action: generate TOTP for a stored credential
var TotpService = Packages.io.openauth.sim.application.totp.TotpEvaluationApplicationService;
var StoredCommand =
        Packages.io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand.Stored;
var TelemetryStatus =
        Packages.io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
var TotpDriftWindow = Packages.io.openauth.sim.core.otp.totp.TotpDriftWindow;
var CredentialStoreFactory = Packages.io.openauth.sim.infra.persistence.CredentialStoreFactory;
var Instant = Packages.java.time.Instant;
var Optional = Packages.java.util.Optional;

// Inputs (must be provided via NeoLoad variables)
var credentialId = requirePresent(
        context.variableManager.getValue("totpStoredCredentialId"),
        "NeoLoad variable 'totpStoredCredentialId'");
if (credentialId === null) {
    return;
}

var databasePath = requirePresent(
        context.variableManager.getValue("totpDatabasePath"),
        "NeoLoad variable 'totpDatabasePath'");
if (databasePath === null) {
    return;
}
var evaluationInstant = Instant.now();

// Resolve and open the shared credentials.db used by other facades
var resolvedPath = CredentialStoreFactory.resolveDatabasePath(databasePath, "credentials.db");
var store = CredentialStoreFactory.openFileStore(resolvedPath);
var service = new TotpService(store);

var drift = TotpDriftWindow.of(0, 0);
var storedCommand = new StoredCommand(
        credentialId,
        "", // empty OTP => generate-only
        drift,
        evaluationInstant,
        Optional.empty());

var storedResult = service.evaluate(storedCommand);
if (!TelemetryStatus.SUCCESS.equals(storedResult.telemetry().status())) {
    throw "Stored TOTP generation failed: " + String(storedResult.telemetry().reason());
}

var storedOtp = storedResult.otp();
context.variableManager.setValue("totpStoredOtp", storedOtp);
logger.debug("Stored TOTP for " + credentialId + " at " + evaluationInstant.toString() + ": " + storedOtp);
```

These two actions give NeoLoad both inline and stored TOTP flows using the same Native Java seam as the JMeter and
Java guides, without any NeoLoad-specific Java helper classes.
