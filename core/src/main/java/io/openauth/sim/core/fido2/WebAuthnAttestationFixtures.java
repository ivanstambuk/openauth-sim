package io.openauth.sim.core.fido2;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Loader for the attestation fixtures stored under {@code docs/webauthn_attestation/}. */
public final class WebAuthnAttestationFixtures {

  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> FIXTURES =
      loadAllFixtures();

  private WebAuthnAttestationFixtures() {
    throw new AssertionError("Utility class");
  }

  /** Returns a stream of every attestation vector across all formats. */
  public static Stream<WebAuthnAttestationVector> allVectors() {
    return FIXTURES.values().stream().flatMap(List::stream);
  }

  /** Returns the attestation vectors registered for the supplied format. */
  public static List<WebAuthnAttestationVector> vectorsFor(WebAuthnAttestationFormat format) {
    return FIXTURES.getOrDefault(format, List.of());
  }

  private static Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> loadAllFixtures() {
    Map<WebAuthnAttestationFormat, List<WebAuthnAttestationVector>> byFormat =
        new EnumMap<>(WebAuthnAttestationFormat.class);
    for (WebAuthnAttestationFormat format : WebAuthnAttestationFormat.values()) {
      Path path = resolveFixturePath(format.label());
      if (!Files.exists(path)) {
        byFormat.put(format, List.of());
        continue;
      }
      byFormat.put(format, Collections.unmodifiableList(parseFixtureFile(path, format)));
    }
    return Collections.unmodifiableMap(byFormat);
  }

  private static List<WebAuthnAttestationVector> parseFixtureFile(
      Path path, WebAuthnAttestationFormat format) {
    Object parsed = parseJson(readFile(path));
    if (!(parsed instanceof List<?> entries)) {
      throw new IllegalStateException("Expected top-level array in " + path);
    }

    List<WebAuthnAttestationVector> vectors = new ArrayList<>();
    for (Object entry : entries) {
      Map<String, Object> root = asObject(entry, path + " entry");

      String vectorId = requireString(root, "vector_id");
      String section = requireString(root, "w3c_section");
      String title = requireString(root, "title");
      String rpId = requireString(root, "rpId");
      String origin = requireString(root, "origin");

      WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(requireString(root, "algorithm"));

      Map<String, Object> registration =
          asObject(root.get("registration"), vectorId + ".registration");
      Map<String, Object> authenticationRaw =
          root.containsKey("authentication")
              ? asObject(root.get("authentication"), vectorId + ".authentication")
              : Map.of();
      Map<String, Object> keyMaterial =
          asObject(root.get("key_material"), vectorId + ".key_material");

      Registration registrationPayload =
          new Registration(
              decodeBytes(registration.get("challenge_b64u"), vectorId + ".registration.challenge"),
              decodeBytes(
                  registration.get("clientDataJSON_b64u"),
                  vectorId + ".registration.clientDataJSON"),
              decodeBytes(
                  registration.get("attestationObject_b64u"),
                  vectorId + ".registration.attestationObject"),
              decodeBytes(
                  registration.get("credentialId_b64u"), vectorId + ".registration.credentialId"),
              decodeBytes(registration.get("aaguid_b64u"), vectorId + ".registration.aaguid"));

      Optional<Authentication> authentication =
          authenticationRaw.isEmpty()
              ? Optional.empty()
              : Optional.of(
                  new Authentication(
                      decodeBytes(
                          authenticationRaw.get("challenge_b64u"),
                          vectorId + ".authentication.challenge"),
                      decodeBytes(
                          authenticationRaw.get("clientDataJSON_b64u"),
                          vectorId + ".authentication.clientDataJSON"),
                      decodeBytes(
                          authenticationRaw.get("authenticatorData_b64u"),
                          vectorId + ".authentication.authenticatorData"),
                      decodeBytes(
                          authenticationRaw.get("signature_b64u"),
                          vectorId + ".authentication.signature")));

      KeyMaterial material =
          new KeyMaterial(
              decodeString(
                  keyMaterial.get("credential_private_key_b64u"),
                  vectorId + ".key_material.credential_private_key_b64u"),
              decodeString(
                  keyMaterial.get("attestation_private_key_b64u"),
                  vectorId + ".key_material.attestation_private_key_b64u"),
              decodeString(
                  keyMaterial.get("attestation_cert_serial_b64u"),
                  vectorId + ".key_material.attestation_cert_serial_b64u"));

      vectors.add(
          new WebAuthnAttestationVector(
              vectorId,
              format,
              algorithm,
              section,
              title,
              rpId,
              origin,
              registrationPayload,
              authentication,
              material));
    }

    return vectors;
  }

  private static Object parseJson(String content) {
    return SimpleJson.parse(content);
  }

  private static Map<String, Object> asObject(Object value, String context) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object key = entry.getKey();
        if (!(key instanceof String)) {
          throw new IllegalStateException(context + " contains non-string key");
        }
        result.put((String) key, entry.getValue());
      }
      return result;
    }
    throw new IllegalStateException(context + " must be a JSON object");
  }

  private static String requireString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value instanceof String str && !str.isBlank()) {
      return str;
    }
    throw new IllegalStateException(
        "Missing string field '" + key + "' in " + objectDescription(object));
  }

  private static String decodeString(Object node, String context) {
    if (node == null) {
      return null;
    }
    if (node instanceof String str) {
      return str;
    }
    throw new IllegalStateException(context + " must be a string");
  }

  private static byte[] decodeBytes(Object node, String context) {
    if (node == null) {
      return new byte[0];
    }
    if (node instanceof String str) {
      return URL_DECODER.decode(str);
    }
    throw new IllegalStateException(context + " must be a base64url string");
  }

  private static String objectDescription(Map<String, Object> object) {
    return object.entrySet().stream()
        .map(e -> e.getKey() + "=" + Objects.toString(e.getValue()))
        .collect(Collectors.joining(", ", "{", "}"));
  }

  private static String readFile(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read fixture file " + path, ex);
    }
  }

  private static Path resolveFixturePath(String formatLabel) {
    Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    Path direct = workingDirectory.resolve("docs/webauthn_attestation/" + formatLabel + ".json");
    if (Files.exists(direct)) {
      return direct;
    }
    Path parent = workingDirectory.getParent();
    if (parent != null) {
      Path candidate = parent.resolve("docs/webauthn_attestation/" + formatLabel + ".json");
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    return direct;
  }

  private static WebAuthnSignatureAlgorithm resolveAlgorithm(String label) {
    if ("Ed25519".equalsIgnoreCase(label)) {
      return WebAuthnSignatureAlgorithm.EDDSA;
    }
    return WebAuthnSignatureAlgorithm.fromLabel(label);
  }

  /** Container for attestation registration payload fields. */
  public record Registration(
      byte[] challenge,
      byte[] clientDataJson,
      byte[] attestationObject,
      byte[] credentialId,
      byte[] aaguid) {
    // Marker record used for transporting attestation registration payloads.
  }

  /** Optional authentication payload associated with an attestation vector. */
  public record Authentication(
      byte[] challenge, byte[] clientDataJson, byte[] authenticatorData, byte[] signature) {
    // Value carrier for authentication payloads associated with an attestation vector.
  }

  /** Key material required for generating deterministic attestation objects. */
  public record KeyMaterial(
      String credentialPrivateKeyBase64Url,
      String attestationPrivateKeyBase64Url,
      String attestationCertificateSerialBase64Url) {
    // Holds deterministic key material for generator flows.
  }

  /** Complete attestation vector record. */
  public record WebAuthnAttestationVector(
      String vectorId,
      WebAuthnAttestationFormat format,
      WebAuthnSignatureAlgorithm algorithm,
      String w3cSection,
      String title,
      String relyingPartyId,
      String origin,
      Registration registration,
      Optional<Authentication> authentication,
      KeyMaterial keyMaterial) {
    // Aggregate attestation vector definition.
  }
}
