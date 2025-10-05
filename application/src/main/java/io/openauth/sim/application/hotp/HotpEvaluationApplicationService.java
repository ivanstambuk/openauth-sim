package io.openauth.sim.application.hotp;

import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level HOTP evaluation orchestrator (tests drive implementation). */
public final class HotpEvaluationApplicationService {

  private static final String ATTR_ALGORITHM = "hotp.algorithm";
  private static final String ATTR_DIGITS = "hotp.digits";
  private static final String ATTR_COUNTER = "hotp.counter";

  private static final String INLINE_DESCRIPTOR_NAME = "hotp-inline-request";

  private final CredentialStore credentialStore;

  public HotpEvaluationApplicationService(CredentialStore credentialStore) {
    this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
  }

  public EvaluationResult evaluate(EvaluationCommand command) {
    Objects.requireNonNull(command, "command");

    if (command instanceof EvaluationCommand.Stored stored) {
      return evaluateStored(stored);
    }
    if (command instanceof EvaluationCommand.Inline inline) {
      return evaluateInline(inline);
    }
    throw new IllegalStateException("Unsupported HOTP evaluation command: " + command);
  }

  public sealed interface EvaluationCommand
      permits EvaluationCommand.Stored, EvaluationCommand.Inline {

    record Stored(String credentialId) implements EvaluationCommand {

      public Stored {
        credentialId = Objects.requireNonNull(credentialId, "credentialId").trim();
      }
    }

    record Inline(
        String sharedSecretHex,
        HotpHashAlgorithm algorithm,
        int digits,
        Long counter,
        Map<String, String> metadata)
        implements EvaluationCommand {

      public Inline {
        sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex").trim();
        algorithm = Objects.requireNonNull(algorithm, "algorithm");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
      }
    }
  }

  public record EvaluationResult(
      TelemetrySignal telemetry,
      boolean credentialReference,
      String credentialId,
      long previousCounter,
      long nextCounter,
      HotpHashAlgorithm algorithm,
      Integer digits,
      String otp) {

    public EvaluationFrame evaluationFrame(HotpTelemetryAdapter adapter, String telemetryId) {
      return new EvaluationFrame(telemetry.emit(adapter, telemetryId));
    }
  }

  public record EvaluationFrame(TelemetryFrame frame) {
    // Marker type for fluent telemetry access.
  }

  public record TelemetrySignal(
      TelemetryStatus status,
      String reasonCode,
      String reason,
      boolean sanitized,
      Map<String, Object> fields,
      String statusOverride) {

    public TelemetryFrame emit(HotpTelemetryAdapter adapter, String telemetryId) {
      Objects.requireNonNull(adapter, "adapter");
      Objects.requireNonNull(telemetryId, "telemetryId");
      Objects.requireNonNull(fields, "fields");

      if (statusOverride != null && !statusOverride.isBlank()) {
        return adapter.status(statusOverride, telemetryId, reasonCode, sanitized, reason, fields);
      }

      return switch (status) {
        case SUCCESS -> adapter.success(telemetryId, fields);
        case INVALID ->
            adapter.validationFailure(telemetryId, reasonCode, reason, sanitized, fields);
        case ERROR -> adapter.error(telemetryId, reasonCode, reason, sanitized, fields);
      };
    }
  }

  public enum TelemetryStatus {
    SUCCESS,
    INVALID,
    ERROR
  }

  private EvaluationResult evaluateStored(EvaluationCommand.Stored command) {
    try {
      StoredCredential storedCredential = resolveStored(command.credentialId());
      if (storedCredential == null) {
        return notFoundResult(command.credentialId());
      }

      long previousCounter = storedCredential.counter();
      String otp = HotpGenerator.generate(storedCredential.descriptor(), previousCounter);
      long nextCounter;
      try {
        nextCounter = Math.addExact(previousCounter, 1L);
      } catch (ArithmeticException ex) {
        return validationFailure(
            command.credentialId(),
            true,
            "stored",
            storedCredential.descriptor().algorithm(),
            storedCredential.descriptor().digits(),
            previousCounter,
            previousCounter,
            "counter_overflow",
            ex.getMessage());
      }

      persistCounter(storedCredential.credential(), nextCounter);
      return successResult(
          true,
          storedCredential.descriptor().name(),
          "stored",
          storedCredential.descriptor().algorithm(),
          storedCredential.descriptor().digits(),
          previousCounter,
          nextCounter,
          otp);
    } catch (IllegalArgumentException ex) {
      return invalidMetadataResult(command.credentialId(), ex.getMessage());
    } catch (RuntimeException ex) {
      return unexpectedErrorResult(command.credentialId(), true, "stored", null, null, 0L, ex);
    }
  }

  private EvaluationResult evaluateInline(EvaluationCommand.Inline command) {
    try {
      Long counterValue = command.counter();
      if (counterValue == null) {
        return validationFailure(
            null,
            false,
            "inline",
            command.algorithm(),
            command.digits(),
            0L,
            0L,
            "counter_required",
            "counter is required");
      }
      if (!hasText(command.sharedSecretHex())) {
        return validationFailure(
            null,
            false,
            "inline",
            command.algorithm(),
            command.digits(),
            0L,
            0L,
            "sharedSecretHex_required",
            "sharedSecretHex is required");
      }
      HotpDescriptor descriptor =
          HotpDescriptor.create(
              INLINE_DESCRIPTOR_NAME,
              SecretMaterial.fromHex(command.sharedSecretHex()),
              command.algorithm(),
              command.digits());

      long previousCounter = counterValue;
      String otp = HotpGenerator.generate(descriptor, previousCounter);
      long nextCounter;
      try {
        nextCounter = Math.addExact(previousCounter, 1L);
      } catch (ArithmeticException ex) {
        return validationFailure(
            null,
            false,
            "inline",
            descriptor.algorithm(),
            descriptor.digits(),
            previousCounter,
            previousCounter,
            "counter_overflow",
            ex.getMessage());
      }

      return successResult(
          false,
          null,
          "inline",
          descriptor.algorithm(),
          descriptor.digits(),
          previousCounter,
          nextCounter,
          otp);
    } catch (IllegalArgumentException ex) {
      return validationFailure(
          null,
          false,
          "inline",
          command.algorithm(),
          command.digits(),
          command.counter() != null ? command.counter() : 0L,
          command.counter() != null ? command.counter() : 0L,
          "validation_error",
          ex.getMessage());
    } catch (RuntimeException ex) {
      return unexpectedErrorResult(
          null, false, "inline", command.algorithm(), command.digits(), command.counter(), ex);
    }
  }

  private StoredCredential resolveStored(String credentialId) {
    Optional<Credential> credentialOptional = credentialStore.findByName(credentialId);
    if (credentialOptional.isEmpty()) {
      return null;
    }

    Credential credential = credentialOptional.get();
    if (credential.type() != CredentialType.OATH_HOTP) {
      return null;
    }

    Map<String, String> attributes = credential.attributes();
    String algorithmName = attributes.get(ATTR_ALGORITHM);
    String digitsValue = attributes.get(ATTR_DIGITS);
    String counterValue = attributes.get(ATTR_COUNTER);

    if (!hasText(algorithmName) || !hasText(digitsValue) || !hasText(counterValue)) {
      throw new IllegalArgumentException("Missing HOTP metadata for credential " + credentialId);
    }

    try {
      HotpHashAlgorithm algorithm = HotpHashAlgorithm.valueOf(algorithmName);
      int digits = Integer.parseInt(digitsValue);
      long counter = Long.parseLong(counterValue);

      HotpDescriptor descriptor =
          HotpDescriptor.create(credential.name(), credential.secret(), algorithm, digits);
      return new StoredCredential(credential, descriptor, counter);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Invalid HOTP metadata for credential " + credentialId + ": " + safeMessage(ex), ex);
    }
  }

  private EvaluationResult successResult(
      boolean credentialReference,
      String credentialId,
      String credentialSource,
      HotpHashAlgorithm algorithm,
      int digits,
      long previousCounter,
      long nextCounter,
      String otp) {

    Map<String, Object> fields =
        evaluationFields(
            credentialSource, credentialId, algorithm, digits, previousCounter, nextCounter);
    TelemetrySignal signal =
        new TelemetrySignal(TelemetryStatus.SUCCESS, "generated", null, true, fields, null);
    return new EvaluationResult(
        signal,
        credentialReference,
        credentialId,
        previousCounter,
        nextCounter,
        algorithm,
        digits,
        otp);
  }

  private EvaluationResult validationFailure(
      String credentialId,
      boolean credentialReference,
      String credentialSource,
      HotpHashAlgorithm algorithm,
      Integer digits,
      long previousCounter,
      long nextCounter,
      String reasonCode,
      String reason) {

    Map<String, Object> fields =
        evaluationFields(
            credentialSource, credentialId, algorithm, digits, previousCounter, nextCounter);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID, reasonCode, safeMessage(reason), true, fields, null);
    return new EvaluationResult(
        signal,
        credentialReference,
        credentialId,
        previousCounter,
        nextCounter,
        algorithm,
        digits,
        null);
  }

  private EvaluationResult notFoundResult(String credentialId) {
    Map<String, Object> fields = evaluationFields("stored", credentialId, null, null, 0L, 0L);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID,
            "credential_not_found",
            "credentialId " + credentialId + " not found",
            true,
            fields,
            null);
    return new EvaluationResult(signal, true, credentialId, 0L, 0L, null, null, null);
  }

  private EvaluationResult invalidMetadataResult(String credentialId, String reason) {
    Map<String, Object> fields = evaluationFields("stored", credentialId, null, null, 0L, 0L);
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.INVALID,
            "invalid_hotp_metadata",
            safeMessage(reason),
            true,
            fields,
            null);
    return new EvaluationResult(signal, true, credentialId, 0L, 0L, null, null, null);
  }

  private EvaluationResult unexpectedErrorResult(
      String credentialId,
      boolean credentialReference,
      String credentialSource,
      HotpHashAlgorithm algorithm,
      Integer digits,
      long previousCounter,
      Throwable error) {

    Map<String, Object> fields =
        evaluationFields(
            credentialSource, credentialId, algorithm, digits, previousCounter, previousCounter);
    if (error != null) {
      fields.put("exception", error.getClass().getName() + ": " + safeMessage(error));
    }
    TelemetrySignal signal =
        new TelemetrySignal(
            TelemetryStatus.ERROR, "unexpected_error", safeMessage(error), false, fields, null);
    return new EvaluationResult(
        signal,
        credentialReference,
        credentialId,
        previousCounter,
        previousCounter,
        algorithm,
        digits,
        null);
  }

  private void persistCounter(Credential credential, long nextCounter) {
    Map<String, String> updated = new LinkedHashMap<>(credential.attributes());
    updated.put(ATTR_COUNTER, Long.toString(nextCounter));
    credentialStore.save(credential.withAttributes(updated));
  }

  private static Map<String, Object> evaluationFields(
      String credentialSource,
      String credentialId,
      HotpHashAlgorithm algorithm,
      Integer digits,
      long previousCounter,
      long nextCounter) {

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", credentialSource);
    fields.put("credentialId", credentialId);
    if (algorithm != null) {
      fields.put("hashAlgorithm", algorithm.name());
    }
    if (digits != null) {
      fields.put("digits", digits);
    }
    fields.put("previousCounter", previousCounter);
    fields.put("nextCounter", nextCounter);
    return fields;
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String safeMessage(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    return safeMessage(throwable.getMessage());
  }

  private static String safeMessage(String message) {
    if (message == null) {
      return "";
    }
    return message.trim().replaceAll("\\s+", " ");
  }

  private record StoredCredential(Credential credential, HotpDescriptor descriptor, long counter) {
    // Simple tuple carrying resolved state.
  }
}
