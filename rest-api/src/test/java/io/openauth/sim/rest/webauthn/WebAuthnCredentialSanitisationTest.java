package io.openauth.sim.rest.webauthn;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class WebAuthnCredentialSanitisationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Stored sample endpoint redacts credential private keys")
    void storedSampleRedactsCredentialPrivateKeys() throws Exception {
        mockMvc.perform(get("/api/v1/webauthn/credentials/packed-es256/sample").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateKeyJwk").doesNotExist())
                .andExpect(jsonPath("$.privateKeyPlaceholder").value("[stored-server-side]"))
                .andExpect(jsonPath("$.signingKeyHandle").value(matchesPattern("^[0-9a-f]{12}$")));
    }
}
