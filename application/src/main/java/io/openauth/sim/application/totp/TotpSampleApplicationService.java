package io.openauth.sim.application.totp;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Computes deterministic stored-sample payloads for TOTP credentials. */
public final class TotpSampleApplicationService {

  private static final String METADATA_PREFIX = "totp.metadata.";
  private static final String SAMPLE_PRESET_KEY = "samplePresetKey";
  private static final String SAMPLE_PRESET_LABEL = "samplePresetLabel";
  private static final String PRESET_KEY = "presetKey";
  private static final String PRESET_LABEL = "presetLabel";
  private static final String SAMPLE_TIMESTAMP_KEY = "sampleTimestamp";

  private final CredentialStore credentialStore;
  private final TotpCredentialPersistenceAdapter persistenceAdapter;
  private final Clock clock;

  public TotpSampleApplicationService(CredentialStore credentialStore) {
    this(credentialStore, Clock.systemUTC());
  }

  public TotpSampleApplicationService(CredentialStore credentialStore, Clock clock) {
    this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.persistenceAdapter = new TotpCredentialPersistenceAdapter();
  }

  /**
   * Returns a deterministic sample payload for the requested credential if it exists and is a TOTP
   * entry.
   */
  public Optional<StoredSample> storedSample(String credentialId) {
    if (credentialId == null || credentialId.isBlank()) {
      return Optional.empty();
    }

    return credentialStore
        .findByName(credentialId.trim())
        .filter(credential -> credential.type() == CredentialType.OATH_TOTP)
        .flatMap(this::toSample);
  }

  private Optional<StoredSample> toSample(Credential credential) {
    TotpDescriptor descriptor;
    try {
      descriptor =
          persistenceAdapter.deserialize(VersionedCredentialRecordMapper.toRecord(credential));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }

    TotpHashAlgorithm algorithm = descriptor.algorithm();
    int digits = descriptor.digits();
    Duration stepDuration = descriptor.stepDuration();
    TotpDriftWindow driftWindow = descriptor.driftWindow();

    Map<String, String> metadata =
        extractMetadata(credential.attributes(), algorithm, digits, stepDuration, driftWindow);

    long timestampEpochSeconds = resolveTimestamp(metadata, stepDuration);
    metadata.putIfAbsent(SAMPLE_TIMESTAMP_KEY, Long.toString(timestampEpochSeconds));

    Instant sampleInstant = Instant.ofEpochSecond(timestampEpochSeconds);
    String otp = TotpGenerator.generate(descriptor, sampleInstant);

    return Optional.of(
        new StoredSample(
            credential.name(),
            algorithm,
            digits,
            stepDuration.toSeconds(),
            driftWindow.backwardSteps(),
            driftWindow.forwardSteps(),
            timestampEpochSeconds,
            otp,
            Map.copyOf(metadata)));
  }

  private long resolveTimestamp(Map<String, String> metadata, Duration stepDuration) {
    String value = metadata.get(SAMPLE_TIMESTAMP_KEY);
    if (value != null && !value.isBlank()) {
      try {
        return Long.parseLong(value.trim());
      } catch (NumberFormatException ignored) {
        // fall through to default path
      }
    }
    long stepSeconds = Math.max(1L, stepDuration.toSeconds());
    long now = clock.instant().getEpochSecond();
    return (now / stepSeconds) * stepSeconds;
  }

  private LinkedHashMap<String, String> extractMetadata(
      Map<String, String> attributes,
      TotpHashAlgorithm algorithm,
      int digits,
      Duration stepDuration,
      TotpDriftWindow driftWindow) {
    LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    attributes.forEach(
        (key, value) -> {
          if (key != null && key.startsWith(METADATA_PREFIX) && value != null) {
            metadata.put(key.substring(METADATA_PREFIX.length()), value);
          }
        });

    String presetKey = metadata.get(PRESET_KEY);
    if (presetKey != null && !presetKey.isBlank()) {
      metadata.putIfAbsent(SAMPLE_PRESET_KEY, presetKey);
    }
    String presetLabel = metadata.get(PRESET_LABEL);
    if (presetLabel != null && !presetLabel.isBlank()) {
      metadata.putIfAbsent(SAMPLE_PRESET_LABEL, presetLabel);
    }

    return metadata;
  }

  /** Canonical representation of a stored TOTP sample response. */
  public record StoredSample(
      String credentialId,
      TotpHashAlgorithm algorithm,
      int digits,
      long stepSeconds,
      int driftBackwardSteps,
      int driftForwardSteps,
      long timestampEpochSeconds,
      String otp,
      Map<String, String> metadata) {

    public StoredSample {
      Objects.requireNonNull(credentialId, "credentialId");
      Objects.requireNonNull(algorithm, "algorithm");
      Objects.requireNonNull(otp, "otp");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }
}
