package io.openauth.sim.core.fido2;

import io.openauth.sim.core.json.SimpleJson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Generates attestation payloads from deterministic fixtures or manual inputs. */
public final class WebAuthnAttestationGenerator {

    private static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte USER_PRESENT_FLAG = 0x01;
    private static final byte AT_FLAG = 0x40;
    private static final String CERT_NOT_BEFORE = "20250101000000Z";
    private static final String CERT_NOT_AFTER = "20300101000000Z";

    private static final String ERROR_PREFIX = "Unexpected attestation generation input: ";

    /** Supported attestation signing modes. */
    public enum SigningMode {
        SELF_SIGNED,
        UNSIGNED,
        CUSTOM_ROOT
    }

    /** Command marker for attestation generation requests. */
    public sealed interface GenerationCommand permits GenerationCommand.Inline, GenerationCommand.Manual {
        String attestationId();

        WebAuthnAttestationFormat format();

        String relyingPartyId();

        String origin();

        byte[] challenge();

        String credentialPrivateKeyBase64Url();

        String attestationPrivateKeyBase64Url();

        String attestationCertificateSerialBase64Url();

        SigningMode signingMode();

        List<String> customRootCertificatesPem();

        /** Inline attestation generation command (PRESET input source). */
        record Inline(
                String attestationId,
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] challenge,
                String credentialPrivateKeyBase64Url,
                String attestationPrivateKeyBase64Url,
                String attestationCertificateSerialBase64Url,
                SigningMode signingMode,
                List<String> customRootCertificatesPem)
                implements GenerationCommand {

            public Inline {
                Objects.requireNonNull(attestationId, "attestationId");
                Objects.requireNonNull(format, "format");
                Objects.requireNonNull(relyingPartyId, "relyingPartyId");
                Objects.requireNonNull(origin, "origin");
                Objects.requireNonNull(challenge, "challenge");
                Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
                Objects.requireNonNull(signingMode, "signingMode");
                if (customRootCertificatesPem == null) {
                    customRootCertificatesPem = List.of();
                } else {
                    customRootCertificatesPem = customRootCertificatesPem.stream()
                            .filter(Objects::nonNull)
                            .filter(value -> !value.trim().isEmpty())
                            .toList();
                }
            }
        }

        /** Manual attestation generation command (no preset fixture id). */
        record Manual(
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] challenge,
                String credentialPrivateKeyBase64Url,
                String attestationPrivateKeyBase64Url,
                String attestationCertificateSerialBase64Url,
                SigningMode signingMode,
                List<String> customRootCertificatesPem)
                implements GenerationCommand {

            public Manual {
                Objects.requireNonNull(format, "format");
                Objects.requireNonNull(relyingPartyId, "relyingPartyId");
                Objects.requireNonNull(origin, "origin");
                Objects.requireNonNull(challenge, "challenge");
                Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
                Objects.requireNonNull(signingMode, "signingMode");
                if (customRootCertificatesPem == null) {
                    customRootCertificatesPem = List.of();
                } else {
                    customRootCertificatesPem = customRootCertificatesPem.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                }
            }

            @Override
            public String attestationId() {
                return "manual";
            }
        }
    }

    /** Result payload for generated attestation objects. */
    public record GenerationResult(
            String attestationId,
            WebAuthnAttestationFormat format,
            byte[] attestationObject,
            byte[] clientDataJson,
            byte[] expectedChallenge,
            List<String> certificateChainPem,
            boolean signatureIncluded,
            byte[] credentialId) {
        // Provides canonical accessors for generation outputs.
    }

    /** Generates a WebAuthn attestation payload. */
    public GenerationResult generate(GenerationCommand command) {
        Objects.requireNonNull(command, "command");
        if (command instanceof GenerationCommand.Inline inline) {
            WebAuthnAttestationFixtures.WebAuthnAttestationVector vector = WebAuthnAttestationFixtures.findById(
                            inline.attestationId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            ERROR_PREFIX + "unknown attestationId " + inline.attestationId()));

            validateInline(inline, vector);

            byte[] attestationObject = vector.registration().attestationObject().clone();
            byte[] clientDataJson = vector.registration().clientDataJson().clone();
            byte[] expectedChallenge = inline.challenge().clone();

            List<String> certificateChain =
                    switch (inline.signingMode()) {
                        case SELF_SIGNED -> convertCertificatesToPem(vector);
                        case UNSIGNED -> List.of();
                        case CUSTOM_ROOT -> {
                            if (inline.customRootCertificatesPem().isEmpty()) {
                                throw new IllegalArgumentException(
                                        ERROR_PREFIX + "custom-root signing requires at least one certificate");
                            }
                            yield List.copyOf(inline.customRootCertificatesPem());
                        }
                    };

            boolean signatureIncluded = inline.signingMode() != SigningMode.UNSIGNED;

            return new GenerationResult(
                    inline.attestationId(),
                    inline.format(),
                    attestationObject,
                    clientDataJson,
                    expectedChallenge,
                    certificateChain,
                    signatureIncluded,
                    vector.registration().credentialId().clone());
        }

        GenerationCommand.Manual manual = (GenerationCommand.Manual) command;

        validateManual(manual);

        try {
            return new ManualAttestationBuilder(manual).build();
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException(ERROR_PREFIX + ex.getMessage(), ex);
        }
    }

    private static void validateInline(
            GenerationCommand.Inline command, WebAuthnAttestationFixtures.WebAuthnAttestationVector vector) {
        if (vector.format() != command.format()) {
            throw new IllegalArgumentException(ERROR_PREFIX
                    + "format mismatch (expected "
                    + vector.format().label()
                    + " but was "
                    + command.format().label()
                    + ')');
        }
        if (!vector.relyingPartyId().equals(command.relyingPartyId())) {
            throw new IllegalArgumentException(ERROR_PREFIX + "relying party mismatch for " + command.attestationId());
        }
        if (!vector.origin().equals(command.origin())) {
            throw new IllegalArgumentException(ERROR_PREFIX + "origin mismatch for " + command.attestationId());
        }

        WebAuthnAttestationFixtures.KeyMaterial keyMaterial = vector.keyMaterial();
        validateKey(
                "credentialPrivateKey",
                keyMaterial.credentialPrivateKeyBase64Url(),
                command.credentialPrivateKeyBase64Url());
        validateKey(
                "attestationPrivateKey",
                keyMaterial.attestationPrivateKeyBase64Url(),
                command.attestationPrivateKeyBase64Url());

        if (command.signingMode() != SigningMode.UNSIGNED) {
            validateKey(
                    "attestationCertificateSerial",
                    keyMaterial.attestationCertificateSerialBase64Url(),
                    command.attestationCertificateSerialBase64Url());
        }
    }

    private static void validateManual(GenerationCommand.Manual command) {
        if (sanitize(command.credentialPrivateKeyBase64Url()).isEmpty()) {
            throw new IllegalArgumentException(ERROR_PREFIX + "credentialPrivateKey must be provided for manual mode");
        }
        if (command.signingMode() == SigningMode.CUSTOM_ROOT
                && command.customRootCertificatesPem().isEmpty()) {
            throw new IllegalArgumentException(ERROR_PREFIX + "custom-root signing requires at least one certificate");
        }
        if (command.signingMode() != SigningMode.UNSIGNED
                && sanitize(command.attestationCertificateSerialBase64Url()).isEmpty()) {
            throw new IllegalArgumentException(
                    ERROR_PREFIX + "attestationCertificateSerial is required for signed modes");
        }
    }

    private static void validateKey(String field, String expected, String provided) {
        if (!Objects.equals(expected, sanitize(provided))) {
            throw new IllegalArgumentException(ERROR_PREFIX + field + " mismatch for attestation fixture");
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static List<String> convertCertificatesToPem(WebAuthnAttestationFixtures.WebAuthnAttestationVector vector) {
        List<X509Certificate> certificates = loadCertificateChain(vector);
        if (certificates.isEmpty()) {
            return List.of();
        }
        return certificates.stream().map(WebAuthnAttestationGenerator::toPem).toList();
    }

    private static List<X509Certificate> loadCertificateChain(
            WebAuthnAttestationFixtures.WebAuthnAttestationVector vector) {
        WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
        WebAuthnAttestationRequest request = new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin());
        WebAuthnAttestationVerification verification = verifier.verify(request);
        return verification.certificateChain();
    }

    private static String toPem(X509Certificate certificate) {
        try {
            String encoded = MIME_ENCODER.encodeToString(certificate.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
        } catch (CertificateEncodingException ex) {
            throw new IllegalArgumentException("Unable to encode certificate to PEM", ex);
        }
    }

    private static final class ManualAttestationBuilder {

        private final GenerationCommand.Manual manual;
        private final WebAuthnAttestationFixtures.WebAuthnAttestationVector template;
        private final Map<String, Object> templateAttStmt;

        ManualAttestationBuilder(GenerationCommand.Manual manual) throws GeneralSecurityException {
            this.manual = manual;
            this.template = templateFor(manual.format());
            this.templateAttStmt =
                    decodeTemplateAttestationStatement(template.registration().attestationObject());
        }

        GenerationResult build() throws GeneralSecurityException {
            ManualEcKey credentialKey =
                    parseEcPrivateKey(manual.credentialPrivateKeyBase64Url(), "credentialPrivateKey");
            byte[] credentialId = generateCredentialId();
            byte[] credentialPublicKeyCose = encodeCoseEcPublicKey(credentialKey);

            byte[] rpIdHash = sha256(manual.relyingPartyId().getBytes(StandardCharsets.UTF_8));
            byte[] authData =
                    buildAuthData(rpIdHash, template.registration().aaguid(), credentialId, credentialPublicKeyCose);

            byte[] clientDataJson = buildClientDataJson(manual.origin(), manual.challenge());
            byte[] clientDataHash = sha256(clientDataJson);

            ManualCertificateBundle certificates = ManualCertificateBundle.create(manual, credentialKey);

            Map<String, Object> attStmt = buildAttestationStatement(
                    credentialKey, certificates, authData, clientDataHash, rpIdHash, credentialId);

            byte[] attestationObject = encodeAttestationObject(manual.format().label(), authData, attStmt);

            return new GenerationResult(
                    manual.attestationId(),
                    manual.format(),
                    attestationObject,
                    clientDataJson,
                    manual.challenge().clone(),
                    certificates.chainPem(),
                    certificates.signatureIncluded(),
                    credentialId);
        }

        private Map<String, Object> buildAttestationStatement(
                ManualEcKey credentialKey,
                ManualCertificateBundle certificates,
                byte[] authData,
                byte[] clientDataHash,
                byte[] rpIdHash,
                byte[] credentialId)
                throws GeneralSecurityException {
            return switch (manual.format()) {
                case PACKED ->
                    buildPacked(
                            effectiveAlgorithm(certificates, credentialKey), certificates, authData, clientDataHash);
                case ANDROID_KEY ->
                    buildPacked(
                            effectiveAlgorithm(certificates, credentialKey), certificates, authData, clientDataHash);
                case FIDO_U2F -> buildFidoU2f(certificates, clientDataHash, rpIdHash, credentialId, credentialKey);
                case TPM -> buildTpm(certificates, authData, clientDataHash);
            };
        }

        private Map<String, Object> buildPacked(
                WebAuthnSignatureAlgorithm algorithm,
                ManualCertificateBundle certificates,
                byte[] authData,
                byte[] clientDataHash)
                throws GeneralSecurityException {
            Map<String, Object> attStmt = new LinkedHashMap<>();
            attStmt.put("alg", algorithm.coseIdentifier());
            if (certificates.signatureIncluded()) {
                byte[] payload = concat(authData, clientDataHash);
                byte[] signature = sign(certificates.attestationKey(), algorithm, payload);
                attStmt.put("sig", signature);
                if (!certificates.x5cDer().isEmpty()) {
                    attStmt.put("x5c", certificates.x5cDer());
                }
            }
            return attStmt;
        }

        private Map<String, Object> buildFidoU2f(
                ManualCertificateBundle certificates,
                byte[] clientDataHash,
                byte[] rpIdHash,
                byte[] credentialId,
                ManualEcKey credentialKey)
                throws GeneralSecurityException {
            if (!certificates.signatureIncluded()) {
                return new LinkedHashMap<>();
            }
            byte[] payload = buildFidoU2fPayload(rpIdHash, clientDataHash, credentialId, credentialKey);
            byte[] signature = sign(
                    certificates.attestationKey(), certificates.attestationKey().algorithm(), payload);
            Map<String, Object> attStmt = new LinkedHashMap<>();
            attStmt.put("sig", signature);
            attStmt.put("x5c", certificates.x5cDer());
            return attStmt;
        }

        private Map<String, Object> buildTpm(
                ManualCertificateBundle certificates, byte[] authData, byte[] clientDataHash)
                throws GeneralSecurityException {
            byte[] templateCertInfo = expectByteArray("certInfo");
            byte[] templatePubArea = expectByteArray("pubArea");
            String version = (String) templateAttStmt.getOrDefault("ver", "2.0");
            Number alg = (Number) templateAttStmt.get("alg");

            int nameAlg = readTpmNameAlg(templatePubArea);
            MessageDigest digest = MessageDigest.getInstance(digestAlgorithmForTpm(nameAlg));
            digest.update(authData);
            digest.update(clientDataHash);
            byte[] extraData = digest.digest();

            byte[] certInfo = updateTpmCertInfo(templateCertInfo, extraData);

            if (!certificates.signatureIncluded()) {
                Map<String, Object> attStmt = new LinkedHashMap<>();
                attStmt.put("ver", version);
                if (alg != null) {
                    attStmt.put("alg", alg.intValue());
                }
                attStmt.put("certInfo", certInfo);
                attStmt.put("pubArea", templatePubArea);
                return attStmt;
            }

            byte[] signature = sign(
                    certificates.attestationKey(), certificates.attestationKey().algorithm(), certInfo);

            Map<String, Object> attStmt = new LinkedHashMap<>();
            attStmt.put("ver", version);
            attStmt.put(
                    "alg",
                    alg != null
                            ? alg.intValue()
                            : certificates.attestationKey().algorithm().coseIdentifier());
            attStmt.put("sig", signature);
            attStmt.put("certInfo", certInfo);
            attStmt.put("pubArea", templatePubArea);
            attStmt.put("x5c", certificates.x5cDer());
            return attStmt;
        }

        private WebAuthnSignatureAlgorithm effectiveAlgorithm(
                ManualCertificateBundle certificates, ManualEcKey fallback) {
            return certificates.signatureIncluded()
                    ? certificates.attestationKey().algorithm()
                    : fallback.algorithm();
        }

        private byte[] expectByteArray(String key) {
            Object value = templateAttStmt.get(key);
            if (value instanceof byte[] bytes) {
                return bytes.clone();
            }
            throw new IllegalStateException("Template attestation statement missing field '" + key + "'");
        }

        private byte[] buildFidoU2fPayload(
                byte[] rpIdHash, byte[] clientDataHash, byte[] credentialId, ManualEcKey credentialKey) {
            byte[] publicKey = uncompressedPoint(credentialKey);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            buffer.write(0x00);
            buffer.writeBytes(rpIdHash);
            buffer.writeBytes(clientDataHash);
            buffer.writeBytes(credentialId);
            buffer.writeBytes(publicKey);
            return buffer.toByteArray();
        }
    }

    private record ManualEcKey(WebAuthnSignatureAlgorithm algorithm, ECPrivateKey privateKey, ECPublicKey publicKey) {
        // Value object bundling manual EC key material.
    }

    private record ManualCertificateBundle(
            ManualEcKey attestationKey, List<byte[]> x5cDer, List<String> chainPem, boolean signatureIncluded) {

        static ManualCertificateBundle create(GenerationCommand.Manual manual, ManualEcKey credentialKey)
                throws GeneralSecurityException {
            if (manual.signingMode() == SigningMode.UNSIGNED) {
                return new ManualCertificateBundle(null, List.of(), List.of(), false);
            }

            String attestationInput = sanitize(manual.attestationPrivateKeyBase64Url());
            ManualEcKey attestationKey = attestationInput.isEmpty()
                    ? credentialKey
                    : parseEcPrivateKey(attestationInput, "attestationPrivateKey");
            byte[] serialBytes =
                    decodeBase64Url(manual.attestationCertificateSerialBase64Url(), "attestationCertificateSerial");
            if (serialBytes.length == 0) {
                throw new IllegalArgumentException(
                        ERROR_PREFIX + "attestationCertificateSerial must not be empty for signed modes");
            }

            X509Certificate certificate = generateCertificate(attestationKey, serialBytes);
            byte[] attestationDer = certificate.getEncoded();
            String attestationPem = toPem("CERTIFICATE", attestationDer).trim();

            List<byte[]> x5c = new ArrayList<>();
            x5c.add(attestationDer);

            List<String> chainPem;
            if (manual.customRootCertificatesPem().isEmpty()) {
                chainPem = List.of(attestationPem);
            } else {
                List<String> normalizedRoots = new ArrayList<>();
                for (String pem : manual.customRootCertificatesPem()) {
                    PemCertificate parsed = parsePemCertificate(pem);
                    x5c.add(parsed.der());
                    normalizedRoots.add(parsed.pem());
                }
                chainPem = List.copyOf(normalizedRoots);
            }

            return new ManualCertificateBundle(attestationKey, immutableByteArrayList(x5c), chainPem, true);
        }
    }

    private record PemCertificate(byte[] der, String pem) {
        // Normalized PEM certificate representation.
    }

    private static Map<String, Object> decodeTemplateAttestationStatement(byte[] attestationObject)
            throws GeneralSecurityException {
        Object decoded = CborDecoder.decode(attestationObject);
        Map<String, Object> root = toStringKeyedMap(decoded);
        Object attStmt = root.get("attStmt");
        if (attStmt == null) {
            return Map.of();
        }
        return toStringKeyedMap(attStmt);
    }

    private static ManualEcKey parseEcPrivateKey(String value, String fieldName) throws GeneralSecurityException {
        String sanitized = sanitize(value);
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException(ERROR_PREFIX + fieldName + " must not be blank");
        }
        byte[] scalar;
        WebAuthnSignatureAlgorithm algorithm;
        if (sanitized.startsWith("{") && sanitized.endsWith("}")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> jwk = (Map<String, Object>) SimpleJson.parse(sanitized);
            String kty = requireJwkString(jwk, "kty");
            if (!"EC".equalsIgnoreCase(kty)) {
                throw new IllegalArgumentException(ERROR_PREFIX + "unsupported JWK key type: " + kty);
            }
            String curve = requireJwkString(jwk, "crv");
            algorithm = algorithmForCurve(curve);
            String d = requireJwkString(jwk, "d");
            try {
                scalar = URL_DECODER.decode(d);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(ERROR_PREFIX + "invalid JWK private key encoding", ex);
            }
        } else {
            scalar = decodeBase64Url(sanitized, fieldName);
            algorithm = inferEcAlgorithm(scalar.length);
        }

        ECParameterSpec params = ecParameters(algorithm);

        BigInteger d = new BigInteger(1, scalar);
        ECPrivateKeySpec privateSpec = new ECPrivateKeySpec(d, params);
        KeyFactory factory = KeyFactory.getInstance("EC");
        ECPrivateKey privateKey = (ECPrivateKey) factory.generatePrivate(privateSpec);

        ECPoint generator = params.getGenerator();
        ECPoint publicPoint = scalarMultiply(generator, d, params);
        if (publicPoint.equals(ECPoint.POINT_INFINITY)) {
            throw new GeneralSecurityException("Unable to derive EC public point");
        }
        ECPublicKeySpec publicSpec = new ECPublicKeySpec(publicPoint, params);
        ECPublicKey publicKey = (ECPublicKey) factory.generatePublic(publicSpec);

        return new ManualEcKey(algorithm, privateKey, publicKey);
    }

    private static String requireJwkString(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        throw new IllegalArgumentException(ERROR_PREFIX + "JWK field '" + name + "' is required");
    }

    private static byte[] decodeBase64Url(String value, String fieldName) {
        String sanitized = sanitize(value);
        if (sanitized.isEmpty()) {
            return new byte[0];
        }
        try {
            return URL_DECODER.decode(sanitized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ERROR_PREFIX + "invalid " + fieldName + " encoding", ex);
        }
    }

    private static List<byte[]> immutableByteArrayList(List<byte[]> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<byte[]> copy = new ArrayList<>(source.size());
        for (byte[] bytes : source) {
            copy.add(bytes.clone());
        }
        return List.copyOf(copy);
    }

    private static byte[] encodeCoseEcPublicKey(ManualEcKey key) {
        Map<Integer, Object> cose = new LinkedHashMap<>();
        cose.put(1, 2);
        cose.put(3, key.algorithm().coseIdentifier());
        cose.put(-1, curveIdFor(key.algorithm()));
        cose.put(-2, coordinateBytes(key.publicKey().getW().getAffineX(), key.algorithm()));
        cose.put(-3, coordinateBytes(key.publicKey().getW().getAffineY(), key.algorithm()));
        return CborEncoder.encodeMap(cose);
    }

    private static int curveIdFor(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> 1;
            case ES384 -> 2;
            case ES512 -> 3;
            default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + algorithm);
        };
    }

    private static byte[] buildAuthData(
            byte[] rpIdHash, byte[] aaguid, byte[] credentialId, byte[] credentialPublicKey) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.writeBytes(rpIdHash);
        buffer.write(USER_PRESENT_FLAG | AT_FLAG);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.writeBytes(aaguid.clone());
        buffer.write((credentialId.length >>> 8) & 0xFF);
        buffer.write(credentialId.length & 0xFF);
        buffer.writeBytes(credentialId);
        buffer.writeBytes(credentialPublicKey);
        return buffer.toByteArray();
    }

    private static byte[] encodeAttestationObject(String fmt, byte[] authData, Map<String, Object> attStmt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fmt", fmt);
        map.put("authData", authData);
        map.put("attStmt", attStmt);
        return CborEncoder.encodeMap(map);
    }

    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 digest not available", ex);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] generateCredentialId() {
        byte[] id = new byte[32];
        RANDOM.nextBytes(id);
        return id;
    }

    private static byte[] uncompressedPoint(ManualEcKey key) {
        byte[] x = coordinateBytes(key.publicKey().getW().getAffineX(), key.algorithm());
        byte[] y = coordinateBytes(key.publicKey().getW().getAffineY(), key.algorithm());
        byte[] result = new byte[1 + x.length + y.length];
        result[0] = 0x04;
        System.arraycopy(x, 0, result, 1, x.length);
        System.arraycopy(y, 0, result, 1 + x.length, y.length);
        return result;
    }

    private static byte[] coordinateBytes(BigInteger value, WebAuthnSignatureAlgorithm algorithm) {
        int length = coordinateLength(algorithm);
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        if (raw.length == length + 1 && raw[0] == 0) {
            byte[] trimmed = new byte[length];
            System.arraycopy(raw, 1, trimmed, 0, length);
            return trimmed;
        }
        byte[] result = new byte[length];
        int copy = Math.min(raw.length, length);
        System.arraycopy(raw, raw.length - copy, result, length - copy, copy);
        return result;
    }

    private static int coordinateLength(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> 32;
            case ES384 -> 48;
            case ES512 -> 66;
            default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + algorithm);
        };
    }

    private static WebAuthnSignatureAlgorithm inferEcAlgorithm(int scalarLength) {
        return switch (scalarLength) {
            case 32 -> WebAuthnSignatureAlgorithm.ES256;
            case 48 -> WebAuthnSignatureAlgorithm.ES384;
            case 66 -> WebAuthnSignatureAlgorithm.ES512;
            default ->
                throw new IllegalArgumentException(ERROR_PREFIX + "unsupported EC private key length: " + scalarLength);
        };
    }

    private static ECParameterSpec ecParameters(WebAuthnSignatureAlgorithm algorithm) throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveForAlgorithm(algorithm)));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static String curveForAlgorithm(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "secp256r1";
            case ES384 -> "secp384r1";
            case ES512 -> "secp521r1";
            default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + algorithm);
        };
    }

    private static WebAuthnSignatureAlgorithm algorithmForCurve(String curve) {
        String normalized = curve.trim().toUpperCase();
        return switch (normalized) {
            case "P-256", "SECP256R1" -> WebAuthnSignatureAlgorithm.ES256;
            case "P-384", "SECP384R1" -> WebAuthnSignatureAlgorithm.ES384;
            case "P-521", "SECP521R1" -> WebAuthnSignatureAlgorithm.ES512;
            default -> throw new IllegalArgumentException(ERROR_PREFIX + "unsupported EC curve: " + curve);
        };
    }

    private static ECPoint scalarMultiply(ECPoint point, BigInteger scalar, ECParameterSpec params) {
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = point;
        BigInteger k = scalar;
        while (k.signum() > 0) {
            if (k.testBit(0)) {
                result = addPoints(result, addend, params);
            }
            addend = addPoints(addend, addend, params);
            k = k.shiftRight(1);
        }
        return result;
    }

    private static ECPoint addPoints(ECPoint p, ECPoint q, ECParameterSpec params) {
        if (p.equals(ECPoint.POINT_INFINITY)) {
            return q;
        }
        if (q.equals(ECPoint.POINT_INFINITY)) {
            return p;
        }

        var curve = params.getCurve();
        if (!(curve.getField() instanceof ECFieldFp primeField)) {
            throw new IllegalArgumentException("Only prime-field curves are supported");
        }
        BigInteger modulus = primeField.getP();
        BigInteger a = curve.getA();

        BigInteger px = p.getAffineX();
        BigInteger py = p.getAffineY();
        BigInteger qx = q.getAffineX();
        BigInteger qy = q.getAffineY();

        if (px.equals(qx) && py.add(qy).mod(modulus).equals(BigInteger.ZERO)) {
            return ECPoint.POINT_INFINITY;
        }

        BigInteger lambda;
        if (px.equals(qx) && py.equals(qy)) {
            BigInteger numerator =
                    px.pow(2).multiply(BigInteger.valueOf(3)).add(a).mod(modulus);
            BigInteger denominator = py.shiftLeft(1).modInverse(modulus);
            lambda = numerator.multiply(denominator).mod(modulus);
        } else {
            BigInteger numerator = qy.subtract(py).mod(modulus);
            BigInteger denominator = qx.subtract(px).mod(modulus).modInverse(modulus);
            lambda = numerator.multiply(denominator).mod(modulus);
        }

        BigInteger rx = lambda.pow(2).subtract(px).subtract(qx).mod(modulus);
        BigInteger ry = lambda.multiply(px.subtract(rx)).subtract(py).mod(modulus);
        return new ECPoint(rx, ry);
    }

    private static byte[] sign(ManualEcKey key, WebAuthnSignatureAlgorithm algorithm, byte[] payload)
            throws GeneralSecurityException {
        Signature signature = Signature.getInstance(signatureAlgorithmName(algorithm));
        signature.initSign(key.privateKey());
        signature.update(payload);
        return signature.sign();
    }

    private static String signatureAlgorithmName(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "SHA256withECDSA";
            case ES384 -> "SHA384withECDSA";
            case ES512 -> "SHA512withECDSA";
            default -> throw new IllegalArgumentException("Unsupported signing algorithm: " + algorithm);
        };
    }

    private static X509Certificate generateCertificate(ManualEcKey attestationKey, byte[] serialBytes)
            throws GeneralSecurityException {
        BigInteger serial = normalizeSerial(serialBytes);
        byte[] subjectPublicKeyInfo = attestationKey.publicKey().getEncoded();
        byte[] tbsCertificate = DerEncoder.sequence(
                DerEncoder.explicit(0, DerEncoder.integer(BigInteger.valueOf(2))),
                DerEncoder.integer(serial),
                DerEncoder.algorithmIdentifier(attestationKey.algorithm()),
                DerEncoder.name("OpenAuth Simulator Attestation"),
                DerEncoder.validity(CERT_NOT_BEFORE, CERT_NOT_AFTER),
                DerEncoder.name("OpenAuth Simulator Attestation"),
                subjectPublicKeyInfo);
        byte[] signatureAlgorithm = DerEncoder.algorithmIdentifier(attestationKey.algorithm());
        byte[] signature = sign(attestationKey, attestationKey.algorithm(), tbsCertificate);
        byte[] certificate = DerEncoder.sequence(tbsCertificate, signatureAlgorithm, DerEncoder.bitString(signature));
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificate));
    }

    private static BigInteger normalizeSerial(byte[] serialBytes) {
        BigInteger value = new BigInteger(1, serialBytes);
        if (value.signum() == 0) {
            return BigInteger.ONE;
        }
        return value;
    }

    private static PemCertificate parsePemCertificate(String pem) throws GeneralSecurityException {
        String normalized = sanitize(pem);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(ERROR_PREFIX + "custom root certificate must not be blank");
        }
        if (!normalized.startsWith("-----BEGIN")) {
            throw new IllegalArgumentException(ERROR_PREFIX + "custom root certificate must be PEM encoded");
        }
        String base64 = normalized
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ERROR_PREFIX + "invalid PEM certificate payload", ex);
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
        byte[] canonical = certificate.getEncoded();
        String pemBody = toPem("CERTIFICATE", canonical).trim();
        return new PemCertificate(canonical, pemBody);
    }

    private static String digestAlgorithmForTpm(int nameAlg) {
        return switch (nameAlg) {
            case 0x0004 -> "SHA-1";
            case 0x000B -> "SHA-256";
            case 0x000C -> "SHA-384";
            case 0x000D -> "SHA-512";
            default -> throw new IllegalArgumentException("Unsupported TPM name algorithm: " + nameAlg);
        };
    }

    private static int readTpmNameAlg(byte[] pubArea) {
        if (pubArea.length < 4) {
            throw new IllegalArgumentException("TPM pubArea truncated");
        }
        ByteBuffer buffer = ByteBuffer.wrap(pubArea).order(ByteOrder.BIG_ENDIAN);
        buffer.getShort(); // type
        return Short.toUnsignedInt(buffer.getShort());
    }

    private static byte[] updateTpmCertInfo(byte[] template, byte[] extraData) {
        byte[] updated = template.clone();
        ByteBuffer buffer = ByteBuffer.wrap(updated).order(ByteOrder.BIG_ENDIAN);
        buffer.getInt(); // magic
        buffer.getShort(); // type
        int qualifiedLength = Short.toUnsignedInt(buffer.getShort());
        buffer.position(buffer.position() + qualifiedLength);
        int extraLengthPosition = buffer.position();
        int originalLength = Short.toUnsignedInt(buffer.getShort());
        if (extraData.length > originalLength) {
            throw new IllegalArgumentException("TPM certInfo extraData length exceeds template allocation");
        }
        int extraDataOffset = buffer.position();
        System.arraycopy(extraData, 0, updated, extraDataOffset, extraData.length);
        if (extraData.length < originalLength) {
            Arrays.fill(updated, extraDataOffset + extraData.length, extraDataOffset + originalLength, (byte) 0x00);
        }
        buffer.position(extraLengthPosition);
        buffer.putShort((short) extraData.length);
        return updated;
    }

    private static Map<String, Object> toStringKeyedMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("CBOR value is not a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String toPem(String label, byte[] der) {
        String body = MIME_ENCODER.encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private static final class DerEncoder {

        private DerEncoder() {
            // utility
        }

        static byte[] sequence(byte[]... elements) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (byte[] element : elements) {
                buffer.writeBytes(element);
            }
            return prefix((byte) 0x30, buffer.toByteArray());
        }

        static byte[] set(byte[]... elements) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (byte[] element : elements) {
                buffer.writeBytes(element);
            }
            return prefix((byte) 0x31, buffer.toByteArray());
        }

        static byte[] explicit(int tagNumber, byte[] content) {
            byte tag = (byte) (0xA0 | (tagNumber & 0x1F));
            return prefix(tag, content);
        }

        static byte[] integer(BigInteger value) {
            byte[] bytes = value.toByteArray();
            return prefix((byte) 0x02, bytes);
        }

        static byte[] algorithmIdentifier(WebAuthnSignatureAlgorithm algorithm) {
            return sequence(objectIdentifier(oidForAlgorithm(algorithm)), nullValue());
        }

        static byte[] name(String commonName) {
            byte[] oid = objectIdentifier("2.5.4.3");
            byte[] value = utf8String(commonName);
            byte[] attribute = sequence(oid, value);
            return sequence(set(attribute));
        }

        static byte[] validity(String notBefore, String notAfter) {
            return sequence(generalizedTime(notBefore), generalizedTime(notAfter));
        }

        static byte[] generalizedTime(String value) {
            String normalized = value.endsWith("Z") ? value : value + "Z";
            byte[] bytes = normalized.getBytes(StandardCharsets.US_ASCII);
            return prefix((byte) 0x18, bytes);
        }

        static byte[] objectIdentifier(String oid) {
            String[] parts = oid.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid object identifier: " + oid);
            }
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            buffer.write(40 * first + second);
            for (int i = 2; i < parts.length; i++) {
                long component = Long.parseLong(parts[i]);
                buffer.writeBytes(encodeBase128(component));
            }
            return prefix((byte) 0x06, buffer.toByteArray());
        }

        static byte[] utf8String(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            return prefix((byte) 0x0C, bytes);
        }

        static byte[] bitString(byte[] value) {
            byte[] content = new byte[value.length + 1];
            System.arraycopy(value, 0, content, 1, value.length);
            return prefix((byte) 0x03, content);
        }

        static byte[] nullValue() {
            return new byte[] {0x05, 0x00};
        }

        private static byte[] prefix(byte tag, byte[] content) {
            byte[] length = encodeLength(content.length);
            byte[] result = new byte[1 + length.length + content.length];
            result[0] = tag;
            System.arraycopy(length, 0, result, 1, length.length);
            System.arraycopy(content, 0, result, 1 + length.length, content.length);
            return result;
        }

        private static byte[] encodeLength(int length) {
            if (length < 0x80) {
                return new byte[] {(byte) length};
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int remaining = length;
            while (remaining > 0) {
                buffer.write(remaining & 0xFF);
                remaining >>= 8;
            }
            byte[] bytes = buffer.toByteArray();
            byte[] result = new byte[bytes.length + 1];
            result[0] = (byte) (0x80 | bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                result[i + 1] = bytes[bytes.length - 1 - i];
            }
            return result;
        }

        private static byte[] encodeBase128(long value) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            buffer.write((int) (value & 0x7F));
            value >>= 7;
            while (value > 0) {
                buffer.write((int) ((value & 0x7F) | 0x80));
                value >>= 7;
            }
            byte[] bytes = buffer.toByteArray();
            for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
                byte tmp = bytes[i];
                bytes[i] = bytes[j];
                bytes[j] = tmp;
            }
            return bytes;
        }

        private static String oidForAlgorithm(WebAuthnSignatureAlgorithm algorithm) {
            return switch (algorithm) {
                case ES256 -> "1.2.840.10045.4.3.2";
                case ES384 -> "1.2.840.10045.4.3.3";
                case ES512 -> "1.2.840.10045.4.3.4";
                default -> throw new IllegalArgumentException("Unsupported signing algorithm: " + algorithm);
            };
        }
    }

    private static final class CborEncoder {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private CborEncoder() {
            // utility
        }

        static byte[] encodeMap(Map<?, ?> map) {
            CborEncoder encoder = new CborEncoder();
            encoder.writeMap(map);
            return encoder.out.toByteArray();
        }

        private void writeMap(Map<?, ?> map) {
            writeMajorType(5, map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeValue(entry.getKey());
                writeValue(entry.getValue());
            }
        }

        private void writeArray(List<?> values) {
            writeMajorType(4, values.size());
            for (Object value : values) {
                writeValue(value);
            }
        }

        private void writeValue(Object value) {
            if (value == null) {
                writeNull();
            } else if (value instanceof String s) {
                writeTextString(s);
            } else if (value instanceof byte[] bytes) {
                writeByteString(bytes);
            } else if (value instanceof Number number) {
                writeNumber(number.longValue());
            } else if (value instanceof Boolean bool) {
                writeBoolean(bool);
            } else if (value instanceof Map<?, ?> map) {
                writeMap(map);
            } else if (value instanceof List<?> list) {
                writeArray(list);
            } else {
                throw new IllegalArgumentException("Unsupported CBOR value type: " + value.getClass());
            }
        }

        private void writeNumber(long value) {
            if (value >= 0) {
                writeMajorType(0, value);
            } else {
                writeMajorType(1, -value - 1);
            }
        }

        private void writeTextString(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeMajorType(3, bytes.length);
            out.writeBytes(bytes);
        }

        private void writeByteString(byte[] value) {
            writeMajorType(2, value.length);
            out.writeBytes(value);
        }

        private void writeBoolean(boolean value) {
            out.write(value ? 0xF5 : 0xF4);
        }

        private void writeNull() {
            out.write(0xF6);
        }

        private void writeMajorType(int majorType, long value) {
            if (value < 24) {
                out.write((majorType << 5) | (int) value);
            } else if (value < 0x100) {
                out.write((majorType << 5) | 24);
                out.write((int) value);
            } else if (value < 0x10000) {
                out.write((majorType << 5) | 25);
                out.write((int) (value >>> 8));
                out.write((int) value);
            } else if (value < 0x1_0000_0000L) {
                out.write((majorType << 5) | 26);
                for (int shift = 24; shift >= 0; shift -= 8) {
                    out.write((int) (value >>> shift) & 0xFF);
                }
            } else {
                out.write((majorType << 5) | 27);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    out.write((int) (value >>> shift) & 0xFF);
                }
            }
        }
    }

    private static WebAuthnAttestationFixtures.WebAuthnAttestationVector templateFor(WebAuthnAttestationFormat format) {
        return WebAuthnAttestationFixtures.vectorsFor(format).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        ERROR_PREFIX + "no template vector available for format " + format.label()));
    }

    private static byte[] buildClientDataJson(String origin, byte[] challenge) {
        String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);
        String json = "{"
                + "\"type\":\"webauthn.create\","
                + "\"origin\":\""
                + sanitize(origin)
                + "\","
                + "\"challenge\":\""
                + encodedChallenge
                + "\"}";
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
