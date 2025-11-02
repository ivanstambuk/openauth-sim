package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
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
}
