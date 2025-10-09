package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TotpOperatorSampleDataTest {

  @Test
  @DisplayName("Seed definitions generate valid sample OTPs for all algorithms")
  void seedDefinitionsGenerateValidOtps() {
    for (TotpOperatorSampleData.SampleDefinition definition :
        TotpOperatorSampleData.seedDefinitions()) {
      String timestampValue = definition.metadata().get("sampleTimestamp");
      assertFalse(
          timestampValue == null || timestampValue.isBlank(),
          () -> "sampleTimestamp metadata missing for " + definition.credentialId());
      long timestamp = Long.parseLong(timestampValue.trim());
      TotpDescriptor descriptor =
          TotpDescriptor.create(
              definition.credentialId(),
              SecretMaterial.fromHex(definition.sharedSecretHex()),
              definition.algorithm(),
              definition.digits(),
              Duration.ofSeconds(definition.stepSeconds()),
              TotpDriftWindow.of(definition.driftBackwardSteps(), definition.driftForwardSteps()));

      assertDoesNotThrow(
          () -> TotpGenerator.generate(descriptor, Instant.ofEpochSecond(timestamp)),
          () -> "Unable to generate TOTP for " + definition.credentialId());
    }
  }
}
