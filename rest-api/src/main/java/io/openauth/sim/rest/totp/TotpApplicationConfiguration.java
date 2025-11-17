package io.openauth.sim.rest.totp;

import io.openauth.sim.application.totp.TotpCurrentOtpHelperService;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.application.totp.TotpSampleApplicationService;
import io.openauth.sim.application.totp.TotpSeedApplicationService;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TotpApplicationConfiguration {

    @Bean
    TotpEvaluationApplicationService totpEvaluationApplicationService(CredentialStore credentialStore) {
        return new TotpEvaluationApplicationService(credentialStore);
    }

    @Bean
    TotpReplayApplicationService totpReplayApplicationService(
            TotpEvaluationApplicationService evaluationApplicationService) {
        return new TotpReplayApplicationService(evaluationApplicationService);
    }

    @Bean
    TotpSeedApplicationService totpSeedApplicationService() {
        return new TotpSeedApplicationService();
    }

    @Bean
    TotpSampleApplicationService totpSampleApplicationService(CredentialStore credentialStore) {
        return new TotpSampleApplicationService(credentialStore);
    }

    @Bean
    TotpCurrentOtpHelperService totpCurrentOtpHelperService(
            TotpEvaluationApplicationService evaluationApplicationService, ObjectProvider<Clock> clockProvider) {
        Clock clock = Optional.ofNullable(clockProvider.getIfAvailable()).orElse(Clock.systemUTC());
        return new TotpCurrentOtpHelperService(evaluationApplicationService, clock);
    }
}
