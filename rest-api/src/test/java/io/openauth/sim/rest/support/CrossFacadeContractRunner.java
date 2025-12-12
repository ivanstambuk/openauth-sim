package io.openauth.sim.rest.support;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.CanonicalScenarios;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.ScenarioStores;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;

public final class CrossFacadeContractRunner {

    private CrossFacadeContractRunner() {
        throw new AssertionError("No instances");
    }

    @FunctionalInterface
    public interface ScenarioProvider {
        List<CanonicalScenario> scenarios(ScenarioEnvironment env);
    }

    @FunctionalInterface
    public interface FacadeExecutor {
        CanonicalFacadeResult execute(ScenarioEnvironment env, CanonicalScenario scenario) throws Exception;
    }

    @FunctionalInterface
    public interface CliExecutor {
        Optional<CanonicalFacadeResult> execute(
                CliContext ctx, CanonicalScenario descriptor, CanonicalScenario scenario) throws Exception;
    }

    @FunctionalInterface
    public interface CliAssertions {
        void assertParity(CanonicalFacadeResult expected, CanonicalFacadeResult actual, String message);
    }

    public record CliContext(Path tempDir, Instant instant, ScenarioProvider scenarioProvider) {
        public CliContext {
            Objects.requireNonNull(tempDir, "tempDir");
            Objects.requireNonNull(instant, "instant");
            Objects.requireNonNull(scenarioProvider, "scenarioProvider");
        }

        public Path seededFileStore(String scenarioId) throws IOException {
            Objects.requireNonNull(scenarioId, "scenarioId");
            Path dbPath = tempDir.resolve(scenarioId + ".db");
            ScenarioStores.seedFileStore(dbPath, instant, scenarioProvider::scenarios);
            return dbPath;
        }
    }

    public static void assertParity(
            Instant instant, ScenarioProvider scenarios, FacadeExecutor nativeExecutor, FacadeExecutor restExecutor)
            throws Exception {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(scenarios, "scenarios");
        Objects.requireNonNull(nativeExecutor, "nativeExecutor");
        Objects.requireNonNull(restExecutor, "restExecutor");

        List<CanonicalScenario> descriptors = scenarios.scenarios(ScenarioEnvironment.fixedAt(instant));
        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(instant);
            CanonicalScenario nativeScenario =
                    CanonicalScenarios.scenarioForDescriptor(nativeEnv, descriptor, scenarios::scenarios);
            CanonicalFacadeResult nativeResult = nativeExecutor.execute(nativeEnv, nativeScenario);
            Assertions.assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(instant);
            CanonicalScenario restScenario =
                    CanonicalScenarios.scenarioForDescriptor(restEnv, descriptor, scenarios::scenarios);
            CanonicalFacadeResult restResult = restExecutor.execute(restEnv, restScenario);
            Assertions.assertEquals(expected, restResult, descriptor.scenarioId() + " rest");
        }
    }

    public static void assertParity(
            Instant instant,
            ScenarioProvider scenarios,
            FacadeExecutor nativeExecutor,
            FacadeExecutor restExecutor,
            CliContext cliContext,
            CliExecutor cliExecutor,
            CliAssertions cliAssertions)
            throws Exception {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(scenarios, "scenarios");
        Objects.requireNonNull(nativeExecutor, "nativeExecutor");
        Objects.requireNonNull(restExecutor, "restExecutor");
        Objects.requireNonNull(cliContext, "cliContext");
        Objects.requireNonNull(cliExecutor, "cliExecutor");
        Objects.requireNonNull(cliAssertions, "cliAssertions");

        List<CanonicalScenario> descriptors = scenarios.scenarios(ScenarioEnvironment.fixedAt(instant));
        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(instant);
            CanonicalScenario nativeScenario =
                    CanonicalScenarios.scenarioForDescriptor(nativeEnv, descriptor, scenarios::scenarios);
            CanonicalFacadeResult nativeResult = nativeExecutor.execute(nativeEnv, nativeScenario);
            Assertions.assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(instant);
            CanonicalScenario restScenario =
                    CanonicalScenarios.scenarioForDescriptor(restEnv, descriptor, scenarios::scenarios);
            CanonicalFacadeResult restResult = restExecutor.execute(restEnv, restScenario);
            Assertions.assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            Optional<CanonicalFacadeResult> cliResult = cliExecutor.execute(cliContext, descriptor, restScenario);
            if (cliResult.isPresent()) {
                cliAssertions.assertParity(expected, cliResult.orElseThrow(), descriptor.scenarioId() + " cli");
            }
        }
    }
}
