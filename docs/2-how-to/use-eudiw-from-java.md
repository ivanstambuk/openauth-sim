# How to Drive EUDIW OpenID4VP Flows with the Native Java API

This guide shows how to embed the EUDIW OpenID4VP simulator directly inside Java&nbsp;17
applications. You will construct `OpenId4VpWalletSimulationService` and
`OpenId4VpValidationService` and drive wallet simulation and validation flows via the Native Java
API instead of CLI/REST/UI.

Native Java usage for EUDIW OpenID4VP is governed by:
- Feature 006 – EUDIW OpenID4VP Simulator (FR-006-01.., FR-040-xx).
- Feature 014 – Native Java API Facade (FR-014-02/04).
- ADR-0007 – Native Java API Facade Strategy.

## Prerequisites

- Java 17 JDK (`JAVA_HOME` must point to it per the project constitution).
- OpenAuth Simulator on your classpath (at minimum: `core`, `core-shared`, `core-ocra`, and
  `application`).
- For realistic simulations, ensure the EUDIW OpenID4VP fixtures under
  ``docs/test-vectors/eudiw/openid4vp`/` are available; they include presets such as
  `pid-haip-baseline` (SD-JWT VC PID) and `pid-mdoc` (ISO/IEC 18013-5 mdoc PID) that the REST/CLI
  and operator UI guides use in their examples.

## 1. Create wallet simulation and validation services

The EUDIW Native Java seams are:
- `OpenId4VpWalletSimulationService` – wallet simulation (Generate mode).
- `OpenId4VpValidationService` – validation of VP Tokens / DeviceResponses (Validate mode).

Both services take `Dependencies` aggregates so you can plug in repositories, Trusted Authorities,
and telemetry adapters.

```java
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.TrustedAuthorityEvaluator;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;

public final class EudiwNativeJavaBootstrap {

    public static OpenId4VpWalletSimulationService walletSimulationService(
            OpenId4VpWalletSimulationService.WalletPresetRepository presets,
            TrustedAuthorityEvaluator evaluator,
            OpenId4VpWalletSimulationService.TelemetryPublisher telemetry) {
        OpenId4VpWalletSimulationService.Dependencies deps =
                new OpenId4VpWalletSimulationService.Dependencies(presets, evaluator, telemetry);
        return new OpenId4VpWalletSimulationService(deps);
    }

    public static OpenId4VpValidationService validationService(
            OpenId4VpValidationService.StoredPresentationRepository repository,
            TrustedAuthorityEvaluator evaluator,
            OpenId4VpValidationService.TelemetryPublisher telemetry) {
        OpenId4VpValidationService.Dependencies deps =
                new OpenId4VpValidationService.Dependencies(repository, evaluator, telemetry);
        return new OpenId4VpValidationService(deps);
    }

    public static TrustedAuthorityEvaluator haipBaselineEvaluator() {
        return TrustedAuthorityEvaluator.fromSnapshot(
                TrustedAuthorityFixtures.loadSnapshot("haip-baseline"));
    }
}
```

## 2. Simulate a wallet response

Wallet simulation accepts a `SimulateRequest` that references either a stored wallet preset or an
inline SD‑JWT/misc payload.

```java
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.Profile;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.ResponseMode;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.SimulateRequest;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService.SimulationResult;

public final class EudiwWalletRunner {

    private final OpenId4VpWalletSimulationService walletService;

    public EudiwWalletRunner(OpenId4VpWalletSimulationService walletService) {
        this.walletService = walletService;
    }

    public SimulationResult simulatePreset(String requestId, String walletPresetId, String trustedPolicy) {
        SimulateRequest request = new SimulateRequest(
                requestId,
                Profile.HAIP,
                ResponseMode.DIRECT_POST_JWT,
                java.util.Optional.of(walletPresetId),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.of(trustedPolicy));

        return walletService.simulate(request);
    }
}
```

The `SimulationResult` record exposes:
- `status` / `profile` / `responseMode`.
- `presentations` – a list containing credential IDs, formats, and holder‑binding flags.
- `trace` – hashes and Trusted Authority verdicts.
- `telemetry` – an OpenID4VP wallet telemetry signal.

The `trustedPolicy` field should use one of the Trusted Authority policy identifiers exposed by the
fixtures (for example, the `aki:…` values documented in the REST/CLI EUDIW how-to guides); policies
and snapshots must remain in sync with the governing Feature 006/040 specifications.

## 3. Validate a wallet response

Validation uses `ValidateRequest` and `ValidationResult` on
`OpenId4VpValidationService`. You can validate stored presentations (by ID) or inline VP Tokens.

```java
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService.InlineVpToken;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService.ValidateRequest;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService.ValidationResult;

public final class EudiwValidationRunner {

    private final OpenId4VpValidationService validationService;

    public EudiwValidationRunner(OpenId4VpValidationService validationService) {
        this.validationService = validationService;
    }

    public ValidationResult validateInlineSdJwt(
            String requestId, String credentialId, String vpToken, String trustedPolicy) {
        InlineVpToken inlineVpToken = new InlineVpToken(
                credentialId,
                "dc+sd-jwt",
                java.util.Map.of("vp_token", vpToken, "presentation_submission", java.util.Map.of()),
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of(trustedPolicy));

        ValidateRequest request = new ValidateRequest(
                requestId,
                java.util.Optional.empty(),
                java.util.Optional.of(inlineVpToken),
                java.util.Optional.of(trustedPolicy));

        return validationService.validate(request);
    }
}
```

On success, the `ValidationResult` contains a list of `Presentation` records plus a `Trace` and a
validation telemetry signal. On failure, the service throws `Oid4vpValidationException` with
problem-details content; callers should catch it and surface the sanitised error response.

## 4. Telemetry and tracing

Telemetry is emitted via the supplied `TelemetryPublisher` collaborators:
- Wallet simulations use `oid4vp.wallet.responded` events.
- Validation uses `oid4vp.response.validated` / `oid4vp.response.failed` events.

The `Trace` records carry VP Token hashes, Trusted Authority verdicts, preset identifiers, and
disclosure hashes, mirroring the REST/CLI/operator-console behaviour.

## 5. Wiring into your application

- Treat `OpenId4VpWalletSimulationService` and `OpenId4VpValidationService` as the façade seams for
  OpenID4VP Native Java usage and avoid reaching into REST/CLI/controller internals.
- Use your own repositories/preset stores and implement the required repository/telemetry interfaces
  when constructing `Dependencies`.
- Keep this guide and your usage aligned with Feature 006 and Feature 014; when behaviour changes,
  update specs, tests, and this how-to in the same increment.

By relying on the Native Java EUDIW OpenID4VP APIs, Java applications can simulate and validate
remote verifier/wallet interactions without running the REST API or CLI in-process, while still
benefiting from the simulator’s fixtures, Trusted Authorities logic, and telemetry contracts.
