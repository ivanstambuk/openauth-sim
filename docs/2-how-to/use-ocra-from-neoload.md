# How to Drive OCRA Evaluations from Neoload via the Native Java API

This guide shows how to call the Native Java OCRA facade from NeoLoad using JavaScript actions that talk directly to the
core OCRA types (`OcraCredentialFactory`, `OcraResponseCalculator`). You do not need NeoLoad-specific Java utilities;
scripts call the Java APIs via `Packages.*`.

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- NeoLoad’s embedded JRE must also be Java 17 or newer, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check `<neoload-install>/jre/bin/java -version` rather than relying on `JAVA_HOME`.
- OpenAuth Simulator JARs on the NeoLoad classpath (at minimum: `core`, `core-shared`, `core-ocra`) via
  `<project>/lib/jslib`.

## 1. JavaScript action for inline OCRA

```javascript
// NeoLoad JavaScript action: evaluate an inline OCRA descriptor
var OcraCredentialFactory =
        Packages.io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
var OcraCredentialRequest =
        Packages.io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
var OcraCredentialDescriptor =
        Packages.io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
var OcraResponseCalculator =
        Packages.io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
var OcraExecutionContext =
        Packages.io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
var SecretEncoding = Packages.io.openauth.sim.core.model.SecretEncoding;

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

var credentialId = requirePresent(
        context.variableManager.getValue("ocraInlineCredentialId"),
        "NeoLoad variable 'ocraInlineCredentialId'");
if (credentialId === null) {
    return;
}
var suite = requirePresent(
        context.variableManager.getValue("ocraSuite"),
        "NeoLoad variable 'ocraSuite'");
if (suite === null) {
    return;
}
var secretHex = requirePresent(
        context.variableManager.getValue("ocraSecretHex"),
        "NeoLoad variable 'ocraSecretHex'");
if (secretHex === null) {
    return;
}
var challenge = requirePresent(
        context.variableManager.getValue("ocraInlineChallenge"),
        "NeoLoad variable 'ocraInlineChallenge'");
if (challenge === null) {
    return;
}
var sessionHex = requirePresent(
        context.variableManager.getValue("ocraInlineSessionHex"),
        "NeoLoad variable 'ocraInlineSessionHex'");
if (sessionHex === null) {
    return;
}

var factory = new OcraCredentialFactory();
var request = new OcraCredentialRequest(
        credentialId,
        suite,
        secretHex,
        SecretEncoding.HEX,
        null,
        null,
        null,
        java.util.Map.of("owner", "neoload-inline"));
var descriptor = factory.createDescriptor(request);

var contextObj = new OcraExecutionContext(
        null,
        challenge,
        sessionHex,
        null,
        null,
        null,
        null);

var otp = OcraResponseCalculator.generate(descriptor, contextObj);
context.variableManager.setValue("ocraInlineOtp", otp);
logger.debug("Inline OCRA OTP for " + credentialId + ": " + otp);
```

## 2. JavaScript action for stored OCRA descriptors

For “stored” descriptors, many scenarios keep a small registry keyed by identifier. The snippet below shows how to build
a simple in-script registry of descriptors and evaluate a stored entry:

```javascript
// NeoLoad JavaScript action: evaluate a stored OCRA descriptor from a simple registry
var OcraCredentialFactory =
        Packages.io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
var OcraCredentialRequest =
        Packages.io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
var OcraCredentialDescriptor =
        Packages.io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
var OcraResponseCalculator =
        Packages.io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
var OcraExecutionContext =
        Packages.io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
var SecretEncoding = Packages.io.openauth.sim.core.model.SecretEncoding;

var factory = new OcraCredentialFactory();

// Simple registry: in real usage, load from configuration or a JSON file
var registry = new java.util.HashMap();
registry.put(
        "operator-demo",
        factory.createDescriptor(
                new OcraCredentialRequest(
                        "operator-demo",
                        "OCRA-1:HOTP-SHA256-8:QA08-S064",
                        "3132333435363738393031323334353637383930313233343536373839303132",
                        SecretEncoding.HEX,
                        null,
                        null,
                        null,
                        java.util.Map.of("owner", "registry"))));

// Inputs (must be provided via NeoLoad variables)
var credentialId = requirePresent(
        context.variableManager.getValue("ocraStoredCredentialId"),
        "NeoLoad variable 'ocraStoredCredentialId'");
if (credentialId === null) {
    return;
}
var challenge = requirePresent(
        context.variableManager.getValue("ocraStoredChallenge"),
        "NeoLoad variable 'ocraStoredChallenge'");
if (challenge === null) {
    return;
}
var sessionHex = requirePresent(
        context.variableManager.getValue("ocraStoredSessionHex"),
        "NeoLoad variable 'ocraStoredSessionHex'");
if (sessionHex === null) {
    return;
}

var storedDescriptor = registry.get(credentialId);
if (storedDescriptor == null) {
    throw "Unknown OCRA credential in registry: " + credentialId;
}

var storedContext = new OcraExecutionContext(
        null,
        challenge,
        sessionHex,
        null,
        null,
        null,
        null);

var storedOtp = OcraResponseCalculator.generate(storedDescriptor, storedContext);
context.variableManager.setValue("ocraStoredOtp", storedOtp);
logger.debug("Stored OCRA OTP for " + credentialId + ": " + storedOtp);
```

These examples mirror the inline and stored OCRA flows from the JMeter and Java guides, keeping NeoLoad focused on
response calculation with no replay semantics.
