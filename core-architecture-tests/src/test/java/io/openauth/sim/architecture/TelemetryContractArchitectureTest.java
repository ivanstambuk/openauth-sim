package io.openauth.sim.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class TelemetryContractArchitectureTest {

    private static final String CLI_PACKAGE = "io.openauth.sim.cli";
    private static final String REST_PACKAGE = "io.openauth.sim.rest";
    private static final String UI_PACKAGE = "io.openauth.sim.rest.ui";
    private static final String APPLICATION_TELEMETRY_PACKAGE = "io.openauth.sim.application.telemetry";

    @Test
    @DisplayName("Facade telemetry implementations move to the shared adapter")
    void facadeTelemetryClassesAreRelocated() {
        JavaClasses imported = new ClassFileImporter().importPackages(CLI_PACKAGE, REST_PACKAGE, UI_PACKAGE);

        ArchRule rule = ArchRuleDefinition.classes()
                .that()
                .resideInAnyPackage(CLI_PACKAGE + "..", REST_PACKAGE + "..", UI_PACKAGE + "..")
                .should()
                .haveSimpleNameNotEndingWith("Telemetry")
                .because("Telemetry implementations belong to the shared application adapter");

        EvaluationResult result = rule.evaluate(imported);
        assertFalse(
                result.hasViolation(),
                () -> "Facade packages still declare telemetry classes: " + result.getFailureReport());
    }

    @Test
    @DisplayName("Facade telemetry depends on shared application telemetry adapter")
    void facadeTelemetryDependsOnApplicationAdapter() {
        JavaClasses imported = new ClassFileImporter()
                .importPackages(CLI_PACKAGE, REST_PACKAGE, UI_PACKAGE, APPLICATION_TELEMETRY_PACKAGE);

        ArchRule rule = ArchRuleDefinition.classes()
                .that()
                .resideInAnyPackage(CLI_PACKAGE + "..", REST_PACKAGE + "..", UI_PACKAGE + "..")
                .and()
                .haveSimpleNameContaining("Telemetry")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(APPLICATION_TELEMETRY_PACKAGE + "..")
                .because("Telemetry must flow through the shared application adapter");

        EvaluationResult result = rule.allowEmptyShould(true).evaluate(imported);
        assertFalse(
                result.hasViolation(),
                () -> "Facade telemetry classes should depend on "
                        + APPLICATION_TELEMETRY_PACKAGE
                        + ": "
                        + result.getFailureReport());
    }
}
