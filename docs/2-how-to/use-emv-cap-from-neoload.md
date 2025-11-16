# How to Drive EMV/CAP Evaluations from Neoload via the Native Java API

This guide shows how to call the Native Java EMV/CAP facade from NeoLoad using JavaScript actions that talk directly to
`EmvCapEvaluationApplicationService`. Scripts call the Java APIs via `Packages.*` and focus on OTP calculation only.

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- NeoLoad’s embedded JRE must also be Java 17 or newer, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check `<neoload-install>/jre/bin/java -version` rather than relying on `JAVA_HOME`.
- OpenAuth Simulator JARs on the NeoLoad classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`) via
  `<project>/lib/jslib`.

## 1. JavaScript action for inline EMV/CAP Identify mode

```javascript
// NeoLoad JavaScript action: inline EMV/CAP Identify-mode OTP generation
var EmvCapService = Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
var EvaluationRequest =
        Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
var CustomerInputs =
        Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
var TransactionData =
        Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
var EmvCapMode = Packages.io.openauth.sim.core.emv.cap.EmvCapMode;

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

var masterKeyHex = requirePresent(
        context.variableManager.getValue("emvMasterKeyHex"),
        "NeoLoad variable 'emvMasterKeyHex'");
if (masterKeyHex === null) {
    return;
}
var atcHex = requirePresent(
        context.variableManager.getValue("emvAtcHex"),
        "NeoLoad variable 'emvAtcHex'");
if (atcHex === null) {
    return;
}
var challenge = requirePresent(
        context.variableManager.getValue("emvChallenge"),
        "NeoLoad variable 'emvChallenge'");
if (challenge === null) {
    return;
}
var reference = requirePresent(
        context.variableManager.getValue("emvReference"),
        "NeoLoad variable 'emvReference'");
if (reference === null) {
    return;
}
var amount = requirePresent(
        context.variableManager.getValue("emvAmount"),
        "NeoLoad variable 'emvAmount'");
if (amount === null) {
    return;
}

var request = new EvaluationRequest(
        EmvCapMode.IDENTIFY,
        masterKeyHex,
        atcHex,
        4,   // branchFactor
        8,   // height
        0,   // previewWindowBackward
        0,   // previewWindowForward
        "0000000000000000",          // ivHex
        "000000000000",              // cdol1Hex (example)
        "00001F0000000000000000000000000000000000", // issuerProprietaryBitmapHex
        new CustomerInputs(challenge, reference, amount),
        TransactionData.empty(),
        "",                          // iccDataTemplateHex
        ""                           // issuerApplicationDataHex
);

var service = new EmvCapService();
var result = service.evaluate(request);

var otp = result.otp();
context.variableManager.setValue("emvOtp", otp);
logger.debug("EMV/CAP Identify OTP: " + otp + ", maskLength=" + result.maskLength());
```

## 2. JavaScript action for EMV/CAP presets

```javascript
// NeoLoad JavaScript action: EMV/CAP Identify-mode OTP using a named preset
var EmvCapService = Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
var EvaluationRequest =
        Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
var CustomerInputs =
        Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
var TransactionData =
        Packages.io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
var EmvCapMode = Packages.io.openauth.sim.core.emv.cap.EmvCapMode;

// Simple preset registry keyed by presetId
var presets = {
    "identify-demo": {
        masterKeyHex: "00112233445566778899AABBCCDDEEFF",
        atcHex: "0001",
        branchFactor: 4,
        height: 8,
        ivHex: "0000000000000000",
        cdol1Hex: "000000000000",
        issuerBitmap: "00001F0000000000000000000000000000000000"
    }
};

var presetId = requirePresent(
        context.variableManager.getValue("emvPresetId"),
        "NeoLoad variable 'emvPresetId'");
if (presetId === null) {
    return;
}
var preset = presets[presetId];
if (!preset) {
    throw "Unknown EMV/CAP presetId: " + presetId;
}

var storedChallenge = requirePresent(
        context.variableManager.getValue("emvStoredChallenge"),
        "NeoLoad variable 'emvStoredChallenge'");
if (storedChallenge === null) {
    return;
}
var storedReference = requirePresent(
        context.variableManager.getValue("emvStoredReference"),
        "NeoLoad variable 'emvStoredReference'");
if (storedReference === null) {
    return;
}
var storedAmount = requirePresent(
        context.variableManager.getValue("emvStoredAmount"),
        "NeoLoad variable 'emvStoredAmount'");
if (storedAmount === null) {
    return;
}

var storedRequest = new EvaluationRequest(
        EmvCapMode.IDENTIFY,
        preset.masterKeyHex,
        preset.atcHex,
        preset.branchFactor,
        preset.height,
        0,
        0,
        preset.ivHex,
        preset.cdol1Hex,
        preset.issuerBitmap,
        new CustomerInputs(storedChallenge, storedReference, storedAmount),
        TransactionData.empty(),
        "",
        ""
);

var storedService = new EmvCapService();
var storedResult = storedService.evaluate(storedRequest);

var storedOtp = storedResult.otp();
context.variableManager.setValue("emvStoredOtp", storedOtp);
logger.debug("EMV/CAP Identify OTP (preset=" + presetId + "): " + storedOtp);
```

These patterns mirror the inline and preset EMV/CAP flows from the JMeter and Java guides while keeping NeoLoad focused
on generation of OTP values, not replay of existing traces.
