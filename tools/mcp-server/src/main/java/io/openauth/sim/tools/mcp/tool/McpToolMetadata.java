package io.openauth.sim.tools.mcp.tool;

import java.util.List;

/** Static metadata for MCP tools (versioning and prompt hints). */
final class McpToolMetadata {

    private static final String CATALOG_VERSION = "0.1.0";

    private McpToolMetadata() {
        throw new AssertionError("No instances");
    }

    static String catalogVersion() {
        return CATALOG_VERSION;
    }

    static List<String> promptHints(String toolName) {
        return switch (toolName) {
            case "hotp.evaluate" ->
                List.of(
                        "Use for RFC 4226 HOTP flows with stored or inline credentials.",
                        "Pass the JSON body you would send to /api/v1/hotp/evaluate as the payload.");
            case "totp.evaluate" ->
                List.of(
                        "Use for RFC 6238 TOTP flows with stored or inline credentials.",
                        "Prefer totp.helper.currentOtp when you only need the current OTP for a stored credential.");
            case "totp.helper.currentOtp" ->
                List.of(
                        "Use to peek at the current OTP and metadata for a stored TOTP credential.",
                        "Requires a stored TOTP credential identifier; secrets remain redacted.");
            case "ocra.evaluate" ->
                List.of(
                        "Use for OCRA challenge–response OTP flows using stored presets or inline suites.",
                        "Pass the JSON body you would send to /api/v1/ocra/evaluate as the payload.");
            case "emv.cap.evaluate" ->
                List.of(
                        "Use for EMV/CAP Identify/Respond/Sign simulations with stored card presets or inline payloads.",
                        "Pass the JSON body you would send to /api/v1/emv/cap/evaluate as the payload.");
            case "fido2.assertion.evaluate" ->
                List.of(
                        "Use for WebAuthn assertion generation against stored or inline credentials.",
                        "Pass the JSON body you would send to /api/v1/webauthn/evaluate as the payload.");
            case "eudiw.wallet.simulate" ->
                List.of(
                        "Use to simulate wallet-side OpenID4VP responses (SD-JWT VC and mdoc) for HAIP/Baseline profiles.",
                        "Pass the JSON body you would send to /api/v1/eudiw/openid4vp/wallet/simulate as the payload.");
            case "eudiw.presentation.validate" ->
                List.of(
                        "Use to validate OpenID4VP presentations against Trusted Authority fixtures and HAIP/Baseline profiles.",
                        "Pass the JSON body you would send to /api/v1/eudiw/openid4vp/validate as the payload.");
            case "fixtures.list" ->
                List.of(
                        "Use to discover stored credential presets (fixtures) for a protocol.",
                        "Set protocol to hotp, totp, ocra, emv, or fido2 to mirror the REST credentials endpoints.");
            default -> List.of();
        };
    }
}
