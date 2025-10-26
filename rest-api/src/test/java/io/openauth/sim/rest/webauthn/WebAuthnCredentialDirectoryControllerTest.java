package io.openauth.sim.rest.webauthn;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

final class WebAuthnCredentialDirectoryControllerTest {

    @Test
    @DisplayName("sorts stored credentials by algorithm order, label, then identifier")
    void listCredentialsSortsByAlgorithmThenLabel() {
        Credential es256Alpha = credential("alpha-es256", "ES256", "Alpha ES256");
        Credential es256Bravo = credential("bravo-es256", "ES256", "Bravo ES256");
        Credential es384 = credential("c-es384", "ES384", "Gamma ES384");
        Credential es512 = credential("d-es512", "ES512", "Delta ES512");
        Credential rs256 = credential("e-rs256", "RS256", "Epsilon RS256");
        Credential ps256 = credential("f-ps256", "PS256", "Zeta PS256");
        Credential eddsa = credential("g-eddsa", "EdDSA", "Eta EdDSA");
        Credential unknownAlgorithm = credential("h-unknown", "unknown", "Unknown Algorithm");
        Credential blankAlgorithm = credential("i-blank", " ", "Blank Algorithm");
        Credential otherType = credential("not-fido2", "ES256", "Irrelevant", CredentialType.OATH_TOTP);

        List<Credential> storedCredentials = List.of(
                rs256, blankAlgorithm, es512, es384, es256Bravo, unknownAlgorithm, es256Alpha, otherType, ps256, eddsa);
        FixedCredentialStore store = new FixedCredentialStore(storedCredentials);
        WebAuthnCredentialDirectoryController controller = controller(provider(store));

        List<WebAuthnCredentialDirectoryController.WebAuthnCredentialSummary> summaries = controller.listCredentials();

        assertThat(summaries)
                .extracting(WebAuthnCredentialDirectoryController.WebAuthnCredentialSummary::id)
                .containsExactly(
                        "alpha-es256",
                        "bravo-es256",
                        "c-es384",
                        "d-es512",
                        "e-rs256",
                        "f-ps256",
                        "g-eddsa",
                        "i-blank",
                        "h-unknown");
        assertThat(summaries)
                .extracting(WebAuthnCredentialDirectoryController.WebAuthnCredentialSummary::algorithm)
                .containsExactly("ES256", "ES256", "ES384", "ES512", "RS256", "PS256", "EdDSA", " ", "unknown");
    }

    private WebAuthnCredentialDirectoryController controller(ObjectProvider<CredentialStore> provider) {
        return new WebAuthnCredentialDirectoryController(provider);
    }

    private Credential credential(String id, String algorithm, String label) {
        return credential(id, algorithm, label, CredentialType.FIDO2);
    }

    private Credential credential(String id, String algorithm, String label, CredentialType type) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("fido2.rpId", "example.org");
        attributes.put("fido2.algorithm", algorithm);
        attributes.put("fido2.metadata.label", label);
        attributes.put("fido2.userVerificationRequired", "false");
        return new Credential(
                id,
                type,
                SecretMaterial.fromHex("31323334353637383930313233343536"),
                attributes,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"));
    }

    private ObjectProvider<CredentialStore> provider(CredentialStore store) {
        return new ObjectProvider<>() {
            @Override
            public CredentialStore getObject(Object... args) {
                return store;
            }

            @Override
            public CredentialStore getObject() {
                return store;
            }

            @Override
            public CredentialStore getIfAvailable() {
                return store;
            }

            @Override
            public CredentialStore getIfUnique() {
                return store;
            }

            @Override
            public java.util.stream.Stream<CredentialStore> stream() {
                if (store == null) {
                    return java.util.stream.Stream.empty();
                }
                return java.util.stream.Stream.of(store);
            }

            @Override
            public java.util.stream.Stream<CredentialStore> orderedStream() {
                return stream();
            }
        };
    }

    private static final class FixedCredentialStore implements CredentialStore {
        private final List<Credential> credentials;

        private FixedCredentialStore(List<Credential> credentials) {
            this.credentials = new ArrayList<>(credentials);
        }

        @Override
        public void save(Credential credential) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Credential> findAll() {
            return List.copyOf(credentials);
        }

        @Override
        public java.util.Optional<Credential> findByName(String name) {
            return credentials.stream()
                    .filter(credential -> credential.name().equals(name))
                    .findFirst();
        }

        @Override
        public boolean delete(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
