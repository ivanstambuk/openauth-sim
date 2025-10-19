package io.openauth.sim.application.telemetry;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("telemetry")
final class TotpTelemetryContractTest {

    @Test
    void totpSeedingAdapterProducesSeedFrame() {
        TotpTelemetryAdapter adapter = TelemetryContracts.totpSeedingAdapter();

        TelemetryFrame frame = adapter.status(
                "seeded",
                TelemetryContractTestSupport.telemetryId(),
                "seeded",
                true,
                null,
                TelemetryContractTestSupport.totpSeedFields());

        TelemetryContractTestSupport.assertTotpSeedFrame(frame);
    }

    @Test
    void totpSampleAdapterProducesSampleFrame() {
        TotpTelemetryAdapter adapter = TelemetryContracts.totpSampleAdapter();

        TelemetryFrame frame = adapter.status(
                "sampled",
                TelemetryContractTestSupport.telemetryId(),
                "sampled",
                true,
                null,
                TelemetryContractTestSupport.totpSampleFields());

        TelemetryContractTestSupport.assertTotpSampleFrame(frame);
    }
}
