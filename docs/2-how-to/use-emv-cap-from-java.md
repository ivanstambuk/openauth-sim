# How to Drive EMV/CAP Evaluations with the Native Java API

This guide shows how to embed the OpenAuth Simulator’s EMV/CAP engine directly inside Java&nbsp;17
applications. You will construct `EmvCapEvaluationApplicationService`, build `EvaluationRequest`
records, and drive Identify/Respond/Sign simulations via the Native Java API instead of CLI/REST/UI.

Native Java usage for EMV/CAP is governed by:
- Feature 005 – EMV/CAP Simulation Services (FR-005-01..07).
- Feature 014 – Native Java API Facade (FR-014-02/04).
- ADR-0007 – Native Java API Facade Strategy.

## Prerequisites

- Java 17 JDK (`JAVA_HOME` must point to it per the project constitution).
- OpenAuth Simulator on your classpath (at minimum: `core`, `core-shared`, `core-ocra`, and
  `application`).
- Optional: shared configuration for master keys and CDOL templates that your application uses to
  populate EMV/CAP requests.

## 1. Create the EMV/CAP evaluation service

The EMV/CAP Native Java seam is `EmvCapEvaluationApplicationService`. It is a stateless orchestrator
that wraps the core EMV/CAP engine and telemetry emitters.

```java
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;

public final class EmvCapNativeJavaBootstrap {

    private static final EmvCapEvaluationApplicationService SERVICE = new EmvCapEvaluationApplicationService();

    public static EmvCapEvaluationApplicationService evaluationService() {
        return SERVICE;
    }
}
```

Unlike HOTP/TOTP/WebAuthn, EMV/CAP does not use `CredentialStore`; all parameters are supplied in
the `EvaluationRequest`.

## 2. Build an Identify-mode EvaluationRequest

Identify mode uses a subset of the full EMV/CAP inputs but still follows the same request record
schema. An example Identify request:

```java
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.core.emv.cap.EmvCapMode;

public final class EmvCapIdentifyEvaluator {

    private final EmvCapEvaluationApplicationService service;

    public EmvCapIdentifyEvaluator(EmvCapEvaluationApplicationService service) {
        this.service = service;
    }

    public EvaluationRequest buildRequest(
            String masterKeyHex, String atcHex, String challenge, String reference, String amount) {
        return new EvaluationRequest(
                EmvCapMode.IDENTIFY,
                masterKeyHex,
                atcHex,
                4,  // branchFactor
                8,  // height
                0,  // previewWindowBackward
                0,  // previewWindowForward
                "0000000000000000", // ivHex
                "000000000000",     // cdol1Hex (example)
                "00001F0000000000000000000000000000000000", // issuerProprietaryBitmapHex
                new CustomerInputs(challenge, reference, amount),
                TransactionData.empty(),
                "", // iccDataTemplateHex
                ""); // issuerApplicationDataHex
    }
}
```

## 3. Evaluate a request and inspect results

Use the service’s `evaluate` method to obtain an `EvaluationResult`. The result contains the OTP,
mask length, preview entries, and telemetry that can be bridged into your logging system.

```java
public final class EmvCapIdentifyRunner {

    private final EmvCapEvaluationApplicationService service;

    public EmvCapIdentifyRunner(EmvCapEvaluationApplicationService service) {
        this.service = service;
    }

    public void runIdentify(EvaluationRequest request) {
        EvaluationResult result = service.evaluate(request);

        System.out.println("OTP: " + result.otp());
        System.out.println("Mask length: " + result.maskLength());
        result.previews().forEach(preview ->
                System.out.printf("Δ=%d ATC=%s OTP=%s%n", preview.delta(), preview.counter(), preview.otp()));
    }
}
```

On validation errors (for example, malformed hex or negative branch factors), the result’s OTP is
empty and the telemetry signal captures the error reason. Callers should inspect the telemetry
before using the OTP.

## 4. Drive Respond/Sign modes

Respond and Sign modes use the same `EvaluationRequest` record but may require different customer
inputs and CDOL templates. For example, Sign mode typically requires `challenge`, `reference`, and
`amount` to be populated and may use a different branch/height configuration. You can construct
mode-specific helper methods that fill in the appropriate fields and reuse the same
`EmvCapEvaluationApplicationService`.

## 5. Bridge telemetry into your logging/monitoring stack

`EvaluationResult.telemetry()` returns an EMV/CAP-specific telemetry signal encapsulating:
- `mode`, `atc`, `branchFactor`, `height`.
- `maskLength`, preview-window offsets.
- Sanitised error details for validation failures.

Use `EvaluationResult.telemetryFrame(telemetryId)` to turn this signal into a
`TelemetryFrame` via `TelemetryContracts.emvCap*` adapters, then ship it to your preferred sink.

## 6. Wire the Native Java API into your application

- Treat `EmvCapEvaluationApplicationService` as the façade seam for EMV/CAP Native Java usage and
  avoid reaching into REST/CLI/controller internals.
- Keep request construction in your own domain types, then map into `EvaluationRequest` just before
  calling the service.
- Align your usage with Feature 005 and Feature 014; when EMV/CAP behaviour changes, update specs,
  tests, and this how-to together so docs and code never drift.

By relying on the Native Java EMV/CAP API, Java applications can simulate Identify/Respond/Sign
flows without running the REST API or CLI in-process, while still benefiting from the simulator’s
trace schema and telemetry contracts.

