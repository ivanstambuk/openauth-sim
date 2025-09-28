package io.openauth.sim.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Phase 1/T006: ArchUnit guardrails for the OCRA credential package.
 *
 * <p>Phase 2 established the public API surface, so the rules now execute as part of the
 * architecture test suite.
 */
@Tag("architecture")
final class OcraArchitectureTest {

  private static final String BASE_PACKAGE = "io.openauth.sim.core";
  private static final String OCRA_PACKAGE = BASE_PACKAGE + ".credentials.ocra";

  @Test
  @DisplayName("Only approved packages may depend on OCRA internals")
  void onlyApprovedPackagesTouchOcraPackage() {
    JavaClasses imported = new ClassFileImporter().importPackages(BASE_PACKAGE);

    ArchRuleDefinition.classes()
        .that()
        .resideInAPackage(OCRA_PACKAGE)
        .should()
        .onlyBeAccessed()
        .byAnyPackage(
            OCRA_PACKAGE,
            BASE_PACKAGE + ".credentials..",
            BASE_PACKAGE + ".credentials.registry..",
            BASE_PACKAGE + ".store..",
            BASE_PACKAGE + ".model..")
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
