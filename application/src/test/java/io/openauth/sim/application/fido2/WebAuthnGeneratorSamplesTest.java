package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WebAuthnGeneratorSamplesTest {

  @Test
  void prefersW3cFixtureWhenAvailable() {
    boolean containsW3cSample =
        WebAuthnGeneratorSamples.samples().stream()
            .anyMatch(sample -> "w3c".equals(sample.metadata().get("source")));

    assertTrue(containsW3cSample, "Expected at least one W3C-backed generator sample");
  }

  @Test
  void w3cFixtureAlgorithmsPreferSpecificationSamples() {
    Set<WebAuthnSignatureAlgorithm> algorithmsWithPrivateKeys =
        WebAuthnFixtures.w3cFixtures().stream()
            .filter(hasPrivateKey())
            .map(WebAuthnFixture::algorithm)
            .collect(Collectors.toSet());

    assertFalse(algorithmsWithPrivateKeys.isEmpty(), "W3C fixtures should expose private keys");

    for (WebAuthnSignatureAlgorithm algorithm : algorithmsWithPrivateKeys) {
      String presetKey = "generator-" + algorithm.label().toLowerCase(Locale.US);
      WebAuthnGeneratorSamples.Sample sample =
          WebAuthnGeneratorSamples.findByKey(presetKey)
              .orElseThrow(() -> new AssertionError("Missing preset for " + algorithm));
      assertEquals(
          "w3c",
          sample.metadata().get("source"),
          () -> "Expected W3C source for algorithm " + algorithm);
    }
  }

  @Test
  void syntheticVectorsFillGapsWhenSpecificationIsSilent() {
    WebAuthnGeneratorSamples.Sample ps256 =
        WebAuthnGeneratorSamples.findByKey("generator-ps256")
            .orElseThrow(() -> new AssertionError("Missing PS256 preset"));

    assertEquals(
        "synthetic",
        ps256.metadata().get("source"),
        "PS256 should fall back to synthetic fixture bundle");
  }

  private static Predicate<WebAuthnFixture> hasPrivateKey() {
    return fixture -> {
      String privateKey = fixture.credentialPrivateKeyJwk();
      return privateKey != null && !privateKey.isBlank();
    };
  }
}
