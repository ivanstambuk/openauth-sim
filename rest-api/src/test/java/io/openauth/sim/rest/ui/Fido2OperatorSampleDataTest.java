package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class Fido2OperatorSampleDataTest {

  @Test
  void seedDefinitionsCoverAllAlgorithmsWithVectorMetadata() {
    List<Fido2OperatorSampleData.SeedDefinition> definitions =
        Fido2OperatorSampleData.seedDefinitions();
    assertFalse(definitions.isEmpty(), "Expected curated seed definitions");

    EnumSet<WebAuthnSignatureAlgorithm> algorithms =
        EnumSet.noneOf(WebAuthnSignatureAlgorithm.class);
    for (Fido2OperatorSampleData.SeedDefinition definition : definitions) {
      algorithms.add(definition.algorithm());
      Map<String, String> metadata = definition.metadata();
      assertNotNull(metadata.get("presetKey"), "presetKey metadata missing");
      assertNotNull(metadata.get("algorithm"), "algorithm metadata missing");
      assertTrue(metadata.containsKey("source"), "source metadata missing");
    }
    EnumSet<WebAuthnSignatureAlgorithm> inlineAlgorithms =
        Fido2OperatorSampleData.inlineVectors().stream()
            .map(vector -> WebAuthnSignatureAlgorithm.fromLabel(vector.algorithm()))
            .collect(
                Collectors.toCollection(() -> EnumSet.noneOf(WebAuthnSignatureAlgorithm.class)));

    assertTrue(
        algorithms.containsAll(inlineAlgorithms), "Seed definitions cover inline algorithms");
  }

  @Test
  void inlineVectorsExposeJsonBundleEntries() {
    List<Fido2OperatorSampleData.InlineVector> vectors = Fido2OperatorSampleData.inlineVectors();
    assertFalse(vectors.isEmpty(), "Expected inline vectors from JSON bundle");
    Fido2OperatorSampleData.InlineVector sample = vectors.get(0);
    assertNotNull(sample.key());
    assertNotNull(sample.metadata().get("presetKey"));
    assertNotNull(sample.privateKeyJwk(), "Expected private key JWK for inline presets");
  }

  @Test
  void seedDefinitionsIncludePrivateKeys() {
    List<Fido2OperatorSampleData.SeedDefinition> definitions =
        Fido2OperatorSampleData.seedDefinitions();
    assertFalse(definitions.isEmpty(), "Expected curated seed definitions");
    for (Fido2OperatorSampleData.SeedDefinition definition : definitions) {
      String privateKey = definition.privateKeyJwk();
      assertNotNull(privateKey, "Private key JWK missing");
      assertTrue(
          privateKey.contains("\"kty\":\"EC\"") || privateKey.contains("\"kty\":\"OKP\""),
          "Unsupported private key format: " + privateKey);
    }
  }
}
