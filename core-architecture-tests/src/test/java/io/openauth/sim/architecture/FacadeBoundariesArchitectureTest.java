package io.openauth.sim.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class FacadeBoundariesArchitectureTest {

    static final String ROOT_PACKAGE = "io.openauth.sim";
    static final String APPLICATION_PACKAGE = ROOT_PACKAGE + ".application";
    static final String CORE_PACKAGE = ROOT_PACKAGE + ".core";
    static final String CORE_STORE_PACKAGE = CORE_PACKAGE + ".store";
    static final String MAPDB_CREDENTIAL_STORE = CORE_STORE_PACKAGE + ".MapDbCredentialStore";

    static final String CLI_PACKAGE = ROOT_PACKAGE + ".cli";
    static final String REST_PACKAGE = ROOT_PACKAGE + ".rest";
    static final String UI_PACKAGE = REST_PACKAGE + ".ui";
    static final String TOOLS_MCP_PACKAGE = ROOT_PACKAGE + ".tools.mcp";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT_PACKAGE);

    @Test
    @DisplayName("CLI facades avoid MapDbCredentialStore (production)")
    void cliDoesNotDependOnMapDbCredentialStore() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(CLI_PACKAGE + "..")
                .and()
                .haveSimpleNameNotContaining("Maintenance") // maintenance tooling is allowed to touch MapDB directly
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MAPDB_CREDENTIAL_STORE)
                .because("CLI facades should obtain persistence via CredentialStoreFactory, not MapDbCredentialStore");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("REST facades avoid MapDbCredentialStore")
    void restDoesNotDependOnMapDbCredentialStore() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(REST_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MAPDB_CREDENTIAL_STORE)
                .because("REST should obtain persistence via CredentialStoreFactory, not MapDbCredentialStore");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("UI facades avoid MapDbCredentialStore")
    void uiDoesNotDependOnMapDbCredentialStore() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(UI_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MAPDB_CREDENTIAL_STORE)
                .because("UI should obtain persistence via CredentialStoreFactory, not MapDbCredentialStore");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("MCP facade does not depend on core internals")
    void mcpDoesNotDependOnCore() {
        forbidFacadeDependency(
                        TOOLS_MCP_PACKAGE, CORE_PACKAGE, "MCP facades should call REST endpoints, not core internals")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("CLI facade does not depend on REST facade")
    void cliDoesNotDependOnRest() {
        forbidFacadeDependency(CLI_PACKAGE, REST_PACKAGE, "CLI should not depend on REST")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("REST facade does not depend on CLI facade")
    void restDoesNotDependOnCli() {
        forbidFacadeDependency(REST_PACKAGE, CLI_PACKAGE, "REST should not depend on CLI")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("MCP facade does not depend on REST facade")
    void mcpDoesNotDependOnRest() {
        forbidFacadeDependency(TOOLS_MCP_PACKAGE, REST_PACKAGE, "MCP should not depend on REST")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("REST facade does not depend on MCP facade")
    void restDoesNotDependOnMcp() {
        forbidFacadeDependency(REST_PACKAGE, TOOLS_MCP_PACKAGE, "REST should not depend on MCP")
                .check(PRODUCTION_CLASSES);
    }

    static ArchRule forbidFacadeDependency(String sourceFacadePackage, String forbiddenFacadePackage, String reason) {
        return ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(sourceFacadePackage + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(forbiddenFacadePackage + "..")
                .because(reason)
                .allowEmptyShould(true);
    }
}
