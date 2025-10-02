package io.openauth.sim.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  @DisplayName("CLI still depends directly on core OCRA internals")
  void cliStillTouchesCoreOcraPackage() {
    JavaClasses imported =
        new ClassFileImporter()
            .importPackages(CLI_PACKAGE, CORE_OCRA_PACKAGE, "io.openauth.sim.core");

    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(CLI_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(CORE_OCRA_PACKAGE + "..")
            .because(
                "CLI should delegate via the shared application layer instead of core internals");

    EvaluationResult result = rule.evaluate(imported);
    assertTrue(
        result.hasViolation(),
        () ->
            "CLI currently reaches into %s; add application layer seams"
                .formatted(CORE_OCRA_PACKAGE));
  }

  @Test
  @DisplayName("CLI still instantiates MapDbCredentialStore directly")
  void cliStillCreatesMapDbStores() {
    JavaClasses imported =
        new ClassFileImporter().importPackages(CLI_PACKAGE, "io.openauth.sim.core");

    ArchRule rule =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(CLI_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(MapDbCredentialStore.class)
            .because("CredentialStoreFactory will own MapDB instantiation");

    EvaluationResult result = rule.evaluate(imported);
    assertTrue(
        result.hasViolation(),
        () -> "CLI still calls MapDbCredentialStore directly; migrate to CredentialStoreFactory");
  }

  @Test
  @DisplayName("REST OCRA services still construct domain primitives directly")
  void restServicesStillUseCoreFactories() {
    JavaClasses imported =
        new ClassFileImporter()
            .importPackages(
                REST_PACKAGE, UI_PACKAGE, CORE_OCRA_PACKAGE, "io.openauth.sim.core.store");

    EvaluationResult factoryResult =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(REST_PACKAGE + ".ocra..")
            .or()
            .resideInAPackage(UI_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(OcraCredentialFactory.class)
            .because("Application layer should expose credential creation seams")
            .evaluate(imported);
    EvaluationResult calculatorResult =
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(REST_PACKAGE + ".ocra..")
            .or()
            .resideInAPackage(UI_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(OcraResponseCalculator.class)
            .because("OTP execution will move into the shared application layer")
            .evaluate(imported);
    EvaluationResult verifierResult =
        ArchRuleDefinition.noClasses()
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
        () ->
            "REST/UI still instantiate %s; update services to delegate via application layer"
                .formatted(OcraCredentialFactory.class));
    assertFalse(
        calculatorResult.hasViolation(),
        () ->
            "REST/UI still call %s directly; ensure application layer owns OTP execution"
                .formatted(OcraResponseCalculator.class));
    assertFalse(
        verifierResult.hasViolation(),
        () ->
            "REST/UI still use %s directly; ensure verification delegates to application layer"
                .formatted(OcraReplayVerifier.class));
  }
}
