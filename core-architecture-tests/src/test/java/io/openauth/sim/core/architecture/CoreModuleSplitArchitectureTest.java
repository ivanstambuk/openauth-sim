package io.openauth.sim.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Anticipates the split between core-shared and core-ocra modules. */
@Tag("architecture")
final class CoreModuleSplitArchitectureTest {

  private static final JavaClasses APPLICATION_CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("io.openauth.sim");

  @Test
  @DisplayName("Core OCRA packages remain isolated from facades")
  void coreOcraDoesNotDependOnFacades() {
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("io.openauth.sim.core.credentials.ocra..")
        .or()
        .resideInAPackage("io.openauth.sim.core.store..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.openauth.sim.cli..", "io.openauth.sim.rest..", "io.openauth.sim.ui..")
        .because("core-ocra must expose stable APIs without pulling facade concerns")
        .allowEmptyShould(true)
        .check(APPLICATION_CLASSES);
  }

  @Test
  @DisplayName("Shared core packages stay protocol-agnostic")
  void coreSharedAvoidsOcra() {
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("io.openauth.sim.core.model..")
        .or()
        .resideInAPackage("io.openauth.sim.core.support..")
        .or()
        .resideInAPackage("io.openauth.sim.core.store.serialization..")
        .or()
        .resideInAPackage("io.openauth.sim.core.store.encryption..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.openauth.sim.core.credentials.ocra..")
        .because("core-shared must not take dependencies on protocol-specific implementations")
        .allowEmptyShould(true)
        .check(APPLICATION_CLASSES);
  }

  @Test
  @DisplayName("Non-OCRA core packages avoid OCRA internals")
  void corePackagesAvoidOcraInternals() {
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("io.openauth.sim.core..")
        .and()
        .resideOutsideOfPackage("io.openauth.sim.core.credentials.ocra..")
        .and()
        .resideOutsideOfPackage("io.openauth.sim.core.store.ocra..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.openauth.sim.core.credentials.ocra..")
        .because("core module should rely on shared abstractions rather than protocol specifics")
        .allowEmptyShould(true)
        .check(APPLICATION_CLASSES);
  }
}
