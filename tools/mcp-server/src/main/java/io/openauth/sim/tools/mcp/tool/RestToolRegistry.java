package io.openauth.sim.tools.mcp.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RestToolRegistry {
    private final Map<String, RestToolDefinition> definitions;

    private RestToolRegistry(List<RestToolDefinition> definitions) {
        Map<String, RestToolDefinition> ordered = new LinkedHashMap<>();
        definitions.forEach(def -> ordered.put(def.name(), def));
        this.definitions = Map.copyOf(ordered);
    }

    public static RestToolRegistry defaultRegistry() {
        List<RestToolDefinition> defs = List.of(
                new PayloadForwardToolDefinition(
                        "hotp.evaluate",
                        "Evaluate HOTP credentials (stored or inline)",
                        "POST",
                        "/api/v1/hotp/evaluate"),
                new PayloadForwardToolDefinition(
                        "totp.evaluate",
                        "Evaluate TOTP credentials (stored or inline)",
                        "POST",
                        "/api/v1/totp/evaluate"),
                new PayloadForwardToolDefinition(
                        "totp.helper.currentOtp",
                        "Fetch the current OTP for a stored TOTP credential",
                        "POST",
                        "/api/v1/totp/helper/current"),
                new PayloadForwardToolDefinition(
                        "ocra.evaluate",
                        "Evaluate OCRA requests using stored credentials or inline secrets",
                        "POST",
                        "/api/v1/ocra/evaluate"),
                new PayloadForwardToolDefinition(
                        "emv.cap.evaluate", "Evaluate EMV/CAP payloads", "POST", "/api/v1/emv/cap/evaluate"),
                new PayloadForwardToolDefinition(
                        "fido2.assertion.evaluate",
                        "Generate WebAuthn assertions (stored or inline)",
                        "POST",
                        "/api/v1/webauthn/evaluate"),
                new PayloadForwardToolDefinition(
                        "eudiw.wallet.simulate",
                        "Simulate an OpenID4VP wallet response",
                        "POST",
                        "/api/v1/eudiw/openid4vp/wallet/simulate"),
                new PayloadForwardToolDefinition(
                        "eudiw.presentation.validate",
                        "Validate OpenID4VP presentations",
                        "POST",
                        "/api/v1/eudiw/openid4vp/validate"),
                new FixturesListToolDefinition(Map.of(
                        "hotp", "/api/v1/hotp/credentials",
                        "totp", "/api/v1/totp/credentials",
                        "ocra", "/api/v1/ocra/credentials",
                        "emv", "/api/v1/emv/cap/credentials",
                        "fido2", "/api/v1/webauthn/credentials")));
        return new RestToolRegistry(defs);
    }

    public List<RestToolDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<RestToolDefinition> findByName(String name) {
        return Optional.ofNullable(definitions.get(name));
    }
}
