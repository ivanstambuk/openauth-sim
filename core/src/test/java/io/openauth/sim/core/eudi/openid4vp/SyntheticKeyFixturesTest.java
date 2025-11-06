package io.openauth.sim.core.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SyntheticKeyFixturesTest {

    @Test
    void loadIssuerKey() {
        SyntheticKeyFixtures.Jwk jwk = SyntheticKeyFixtures.loadIssuerKey("pid-haip-baseline");

        assertEquals("pid-haip-baseline-issuer", jwk.kid());
        assertEquals("EC", jwk.kty());
        assertEquals("ES256", jwk.alg());
        assertEquals("P-256", jwk.crv());
    }

    @Test
    void loadHolderKey() {
        SyntheticKeyFixtures.Jwk jwk = SyntheticKeyFixtures.loadHolderKey("pid-haip-baseline");

        assertEquals("pid-haip-baseline-holder", jwk.kid());
        assertEquals("EC", jwk.kty());
        assertEquals("ES256", jwk.alg());
        assertEquals("P-256", jwk.crv());
    }

    @Test
    void unknownKeyThrows() {
        assertThrows(IllegalStateException.class, () -> SyntheticKeyFixtures.loadIssuerKey("does-not-exist"));
    }
}
