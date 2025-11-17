# How to Drive HOTP Evaluations with the Native Java API

This guide shows how to embed the OpenAuth Simulator’s HOTP engine directly inside Java&nbsp;17
applications. You will open a `CredentialStore`, construct a `HotpEvaluationApplicationService`,
and drive stored or inline evaluations via the Native Java API instead of CLI/REST/UI.

Native Java usage for HOTP is governed by:
- Feature 001 – HOTP Simulator & Tooling (FR-001-01..07).
- Feature 014 – Native Java API Facade (FR-014-02/04).
- ADR-0007 – Native Java API Facade Strategy.

## Prerequisites

- Java 17 JDK (`JAVA_HOME` must point to it per the project constitution).
- OpenAuth Simulator on your classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`, and `infra-persistence`).
- Optional: an existing `credentials.db` MapDB file seeded via the CLI’s `maintenance hotp import` command.

## 1. Open a CredentialStore and create the HOTP service

Most Java applications will reuse the same MapDB store as the CLI/REST/UI layers. Use
`CredentialStoreFactory` to acquire it and construct the HOTP Native Java entry point
`HotpEvaluationApplicationService`.

```java
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class HotpNativeJavaBootstrap {

    public static HotpEvaluationApplicationService openService() throws IOException {
        Path databasePath = CredentialStoreFactory.resolveDatabasePath(null, "credentials.db");
        MapDbCredentialStore store = CredentialStoreFactory.openFileStore(databasePath);
        return new HotpEvaluationApplicationService(store);
    }
}
```

For ephemeral experiments, you can swap `openFileStore` for `openInMemoryStore()` to avoid touching
disk.

## 2. Evaluate stored HOTP credentials

Stored evaluations reuse the same credential metadata and counter semantics as the CLI/REST facades.
The `credentialId` corresponds to the name field used when the credential was imported or created.

```java
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;

public final class HotpStoredEvaluator {

    private final HotpEvaluationApplicationService service;

    public HotpStoredEvaluator(HotpEvaluationApplicationService service) {
        this.service = service;
    }

    public String evaluate(String credentialId) {
        // windowBackward/windowForward mirror the HOTP operator UI options
        EvaluationCommand.Stored command = new EvaluationCommand.Stored(credentialId, 0, 0);

        EvaluationResult result = service.evaluate(command);
        if (result.telemetry().status() != HotpEvaluationApplicationService.TelemetryStatus.SUCCESS) {
            throw new IllegalStateException("HOTP evaluation failed: " + result.telemetry().reason());
        }

        // The counter is incremented in the underlying CredentialStore on success.
        System.out.printf(
                "HOTP %s: previous=%d, next=%d%n",
                credentialId, result.previousCounter(), result.nextCounter());

        return result.otp();
    }
}
```

This mirrors the behaviour of `maintenance hotp evaluate` and the HOTP operator UI: successful
evaluations increment the moving factor and emit telemetry events such as `hotp.evaluate`.

## 3. Evaluate inline HOTP requests

Inline evaluations keep all parameters in-process and do not require a pre-seeded credential in the
store. The shared secret is supplied as hexadecimal, matching the HOTP validation vectors bundle.

```java
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;

public final class HotpInlineEvaluator {

    private final HotpEvaluationApplicationService service;

    public HotpInlineEvaluator(HotpEvaluationApplicationService service) {
        this.service = service;
    }

    public String evaluateInline(String secretHex, long counter) {
        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                secretHex,
                HotpHashAlgorithm.SHA1, // or SHA256/SHA512
                6,                      // digits
                counter,
                Map.of(),               // optional metadata (presetKey/presetLabel)
                0,                      // windowBackward
                0);                     // windowForward

        EvaluationResult result = service.evaluate(command);
        if (result.telemetry().status() != HotpEvaluationApplicationService.TelemetryStatus.SUCCESS) {
            throw new IllegalArgumentException("Inline HOTP evaluation failed: " + result.telemetry().reason());
        }

        return result.otp();
    }
}
```

Invalid inputs (for example, a missing counter or too-short secret) produce an `EvaluationResult`
with `TelemetryStatus.INVALID`, a descriptive `reason`, and `otp == null`. Callers should surface
the sanitized reason to operators and allow retry with corrected input.

## 4. Bridge telemetry into your logging/monitoring stack

The Native Java API reuses the same telemetry adapters as CLI/REST/UI. Use
`HotpEvaluationApplicationService.EvaluationResult.evaluationFrame(...)` to create a
`TelemetryFrame` and then ship it to your preferred sink.

```java
import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;

public final class HotpTelemetryBridge {

    private final HotpTelemetryAdapter adapter;

    public HotpTelemetryBridge(HotpTelemetryAdapter adapter) {
        this.adapter = adapter;
    }

    public TelemetryFrame toFrame(EvaluationResult result, String telemetryId) {
        return result.evaluationFrame(adapter, telemetryId).frame();
    }
}
```

Telemetry fields mirror the REST/CLI events:
- `credentialSource` – `stored` or `inline`.
- `credentialId` – populated for stored evaluations.
- `hashAlgorithm`, `digits` – chosen HOTP parameters.
- `previousCounter`, `nextCounter` – moving factor before/after evaluation.
- Optional inline preset metadata used by the operator UI.

## 5. Wire the Native Java API into your application

- Treat `HotpEvaluationApplicationService` as the façade seam for HOTP Native Java usage and avoid
  reaching into controller or repository internals.
- Use the same credential store across CLI/REST/UI/Native Java when you want counters and fixtures
  to align; for isolated tests, prefer `openInMemoryStore()`.
- Add thin request DTOs or service wrappers in your own code that collect the minimal inputs
  (credential identifier or inline parameters) and delegate to the Native Java API.
- Keep this guide and your usage in sync with Feature 001 and Feature 014; when behaviour changes,
  update specs, Javadoc, tests, and this how-to in the same increment.

By relying on the Native Java HOTP API, Java applications can generate and replay HOTP values
without running the REST API or CLI in-process, while still benefiting from the simulator’s
persistence, fixtures, and telemetry contracts.

