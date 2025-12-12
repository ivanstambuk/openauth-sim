package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapCredentialDirectoryApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EmvCapApplicationConfiguration {

    @Bean
    EmvCapEvaluationApplicationService emvCapEvaluationApplicationService() {
        return new EmvCapEvaluationApplicationService();
    }

    @Bean
    EmvCapSeedApplicationService emvCapSeedApplicationService() {
        return new EmvCapSeedApplicationService();
    }

    @Bean
    EmvCapCredentialDirectoryApplicationService emvCapCredentialDirectoryApplicationService(
            CredentialStore credentialStore) {
        return new EmvCapCredentialDirectoryApplicationService(credentialStore);
    }

    @Bean
    EmvCapReplayApplicationService emvCapReplayApplicationService(
            CredentialStore credentialStore, EmvCapEvaluationApplicationService evaluationService) {
        return new EmvCapReplayApplicationService(credentialStore, evaluationService);
    }
}
