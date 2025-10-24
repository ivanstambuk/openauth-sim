package io.openauth.sim.core.fido2;

import java.net.IDN;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility for normalising WebAuthn relying party identifiers.
 *
 * <p>Canonicalisation trims surrounding whitespace, converts the value to its ASCII form using IDNA,
 * and lower-cases the result so downstream hash comparisons operate on a deterministic string.</p>
 */
public final class WebAuthnRelyingPartyId {

    private WebAuthnRelyingPartyId() {
        throw new AssertionError("No instances");
    }

    /**
     * Canonicalises the supplied relying party identifier.
     *
     * @param relyingPartyId raw identifier provided by an operator or fixture
     * @return trimmed, IDNA-to-ASCII, lower-case representation
     * @throws NullPointerException if {@code relyingPartyId} is {@code null}
     * @throws IllegalArgumentException if the identifier is blank or cannot be converted to ASCII
     */
    public static String canonicalize(String relyingPartyId) {
        Objects.requireNonNull(relyingPartyId, "relyingPartyId");
        String trimmed = relyingPartyId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Relying party identifier must not be blank");
        }
        String ascii = IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES);
        if (ascii.isEmpty()) {
            throw new IllegalArgumentException("Relying party identifier must not be blank");
        }
        return ascii.toLowerCase(Locale.ROOT);
    }
}
