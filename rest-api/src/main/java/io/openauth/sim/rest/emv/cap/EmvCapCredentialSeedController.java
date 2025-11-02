package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.rest.emv.cap.EmvCapCredentialSeedService.SeedOperationResult;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/emv/cap", produces = MediaType.APPLICATION_JSON_VALUE)
final class EmvCapCredentialSeedController {

    private final EmvCapCredentialSeedService seedService;

    EmvCapCredentialSeedController(EmvCapCredentialSeedService seedService) {
        this.seedService = Objects.requireNonNull(seedService, "seedService");
    }

    @PostMapping("/credentials/seed")
    SeedResponse seedCredentials() {
        SeedOperationResult result = seedService.seedCanonicalCredentials();
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
