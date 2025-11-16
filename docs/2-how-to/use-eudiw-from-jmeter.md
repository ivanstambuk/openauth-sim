# How to Drive EUDIW OpenID4VP Flows from JMeter via the Native Java API

This guide shows how to call the Native Java EUDIW OpenID4VP facades from Apache JMeter using a JSR223 Sampler with
Groovy. The snippets below construct wallet-simulation services and calculate OpenID4VP responses using stored presets
or inline SD-JWT payloads (no replay or validation flows).

## Prerequisites

- Java 17 JDK to build and run OpenAuth Simulator itself (per the project constitution).
- JMeter must also run on a Java 17 (or newer) runtime, because the OpenAuth Simulator JARs are compiled with
  `targetCompatibility = 17`. Check the `java -version` used by `jmeter`/`jmeter.bat` rather than relying only on
  `JAVA_HOME`.
- OpenAuth Simulator JARs on JMeter’s classpath (at minimum: `core`, `core-shared`, `core-ocra`, and `application`).
- JMeter configured with a JSR223 Sampler using `groovy`.

## 1. JSR223 Sampler for wallet simulation with stored presets

The Groovy script below simulates a wallet response using the Native Java `OpenId4VpWalletSimulationService` and a
simple in-script `WalletPresetRepository`. This models stored wallet presets keyed by `walletPresetId`:

```groovy
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.SimulateRequest
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.WalletPreset
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.WalletPresetRepository
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.TelemetryPublisher
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.TelemetrySignal
import io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures

import java.util.Optional

// Guard: fail fast if required inputs are missing
def requirePresentPreset = { String value, String label ->
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

// Registry-backed WalletPresetRepository keyed by presetId
WalletPresetRepository presets = { String id ->
    if ("preset-1".equals(id)) {
        String presetVpToken = requirePresentPreset(vars.get("eudiwPresetVpToken"), "JMeter variable 'eudiwPresetVpToken'")
        if (presetVpToken == null) {
            return null
        }
        return new WalletPreset(
                id,
                "pid-eu.europa.ec.eudi.pid.1",
                "dc+sd-jwt",
                presetVpToken,
                java.util.List.of(),
                java.util.List.of(),
                Optional.empty(),
                java.util.List.of()
        )
    }
    throw new IllegalArgumentException("Unknown EUDIW presetId: " + id)
} as WalletPresetRepository

TelemetryPublisher telemetry = { String requestId,
                                 Profile profile,
                                 java.util.List presentations,
                                 java.util.Map fields ->
    return new TelemetrySignal() {
        @Override
        String event() { 'oid4vp.wallet.responded' }

        @Override
        java.util.Map fields() { fields }
    }
}

TrustedAuthorityEvaluator evaluator = TrustedAuthorityEvaluator.fromSnapshot(
        TrustedAuthorityFixtures.loadSnapshot('haip-baseline')
)

def deps = new OpenId4VpWalletSimulationService.Dependencies(presets, telemetry, evaluator)
def service = new OpenId4VpWalletSimulationService(deps)

String requestId = requirePresentPreset(vars.get('eudiwRequestId'), "JMeter variable 'eudiwRequestId'")
if (requestId == null) return
String presetId = requirePresentPreset(vars.get('eudiwPresetId'), "JMeter variable 'eudiwPresetId'")
if (presetId == null) return
String trustedPolicy = requirePresentPreset(vars.get('eudiwTrustedPolicy'), "JMeter variable 'eudiwTrustedPolicy'")
if (trustedPolicy == null) return

SimulateRequest request = new SimulateRequest(
        requestId,
        Profile.HAIP,
        ResponseMode.DIRECT_POST_JWT,
        Optional.of(presetId),
        Optional.empty(),
        Optional.of(trustedPolicy)
)

def result = service.simulate(request)

vars.put('eudiwProfile', result.profile().name())
vars.put('eudiwStatus', result.status().name())
log.info("EUDIW wallet simulation status=${result.status()}, profile=${result.profile()}")
```

This script demonstrates calculation flows for stored wallet presets using the HAIP profile. You can extend the preset
registry to model additional stored wallets.

## 2. JSR223 Sampler for wallet simulation with inline SD-JWT

The next script bypasses presets and uses an inline SD-JWT payload via `InlineSdJwt`, matching the “inline credential”
pattern:

```groovy
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.SimulateRequest
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.InlineSdJwt
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.TelemetryPublisher
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.TelemetrySignal
import io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures

import java.util.Optional

TelemetryPublisher inlineTelemetry = { String requestId,
                                      Profile profile,
                                      java.util.List presentations,
                                      java.util.Map fields ->
    return new TelemetrySignal() {
        @Override
        String event() { "oid4vp.wallet.responded" }

        @Override
        java.util.Map fields() { fields }
    }
}

TrustedAuthorityEvaluator inlineEvaluator = TrustedAuthorityEvaluator.fromSnapshot(
        TrustedAuthorityFixtures.loadSnapshot("haip-baseline")
)

def inlineDeps = new OpenId4VpWalletSimulationService.Dependencies(
        null, // no preset repository when using InlineSdJwt
        inlineTelemetry,
        inlineEvaluator
)
def inlineService = new OpenId4VpWalletSimulationService(inlineDeps)

// Guard: fail fast if required inputs are missing
def requirePresentInline = { String value, String label ->
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

String inlineRequestId = requirePresentInline(vars.get("eudiwInlineRequestId"), "JMeter variable 'eudiwInlineRequestId'")
if (inlineRequestId == null) return
String credentialId = requirePresentInline(vars.get("eudiwInlineCredentialId"), "JMeter variable 'eudiwInlineCredentialId'")
if (credentialId == null) return
String vpToken = requirePresentInline(vars.get("eudiwInlineVpToken"), "JMeter variable 'eudiwInlineVpToken'")
if (vpToken == null) return
String trustedPolicyInline = requirePresentInline(vars.get("eudiwInlineTrustedPolicy"), "JMeter variable 'eudiwInlineTrustedPolicy'")
if (trustedPolicyInline == null) return

InlineSdJwt inlineSdJwt = new InlineSdJwt(
        credentialId,
        "dc+sd-jwt",
        vpToken,
        java.util.List.of(),
        Optional.empty(),
        java.util.List.of(trustedPolicyInline)
)

SimulateRequest inlineRequest = new SimulateRequest(
        inlineRequestId,
        Profile.HAIP,
        ResponseMode.DIRECT_POST_JWT,
        Optional.empty(),
        Optional.of(inlineSdJwt),
        Optional.of(trustedPolicyInline)
)

def inlineResult = inlineService.simulate(inlineRequest)

vars.put("eudiwInlineProfile", inlineResult.profile().name())
vars.put("eudiwInlineStatus", inlineResult.status().name())
log.info("EUDIW inline wallet simulation status=${inlineResult.status()}, profile=${inlineResult.profile()}")
```

Together, these examples give you both stored/preset and inline credential flows for EUDIW wallet simulation, without
introducing replay or validation semantics.
