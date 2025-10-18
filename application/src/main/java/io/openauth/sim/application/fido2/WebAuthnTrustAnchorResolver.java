package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves PEM trust anchor bundles with simple in-memory caching while surfacing pre-loaded
 * metadata entries from the offline WebAuthn Metadata Service dataset.
 */
public final class WebAuthnTrustAnchorResolver {

  private static final CertificateFactory CERTIFICATE_FACTORY = certificateFactory();
  private static final Map<String, MetadataAnchors> METADATA_INDEX = buildMetadataIndex();
  private final Map<String, CachedAnchors> cache = new ConcurrentHashMap<>();

  public Resolution resolvePemStrings(
      String attestationId, WebAuthnAttestationFormat format, List<String> inputs) {
    List<String> normalised = normalisePemInputs(inputs);
    return resolveAnchors(attestationId, format, normalised, List.of());
  }

  public Resolution resolveFiles(
      String attestationId, WebAuthnAttestationFormat format, List<Path> paths) {
    if (paths == null || paths.isEmpty()) {
      return resolveAnchors(attestationId, format, List.of(), List.of());
    }

    List<String> pemBlocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (Path path : paths) {
      if (path == null) {
        continue;
      }
      try {
        String data = Files.readString(path, StandardCharsets.UTF_8);
        if (data != null && !data.isBlank()) {
          pemBlocks.add(data);
        } else {
          warnings.add("Trust anchor file " + path + " was empty.");
        }
      } catch (IOException ex) {
        warnings.add("Unable to read trust anchor file " + path + ": " + ex.getMessage());
      }
    }
    return resolveAnchors(attestationId, format, normalisePemInputs(pemBlocks), warnings);
  }

  private Resolution resolveAnchors(
      String attestationId,
      WebAuthnAttestationFormat format,
      List<String> pemBlocks,
      List<String> additionalWarnings) {

    MetadataAnchors metadataAnchors = lookupMetadata(attestationId, format);
    ParseResult manual = parseManualAnchors(pemBlocks);

    List<String> warnings = new ArrayList<>(additionalWarnings);
    warnings.addAll(manual.warnings());

    boolean metadataProvided = metadataAnchors != null && !metadataAnchors.anchors().isEmpty();
    boolean manualProvided = !manual.anchors().isEmpty();

    List<X509Certificate> combinedAnchors = combineAnchors(metadataAnchors, manual.anchors());

    Source source;
    if (metadataProvided && manualProvided) {
      source = Source.COMBINED;
    } else if (metadataProvided) {
      source = Source.METADATA;
    } else if (manualProvided) {
      source = Source.MANUAL;
    } else {
      source = Source.NONE;
    }

    boolean cached;
    if (!manualProvided && metadataProvided) {
      cached = true;
    } else if (!metadataProvided && manualProvided) {
      cached = manual.cached();
    } else if (metadataProvided && manualProvided) {
      cached = manual.cached();
    } else {
      cached = false;
    }

    return new Resolution(
        combinedAnchors,
        cached,
        source,
        metadataAnchors == null ? null : metadataAnchors.entryId(),
        List.copyOf(warnings));
  }

  private ParseResult parseManualAnchors(List<String> pemBlocks) {
    if (pemBlocks.isEmpty()) {
      return ParseResult.empty();
    }

    String cacheKey = String.join("\n", pemBlocks);
    CachedAnchors cached = cache.get(cacheKey);
    if (cached != null) {
      return new ParseResult(cached.anchors(), true, List.of());
    }

    List<X509Certificate> anchors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (String pem : pemBlocks) {
      try (ByteArrayInputStream inputStream =
          new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
        Collection<? extends Certificate> certificates =
            CERTIFICATE_FACTORY.generateCertificates(inputStream);
        if (certificates.isEmpty()) {
          warnings.add("Trust anchor did not contain any certificates.");
          continue;
        }
        for (Certificate certificate : certificates) {
          if (certificate instanceof X509Certificate x509) {
            anchors.add(x509);
          } else {
            warnings.add("Trust anchor entry ignored because it was not an X.509 certificate.");
          }
        }
      } catch (CertificateException ex) {
        warnings.add("Trust anchor parse error: " + ex.getMessage());
      } catch (IOException ex) {
        warnings.add("Trust anchor read error: " + ex.getMessage());
      }
    }

    List<X509Certificate> immutableAnchors = List.copyOf(anchors);
    cache.put(cacheKey, new CachedAnchors(immutableAnchors));
    return new ParseResult(immutableAnchors, false, List.copyOf(warnings));
  }

  private static List<X509Certificate> combineAnchors(
      MetadataAnchors metadataAnchors, List<X509Certificate> manualAnchors) {
    Map<String, X509Certificate> combined = new LinkedHashMap<>();
    if (metadataAnchors != null) {
      for (X509Certificate anchor : metadataAnchors.anchors()) {
        combined.putIfAbsent(certificateKey(anchor), anchor);
      }
    }
    for (X509Certificate anchor : manualAnchors) {
      combined.putIfAbsent(certificateKey(anchor), anchor);
    }
    return List.copyOf(combined.values());
  }

  private static String certificateKey(X509Certificate certificate) {
    try {
      return Base64.getEncoder().encodeToString(certificate.getEncoded());
    } catch (CertificateEncodingException ex) {
      throw new IllegalStateException("Unable to encode trust anchor", ex);
    }
  }

  private static List<String> normalisePemInputs(List<String> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return List.of();
    }
    List<String> normalised = new ArrayList<>(inputs.size());
    for (String input : inputs) {
      if (input == null) {
        continue;
      }
      String trimmed = input.trim();
      if (!trimmed.isEmpty()) {
        normalised.add(trimmed);
      }
    }
    return List.copyOf(normalised);
  }

  private static MetadataAnchors lookupMetadata(
      String attestationId, WebAuthnAttestationFormat format) {
    if (attestationId == null || attestationId.isBlank()) {
      return null;
    }
    String normalised = normaliseKey(attestationId);
    if (format != null) {
      MetadataAnchors byFormat = METADATA_INDEX.get(formattedKey(normalised, format));
      if (byFormat != null) {
        return byFormat;
      }
    }
    MetadataAnchors entry = METADATA_INDEX.get(normalised);
    if (entry != null && (format == null || entry.format() == format)) {
      return entry;
    }
    return null;
  }

  private static Map<String, MetadataAnchors> buildMetadataIndex() {
    Map<String, MetadataAnchors> index = new ConcurrentHashMap<>();
    for (WebAuthnMetadataCatalogue.WebAuthnMetadataEntry entry :
        WebAuthnMetadataCatalogue.entries()) {
      List<X509Certificate> anchors = parseMetadataAnchors(entry);
      MetadataAnchors metadataAnchors =
          new MetadataAnchors(entry.entryId(), entry.attestationFormat(), anchors);

      registerMetadataKey(index, entry.entryId(), entry.attestationFormat(), metadataAnchors);
      Optional.ofNullable(entry.aaguid())
          .map(UUID::toString)
          .ifPresent(
              aaguid ->
                  registerMetadataKey(index, aaguid, entry.attestationFormat(), metadataAnchors));
      for (WebAuthnMetadataCatalogue.MetadataSource source : entry.sources()) {
        if (source.vectorId() != null && !source.vectorId().isBlank()) {
          registerMetadataKey(index, source.vectorId(), entry.attestationFormat(), metadataAnchors);
        }
      }
    }
    return Map.copyOf(index);
  }

  private static void registerMetadataKey(
      Map<String, MetadataAnchors> index,
      String key,
      WebAuthnAttestationFormat format,
      MetadataAnchors metadataAnchors) {
    if (key == null || key.isBlank()) {
      return;
    }
    String normalised = normaliseKey(key);
    index.putIfAbsent(normalised, metadataAnchors);
    index.putIfAbsent(formattedKey(normalised, format), metadataAnchors);
  }

  private static List<X509Certificate> parseMetadataAnchors(
      WebAuthnMetadataCatalogue.WebAuthnMetadataEntry entry) {
    List<X509Certificate> anchors = new ArrayList<>();
    for (WebAuthnMetadataCatalogue.TrustAnchor anchor : entry.trustAnchors()) {
      String certificatePem = anchor.certificatePem();
      if (certificatePem == null || certificatePem.isBlank()) {
        continue;
      }
      try (ByteArrayInputStream inputStream =
          new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.UTF_8))) {
        Collection<? extends Certificate> certificates =
            CERTIFICATE_FACTORY.generateCertificates(inputStream);
        if (certificates.isEmpty()) {
          throw new IllegalStateException(
              "Metadata entry "
                  + entry.entryId()
                  + " trust anchor "
                  + Optional.ofNullable(anchor.label()).orElse("<unnamed>")
                  + " did not contain any certificates.");
        }
        for (Certificate certificate : certificates) {
          if (certificate instanceof X509Certificate x509) {
            anchors.add(x509);
          } else {
            throw new IllegalStateException(
                "Metadata entry " + entry.entryId() + " included a non X.509 trust anchor.");
          }
        }
      } catch (CertificateException | IOException ex) {
        throw new IllegalStateException(
            "Unable to parse metadata trust anchor for entry " + entry.entryId(), ex);
      }
    }
    return List.copyOf(anchors);
  }

  private static String normaliseKey(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private static String formattedKey(String key, WebAuthnAttestationFormat format) {
    return key + "|" + (format == null ? "" : format.label());
  }

  private static CertificateFactory certificateFactory() {
    try {
      return CertificateFactory.getInstance("X.509");
    } catch (CertificateException ex) {
      throw new IllegalStateException("Unable to create X.509 CertificateFactory", ex);
    }
  }

  private record CachedAnchors(List<X509Certificate> anchors) {
    private CachedAnchors {
      anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
    }
  }

  private record ParseResult(List<X509Certificate> anchors, boolean cached, List<String> warnings) {

    static ParseResult empty() {
      return new ParseResult(List.of(), false, List.of());
    }
  }

  private record MetadataAnchors(
      String entryId, WebAuthnAttestationFormat format, List<X509Certificate> anchors) {

    MetadataAnchors {
      entryId = Objects.requireNonNull(entryId, "entryId");
      format = Objects.requireNonNull(format, "format");
      anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
    }
  }

  public record Resolution(
      List<X509Certificate> anchors,
      boolean cached,
      Source source,
      String metadataEntryId,
      List<String> warnings) {

    public Resolution {
      anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
      source = Objects.requireNonNull(source, "source");
      warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
      metadataEntryId =
          metadataEntryId == null || metadataEntryId.isBlank() ? null : metadataEntryId;
    }

    public boolean hasAnchors() {
      return !anchors.isEmpty();
    }

    public boolean metadataProvided() {
      return source == Source.METADATA || source == Source.COMBINED;
    }
  }

  public enum Source {
    NONE,
    MANUAL,
    METADATA,
    COMBINED
  }
}
