# How to Drive TOTP Evaluations from JMeter via the Native Java API

This guide shows how to call the Native Java TOTP facade from Apache JMeter using a JSR223 Sampler with Groovy. The
snippet below constructs an in-memory TOTP evaluation service and calculates TOTP values directly from inline
credentials (no replay/validation flows).

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- JMeter must also run on a Java 17 (or newer) runtime, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check the `java -version` used by `jmeter`/`jmeter.bat` rather than relying only on
  `JAVA_HOME`.
- OpenAuth Simulator JARs on JMeter’s classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`,
  and `infra-persistence`).
- JMeter configured with a JSR223 Sampler using `groovy` as the language.

## 1. JSR223 Sampler for inline TOTP

The Groovy script below can be pasted directly into a JSR223 Sampler. It uses the Native Java
`TotpEvaluationApplicationService` with an in-memory store and evaluates inline TOTP parameters:

```groovy
import io.openauth.sim.application.totp.TotpEvaluationApplicationService
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus
import io.openauth.sim.core.otp.totp.TotpDriftWindow
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm
import io.openauth.sim.infra.persistence.CredentialStoreFactory
import java.time.Duration
import java.time.Instant
import java.util.Optional

// Guard: fail fast if required inputs are missing
def requirePresent = { String value, String label ->
    if (value == null || value.trim().isEmpty()) {
        def msg = "Missing required input: ${label}"
        log.error(msg)
        prev.setSuccessful(false)
        prev.setResponseMessage(msg)
        prev.setResponseCode("500")
        org.apache.jmeter.engine.StandardJMeterEngine.stopTest()
        return null
    }
    return value
}

// Input parameters (must be provided via JMeter variables)
def secretHex = requirePresent(vars.get('totpSecretHex'), "JMeter variable 'totpSecretHex'")
if (secretHex == null) return
def digitsText = requirePresent(vars.get('totpDigits'), "JMeter variable 'totpDigits'")
if (digitsText == null) return
def stepSecondsText = requirePresent(vars.get('totpStepSeconds'), "JMeter variable 'totpStepSeconds'")
if (stepSecondsText == null) return

def digits = Integer.parseInt(digitsText)
def stepSeconds = Long.parseLong(stepSecondsText)
Instant evaluationInstant = Instant.now()

// Create an in-memory CredentialStore and TOTP service
def store = CredentialStoreFactory.openInMemoryStore()
def service = new TotpEvaluationApplicationService(store)

// Build an inline generation command (empty OTP => generate)
EvaluationCommand.Inline command = new EvaluationCommand.Inline(
        secretHex,
        TotpHashAlgorithm.SHA1,                // or SHA256/SHA512
        digits,
        Duration.ofSeconds(stepSeconds),
        "",                                   // empty OTP => generate
        TotpDriftWindow.of(0, 0),
        evaluationInstant,
        Optional.empty()
)

def result = service.evaluate(command)
if (result.telemetry().status() != TelemetryStatus.SUCCESS) {
    throw new IllegalStateException("Inline TOTP generation failed: ${result.telemetry().reason()}")
}

def otp = result.otp()
vars.put('totpOtp', otp)
log.info("Generated TOTP at ${evaluationInstant}: ${otp}")
```

This performs pure TOTP calculation using inline parameters. For stored credentials backed by a
shared MapDB store, adapt the service construction and use `EvaluationCommand.Stored` as shown in
`use-totp-from-java.md`, while keeping the JSR223 script self-contained (no helper classes).

## 2. JSR223 Sampler for TOTP with stored credentials

The next script generates a TOTP code for a stored credential in the shared MapDB store used by
CLI/REST/UI. It uses an empty OTP string to trigger generation-only behaviour.

```groovy
import io.openauth.sim.application.totp.TotpEvaluationApplicationService
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus
import io.openauth.sim.core.otp.totp.TotpDriftWindow
import io.openauth.sim.infra.persistence.CredentialStoreFactory
import java.time.Instant
import java.util.Optional

// Guard: fail fast if required inputs are missing
def requirePresentStored = { String value, String label ->
    if (value == null || value.trim().isEmpty()) {
        def msg = "Missing required input: ${label}"
        log.error(msg)
        prev.setSuccessful(false)
        prev.setResponseMessage(msg)
        prev.setResponseCode("500")
        org.apache.jmeter.engine.StandardJMeterEngine.stopTest()
        return null
    }
    return value
}

// Input parameters (must be provided via JMeter variables)
def credentialId = requirePresentStored(vars.get('totpStoredCredentialId'), "JMeter variable 'totpStoredCredentialId'")
if (credentialId == null) return
def databasePath = requirePresentStored(vars.get('totpDatabasePath'), "JMeter variable 'totpDatabasePath'")
if (databasePath == null) return
Instant evaluationInstant = Instant.now()

// Resolve and open the shared credentials.db used by other facades
def resolvedPath = CredentialStoreFactory.resolveDatabasePath(databasePath, 'credentials.db')
def store = CredentialStoreFactory.openFileStore(resolvedPath)
def service = new TotpEvaluationApplicationService(store)

// Empty OTP string => generate a TOTP value instead of validating one
EvaluationCommand.Stored command = new EvaluationCommand.Stored(
        credentialId,
        \"\", // generate-only
        TotpDriftWindow.of(0, 0),
        evaluationInstant,
        Optional.empty()
)

def result = service.evaluate(command)
if (result.telemetry().status() != TelemetryStatus.SUCCESS) {
    throw new IllegalStateException(\"Stored TOTP generation failed: ${result.telemetry().reason()}\")
}

def otp = result.otp()
vars.put('totpStoredOtp', otp)
log.info(\"Stored TOTP for ${credentialId} at ${evaluationInstant}: ${otp}\")
```

This mirrors the stored TOTP generate-only flow described in `use-totp-from-java.md` and keeps the
behaviour strictly in the “calculation” category—no replay or validation-only paths.
