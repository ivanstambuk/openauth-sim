# How to Drive OCRA Evaluations with the Native Java API

This guide shows operators how to embed the OpenAuth Simulator directly inside their own Java 17 applications. You will create credential descriptors with `OcraCredentialFactory`, store them in-memory under human-friendly identifiers, and compute OTP/assertion values through `OcraResponseCalculator`.

## Prerequisites
- Java 17 JDK (`JAVA_HOME` must point to it per the project constitution).
- The OpenAuth Simulator JAR (at minimum the `core` module) on your classpath.
- Optional: a secure store of descriptor metadata (JSON, database, etc.) that you load before evaluating requests. External clients should **not** manipulate the simulator’s MapDB files directly.

## 1. Build or Load Credential Descriptors
Create descriptors once at startup and cache them by identifier so operators can reference credentials by name.

```java
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.model.SecretEncoding;
import java.time.Duration;
import java.util.Map;

public final class CredentialRegistry {
    private final OcraCredentialFactory factory = new OcraCredentialFactory();
    private final Map<String, OcraCredentialDescriptor> descriptors;

    public CredentialRegistry() {
        descriptors = Map.of(
            "operator-demo",
            factory.createDescriptor(
                new OcraCredentialRequest(
                    "operator-demo",
                    "OCRA-1:HOTP-SHA256-8:QA08-S064",
                    "3132333435363738393031323334353637383930313233343536373839303132",
                    SecretEncoding.HEX,
                    null,              // counter (only for suites requiring C)
                    null,              // optional PIN hash
                    Duration.ofMinutes(2),
                    Map.of("owner", "demo"))));
    }

    public OcraCredentialDescriptor findById(String id) {
        OcraCredentialDescriptor descriptor = descriptors.get(id);
        if (descriptor == null) {
            throw new IllegalArgumentException("credential not registered: " + id);
        }
        return descriptor;
    }
}
```

### Alternatives?
`OcraCredentialFactory` handles secret normalization, suite parsing, and validation telemetry. Low-level factories (such as `OcraCredentialDescriptorFactory`) exist for internal use but they skip validation helpers, so stick with `OcraCredentialFactory` for operator flows.

## 2. Evaluate Stored Credentials
Use `OcraResponseCalculator.generate(...)` with an `OcraExecutionContext` populated for the requested suite.

```java
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;

public final class OcraEvaluator {
    private final CredentialRegistry registry = new CredentialRegistry();

    public String evaluateStored(String credentialId, String challenge, String sessionHex) {
        OcraCredentialDescriptor descriptor = registry.findById(credentialId);
        OcraExecutionContext context = new OcraExecutionContext(
            null,                 // counter (C)
            challenge,            // combined question (Q)
            sessionHex,           // session data (S064)
            null, null,           // client/server challenge (not used in QA suites)
            null,                 // runtime PIN hash
            null);                // timestamp (for T suites)
        return OcraResponseCalculator.generate(descriptor, context);
    }
}
```

Usage example:
```java
OcraEvaluator evaluator = new OcraEvaluator();
String otp = evaluator.evaluateStored(
    "operator-demo",
    "SESSION01", // 8-character alphanumeric per QA08
    "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567");
System.out.println("OTP: " + otp);
```

If required fields are missing or malformed, `OcraResponseCalculator.generate` throws `IllegalArgumentException` with diagnostic text while also emitting validation telemetry.

## 3. Evaluate Inline Requests
When operators provide all parameters at runtime, create a descriptor on the fly and execute immediately.

```java
public String evaluateInline(
        String suite,
        String secretHex,
        String challenge,
        String sessionHex) {
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialDescriptor descriptor = factory.createDescriptor(
        new OcraCredentialRequest(
            "inline-demo",
            suite,
            secretHex,
            SecretEncoding.HEX,
            null,
            null,
            null,
            Map.of()));

    OcraExecutionContext context = new OcraExecutionContext(
        null,
        challenge,
        sessionHex,
        null,
        null,
        null,
        null);
    return OcraResponseCalculator.generate(descriptor, context);
}
```

## 4. Handle Advanced Suites
Suites that include counters, timestamps, split challenges, or PIN verification require additional context fields:

- **Counter (`C`)** – set the first argument of `OcraExecutionContext` and persist the new counter after successful evaluations.
- **Split challenges (`QH` / `QA` with client/server parts)** – populate `clientChallenge` and `serverChallenge` instead of the combined `question` field.
- **Session data (`Sxxx`)** – supply hexadecimal strings padded to the required length; the calculator validates and pads automatically.
- **Timestamp (`T`)** – send hex-encoded timestamps (e.g., unix time) and optionally configure `allowedTimestampDrift` in the descriptor to widen tolerance.
- **PIN hash (`P`)** – either embed the hash in the descriptor metadata or supply `pinHashHex` at runtime.

## 5. Wire into Your Application
- Cache descriptors in your preferred store (database, config service) and hydrate them into memory at startup.
- Pass around lightweight request objects containing `credentialId` or the full inline parameters.
- Capture validation failures, expose the sanitized reason to operators, and retry with corrected inputs.
- Use telemetry emitted by the factory/calculator (Java Util Logging) to integrate with your monitoring pipeline.

By relying on the native Java APIs, external clients can generate and replay OCRA responses without standing up REST or CLI components, while still benefiting from the simulator’s validation rules and telemetry.
