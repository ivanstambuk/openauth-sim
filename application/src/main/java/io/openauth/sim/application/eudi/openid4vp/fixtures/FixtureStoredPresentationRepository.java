package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpValidationService;
import io.openauth.sim.application.eudi.openid4vp.OpenId4VpWalletSimulationService;
import io.openauth.sim.core.json.SimpleJson;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FixtureStoredPresentationRepository
        implements OpenId4VpValidationService.StoredPresentationRepository {

    private final Map<String, OpenId4VpValidationService.StoredPresentation> presentations;

    public FixtureStoredPresentationRepository() {
        this.presentations = Map.of("pid-haip-baseline", loadPidHaipBaseline());
    }

    @Override
    public OpenId4VpValidationService.StoredPresentation load(String presentationId) {
        OpenId4VpValidationService.StoredPresentation presentation = presentations.get(presentationId);
        if (presentation == null) {
            throw new IllegalArgumentException("Unknown stored presentation " + presentationId);
        }
        return presentation;
    }

    private static OpenId4VpValidationService.StoredPresentation loadPidHaipBaseline() {
        Path storedFile = FixturePaths.resolve(
                "docs", "test-vectors", "eudiw", "openid4vp", "stored", "presentations", "pid-haip-baseline.json");
        Map<String, Object> root =
                readJson("docs/test-vectors/eudiw/openid4vp/stored/presentations/pid-haip-baseline.json", storedFile);
        String credentialId = stringValue(root, "credentialId");
        String format = stringValue(root, "format");
        String profileValue = stringValue(root, "profile");
        String source = stringValue(root, "source");
        String normalizedSource = source.endsWith("/") ? source.substring(0, source.length() - 1) : source;
        List<String> policies = readStringList(root, "trustedAuthorities");
        Path sourceDir = FixturePaths.resolve("docs", "test-vectors", "eudiw", "openid4vp", normalizedSource);
        String classpathPrefix = "docs/test-vectors/eudiw/openid4vp/" + normalizedSource.replace("\\", "/");
        String compactSdJwt = FixturePaths.readUtf8(classpathPrefix + "/sdjwt.txt", sourceDir.resolve("sdjwt.txt"))
                .strip();
        List<String> disclosures = FixtureWalletPresetRepository.readDisclosures(
                classpathPrefix + "/disclosures.json", sourceDir.resolve("disclosures.json"));
        List<String> digestValues = FixtureWalletPresetRepository.readDigestValues(
                classpathPrefix + "/digests.json", sourceDir.resolve("digests.json"));
        String kbJwt = FixturePaths.readUtf8(classpathPrefix + "/kb-jwt.json", sourceDir.resolve("kb-jwt.json"));
        Map<String, Object> vpToken = buildVpTokenMap(compactSdJwt, credentialId, format);
        Optional<String> dcqlJson = Optional.ofNullable(readDcql());
        return new OpenId4VpValidationService.StoredPresentation(
                "pid-haip-baseline",
                credentialId,
                format,
                OpenId4VpWalletSimulationService.Profile.valueOf(profileValue),
                OpenId4VpWalletSimulationService.ResponseMode.DIRECT_POST_JWT,
                vpToken,
                Optional.of(kbJwt),
                disclosures,
                policies,
                dcqlJson);
    }

    private static Map<String, Object> buildVpTokenMap(String compactSdJwt, String credentialId, String format) {
        Map<String, Object> submissionEntry = new LinkedHashMap<>();
        submissionEntry.put("id", credentialId);
        submissionEntry.put("format", format);
        submissionEntry.put("path", "$.vp_token");
        Map<String, Object> presentationSubmission = new LinkedHashMap<>();
        presentationSubmission.put("descriptor_map", List.of(Collections.unmodifiableMap(submissionEntry)));
        Map<String, Object> vpToken = new LinkedHashMap<>();
        vpToken.put("vp_token", compactSdJwt);
        vpToken.put("presentation_submission", presentationSubmission);
        return Collections.unmodifiableMap(vpToken);
    }

    private static List<String> readStringList(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(Object::toString).toList();
    }

    private static String readDcql() {
        String classpath = "docs/test-vectors/eudiw/openid4vp/fixtures/dcql/pid-haip-baseline.json";
        Path path = FixturePaths.resolve(
                "docs", "test-vectors", "eudiw", "openid4vp", "fixtures", "dcql", "pid-haip-baseline.json");
        return FixturePaths.readUtf8(classpath, path);
    }

    private static Map<String, Object> readJson(String classpathPath, Path path) {
        Object parsed = SimpleJson.parse(FixturePaths.readUtf8(classpathPath, path));
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Stored presentation " + path + " must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalStateException("Stored presentation missing string '" + key + "'");
        }
        return string;
    }
}
