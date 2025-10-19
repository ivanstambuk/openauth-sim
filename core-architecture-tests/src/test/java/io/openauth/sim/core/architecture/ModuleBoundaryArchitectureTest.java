package io.openauth.sim.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Captures module boundary expectations for the OCRA stack facades and persistence layer. */
@Tag("architecture")
final class ModuleBoundaryArchitectureTest {

    private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.openauth.sim");

    @Test
    @DisplayName("Core module remains independent from facade modules")
    void coreMustNotDependOnFacades() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("io.openauth.sim.core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.openauth.sim.cli..", "io.openauth.sim.rest..", "io.openauth.sim.ui..")
                .because("core should expose stable contracts consumed by facades")
                .check(APPLICATION_CLASSES);
    }

    @Test
    @DisplayName("Facade modules remain isolated from one another")
    void facadesMustNotDependOnEachOther() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("io.openauth.sim.cli..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.openauth.sim.rest..", "io.openauth.sim.ui..")
                .because("CLI should only rely on core abstractions")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);

        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("io.openauth.sim.rest..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.openauth.sim.cli..", "io.openauth.sim.ui..")
                .because("REST API must not leak CLI or UI concerns")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);

        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("io.openauth.sim.ui..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.openauth.sim.cli..", "io.openauth.sim.rest..")
                .because("UI module should remain independent from other facades")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }

    @Test
    @DisplayName("Persistence implementation does not depend on facades")
    void persistenceMustNotDependOnFacades() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("io.openauth.sim.core.store..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.openauth.sim.cli..", "io.openauth.sim.rest..", "io.openauth.sim.ui..")
                .because("persistence must remain internal to core")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }
}
