package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Generates WebAuthn assertion payloads from authenticator inputs (challenge, private key, etc.).
 */
public final class WebAuthnAssertionGenerationApplicationService {

    private static final int AUTHENTICATOR_FLAGS_USER_PRESENT = 0x01;
    private static final int AUTHENTICATOR_FLAGS_USER_VERIFIED = 0x04;

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final byte[] ED25519_PRIVATE_KEY_PREFIX = hexToBytes("302e020100300506032b657004220420");
    private static final byte[] ED25519_PUBLIC_KEY_PREFIX = hexToBytes("302a300506032b6570032100");

    private final CredentialStore credentialStore;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;

    public WebAuthnAssertionGenerationApplicationService(
            CredentialStore credentialStore, WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        if ((credentialStore == null) != (persistenceAdapter == null)) {
            throw new IllegalArgumentException(
                    "credentialStore and persistenceAdapter must both be provided or both be null");
        }
        this.credentialStore = credentialStore;
        this.persistenceAdapter = persistenceAdapter;
    }

    public WebAuthnAssertionGenerationApplicationService() {
        this(null, null);
    }

    public GenerationResult generate(GenerationCommand command) {
        Objects.requireNonNull(command, "command");

        if (command instanceof GenerationCommand.Stored stored) {
            return generateStored(stored);
        }
        if (command instanceof GenerationCommand.Inline inline) {
            return generateInline(inline);
        }

        throw new IllegalArgumentException("Unsupported WebAuthn generation command: " + command);
    }

    private GenerationResult generateInline(GenerationCommand.Inline command) {
        try {
            KeyMaterial keyMaterial = parsePrivateKey(command.privateKey(), command.algorithm());
            byte[] challenge = command.challenge().clone();
            byte[] clientDataJson = createClientDataJson(command.expectedType(), challenge, command.origin());
            byte[] authenticatorData = buildAuthenticatorData(
                    command.relyingPartyId(), command.signatureCounter(), command.userVerificationRequired());

            byte[] signature =
                    signAssertion(command.algorithm(), keyMaterial.privateKey(), authenticatorData, clientDataJson);

            return new GenerationResult(
                    command.credentialName(),
                    command.credentialId(),
                    challenge,
                    clientDataJson,
                    authenticatorData,
                    signature,
                    keyMaterial.publicKeyCose(),
                    command.algorithm(),
                    command.signatureCounter(),
                    command.userVerificationRequired(),
                    false,
                    command.relyingPartyId(),
                    command.origin());
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Unable to generate WebAuthn assertion: " + ex.getMessage(), ex);
        }
    }

    private GenerationResult generateStored(GenerationCommand.Stored command) {
        if (credentialStore == null || persistenceAdapter == null) {
            throw new IllegalStateException("Stored assertion generation requires a credential store");
        }
        Credential credential = credentialStore
                .findByName(command.credentialName())
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        WebAuthnCredentialDescriptor descriptor =
                persistenceAdapter.deserialize(VersionedCredentialRecordMapper.toRecord(credential));

        if (command.relyingPartyId() != null && !command.relyingPartyId().isBlank()) {
            String requested = command.relyingPartyId().trim();
            if (!descriptor.relyingPartyId().equals(requested)) {
                throw new IllegalArgumentException("Stored credential relying party mismatch");
            }
        }

        long signatureCounter = command.signatureCounterOverrideValue().orElse(descriptor.signatureCounter());
        boolean userVerificationRequired =
                command.userVerificationRequiredOverrideValue().orElse(descriptor.userVerificationRequired());

        GenerationCommand.Inline inline = new GenerationCommand.Inline(
                descriptor.name(),
                descriptor.credentialId(),
                descriptor.algorithm(),
                descriptor.relyingPartyId(),
                command.origin(),
                command.expectedType(),
                signatureCounter,
                userVerificationRequired,
                command.challenge(),
                command.privateKey());

        GenerationResult inlineResult = generateInline(inline);
        return new GenerationResult(
                descriptor.name(),
                descriptor.credentialId(),
                command.challenge().clone(),
                inlineResult.clientDataJson(),
                inlineResult.authenticatorData(),
                inlineResult.signature(),
                inlineResult.publicKeyCose(),
                inlineResult.algorithm(),
                signatureCounter,
                userVerificationRequired,
                true,
                descriptor.relyingPartyId(),
                command.origin());
    }

    private static byte[] createClientDataJson(String type, byte[] challenge, String origin) {
        String json = '{'
                + "\"type\":\""
                + sanitize(type)
                + "\","
                + "\"challenge\":\""
                + URL_ENCODER.encodeToString(challenge)
                + "\","
                + "\"origin\":\""
                + sanitize(origin)
                + "\""
                + '}';
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static byte[] buildAuthenticatorData(String relyingPartyId, long counter, boolean userVerified)
            throws GeneralSecurityException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] rpIdHash = sha256.digest(relyingPartyId.getBytes(StandardCharsets.UTF_8));

        byte flags = AUTHENTICATOR_FLAGS_USER_PRESENT;
        if (userVerified) {
            flags |= AUTHENTICATOR_FLAGS_USER_VERIFIED;
        }

        ByteBuffer buffer = ByteBuffer.allocate(rpIdHash.length + 1 + 4);
        buffer.put(rpIdHash);
        buffer.put(flags);
        buffer.putInt((int) counter);
        return buffer.array();
    }

    private static byte[] signAssertion(
            WebAuthnSignatureAlgorithm algorithm,
            PrivateKey privateKey,
            byte[] authenticatorData,
            byte[] clientDataJson)
            throws GeneralSecurityException {

        byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJson);
        byte[] signedPayload = new byte[authenticatorData.length + clientDataHash.length];
        System.arraycopy(authenticatorData, 0, signedPayload, 0, authenticatorData.length);
        System.arraycopy(clientDataHash, 0, signedPayload, authenticatorData.length, clientDataHash.length);

        Signature signature = signatureFor(algorithm);
        signature.initSign(privateKey);
        if (algorithm == WebAuthnSignatureAlgorithm.PS256) {
            signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        }
        signature.update(signedPayload);
        return signature.sign();
    }

    private static Signature signatureFor(WebAuthnSignatureAlgorithm algorithm) throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256 -> Signature.getInstance("SHA256withECDSA");
            case ES384 -> Signature.getInstance("SHA384withECDSA");
            case ES512 -> Signature.getInstance("SHA512withECDSA");
            case RS256 -> Signature.getInstance("SHA256withRSA");
            case PS256 -> Signature.getInstance("RSASSA-PSS");
            case EDDSA -> Signature.getInstance("Ed25519");
        };
    }

    private static KeyMaterial parsePrivateKey(String value, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key material must not be blank");
        }
        String trimmed = value.trim();

        if (trimmed.startsWith("{")) {
            try {
                return parseJwk(trimmed, algorithm);
            } catch (IllegalArgumentException | GeneralSecurityException ex) {
                throw wrap("Unable to parse JWK private key", ex);
            }
        }

        try {
            return parsePem(trimmed, algorithm);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw wrap("Unable to parse PEM private key", ex);
        }
    }

    private static KeyMaterial parseJwk(String jwk, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        Map<String, String> fields = extractJsonFields(jwk);
        String kty = require(fields, "kty");
        String algLabel = fields.get("alg");
        if (algLabel != null && !algLabel.isBlank()) {
            WebAuthnSignatureAlgorithm resolved = WebAuthnSignatureAlgorithm.fromLabel(algLabel);
            if (resolved != algorithm) {
                throw new IllegalArgumentException(
                        "JWK algorithm " + resolved.label() + " does not match requested " + algorithm.label());
            }
        }

        return switch (algorithm) {
            case ES256, ES384, ES512 -> parseEcJwk(kty, fields, algorithm);
            case RS256, PS256 -> parseRsaJwk(kty, fields, algorithm);
            case EDDSA -> parseEdDsaJwk(kty, fields, algorithm);
        };
    }

    private static KeyMaterial parsePem(String pem, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        if (pem.contains("EC PRIVATE KEY")) {
            throw new GeneralSecurityException(
                    "SEC1 EC private keys are not supported yet; provide PKCS#8 (BEGIN PRIVATE KEY)");
        }
        if (pem.contains("RSA PRIVATE KEY")) {
            throw new GeneralSecurityException("RSA private keys are not supported yet");
        }

        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] der = Base64.getDecoder().decode(normalized);

        try {
            KeyFactory factory = keyFactoryFor(algorithm);
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(der));
            PublicKey publicKey = derivePublicKey(privateKey, algorithm);
            return new KeyMaterial(
                    privateKey, publicKey, WebAuthnPublicKeyFormats.encodePublicKey(publicKey, algorithm));
        } catch (InvalidKeySpecException ex) {
            throw new GeneralSecurityException("Unsupported PEM key", ex);
        }
    }

    private static KeyMaterial parseEcJwk(String kty, Map<String, String> fields, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        if (!"EC".equalsIgnoreCase(kty)) {
            throw new IllegalArgumentException("Expected EC key type for algorithm " + algorithm.label());
        }

        String curve = require(fields, "crv");
        String expectedCurve = curveForAlgorithm(algorithm);
        if (!expectedCurve.equalsIgnoreCase(curve)) {
            throw new IllegalArgumentException("JWK curve " + curve + " does not match " + expectedCurve);
        }

        byte[] x = decodeUrl(fields, "x");
        byte[] y = decodeUrl(fields, "y");
        byte[] d = decodeUrl(fields, "d");

        ECParameterSpec params = ecParametersFor(expectedCurve);
        KeyFactory factory = KeyFactory.getInstance("EC");
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        ECPublicKeySpec publicSpec = new ECPublicKeySpec(point, params);
        ECPrivateKeySpec privateSpec = new ECPrivateKeySpec(new BigInteger(1, d), params);
        PublicKey publicKey = factory.generatePublic(publicSpec);
        PrivateKey privateKey = factory.generatePrivate(privateSpec);
        return new KeyMaterial(
                privateKey, publicKey, WebAuthnPublicKeyFormats.encodeEcPublicKey((ECPublicKey) publicKey, algorithm));
    }

    private static KeyMaterial parseRsaJwk(String kty, Map<String, String> fields, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        if (!"RSA".equalsIgnoreCase(kty)) {
            throw new IllegalArgumentException("Expected RSA key type for algorithm " + algorithm.label());
        }

        BigInteger modulus = decodeUnsignedBigInteger(fields, "n");
        BigInteger publicExponent = decodeUnsignedBigInteger(fields, "e");
        BigInteger privateExponent = decodeUnsignedBigInteger(fields, "d");

        KeyFactory factory = KeyFactory.getInstance("RSA");

        PrivateKey privateKey;
        if (fields.containsKey("p")
                && fields.containsKey("q")
                && fields.containsKey("dp")
                && fields.containsKey("dq")
                && fields.containsKey("qi")) {
            BigInteger p = decodeUnsignedBigInteger(fields, "p");
            BigInteger q = decodeUnsignedBigInteger(fields, "q");
            BigInteger dp = decodeUnsignedBigInteger(fields, "dp");
            BigInteger dq = decodeUnsignedBigInteger(fields, "dq");
            BigInteger qi = decodeUnsignedBigInteger(fields, "qi");
            privateKey = factory.generatePrivate(
                    new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent, p, q, dp, dq, qi));
        } else {
            privateKey = factory.generatePrivate(new RSAPrivateKeySpec(modulus, privateExponent));
        }

        PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
        byte[] cose = WebAuthnPublicKeyFormats.encodeRsaPublicKey((RSAPublicKey) publicKey, algorithm);
        return new KeyMaterial(privateKey, publicKey, cose);
    }

    private static KeyMaterial parseEdDsaJwk(
            String kty, Map<String, String> fields, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        if (!"OKP".equalsIgnoreCase(kty)) {
            throw new IllegalArgumentException("Expected OKP key type for algorithm " + algorithm.label());
        }
        String curve = require(fields, "crv");
        if (!"Ed25519".equalsIgnoreCase(curve)) {
            throw new IllegalArgumentException("Unsupported OKP curve: " + curve);
        }

        byte[] publicKeyBytes = decodeUrl(fields, "x");
        byte[] privateKeyBytes = decodeUrl(fields, "d");

        byte[] pkcs8 = encodeEd25519PrivateKey(privateKeyBytes);
        byte[] x509 = encodeEd25519PublicKey(publicKeyBytes);

        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(x509));

        byte[] cose = WebAuthnPublicKeyFormats.encodeOkpPublicKey(publicKeyBytes, algorithm);
        return new KeyMaterial(privateKey, publicKey, cose);
    }

    private static PublicKey derivePublicKey(PrivateKey privateKey, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256, ES384, ES512 -> deriveEcPublicKey(privateKey, algorithm);
            case RS256, PS256 -> deriveRsaPublicKey(privateKey);
            case EDDSA ->
                throw new GeneralSecurityException(
                        "Public key derivation not yet supported for algorithm: " + algorithm.label());
        };
    }

    private static PublicKey deriveEcPublicKey(PrivateKey privateKey, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        if (!(privateKey instanceof ECPrivateKey ecPrivateKey)) {
            throw new GeneralSecurityException("Expected EC private key");
        }
        ECParameterSpec params = ecPrivateKey.getParams();
        BigInteger s = ecPrivateKey.getS();
        ECPoint generator = params.getGenerator();
        ECPoint publicPoint = scalarMultiply(generator, s, params);
        KeyFactory factory = KeyFactory.getInstance("EC");
        return factory.generatePublic(new ECPublicKeySpec(publicPoint, params));
    }

    private static PublicKey deriveRsaPublicKey(PrivateKey privateKey) throws GeneralSecurityException {
        if (!(privateKey instanceof RSAPrivateCrtKey rsa)) {
            throw new GeneralSecurityException("Expected RSA CRT private key");
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
    }

    private static byte[] encodeEd25519PrivateKey(byte[] privateKey) throws GeneralSecurityException {
        if (privateKey.length != 32) {
            throw new GeneralSecurityException("Ed25519 private key must be 32 bytes");
        }
        return concat(ED25519_PRIVATE_KEY_PREFIX, privateKey);
    }

    private static byte[] encodeEd25519PublicKey(byte[] publicKey) throws GeneralSecurityException {
        if (publicKey.length != 32) {
            throw new GeneralSecurityException("Ed25519 public key must be 32 bytes");
        }
        return concat(ED25519_PUBLIC_KEY_PREFIX, publicKey);
    }

    private static byte[] concat(byte[] prefix, byte[] value) {
        byte[] data = new byte[prefix.length + value.length];
        System.arraycopy(prefix, 0, data, 0, prefix.length);
        System.arraycopy(value, 0, data, prefix.length, value.length);
        return data;
    }

    private static byte[] hexToBytes(String hex) {
        int length = hex.length();
        if (length % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return bytes;
    }

    private static Map<String, String> extractJsonFields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object");
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        for (String entry : body.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                String key = unquote(parts[0]);
                String value = unquote(parts[1]);
                fields.put(key, value);
            }
        }
        return fields;
    }

    private static String unquote(String token) {
        String trimmed = token.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String require(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing JWK field: " + key);
        }
        return value;
    }

    private static byte[] decodeUrl(Map<String, String> fields, String key) {
        String value = require(fields, key);
        return URL_DECODER.decode(value);
    }

    private static BigInteger decodeUnsignedBigInteger(Map<String, String> fields, String key) {
        return new BigInteger(1, decodeUrl(fields, key));
    }

    private static ECParameterSpec ecParametersFor(String curve) throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        String specName =
                switch (curve.toUpperCase(Locale.US)) {
                    case "P-256", "SECP256R1" -> "secp256r1";
                    case "P-384", "SECP384R1" -> "secp384r1";
                    case "P-521", "SECP521R1" -> "secp521r1";
                    default -> throw new GeneralSecurityException("Unsupported EC curve: " + curve);
                };
        parameters.init(new ECGenParameterSpec(specName));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static String curveForAlgorithm(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "P-256";
            case ES384 -> "P-384";
            case ES512 -> "P-521";
            default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + algorithm);
        };
    }

    private static KeyFactory keyFactoryFor(WebAuthnSignatureAlgorithm algorithm) throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256, ES384, ES512 -> KeyFactory.getInstance("EC");
            default ->
                throw new GeneralSecurityException(
                        "Private key format not yet supported for algorithm: " + algorithm.label());
        };
    }

    private static GeneralSecurityException wrap(String message, Exception ex) {
        if (ex instanceof GeneralSecurityException gse) {
            return gse;
        }
        return new GeneralSecurityException(message + ": " + ex.getMessage(), ex);
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

        EllipticCurve curve = params.getCurve();
        ECField field = curve.getField();
        if (!(field instanceof ECFieldFp primeField)) {
            throw new IllegalArgumentException("Only prime field curves are supported");
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

    public sealed interface GenerationCommand permits GenerationCommand.Inline, GenerationCommand.Stored {

        record Inline(
                String credentialName,
                byte[] credentialId,
                WebAuthnSignatureAlgorithm algorithm,
                String relyingPartyId,
                String origin,
                String expectedType,
                long signatureCounter,
                boolean userVerificationRequired,
                byte[] challenge,
                String privateKey)
                implements GenerationCommand {

            public Inline {
                credentialName = credentialName == null ? "inline" : credentialName.trim();
                credentialId = credentialId == null ? new byte[0] : credentialId.clone();
                algorithm = Objects.requireNonNull(algorithm, "algorithm");
                relyingPartyId =
                        Objects.requireNonNull(relyingPartyId, "relyingPartyId").trim();
                origin = Objects.requireNonNull(origin, "origin").trim();
                expectedType =
                        Objects.requireNonNull(expectedType, "expectedType").trim();
                challenge = challenge == null ? new byte[0] : challenge.clone();
                privateKey = Objects.requireNonNull(privateKey, "privateKey").trim();
            }

            @Override
            public byte[] credentialId() {
                return credentialId.clone();
            }

            @Override
            public byte[] challenge() {
                return challenge.clone();
            }
        }

        record Stored(
                String credentialName,
                String relyingPartyId,
                String origin,
                String expectedType,
                byte[] challenge,
                String privateKey,
                Long signatureCounterOverride,
                Boolean userVerificationRequiredOverride)
                implements GenerationCommand {

            public Stored {
                credentialName =
                        Objects.requireNonNull(credentialName, "credentialName").trim();
                relyingPartyId = relyingPartyId == null ? "" : relyingPartyId.trim();
                origin = Objects.requireNonNull(origin, "origin").trim();
                expectedType =
                        Objects.requireNonNull(expectedType, "expectedType").trim();
                challenge = challenge == null ? new byte[0] : challenge.clone();
                privateKey = Objects.requireNonNull(privateKey, "privateKey").trim();
            }

            public byte[] challenge() {
                return challenge.clone();
            }

            public Optional<Long> signatureCounterOverrideValue() {
                return Optional.ofNullable(signatureCounterOverride);
            }

            public Optional<Boolean> userVerificationRequiredOverrideValue() {
                return Optional.ofNullable(userVerificationRequiredOverride);
            }
        }
    }

    public record GenerationResult(
            String credentialName,
            byte[] credentialId,
            byte[] challenge,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature,
            byte[] publicKeyCose,
            WebAuthnSignatureAlgorithm algorithm,
            long signatureCounter,
            boolean userVerificationRequired,
            boolean credentialReference,
            String relyingPartyId,
            String origin) {

        public GenerationResult {
            credentialName = Objects.requireNonNull(credentialName, "credentialName");
            credentialId = credentialId == null ? new byte[0] : credentialId.clone();
            challenge = challenge == null ? new byte[0] : challenge.clone();
            clientDataJson = clientDataJson == null ? new byte[0] : clientDataJson.clone();
            authenticatorData = authenticatorData == null ? new byte[0] : authenticatorData.clone();
            signature = signature == null ? new byte[0] : signature.clone();
            publicKeyCose = publicKeyCose == null ? new byte[0] : publicKeyCose.clone();
            algorithm = Objects.requireNonNull(algorithm, "algorithm");
            relyingPartyId = Objects.requireNonNull(relyingPartyId, "relyingPartyId");
            origin = Objects.requireNonNull(origin, "origin");
        }
    }

    private record KeyMaterial(PrivateKey privateKey, PublicKey publicKey, byte[] publicKeyCose) {
        // marker record
    }
}
