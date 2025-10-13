package io.openauth.sim.core.credentials.ocra;

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

/**
 * Shared loader for RFC 6287 Appendix C OCRA validation vectors stored in {@code
 * docs/ocra_validation_vectors.json}.
 */
public final class OcraJsonVectorFixtures {

  private static final List<OcraVectorEntry> CATALOG = loadCatalog();
  private static final Map<String, OcraVectorEntry> CATALOG_BY_ID = indexById(CATALOG);

  private OcraJsonVectorFixtures() {
    throw new AssertionError("Utility class");
  }

  /** Stream all known OCRA vectors regardless of category. */
  public static Stream<OcraVectorEntry> loadAll() {
    return CATALOG.stream();
  }

  /** Stream all one-way challenge/response vectors. */
  public static Stream<OcraOneWayVector> loadOneWayVectors() {
    return loadAll()
        .flatMap(
            entry -> entry instanceof OcraOneWayVector vector ? Stream.of(vector) : Stream.empty());
  }

  /** Stream all mutual challenge vectors. */
  public static Stream<OcraMutualVector> loadMutualVectors() {
    return loadAll()
        .flatMap(
            entry -> entry instanceof OcraMutualVector vector ? Stream.of(vector) : Stream.empty());
  }

  /** Stream all signature-style vectors (plain and timed). */
  public static Stream<OcraSignatureVector> loadSignatureVectors() {
    return loadAll()
        .flatMap(
            entry ->
                entry instanceof OcraSignatureVector vector ? Stream.of(vector) : Stream.empty());
  }

  /** Locate a vector by identifier, returning the concrete entry. */
  public static OcraVectorEntry get(String vectorId) {
    Objects.requireNonNull(vectorId, "vectorId");
    OcraVectorEntry entry = CATALOG_BY_ID.get(vectorId);
    if (entry == null) {
      throw new IllegalArgumentException("Unknown OCRA validation vector: " + vectorId);
    }
    return entry;
  }

  /** Locate a one-way vector by identifier. */
  public static OcraOneWayVector getOneWay(String vectorId) {
    OcraVectorEntry entry = get(vectorId);
    if (entry instanceof OcraOneWayVector vector) {
      return vector;
    }
    throw new IllegalArgumentException("Vector " + vectorId + " is not a one-way vector");
  }

  /** Locate a mutual vector by identifier. */
  public static OcraMutualVector getMutual(String vectorId) {
    OcraVectorEntry entry = get(vectorId);
    if (entry instanceof OcraMutualVector vector) {
      return vector;
    }
    throw new IllegalArgumentException("Vector " + vectorId + " is not a mutual vector");
  }

  /** Locate a signature vector by identifier. */
  public static OcraSignatureVector getSignature(String vectorId) {
    OcraVectorEntry entry = get(vectorId);
    if (entry instanceof OcraSignatureVector vector) {
      return vector;
    }
    throw new IllegalArgumentException("Vector " + vectorId + " is not a signature vector");
  }

  private static List<OcraVectorEntry> loadCatalog() {
    Path bundle = resolveBundlePath();
    String json;
    try {
      json = Files.readString(bundle, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read OCRA validation vectors", ex);
    }

    Object parsed = SimpleJson.parse(json);
    if (!(parsed instanceof List<?> entries)) {
      throw new IllegalStateException("Expected top-level JSON array for OCRA vectors");
    }

    List<OcraVectorEntry> catalog = new ArrayList<>(entries.size());
    for (Object entry : entries) {
      Map<String, Object> root = asObject(entry, "vector");
      catalog.add(toVector(root));
    }
    return Collections.unmodifiableList(catalog);
  }

  private static Map<String, OcraVectorEntry> indexById(List<OcraVectorEntry> catalog) {
    Map<String, OcraVectorEntry> index = new LinkedHashMap<>();
    for (OcraVectorEntry entry : catalog) {
      if (index.put(entry.vectorId(), entry) != null) {
        throw new IllegalStateException("Duplicate OCRA vector id: " + entry.vectorId());
      }
    }
    return Collections.unmodifiableMap(index);
  }

  private static Path resolveBundlePath() {
    Path direct = Path.of("docs", "ocra_validation_vectors.json");
    if (Files.exists(direct)) {
      return direct;
    }
    Path moduleRelative = Path.of("..", "docs", "ocra_validation_vectors.json");
    if (Files.exists(moduleRelative)) {
      return moduleRelative;
    }
    throw new IllegalStateException(
        "Unable to locate docs/ocra_validation_vectors.json from working directory "
            + Path.of("").toAbsolutePath());
  }

  private static OcraVectorEntry toVector(Map<String, Object> root) {
    String vectorId = requireString(root, "vectorId");
    String categoryLabel = requireString(root, "category").toLowerCase(Locale.ROOT).trim();
    String suite = requireString(root, "suite");
    String secretEncoding = requireString(root, "secretEncoding");
    if (!"hex".equalsIgnoreCase(secretEncoding)) {
      throw new IllegalStateException(
          "Unsupported secret encoding '"
              + secretEncoding
              + "' for OCRA vector "
              + vectorId
              + " (expected hex)");
    }
    SecretMaterial secret = SecretMaterial.fromHex(requireString(root, "secret"));
    Optional<String> label = optionalString(root, "label");
    Optional<String> notes = optionalString(root, "notes");
    String expectedOtp = requireString(root, "expectedOtp");

    return switch (categoryLabel) {
      case "oneway", "one_way", "one-way" ->
          toOneWayVector(vectorId, suite, secret, label, notes, expectedOtp, root);
      case "mutual" -> toMutualVector(vectorId, suite, secret, label, notes, expectedOtp, root);
      case "signature" ->
          toSignatureVector(vectorId, suite, secret, label, notes, expectedOtp, root);
      default ->
          throw new IllegalStateException(
              "Unknown OCRA vector category '" + categoryLabel + "' for vector " + vectorId);
    };
  }

  private static OcraOneWayVector toOneWayVector(
      String vectorId,
      String suite,
      SecretMaterial secret,
      Optional<String> label,
      Optional<String> notes,
      String expectedOtp,
      Map<String, Object> root) {
    Optional<String> question = optionalString(root, "challengeQuestion");
    Optional<Long> counter = optionalLong(root, "counter");
    Optional<String> pinHash = optionalString(root, "pinHash").map(s -> s.toUpperCase(Locale.ROOT));
    Optional<String> session =
        optionalString(root, "sessionInformation").map(s -> s.toUpperCase(Locale.ROOT));
    Optional<String> timestamp =
        optionalString(root, "timestampHex").map(s -> s.toUpperCase(Locale.ROOT));
    return new OcraOneWayVector(
        vectorId,
        suite,
        secret,
        label,
        notes,
        question,
        counter,
        pinHash,
        session,
        timestamp,
        expectedOtp);
  }

  private static OcraMutualVector toMutualVector(
      String vectorId,
      String suite,
      SecretMaterial secret,
      Optional<String> label,
      Optional<String> notes,
      String expectedOtp,
      Map<String, Object> root) {
    String challengeA = requireString(root, "challengeA");
    String challengeB = requireString(root, "challengeB");
    Optional<String> pinHash = optionalString(root, "pinHash").map(s -> s.toUpperCase(Locale.ROOT));
    Optional<String> timestamp =
        optionalString(root, "timestampHex").map(s -> s.toUpperCase(Locale.ROOT));
    return new OcraMutualVector(
        vectorId,
        suite,
        secret,
        label,
        notes,
        challengeA,
        challengeB,
        pinHash,
        timestamp,
        expectedOtp);
  }

  private static OcraSignatureVector toSignatureVector(
      String vectorId,
      String suite,
      SecretMaterial secret,
      Optional<String> label,
      Optional<String> notes,
      String expectedOtp,
      Map<String, Object> root) {
    String question = requireString(root, "challengeQuestion");
    Optional<String> timestamp =
        optionalString(root, "timestampHex").map(s -> s.toUpperCase(Locale.ROOT));
    return new OcraSignatureVector(
        vectorId, suite, secret, label, notes, question, timestamp, expectedOtp);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value, String description) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalStateException("Expected JSON object for " + description);
  }

  private static String requireString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      throw new IllegalStateException("Missing required field '" + key + "'");
    }
    return value.toString();
  }

  private static Optional<String> optionalString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return Optional.empty();
    }
    String trimmed = value.toString().trim();
    return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
  }

  private static Optional<Long> optionalLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return Optional.empty();
    }
    if (value instanceof Number number) {
      return Optional.of(number.longValue());
    }
    String text = value.toString().trim();
    if (text.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(text));
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("Field '" + key + "' must be numeric when provided", ex);
    }
  }

  /** Marker interface for all OCRA vector entries. */
  public sealed interface OcraVectorEntry
      permits OcraOneWayVector, OcraMutualVector, OcraSignatureVector {
    String vectorId();

    String suite();

    SecretMaterial secret();

    Optional<String> label();

    Optional<String> notes();

    String expectedOtp();
  }

  /** Representation of a one-way challenge/response OCRA vector. */
  public record OcraOneWayVector(
      String vectorId,
      String suite,
      SecretMaterial secret,
      Optional<String> label,
      Optional<String> notes,
      Optional<String> challengeQuestion,
      Optional<Long> counter,
      Optional<String> pinHashHex,
      Optional<String> sessionInformationHex,
      Optional<String> timestampHex,
      String expectedOtp)
      implements OcraVectorEntry {

    public OcraOneWayVector {
      Objects.requireNonNull(vectorId, "vectorId");
      Objects.requireNonNull(suite, "suite");
      Objects.requireNonNull(secret, "secret");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(notes, "notes");
      Objects.requireNonNull(challengeQuestion, "challengeQuestion");
      Objects.requireNonNull(counter, "counter");
      Objects.requireNonNull(pinHashHex, "pinHashHex");
      Objects.requireNonNull(sessionInformationHex, "sessionInformationHex");
      Objects.requireNonNull(timestampHex, "timestampHex");
      Objects.requireNonNull(expectedOtp, "expectedOtp");
    }
  }

  /** Representation of a mutual (client/server) OCRA vector. */
  public record OcraMutualVector(
      String vectorId,
      String suite,
      SecretMaterial secret,
      Optional<String> label,
      Optional<String> notes,
      String challengeA,
      String challengeB,
      Optional<String> pinHashHex,
      Optional<String> timestampHex,
      String expectedOtp)
      implements OcraVectorEntry {

    public OcraMutualVector {
      Objects.requireNonNull(vectorId, "vectorId");
      Objects.requireNonNull(suite, "suite");
      Objects.requireNonNull(secret, "secret");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(notes, "notes");
      Objects.requireNonNull(challengeA, "challengeA");
      Objects.requireNonNull(challengeB, "challengeB");
      Objects.requireNonNull(pinHashHex, "pinHashHex");
      Objects.requireNonNull(timestampHex, "timestampHex");
      Objects.requireNonNull(expectedOtp, "expectedOtp");
    }
  }

  /** Representation of a signature-focused OCRA vector. */
  public record OcraSignatureVector(
      String vectorId,
      String suite,
      SecretMaterial secret,
      Optional<String> label,
      Optional<String> notes,
      String challengeQuestion,
      Optional<String> timestampHex,
      String expectedOtp)
      implements OcraVectorEntry {

    public OcraSignatureVector {
      Objects.requireNonNull(vectorId, "vectorId");
      Objects.requireNonNull(suite, "suite");
      Objects.requireNonNull(secret, "secret");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(notes, "notes");
      Objects.requireNonNull(challengeQuestion, "challengeQuestion");
      Objects.requireNonNull(timestampHex, "timestampHex");
      Objects.requireNonNull(expectedOtp, "expectedOtp");
    }
  }
}
