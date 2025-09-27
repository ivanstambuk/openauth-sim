package io.openauth.sim.core.credentials.ocra;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * High-level factory providing validation helpers around {@link OcraCredentialDescriptor} creation.
 */
public final class OcraCredentialFactory {

  private final OcraCredentialDescriptorFactory descriptorFactory;

  public OcraCredentialFactory() {
    this(new OcraCredentialDescriptorFactory());
  }

  public OcraCredentialFactory(OcraCredentialDescriptorFactory descriptorFactory) {
    this.descriptorFactory = Objects.requireNonNull(descriptorFactory, "descriptorFactory");
  }

  public OcraCredentialDescriptor createDescriptor(OcraCredentialRequest request) {
    Objects.requireNonNull(request, "request");
    return descriptorFactory.create(
        request.name(),
        request.ocraSuite(),
        request.sharedSecretHex(),
        request.counterValue(),
        request.pinHashHex(),
        request.allowedTimestampDrift(),
        request.metadata());
  }

  public void validateChallenge(OcraCredentialDescriptor descriptor, String challenge) {
    Objects.requireNonNull(descriptor, "descriptor");
    Optional<OcraChallengeQuestion> challengeSpec =
        descriptor.suite().dataInput().challengeQuestion();

    if (challengeSpec.isEmpty()) {
      if (challenge != null && !challenge.isBlank()) {
        throw new IllegalArgumentException(
            "challengeQuestion not permitted for suite: " + descriptor.suite().value());
      }
      return;
    }

    if (challenge == null || challenge.isBlank()) {
      throw new IllegalArgumentException(
          "challengeQuestion required for suite: " + descriptor.suite().value());
    }

    String trimmed = challenge.trim();
    OcraChallengeQuestion spec = challengeSpec.orElseThrow();
    if (trimmed.length() != spec.length()) {
      throw new IllegalArgumentException(
          "challengeQuestion must contain "
              + spec.length()
              + " characters for format "
              + spec.format());
    }

    if (!challengeMatchesFormat(trimmed, spec.format())) {
      throw new IllegalArgumentException(
          "challengeQuestion must match format "
              + spec.format()
              + " for suite: "
              + descriptor.suite().value());
    }
  }

  public void validateSessionInformation(
      OcraCredentialDescriptor descriptor, String sessionInformation) {
    Objects.requireNonNull(descriptor, "descriptor");
    Optional<OcraSessionSpecification> sessionSpec =
        descriptor.suite().dataInput().sessionInformation();

    if (sessionSpec.isEmpty()) {
      if (sessionInformation != null && !sessionInformation.isBlank()) {
        throw new IllegalArgumentException(
            "sessionInformation not permitted for suite: " + descriptor.suite().value());
      }
      return;
    }

    if (sessionInformation == null || sessionInformation.isBlank()) {
      throw new IllegalArgumentException(
          "sessionInformation required for suite: " + descriptor.suite().value());
    }
  }

  public void validateTimestamp(
      OcraCredentialDescriptor descriptor, Instant timestamp, Instant referenceInstant) {
    Objects.requireNonNull(descriptor, "descriptor");
    Optional<OcraTimestampSpecification> timestampSpec = descriptor.suite().dataInput().timestamp();

    if (timestampSpec.isEmpty()) {
      if (timestamp != null) {
        throw new IllegalArgumentException(
            "timestamp not permitted for suite: " + descriptor.suite().value());
      }
      return;
    }

    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(referenceInstant, "referenceInstant");

    Duration allowed =
        descriptor.allowedTimestampDrift().orElse(timestampSpec.orElseThrow().step());

    Duration delta = Duration.between(referenceInstant, timestamp).abs();
    if (delta.compareTo(allowed) > 0) {
      throw new IllegalArgumentException(
          "timestamp outside permitted drift (allowed=" + allowed + ", actual=" + delta + ")");
    }
  }

  private static boolean challengeMatchesFormat(String challenge, OcraChallengeFormat format) {
    return switch (format) {
      case NUMERIC -> challenge.chars().allMatch(Character::isDigit);
      case ALPHANUMERIC -> challenge.chars().allMatch(ch -> Character.isLetterOrDigit((char) ch));
      case HEX -> challenge.chars().allMatch(OcraCredentialFactory::isHexCharacter);
      case CHARACTER -> true;
    };
  }

  private static boolean isHexCharacter(int ch) {
    char c = Character.toUpperCase((char) ch);
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
  }

  public record OcraCredentialRequest(
      String name,
      String ocraSuite,
      String sharedSecretHex,
      Long counterValue,
      String pinHashHex,
      Duration allowedTimestampDrift,
      Map<String, String> metadata) {

    public OcraCredentialRequest {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(ocraSuite, "ocraSuite");
      // sharedSecretHex and others validated downstream to share diagnostics.
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }
}
