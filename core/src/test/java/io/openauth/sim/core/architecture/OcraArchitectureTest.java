package io.openauth.sim.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Phase 1/T006: ArchUnit guardrails for the OCRA credential package.
 *
 * <p>Disabled until the descriptors and public API surface land (Phase 2). Remove {@link Disabled}
 * once Tasks T007–T010 establish the package structure so these rules can enforce boundary
 * integrity.
 */
@Tag("architecture")
@Disabled("Pending Phase 2 OCRA package descriptors")
final class OcraArchitectureTest {

  private static final String BASE_PACKAGE = "io.openauth.sim.core";
  private static final String OCRA_PACKAGE = BASE_PACKAGE + ".credentials.ocra";

  @Test
  @DisplayName("Only core credential registry may depend on OCRA internals")
  void onlyRegistryTouchesOcraPackage() {
    JavaClasses imported = new ClassFileImporter().importPackages(BASE_PACKAGE);

    ArchRuleDefinition.classes()
        .that()
        .resideOutsideOfPackage(OCRA_PACKAGE)
        .should()
        .onlyHaveDependentClassesThat()
        .resideInAnyPackage(BASE_PACKAGE + ".credentials", BASE_PACKAGE + ".credentials.registry")
        .check(imported);
  }

  @Test
  @DisplayName("OCRA package does not depend on other protocol packages")
  void ocraPackageDoesNotDependOnOtherProtocolPackages() {
    JavaClasses imported = new ClassFileImporter().importPackages(BASE_PACKAGE);

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(OCRA_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            BASE_PACKAGE + ".credentials.fido2",
            BASE_PACKAGE + ".credentials.eudiw",
            BASE_PACKAGE + ".credentials.emvcap")
        .because("protocol packages must remain isolated")
        .check(imported);
  }
}
