# How to Generate WebAuthn Assertions from Neoload via the Native Java API

This guide shows how to generate WebAuthn assertions from NeoLoad using JavaScript actions that talk directly to the
Native Java assertion-generation seam (`WebAuthnAssertionGenerationApplicationService`). Scripts call the Java APIs via
`Packages.*` and focus on assertion generation (no verification/replay flows).

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- NeoLoad’s embedded JRE must also be Java 17 or newer, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check `<neoload-install>/jre/bin/java -version` rather than relying on `JAVA_HOME`.
- OpenAuth Simulator JARs on the NeoLoad classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`,
  and `infra-persistence`) via `<project>/lib/jslib`.

## 1. JavaScript action for inline WebAuthn assertion generation

```javascript
// NeoLoad JavaScript action: generate an inline WebAuthn assertion
var Generator =
        Packages.io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
var GenerationCommand =
        Packages.io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
var SignatureAlgorithm = Packages.io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
var Base64 = Packages.java.util.Base64;

var decoder = Base64.getUrlDecoder();
var encoder = Base64.getUrlEncoder().withoutPadding();

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

// Inputs (must be provided via NeoLoad variables)
var credentialName = requirePresent(
        context.variableManager.getValue("webauthnCredentialName"),
        "NeoLoad variable 'webauthnCredentialName'");
if (credentialName === null) {
    return;
}
var rpId = requirePresent(
        context.variableManager.getValue("webauthnRpId"),
        "NeoLoad variable 'webauthnRpId'");
if (rpId === null) {
    return;
}
var origin = requirePresent(
        context.variableManager.getValue("webauthnOrigin"),
        "NeoLoad variable 'webauthnOrigin'");
if (origin === null) {
    return;
}
var expectedType = requirePresent(
        context.variableManager.getValue("webauthnType"),
        "NeoLoad variable 'webauthnType'");
if (expectedType === null) {
    return;
}

var credentialIdB64u = requirePresent(
        context.variableManager.getValue("webauthnCredentialId"),
        "NeoLoad variable 'webauthnCredentialId'");
if (credentialIdB64u === null) {
    return;
}
var credentialId = decoder.decode(credentialIdB64u);

var challengeB64u = requirePresent(
        context.variableManager.getValue("webauthnChallenge"),
        "NeoLoad variable 'webauthnChallenge'");
if (challengeB64u === null) {
    return;
}
var challenge = decoder.decode(challengeB64u);
var privateKey = requirePresent(
        context.variableManager.getValue("webauthnPrivateKey"),
        "NeoLoad variable 'webauthnPrivateKey'");
if (privateKey === null) {
    return;
}

var signatureCounterRaw = requirePresent(
        context.variableManager.getValue("webauthnSignatureCounter"),
        "NeoLoad variable 'webauthnSignatureCounter'");
if (signatureCounterRaw === null) {
    return;
}
var signatureCounter = java.lang.Long.parseLong(signatureCounterRaw);
var userVerificationRequiredRaw =
        requirePresent(
                context.variableManager.getValue("webauthnUserVerificationRequired"),
                "NeoLoad variable 'webauthnUserVerificationRequired'");
if (userVerificationRequiredRaw === null) {
    return;
}
var userVerificationRequired =
        java.lang.Boolean.parseBoolean(userVerificationRequiredRaw);
var algorithmName = requirePresent(
        context.variableManager.getValue("webauthnAlgorithm"),
        "NeoLoad variable 'webauthnAlgorithm'");
if (algorithmName === null) {
    return;
}
var algorithm = SignatureAlgorithm.valueOf(algorithmName);

var inlineCommand = new GenerationCommand.Inline(
        credentialName,
        credentialId,
        algorithm,
        rpId,
        origin,
        expectedType,
        signatureCounter,
        userVerificationRequired,
        challenge,
        privateKey);

var generator = new Generator();
var result = generator.generate(inlineCommand);

context.variableManager.setValue("webauthnCredentialId", encoder.encodeToString(result.credentialId()));
context.variableManager.setValue("webauthnClientDataJson", encoder.encodeToString(result.clientDataJson()));
context.variableManager.setValue(
        "webauthnAuthenticatorData", encoder.encodeToString(result.authenticatorData()));
context.variableManager.setValue("webauthnSignature", encoder.encodeToString(result.signature()));

logger.debug(
        "Generated inline WebAuthn assertion for "
                + credentialName
                + " with algorithm="
                + result.algorithm());
```

## 2. JavaScript action for stored WebAuthn assertion generation

```javascript
// NeoLoad JavaScript action: generate a stored WebAuthn assertion
var Generator =
        Packages.io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
var GenerationCommand =
        Packages.io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
var CredentialStoreFactory =
        Packages.io.openauth.sim.infra.persistence.CredentialStoreFactory;
var PersistenceAdapter =
        Packages.io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
var Base64 = Packages.java.util.Base64;

var decoder = Base64.getUrlDecoder();
var encoder = Base64.getUrlEncoder().withoutPadding();

// Inputs (must be provided via NeoLoad variables)
var credentialName = requirePresent(
        context.variableManager.getValue("webauthnStoredCredentialName"),
        "NeoLoad variable 'webauthnStoredCredentialName'");
if (credentialName === null) {
    return;
}
var rpId = requirePresent(
        context.variableManager.getValue("webauthnRpId"),
        "NeoLoad variable 'webauthnRpId'");
if (rpId === null) {
    return;
}
var origin = requirePresent(
        context.variableManager.getValue("webauthnOrigin"),
        "NeoLoad variable 'webauthnOrigin'");
if (origin === null) {
    return;
}
var expectedType = requirePresent(
        context.variableManager.getValue("webauthnType"),
        "NeoLoad variable 'webauthnType'");
if (expectedType === null) {
    return;
}
var databasePath = requirePresent(
        context.variableManager.getValue("webauthnDatabasePath"),
        "NeoLoad variable 'webauthnDatabasePath'");
if (databasePath === null) {
    return;
}

var challengeStoredB64u = requirePresent(
        context.variableManager.getValue("webauthnChallenge"),
        "NeoLoad variable 'webauthnChallenge'");
if (challengeStoredB64u === null) {
    return;
}
var challenge = decoder.decode(challengeStoredB64u);
var privateKeyOverride = context.variableManager.getValue("webauthnPrivateKey"); // optional

var resolvedPath = CredentialStoreFactory.resolveDatabasePath(databasePath, "credentials.db");
var store = CredentialStoreFactory.openFileStore(resolvedPath);
var persistenceAdapter = new PersistenceAdapter();
var generator = new Generator(store, persistenceAdapter);

var storedCommand = new GenerationCommand.Stored(
        credentialName,
        rpId,
        origin,
        expectedType,
        challenge,
        privateKeyOverride,
        null, // signatureCounterOverride
        null  // userVerificationRequiredOverride
);

var storedResult = generator.generate(storedCommand);

context.variableManager.setValue(
        "webauthnStoredCredentialId", encoder.encodeToString(storedResult.credentialId()));
context.variableManager.setValue(
        "webauthnStoredClientDataJson", encoder.encodeToString(storedResult.clientDataJson()));
context.variableManager.setValue(
        "webauthnStoredAuthenticatorData", encoder.encodeToString(storedResult.authenticatorData()));
context.variableManager.setValue(
        "webauthnStoredSignature", encoder.encodeToString(storedResult.signature()));

logger.debug(
        "Generated stored WebAuthn assertion for "
                + credentialName
                + " with algorithm="
                + storedResult.algorithm());
```

These actions mirror the inline and stored Evaluate flows from the operator UI while keeping NeoLoad focused solely on
assertion generation; verification/replay is handled elsewhere in the simulator.
