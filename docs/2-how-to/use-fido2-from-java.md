# How to Drive WebAuthn Assertion Evaluation with the Native Java API

This guide shows how to embed the OpenAuth Simulator’s WebAuthn assertion verifier directly inside
Java&nbsp;17 applications. You will open a `CredentialStore`, construct a
`WebAuthnEvaluationApplicationService`, and drive stored or inline assertion evaluations via the
Native Java API instead of CLI/REST/UI.

Native Java usage for WebAuthn assertions is governed by:
- Feature 004 – FIDO2/WebAuthn Assertions & Attestations (FR-004-01/02).
- Feature 014 – Native Java API Facade (FR-014-02/04).
- ADR-0007 – Native Java API Facade Strategy.

## Prerequisites

- Java 17 JDK (`JAVA_HOME` must point to it per the project constitution).
- OpenAuth Simulator on your classpath (at minimum: `core`, `core-shared`, `core-ocra`, `application`, and `infra-persistence`).
- Optional: an existing `credentials.db` MapDB file seeded with WebAuthn credentials via the CLI or
  REST seeding flows.

## 1. Open a CredentialStore and create the WebAuthn evaluation service

Most Java applications will reuse the same MapDB store as the CLI/REST/UI layers. Use
`CredentialStoreFactory` and the application `WebAuthnEvaluationApplicationService` to construct
the Native Java seam.

```java
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class WebAuthnNativeJavaBootstrap {

    public static WebAuthnEvaluationApplicationService openEvaluationService() throws IOException {
        Path databasePath = CredentialStoreFactory.resolveDatabasePath(null, "credentials.db");
        MapDbCredentialStore store = CredentialStoreFactory.openFileStore(databasePath);
        WebAuthnAssertionVerifier verifier = WebAuthnAssertionVerifier.defaultVerifier();
        WebAuthnCredentialPersistenceAdapter persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();
        return new WebAuthnEvaluationApplicationService(store, verifier, persistenceAdapter);
    }
}
```

For ephemeral experiments, you can swap `openFileStore` for `openInMemoryStore()` to avoid touching
disk.

## 2. Evaluate stored WebAuthn assertions

Stored evaluations reuse the same credential metadata and verification semantics as the CLI/REST
facades. The `credentialId` corresponds to the name used when seeding the credential; the
`EvaluationCommand.Stored` payload mirrors the REST `fido2.evaluate` request body.

```java
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationResult;

public final class WebAuthnStoredEvaluator {

    private final WebAuthnEvaluationApplicationService service;

    public WebAuthnStoredEvaluator(WebAuthnEvaluationApplicationService service) {
        this.service = service;
    }

    public EvaluationResult evaluateStored(
            String credentialName,
            String rpId,
            String origin,
            String expectedType,
            byte[] expectedChallenge,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature) {

        EvaluationCommand.Stored command = new EvaluationCommand.Stored(
                credentialName,
                rpId,
                origin,
                expectedType,
                expectedChallenge,
                clientDataJson,
                authenticatorData,
                signature);

        return service.evaluate(command);
    }
}
```

The returned `EvaluationResult` exposes:
- `valid` – whether the assertion passed verification.
- `credentialReference` / `credentialId` – whether a stored credential was used.
- `algorithm` / `userVerificationRequired`.
- `error` – an optional `WebAuthnVerificationError` describing the failure category.

## 3. Evaluate inline WebAuthn assertions

Inline evaluations keep all parameters in-process and do not require a pre-seeded credential in the
store. You supply a raw credential ID, public key (COSE), algorithm, and assertion components. This
is useful when modelling production assertions against a synthetic store.

```java
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;

public final class WebAuthnInlineEvaluator {

    private final WebAuthnEvaluationApplicationService service;

    public WebAuthnInlineEvaluator(WebAuthnEvaluationApplicationService service) {
        this.service = service;
    }

    public EvaluationResult evaluateInline(
            String credentialName,
            String rpId,
            String origin,
            String expectedType,
            byte[] credentialId,
            byte[] publicKeyCose,
            long signatureCounter,
            boolean userVerificationRequired,
            WebAuthnSignatureAlgorithm algorithm,
            byte[] expectedChallenge,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature) {

        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                credentialName,
                rpId,
                origin,
                expectedType,
                credentialId,
                publicKeyCose,
                signatureCounter,
                userVerificationRequired,
                algorithm,
                expectedChallenge,
                clientDataJson,
                authenticatorData,
                signature);

        return service.evaluate(command);
    }
}
```

On success, `valid` is `true` and `error` is empty; on failure, `valid` is `false`, `telemetry`
contains a sanitized `reasonCode`, and `error` captures the `WebAuthnVerificationError` category.

## 4. Bridge telemetry into your logging/monitoring stack

The Native Java WebAuthn API reuses `Fido2TelemetryAdapter`. Use
`WebAuthnEvaluationApplicationService.EvaluationResult.evaluationFrame(...)` to obtain a
`TelemetryFrame`.

```java
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;

public final class WebAuthnTelemetryBridge {

    public TelemetryFrame toFrame(EvaluationResult result, String telemetryId) {
        Fido2TelemetryAdapter adapter = TelemetryContracts.fido2EvaluationAdapter();
        return result.evaluationFrame(adapter, telemetryId);
    }
}
```

Telemetry fields mirror the REST/CLI events:
- `credentialSource` – `stored` or `inline`.
- `credentialReference` and optional `credentialId`.
- `relyingPartyId`, `origin`, `algorithm`.
- `userVerificationRequired`, and optional error metadata.

## 5. Wire the Native Java API into your application

- Treat `WebAuthnEvaluationApplicationService` as the façade seam for WebAuthn assertion evaluation
  and avoid reaching into controller or verifier internals.
- Use the same credential store across CLI/REST/UI/Native Java when you want credential contents
  and counters to align; for isolated tests, prefer `openInMemoryStore()`.
- Add thin request DTOs or service wrappers in your own code that collect the minimal inputs
  (credential identifier + assertion payloads) and delegate to the Native Java API.
- Keep this guide and your usage in sync with Feature 004 and Feature 014; when behaviour changes,
  update specs, Javadoc, tests, and this how-to in the same increment.

By relying on the Native Java WebAuthn API, Java applications can evaluate stored and inline
WebAuthn assertions without running the REST API or CLI in-process, while still benefiting from the
simulator’s persistence, fixtures, verbose traces, and telemetry contracts.

