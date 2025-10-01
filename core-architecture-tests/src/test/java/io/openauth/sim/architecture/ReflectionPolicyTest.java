package io.openauth.sim.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class ReflectionPolicyTest {

  private static final String ROOT_PACKAGE = "io.openauth.sim";

  @Test
  @DisplayName("Project code must not depend on java.lang.reflect")
  void reflectionUsageIsForbidden() {
    JavaClasses imported = new ClassFileImporter().importPackages(ROOT_PACKAGE);

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage(ROOT_PACKAGE + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.lang.reflect..")
        .because("reflection hides behaviour from tests and violates Feature 011 policy")
        .check(imported);
  }
}
