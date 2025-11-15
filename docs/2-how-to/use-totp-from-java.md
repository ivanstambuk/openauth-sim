# How to Drive TOTP Evaluations with the Native Java API

This guide shows how to embed the OpenAuth Simulator’s TOTP engine directly inside Java&nbsp;17
applications. You will open a `CredentialStore`, construct a `TotpEvaluationApplicationService`,
and drive stored or inline TOTP evaluations (including drift windows and timestamp overrides) via
the Native Java API instead of CLI/REST/UI.

Native Java usage for TOTP is governed by:
- Feature 002 – TOTP Simulator & Tooling (FR-002-01..07).
- Feature 014 – Native Java API Facade (FR-014-02/04).
- ADR-0007 – Native Java API Facade Strategy.

## Prerequisites

- Java 17 JDK (`JAVA_HOME` must point to it per the project constitution).
- OpenAuth Simulator on your classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`,
  and `infra-persistence`).
- Optional: an existing `credentials.db` MapDB file seeded via the CLI’s `maintenance totp evaluate`
  / `maintenance totp replay` workflows.

## 1. Open a CredentialStore and create the TOTP service

Most Java applications will reuse the same MapDB store as the CLI/REST/UI layers. Use
`CredentialStoreFactory` to acquire it and construct the TOTP Native Java entry point
`TotpEvaluationApplicationService`.

```java
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class TotpNativeJavaBootstrap {

    public static TotpEvaluationApplicationService openService() throws IOException {
        Path databasePath = CredentialStoreFactory.resolveDatabasePath(null, "credentials.db");
        MapDbCredentialStore store = CredentialStoreFactory.openFileStore(databasePath);
        return new TotpEvaluationApplicationService(store);
    }
}
```

For ephemeral experiments, you can swap `openFileStore` for `openInMemoryStore()` to avoid touching
disk.

## 2. Generate TOTP codes for stored credentials

Stored evaluations reuse the same credential metadata and time-window semantics as the CLI/REST
facades. To generate a TOTP value (without validating an operator-provided OTP), send an empty OTP
string and a drift window configuration.

```java
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import java.time.Instant;
import java.util.Optional;

public final class TotpStoredGenerator {

    private final TotpEvaluationApplicationService service;

    public TotpStoredGenerator(TotpEvaluationApplicationService service) {
        this.service = service;
    }

    public String generate(String credentialId, Instant evaluationInstant) {
        EvaluationCommand.Stored command = new EvaluationCommand.Stored(
                credentialId,
                "", // empty OTP triggers generation rather than validation
                TotpDriftWindow.of(0, 0),
                evaluationInstant,
                Optional.empty());

        EvaluationResult result = service.evaluate(command);
        if (result.telemetry().status() != TotpEvaluationApplicationService.TelemetryStatus.SUCCESS) {
            throw new IllegalStateException("TOTP generation failed: " + result.telemetry().reason());
        }

        return result.otp();
    }
}
```

This mirrors the “generate-only” paths exercised by the REST/CLI facades: the descriptor’s drift
window controls acceptable skew when validation is requested; when generating, the window is used
for preview entries.

## 3. Validate TOTP codes for stored credentials

To validate an operator-supplied OTP, pass it in the command and set a drift window and timestamp
override (if you want to validate relative to a time other than “now”).

```java
import io.openauth.sim.core.otp.totp.TotpDriftWindow;

public final class TotpStoredValidator {

    private final TotpEvaluationApplicationService service;

    public TotpStoredValidator(TotpEvaluationApplicationService service) {
        this.service = service;
    }

    public boolean validate(String credentialId, String otp, Instant evaluationInstant) {
        EvaluationCommand.Stored command = new EvaluationCommand.Stored(
                credentialId,
                otp,
                TotpDriftWindow.of(1, 1), // allow ±1 step of drift
                evaluationInstant,
                Optional.empty());

        EvaluationResult result = service.evaluate(command);
        return result.telemetry().status() == TotpEvaluationApplicationService.TelemetryStatus.SUCCESS
                && "validated".equals(result.telemetry().reasonCode());
    }
}
```

On success, the telemetry reason code is `validated`; on failure, it will report
`otp_invalid_format`, `credential_not_found`, or `otp_out_of_window` depending on the cause.

## 4. Drive inline TOTP evaluations

Inline evaluations keep all parameters in-process and do not require a pre-seeded credential in the
store. The shared secret is supplied as hexadecimal, matching the TOTP validation vectors bundle.

```java
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import java.time.Duration;

public final class TotpInlineEvaluator {

    private final TotpEvaluationApplicationService service;

    public TotpInlineEvaluator(TotpEvaluationApplicationService service) {
        this.service = service;
    }

    public String generateInline(String secretHex, Instant evaluationInstant) {
        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                secretHex,
                TotpHashAlgorithm.SHA1,          // or SHA256/SHA512
                8,                               // digits
                Duration.ofSeconds(30),          // step size
                "",                              // empty OTP => generate
                TotpDriftWindow.of(0, 0),
                evaluationInstant,
                Optional.empty());

        EvaluationResult result = service.evaluate(command);
        if (result.telemetry().status() != TotpEvaluationApplicationService.TelemetryStatus.SUCCESS) {
            throw new IllegalStateException("Inline TOTP generation failed: " + result.telemetry().reason());
        }
        return result.otp();
    }
}
```

To validate an inline OTP, provide a non-empty `otp` and the same descriptor parameters; the
result’s telemetry status/reason will indicate success or the specific validation issue.

## 5. Bridge telemetry into your logging/monitoring stack

The Native Java TOTP API reuses `TelemetryContracts.totpEvaluationAdapter()`. Use
`TotpEvaluationApplicationService.EvaluationResult.evaluationFrame(...)` to create a
`TelemetryFrame` and then ship it to your preferred sink.

```java
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;

public final class TotpTelemetryBridge {

    public TelemetryFrame toFrame(EvaluationResult result, String telemetryId) {
        return result.evaluationFrame(telemetryId);
    }
}
```

Telemetry fields mirror the REST/CLI events:
- `credentialReference` and optional `credentialId`.
- `algorithm`, `digits`, `stepSeconds`.
- `driftBackwardSteps`, `driftForwardSteps`.
- `timestampOverrideProvided` and `matchedSkewSteps`.

## 6. Wire the Native Java API into your application

- Treat `TotpEvaluationApplicationService` as the façade seam for TOTP Native Java usage and avoid
  reaching into controller or repository internals.
- Use the same credential store across CLI/REST/UI/Native Java when you want descriptors and clock
  behaviour to align; for isolated tests, prefer `openInMemoryStore()`.
- Add thin request DTOs or service wrappers in your own code that collect the minimal inputs
  (credential identifier + OTP for stored flows, or secret/algorithm/digits/step for inline flows)
  and delegate to the Native Java API.
- Keep this guide and your usage in sync with Feature 002 and Feature 014; when behaviour changes,
  update specs, Javadoc, tests, and this how-to in the same increment.

By relying on the Native Java TOTP API, Java applications can generate and validate TOTP values
without running the REST API or CLI in-process, while still benefiting from the simulator’s
persistence, fixtures, and telemetry contracts.

