# How to Generate WebAuthn Assertions from JMeter via the Native Java API

This guide shows how to call the Native Java WebAuthn assertion generation facade from Apache JMeter using a JSR223
Sampler with Groovy. The snippets below construct a WebAuthn assertion generation service and generate inline or stored
WebAuthn assertions (no replay/verification flows).

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- JMeter must also run on a Java 17 (or newer) runtime, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check the `java -version` used by `jmeter`/`jmeter.bat` rather than relying only on
  `JAVA_HOME`.
- OpenAuth Simulator JARs on JMeter’s classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`,
  and `infra-persistence`).
- JMeter configured with a JSR223 Sampler using `groovy`.

## 1. JSR223 Sampler for inline WebAuthn assertion generation

The Groovy script below can be pasted directly into a JSR223 Sampler. It uses the Native Java
`WebAuthnAssertionGenerationApplicationService` to generate an inline WebAuthn assertion from a private key and
challenge. Inputs are read from JMeter variables (for example, supplied by a CSV Data Set).

```groovy
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm
import java.util.Base64

def urlDecoder = Base64.getUrlDecoder()
def urlEncoder = Base64.getUrlEncoder().withoutPadding()

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

// Inputs (must be provided via JMeter variables)
def credentialName = requirePresent(vars.get('webauthnCredentialName'), "JMeter variable 'webauthnCredentialName'")
if (credentialName == null) return
def rpId = requirePresent(vars.get('webauthnRpId'), "JMeter variable 'webauthnRpId'")
if (rpId == null) return
def origin = requirePresent(vars.get('webauthnOrigin'), "JMeter variable 'webauthnOrigin'")
if (origin == null) return
def expectedType = requirePresent(vars.get('webauthnType'), "JMeter variable 'webauthnType'")
if (expectedType == null) return
def challengeB64u = requirePresent(vars.get('webauthnChallenge'), "JMeter variable 'webauthnChallenge'")
if (challengeB64u == null) return
byte[] challenge = urlDecoder.decode(challengeB64u)
String privateKey = requirePresent(vars.get('webauthnPrivateKey'), "JMeter variable 'webauthnPrivateKey'")
if (privateKey == null) return

def signatureCounterText = requirePresent(vars.get('webauthnSignatureCounter'), "JMeter variable 'webauthnSignatureCounter'")
if (signatureCounterText == null) return
long signatureCounter = Long.parseLong(signatureCounterText)
def userVerificationRequiredText = requirePresent(vars.get('webauthnUserVerificationRequired'), "JMeter variable 'webauthnUserVerificationRequired'")
if (userVerificationRequiredText == null) return
boolean userVerificationRequired = userVerificationRequiredText.toBoolean()
def algorithmName = requirePresent(vars.get('webauthnAlgorithm'), "JMeter variable 'webauthnAlgorithm'")
if (algorithmName == null) return
def algorithm = WebAuthnSignatureAlgorithm.valueOf(algorithmName)

// For inline generation, callers must supply a credentialId; most flows mirror what the UI uses.
def credentialIdB64u = requirePresent(vars.get('webauthnCredentialId'), "JMeter variable 'webauthnCredentialId'")
if (credentialIdB64u == null) return
byte[] credentialId = urlDecoder.decode(credentialIdB64u)

GenerationCommand.Inline command = new GenerationCommand.Inline(
        credentialName,
        credentialId,
        algorithm,
        rpId,
        origin,
        expectedType,
        signatureCounter,
        userVerificationRequired,
        challenge,
        privateKey
)

def generator = new WebAuthnAssertionGenerationApplicationService()
GenerationResult result = generator.generate(command)

// Expose generated assertion components to the rest of the plan
vars.put('webauthnCredentialId', urlEncoder.encodeToString(result.credentialId()))
vars.put('webauthnClientDataJson', urlEncoder.encodeToString(result.clientDataJson()))
vars.put('webauthnAuthenticatorData', urlEncoder.encodeToString(result.authenticatorData()))
vars.put('webauthnSignature', urlEncoder.encodeToString(result.signature()))

log.info("Generated inline WebAuthn assertion for ${credentialName} with algorithm=${result.algorithm()}")
```

This performs assertion **generation** using inline parameters only. The result variables
(`webauthnCredentialId`, `webauthnClientDataJson`, `webauthnAuthenticatorData`, `webauthnSignature`) can be passed to
downstream samplers or to verification tooling if desired.

## 2. JSR223 Sampler for stored WebAuthn assertion generation

The next script generates a WebAuthn assertion for a credential stored in the shared MapDB store
used by CLI/REST/UI. It assumes the credential has been seeded under `webauthnStoredCredentialName` and its private key
material is available in the store (mirroring the operator UI behaviour).

```groovy
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter
import io.openauth.sim.infra.persistence.CredentialStoreFactory
import java.util.Base64

def decoder = Base64.getUrlDecoder()
def encoder = Base64.getUrlEncoder().withoutPadding()

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

// Inputs (must be provided via JMeter variables)
def credentialName = requirePresentStored(vars.get('webauthnStoredCredentialName'), "JMeter variable 'webauthnStoredCredentialName'")
if (credentialName == null) return
def rpId = requirePresentStored(vars.get('webauthnRpId'), "JMeter variable 'webauthnRpId'")
if (rpId == null) return
def origin = requirePresentStored(vars.get('webauthnOrigin'), "JMeter variable 'webauthnOrigin'")
if (origin == null) return
def expectedType = requirePresentStored(vars.get('webauthnType'), "JMeter variable 'webauthnType'")
if (expectedType == null) return
def databasePath = requirePresentStored(vars.get('webauthnDatabasePath'), "JMeter variable 'webauthnDatabasePath'")
if (databasePath == null) return

def challengeB64uStored = requirePresentStored(vars.get('webauthnChallenge'), "JMeter variable 'webauthnChallenge'")
if (challengeB64uStored == null) return
byte[] challenge = decoder.decode(challengeB64uStored)
String privateKeyOverride = vars.get('webauthnPrivateKey') // optional – falls back to stored private key

def resolvedPath = CredentialStoreFactory.resolveDatabasePath(databasePath, 'credentials.db')
def store = CredentialStoreFactory.openFileStore(resolvedPath)
def persistenceAdapter = new WebAuthnCredentialPersistenceAdapter()
def generator = new WebAuthnAssertionGenerationApplicationService(store, persistenceAdapter)

GenerationCommand.Stored command = new GenerationCommand.Stored(
        credentialName,
        rpId,
        origin,
        expectedType,
        challenge,
        privateKeyOverride,
        null,   // signatureCounterOverride
        null    // userVerificationRequiredOverride
)

GenerationResult storedResult = generator.generate(command)

vars.put('webauthnStoredCredentialId', encoder.encodeToString(storedResult.credentialId()))
vars.put('webauthnStoredClientDataJson', encoder.encodeToString(storedResult.clientDataJson()))
vars.put('webauthnStoredAuthenticatorData', encoder.encodeToString(storedResult.authenticatorData()))
vars.put('webauthnStoredSignature', encoder.encodeToString(storedResult.signature()))

log.info("Generated stored WebAuthn assertion for ${credentialName} with algorithm=${storedResult.algorithm()}")
```

This mirrors the stored assertion generation flow exercised by the Evaluate tab in the operator UI: it produces a fresh
assertion given a stored credential and challenge, without performing verification or replay. Downstream JMeter samplers
can treat the generated assertion as an input to other systems under test.
