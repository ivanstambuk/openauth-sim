# How to Drive EUDIW OpenID4VP Wallet Simulation from Neoload via the Native Java API

This guide shows how to call the Native Java EUDIW OpenID4VP wallet-simulation facade from NeoLoad using JavaScript
actions that talk directly to `OpenId4VpWalletSimulationService`. Scripts call the Java APIs via `Packages.*` and focus
on Generate flows only (no validation/replay).

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- NeoLoad’s embedded JRE must also be Java 17 or newer, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check `<neoload-install>/jre/bin/java -version` rather than relying on `JAVA_HOME`.
- OpenAuth Simulator JARs on the NeoLoad classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`) via
  `<project>/lib/jslib`.

## 1. JavaScript action for wallet simulation with stored presets

```javascript
// NeoLoad JavaScript action: EUDIW wallet simulation using stored presets
var WalletService =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
var SimulateRequest =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.SimulateRequest;
var Profile =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile;
var ResponseMode =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
var TrustedAuthorityEvaluator =
        Packages.io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator;
var TrustedAuthorityFixtures =
        Packages.io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;

// Simple preset repository that delegates to the built-in fixtures
var FixtureWalletPresetRepository =
        Packages.io.openauth.sim.application.eudi.openid4vp.fixtures.FixtureWalletPresetRepository;
var presets = new FixtureWalletPresetRepository();

var evaluator = TrustedAuthorityEvaluator.fromSnapshot(
        TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));

var telemetryPublisher = new Packages.io.openauth.sim.application.eudi.openid4vp
        .OpenId4VpWalletSimulationService.TelemetryPublisher({
            walletResponded: function (requestId, profile, presentations, fields) {
                return new Packages.io.openauth.sim.application.eudi.openid4vp
                        .OpenId4VpWalletSimulationService.TelemetrySignal() {
                    event: function () { return "oid4vp.wallet.responded"; },
                    fields: function () { return fields; }
                };
            }
        });

var deps = new WalletService.Dependencies(presets, telemetryPublisher, evaluator);
var walletService = new WalletService(deps);

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

var requestId = requirePresent(
        context.variableManager.getValue("eudiwRequestId"),
        "NeoLoad variable 'eudiwRequestId'");
if (requestId === null) {
    return;
}
var walletPresetId = requirePresent(
        context.variableManager.getValue("eudiwPresetId"),
        "NeoLoad variable 'eudiwPresetId'");
if (walletPresetId === null) {
    return;
}
var trustedPolicy = requirePresent(
        context.variableManager.getValue("eudiwTrustedPolicy"),
        "NeoLoad variable 'eudiwTrustedPolicy'");
if (trustedPolicy === null) {
    return;
}

var request = new SimulateRequest(
        requestId,
        Profile.HAIP,
        ResponseMode.DIRECT_POST_JWT,
        java.util.Optional.of(walletPresetId),
        java.util.Optional.empty(),
        java.util.Optional.of(trustedPolicy));

var result = walletService.simulate(request);

context.variableManager.setValue("eudiwProfile", result.profile().name());
context.variableManager.setValue("eudiwStatus", result.status().name());
logger.debug("EUDIW preset wallet simulation status=" + result.status() + ", profile=" + result.profile());
```

## 2. JavaScript action for wallet simulation with inline SD-JWT

```javascript
// NeoLoad JavaScript action: EUDIW wallet simulation using inline SD-JWT
var WalletService =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
var SimulateRequest =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.SimulateRequest;
var InlineSdJwt =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.InlineSdJwt;
var Profile =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile;
var ResponseMode =
        Packages.io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
var TrustedAuthorityEvaluator =
        Packages.io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator;
var TrustedAuthorityFixtures =
        Packages.io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;

var evaluator = TrustedAuthorityEvaluator.fromSnapshot(
        TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));

var telemetryPublisher = new Packages.io.openauth.sim.application.eudi.openid4vp
        .OpenId4VpWalletSimulationService.TelemetryPublisher({
            walletResponded: function (requestId, profile, presentations, fields) {
                return new Packages.io.openauth.sim.application.eudi.openid4vp
                        .OpenId4VpWalletSimulationService.TelemetrySignal() {
                    event: function () { return "oid4vp.wallet.responded"; },
                    fields: function () { return fields; }
                };
            }
        });

// No preset repository when using InlineSdJwt
var inlineDeps = new WalletService.Dependencies(null, telemetryPublisher, evaluator);
var walletServiceInline = new WalletService(inlineDeps);

// Inputs (must be provided via NeoLoad variables)
var inlineRequestId = requirePresent(
        context.variableManager.getValue("eudiwInlineRequestId"),
        "NeoLoad variable 'eudiwInlineRequestId'");
if (inlineRequestId === null) {
    return;
}
var credentialId = requirePresent(
        context.variableManager.getValue("eudiwInlineCredentialId"),
        "NeoLoad variable 'eudiwInlineCredentialId'");
if (credentialId === null) {
    return;
}
var vpToken = requirePresent(
        context.variableManager.getValue("eudiwInlineVpToken"),
        "NeoLoad variable 'eudiwInlineVpToken'");
if (vpToken === null) {
    return;
}
var trustedPolicyInline = requirePresent(
        context.variableManager.getValue("eudiwInlineTrustedPolicy"),
        "NeoLoad variable 'eudiwInlineTrustedPolicy'");
if (trustedPolicyInline === null) {
    return;
}

var inlineSdJwt = new InlineSdJwt(
        credentialId,
        "dc+sd-jwt",
        vpToken,
        java.util.List.of(),
        java.util.Optional.empty(),
        java.util.List.of(trustedPolicyInline));

var inlineRequest = new SimulateRequest(
        inlineRequestId,
        Profile.HAIP,
        ResponseMode.DIRECT_POST_JWT,
        java.util.Optional.empty(),
        java.util.Optional.of(inlineSdJwt),
        java.util.Optional.of(trustedPolicyInline));

var inlineResult = walletServiceInline.simulate(inlineRequest);

context.variableManager.setValue("eudiwInlineProfile", inlineResult.profile().name());
context.variableManager.setValue("eudiwInlineStatus", inlineResult.status().name());
logger.debug(
        "EUDIW inline wallet simulation status="
                + inlineResult.status()
                + ", profile="
                + inlineResult.profile());
```

These examples mirror the stored and inline EUDIW wallet simulation flows from the JMeter and Java guides, keeping
NeoLoad focused on generation/evaluation of wallet responses without introducing validation or replay flows.
