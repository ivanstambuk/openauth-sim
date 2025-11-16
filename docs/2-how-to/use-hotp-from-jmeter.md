# How to Drive HOTP Evaluations from JMeter via the Native Java API

This guide shows how to call the Native Java HOTP facade from Apache JMeter using a JSR223 Sampler with Groovy. The
snippet below constructs an in-memory HOTP evaluation service and calculates HOTP values directly from inline
credentials (no replay flows).

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- JMeter must also run on a Java 17 (or newer) runtime, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check the `java -version` used by `jmeter`/`jmeter.bat` rather than relying only on
  `JAVA_HOME`.
- OpenAuth Simulator JARs on JMeter’s classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`,
  and `infra-persistence`).
- JMeter configured with a JSR223 Sampler using `groovy` as the language.

## 1. JSR223 Sampler for inline HOTP

The Groovy script below can be pasted directly into a JSR223 Sampler. It uses the Native Java
`HotpEvaluationApplicationService` with an in-memory store and evaluates an inline HOTP secret:

```groovy
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm
import io.openauth.sim.infra.persistence.CredentialStoreFactory

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

def secretHex = requirePresent(vars.get('hotpSecretHex'), "JMeter variable 'hotpSecretHex'")
if (secretHex == null) return
def counterText = requirePresent(vars.get('hotpCounter'), "JMeter variable 'hotpCounter'")
if (counterText == null) return
def digitsText = requirePresent(vars.get('hotpDigits'), "JMeter variable 'hotpDigits'")
if (digitsText == null) return

def counter = counterText as long
def digits = Integer.parseInt(digitsText)

// Create an in-memory CredentialStore and HOTP service
def store = CredentialStoreFactory.openInMemoryStore()
def service = new HotpEvaluationApplicationService(store)

// Build an inline evaluation command (generation-only)
EvaluationCommand.Inline command = new EvaluationCommand.Inline(
        secretHex,
        HotpHashAlgorithm.SHA1,       // or SHA256/SHA512
        digits,
        counter,
        java.util.Map.of(),          // optional metadata
        0,                           // windowBackward
        0                            // windowForward
)

def result = service.evaluate(command)
if (result.telemetry().status() != TelemetryStatus.SUCCESS) {
    throw new IllegalArgumentException("Inline HOTP evaluation failed: ${result.telemetry().reason()}")
}

def otp = result.otp()
vars.put('hotpOtp', otp)
log.info("Generated HOTP (counter=${counter}): ${otp}")
```

This performs pure HOTP calculation using inline parameters. For stored credentials backed by a
shared MapDB store, adapt the service construction to use `CredentialStoreFactory.openFileStore`
as shown in `use-hotp-from-java.md`, but keep the JSR223 script self-contained (no extra helper
classes).

## 2. JSR223 Sampler for HOTP with stored credentials

The next script evaluates HOTP for a stored credential in the shared MapDB store used by CLI/REST/UI.
It assumes the credential has already been seeded under `credentialId`.

```groovy
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus
import io.openauth.sim.infra.persistence.CredentialStoreFactory

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

def credentialId = requirePresentStored(vars.get('hotpStoredCredentialId'), "JMeter variable 'hotpStoredCredentialId'")
if (credentialId == null) return
def databasePath = requirePresentStored(vars.get('hotpDatabasePath'), "JMeter variable 'hotpDatabasePath'")
if (databasePath == null) return

// Resolve and open the shared credentials.db used by other facades
def resolvedPath = CredentialStoreFactory.resolveDatabasePath(databasePath, 'credentials.db')
def store = CredentialStoreFactory.openFileStore(resolvedPath)
def service = new HotpEvaluationApplicationService(store)

// windowBackward/windowForward mirror the HOTP operator UI options
EvaluationCommand.Stored command = new EvaluationCommand.Stored(credentialId, 0, 0)

def result = service.evaluate(command)
if (result.telemetry().status() != TelemetryStatus.SUCCESS) {
    throw new IllegalStateException(\"Stored HOTP evaluation failed: ${result.telemetry().reason()}\")
}

def otp = result.otp()
vars.put('hotpStoredOtp', otp)
log.info(\"Stored HOTP for ${credentialId}: ${otp}\")
```

This mirrors the stored HOTP evaluation behaviour from `use-hotp-from-java.md` but keeps the entire
flow inside a JSR223 Sampler, with no replay semantics—only OTP calculation for an existing
credential.
