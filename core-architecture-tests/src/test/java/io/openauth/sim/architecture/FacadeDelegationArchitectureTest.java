package io.openauth.sim.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.store.MapDbCredentialStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class FacadeDelegationArchitectureTest {

    private static final String CLI_PACKAGE = "io.openauth.sim.cli";
    private static final String REST_PACKAGE = "io.openauth.sim.rest";
    private static final String UI_PACKAGE = "io.openauth.sim.rest.ui";
    private static final String CORE_OCRA_PACKAGE = "io.openauth.sim.core.credentials.ocra";
    private static final String APPLICATION_OCRA_PACKAGE = "io.openauth.sim.application.ocra";
    private static final String REST_EVALUATION_SERVICE = "io.openauth.sim.rest.ocra.OcraEvaluationService";
    private static final String REST_VERIFICATION_SERVICE = "io.openauth.sim.rest.ocra.OcraVerificationService";

    @Test
    @DisplayName("CLI still depends directly on core OCRA internals")
    void cliStillTouchesCoreOcraPackage() {
        JavaClasses imported =
                new ClassFileImporter().importPackages(CLI_PACKAGE, CORE_OCRA_PACKAGE, "io.openauth.sim.core");

        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(CLI_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(CORE_OCRA_PACKAGE + "..")
                .because("CLI should delegate via the shared application layer instead of core internals");

        EvaluationResult result = rule.evaluate(imported);
        assertTrue(result.hasViolation(), () -> "CLI currently reaches into %s; add application layer seams"
                .formatted(CORE_OCRA_PACKAGE));
    }

    @Test
    @DisplayName("CLI delegates MapDbCredentialStore creation to the factory")
    void cliUsesCredentialStoreFactory() {
        JavaClasses imported = new ClassFileImporter().importPackages(CLI_PACKAGE, "io.openauth.sim.core");

        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(CLI_PACKAGE + "..")
                .and()
                .haveSimpleNameNotContaining("Maintenance")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(MapDbCredentialStore.class)
                .because("CredentialStoreFactory will own MapDB instantiation");

        EvaluationResult result = rule.evaluate(imported);
        assertFalse(
                result.hasViolation(),
                () -> "CLI should rely on CredentialStoreFactory for MapDbCredentialStore access");
    }

    @Test
    @DisplayName("REST services depend on application-layer OCRA services")
    void restServicesDelegateThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter().importPackages(REST_PACKAGE, APPLICATION_OCRA_PACKAGE);

        assertTrue(
                dependsOnApplicationLayer(imported.get(REST_EVALUATION_SERVICE)),
                () -> "OcraEvaluationService should depend on application-layer services to avoid"
                        + " direct core coupling");
        assertTrue(
                dependsOnApplicationLayer(imported.get(REST_VERIFICATION_SERVICE)),
                () -> "OcraVerificationService should depend on application-layer services to avoid"
                        + " direct core coupling");
    }

    @Test
    @DisplayName("REST OCRA services still construct domain primitives directly")
    void restServicesStillUseCoreFactories() {
        JavaClasses imported = new ClassFileImporter()
                .importPackages(REST_PACKAGE, UI_PACKAGE, CORE_OCRA_PACKAGE, "io.openauth.sim.core.store");

        EvaluationResult factoryResult = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(REST_PACKAGE + ".ocra..")
                .or()
                .resideInAPackage(UI_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(OcraCredentialFactory.class)
                .because("Application layer should expose credential creation seams")
                .evaluate(imported);
        EvaluationResult calculatorResult = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(REST_PACKAGE + ".ocra..")
                .or()
                .resideInAPackage(UI_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(OcraResponseCalculator.class)
                .because("OTP execution will move into the shared application layer")
                .evaluate(imported);
        EvaluationResult verifierResult = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(REST_PACKAGE + ".ocra..")
                .or()
                .resideInAPackage(UI_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(OcraReplayVerifier.class)
                .because("Verification orchestration will be shared as well")
                .evaluate(imported);

        assertFalse(
                factoryResult.hasViolation(),
                () -> "REST/UI still instantiate %s; update services to delegate via application layer"
                        .formatted(OcraCredentialFactory.class));
        assertFalse(
                calculatorResult.hasViolation(),
                () -> "REST/UI still call %s directly; ensure application layer owns OTP execution"
                        .formatted(OcraResponseCalculator.class));
        assertFalse(
                verifierResult.hasViolation(),
                () -> "REST/UI still use %s directly; ensure verification delegates to application layer"
                        .formatted(OcraReplayVerifier.class));
    }

    private static boolean dependsOnApplicationLayer(JavaClass javaClass) {
        return javaClass.getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getPackageName())
                .anyMatch(packageName -> packageName.startsWith(APPLICATION_OCRA_PACKAGE));
    }
}
