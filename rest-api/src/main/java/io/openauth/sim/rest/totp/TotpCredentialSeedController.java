package io.openauth.sim.rest.totp;

import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/totp", produces = MediaType.APPLICATION_JSON_VALUE)
final class TotpCredentialSeedController {

    private final TotpCredentialSeedService seedService;

    TotpCredentialSeedController(TotpCredentialSeedService seedService) {
        this.seedService = Objects.requireNonNull(seedService, "seedService");
    }

    @PostMapping(value = "/credentials/seed", produces = MediaType.APPLICATION_JSON_VALUE)
    SeedResponse seedCredentials() {
        TotpCredentialSeedService.SeedResult result = seedService.seedCanonicalCredentials();
        return new SeedResponse(result.addedCount(), result.canonicalCount(), result.addedCredentialIds());
    }

    static final class SeedResponse {
        private final int addedCount;
        private final int canonicalCount;
        private final List<String> addedCredentialIds;

        SeedResponse(int addedCount, int canonicalCount, List<String> addedCredentialIds) {
            this.addedCount = addedCount;
            this.canonicalCount = canonicalCount;
            this.addedCredentialIds = List.copyOf(addedCredentialIds);
        }

        public int getAddedCount() {
            return addedCount;
        }

        public int getCanonicalCount() {
            return canonicalCount;
        }

        public List<String> getAddedCredentialIds() {
            return addedCredentialIds;
        }
    }
}
