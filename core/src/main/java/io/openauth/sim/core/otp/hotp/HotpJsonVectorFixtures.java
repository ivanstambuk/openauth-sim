package io.openauth.sim.core.otp.hotp;

import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.SecretMaterial;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Access point for the RFC 4226 HOTP validation vectors JSON catalogue. */
public final class HotpJsonVectorFixtures {

  private static final List<HotpJsonVector> CATALOG = loadCatalog();
  private static final Map<String, HotpJsonVector> CATALOG_BY_ID = indexById(CATALOG);

  private HotpJsonVectorFixtures() {
    throw new AssertionError("Utility class");
  }

  /** Load all HOTP validation vectors from the JSON bundle. */
  public static Stream<HotpJsonVector> loadAll() {
    return CATALOG.stream();
  }

  /** Locate a HOTP validation vector by identifier. */
  public static HotpJsonVector getById(String vectorId) {
    Objects.requireNonNull(vectorId, "vectorId");
    HotpJsonVector vector = CATALOG_BY_ID.get(vectorId);
    if (vector == null) {
      throw new IllegalArgumentException("Unknown HOTP validation vector: " + vectorId);
    }
    return vector;
  }

  private static List<HotpJsonVector> loadCatalog() {
    String json;
    Path bundle = resolveBundlePath();
    try {
      json = Files.readString(bundle, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read HOTP validation vectors", ex);
    }

    Object parsed = SimpleJson.parse(json);
    if (!(parsed instanceof List<?> entries)) {
      throw new IllegalStateException("Expected top-level JSON array in HOTP vector bundle");
    }

    List<HotpJsonVector> vectors = new ArrayList<>(entries.size());
    for (Object entry : entries) {
      Map<String, Object> root = asObject(entry, "vector");
      vectors.add(toVector(root));
    }
    return Collections.unmodifiableList(vectors);
  }

  private static Map<String, HotpJsonVector> indexById(List<HotpJsonVector> vectors) {
    Map<String, HotpJsonVector> index = new LinkedHashMap<>();
    for (HotpJsonVector vector : vectors) {
      if (index.put(vector.vectorId(), vector) != null) {
        throw new IllegalStateException("Duplicate HOTP vector id: " + vector.vectorId());
      }
    }
    return Collections.unmodifiableMap(index);
  }

  private static Path resolveBundlePath() {
    Path candidate = Path.of("docs", "hotp_validation_vectors.json");
    if (Files.exists(candidate)) {
      return candidate;
    }
    Path moduleCandidate = Path.of("..", "docs", "hotp_validation_vectors.json");
    if (Files.exists(moduleCandidate)) {
      return moduleCandidate;
    }
    throw new IllegalStateException(
        "Unable to locate docs/hotp_validation_vectors.json from working directory "
            + Path.of("").toAbsolutePath());
  }

  private static HotpJsonVector toVector(Map<String, Object> root) {
    String vectorId = requireString(root, "vectorId");
    String secretEncoding = requireString(root, "secretEncoding");
    if (!"hex".equalsIgnoreCase(secretEncoding)) {
      throw new IllegalStateException(
          "Unsupported HOTP secret encoding '" + secretEncoding + "' for vector " + vectorId);
    }
    String secretHex = requireString(root, "secret");
    SecretMaterial secret = SecretMaterial.fromHex(secretHex);

    String algorithmLabel = requireString(root, "algorithm");
    HotpHashAlgorithm algorithm =
        HotpHashAlgorithm.valueOf(algorithmLabel.trim().toUpperCase(Locale.ROOT));

    int digits = requireInt(root, "digits");
    long counter = requireLong(root, "counter");
    String otp = requireString(root, "otp");
    if (otp.length() != digits) {
      throw new IllegalStateException(
          "OTP length mismatch for vector " + vectorId + ": expected " + digits);
    }

    Optional<String> label = optionalString(root, "label");
    Optional<String> notes = optionalString(root, "notes");

    return new HotpJsonVector(vectorId, secret, algorithm, digits, counter, otp, label, notes);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value, String description) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalStateException("Expected object for " + description);
  }

  private static String requireString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      throw new IllegalStateException("Missing required field '" + key + "'");
    }
    return value.toString();
  }

  private static int requireInt(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (!(value instanceof Number number)) {
      throw new IllegalStateException("Field '" + key + "' must be numeric");
    }
    return number.intValue();
  }

  private static long requireLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (!(value instanceof Number number)) {
      throw new IllegalStateException("Field '" + key + "' must be numeric");
    }
    return number.longValue();
  }

  private static Optional<String> optionalString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return Optional.empty();
    }
    String text = value.toString().trim();
    return text.isEmpty() ? Optional.empty() : Optional.of(text);
  }

  /** Representation of a single HOTP validation vector entry. */
  public record HotpJsonVector(
      String vectorId,
      SecretMaterial secret,
      HotpHashAlgorithm algorithm,
      int digits,
      long counter,
      String otp,
      Optional<String> label,
      Optional<String> notes) {

    public HotpJsonVector {
      Objects.requireNonNull(vectorId, "vectorId");
      Objects.requireNonNull(secret, "secret");
      Objects.requireNonNull(algorithm, "algorithm");
      Objects.requireNonNull(otp, "otp");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(notes, "notes");
    }
  }
}
