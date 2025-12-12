package io.openauth.sim.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class FacadeDelegationArchitectureTest {

    private static final String CLI_PACKAGE = "io.openauth.sim.cli";
    private static final String REST_PACKAGE = "io.openauth.sim.rest";
    private static final String UI_PACKAGE = "io.openauth.sim.rest.ui";
    private static final String REST_HOTP_PACKAGE = "io.openauth.sim.rest.hotp";
    private static final String REST_TOTP_PACKAGE = "io.openauth.sim.rest.totp";
    private static final String REST_OCRA_PACKAGE = "io.openauth.sim.rest.ocra";
    private static final String REST_EMV_PACKAGE = "io.openauth.sim.rest.emv.cap";
    private static final String REST_FIDO_PACKAGE = "io.openauth.sim.rest.webauthn";
    private static final String REST_EUDIW_PACKAGE = "io.openauth.sim.rest.eudi.openid4vp";
    private static final String TOOLS_MCP_PACKAGE = "io.openauth.sim.tools.mcp";
    private static final String APPLICATION_HOTP_PACKAGE = "io.openauth.sim.application.hotp";
    private static final String APPLICATION_TOTP_PACKAGE = "io.openauth.sim.application.totp";
    private static final String APPLICATION_EMV_PACKAGE = "io.openauth.sim.application.emv.cap";
    private static final String APPLICATION_FIDO_PACKAGE = "io.openauth.sim.application.fido2";
    private static final String APPLICATION_EUDIW_PACKAGE = "io.openauth.sim.application.eudi.openid4vp";
    private static final String CORE_OCRA_PACKAGE = "io.openauth.sim.core.credentials.ocra";
    private static final String APPLICATION_OCRA_PACKAGE = "io.openauth.sim.application.ocra";
    private static final String REST_EVALUATION_SERVICE = "io.openauth.sim.rest.ocra.OcraEvaluationService";
    private static final String REST_VERIFICATION_SERVICE = "io.openauth.sim.rest.ocra.OcraVerificationService";
    private static final String CORE_STORE_PACKAGE = "io.openauth.sim.core.store";
    private static final String MAPDB_CLASS = "io.openauth.sim.core.store.MapDbCredentialStore";

    @Test
    @DisplayName("CLI facades avoid MapDbCredentialStore (production)")
    void cliDoesNotDependOnMapDbCredentialStore() {
        JavaClasses imported = new ClassFileImporter().importPackages(CLI_PACKAGE, CORE_STORE_PACKAGE);

        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(CLI_PACKAGE + "..")
                .and()
                .haveSimpleNameNotContaining("Maintenance") // maintenance tooling is allowed to touch MapDB directly
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MAPDB_CLASS)
                .because("CLI facades should obtain persistence via CredentialStoreFactory, not MapDbCredentialStore");

        rule.check(imported);
    }

    @Test
    @DisplayName("REST facades avoid MapDbCredentialStore")
    void restDoesNotDependOnMapDbCredentialStore() {
        JavaClasses imported = new ClassFileImporter().importPackages(REST_PACKAGE, CORE_STORE_PACKAGE);

        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(REST_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MAPDB_CLASS)
                .because("REST should obtain persistence via CredentialStoreFactory, not MapDbCredentialStore");

        rule.check(imported);
    }

    @Test
    @DisplayName("UI facades avoid MapDbCredentialStore")
    void uiDoesNotDependOnMapDbCredentialStore() {
        JavaClasses imported = new ClassFileImporter().importPackages(UI_PACKAGE, CORE_STORE_PACKAGE);

        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(UI_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MAPDB_CLASS)
                .because("UI should obtain persistence via CredentialStoreFactory, not MapDbCredentialStore");

        rule.check(imported);
    }

    @Test
    @DisplayName("MCP tools avoid direct core dependencies")
    void mcpToolsAvoidCoreDependencies() {
        JavaClasses imported = new ClassFileImporter()
                .importPackages(TOOLS_MCP_PACKAGE, "io.openauth.sim.core", "io.openauth.sim.application");

        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(TOOLS_MCP_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.openauth.sim.core..")
                .because("MCP facades should call REST endpoints, not core internals")
                .allowEmptyShould(true)
                .check(imported);
    }

    @Test
    @DisplayName("REST OCRA services delegate via application layer (not core)")
    void restServicesDelegateThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter()
                .importPackages(REST_OCRA_PACKAGE, UI_PACKAGE, APPLICATION_OCRA_PACKAGE, CORE_OCRA_PACKAGE);

        assertDependsOnPackage(imported.get(REST_EVALUATION_SERVICE), APPLICATION_OCRA_PACKAGE);
        assertDependsOnPackage(imported.get(REST_VERIFICATION_SERVICE), APPLICATION_OCRA_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.ocra.OcraCredentialDirectoryController"), APPLICATION_OCRA_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.ocra.OcraCredentialSeedService"), APPLICATION_OCRA_PACKAGE);

        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(REST_PACKAGE + ".ocra..")
                .or()
                .resideInAPackage(UI_PACKAGE + "..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(OcraCredentialFactory.class)
                .orShould()
                .dependOnClassesThat()
                .areAssignableTo(OcraResponseCalculator.class)
                .because("REST/UI OCRA facades should not construct or execute core OCRA directly")
                .check(imported);
    }

    @Test
    @DisplayName("REST HOTP facades delegate via application layer")
    void restHotpDelegatesThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter().importPackages(REST_HOTP_PACKAGE, APPLICATION_HOTP_PACKAGE);

        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.hotp.HotpEvaluationService"), APPLICATION_HOTP_PACKAGE);
        assertDependsOnPackage(imported.get("io.openauth.sim.rest.hotp.HotpReplayService"), APPLICATION_HOTP_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.hotp.HotpCredentialSeedService"), APPLICATION_HOTP_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.hotp.HotpCredentialDirectoryController"), APPLICATION_HOTP_PACKAGE);
    }

    @Test
    @DisplayName("REST TOTP facades delegate via application layer")
    void restTotpDelegatesThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter().importPackages(REST_TOTP_PACKAGE, APPLICATION_TOTP_PACKAGE);

        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.totp.TotpEvaluationService"), APPLICATION_TOTP_PACKAGE);
        assertDependsOnPackage(imported.get("io.openauth.sim.rest.totp.TotpReplayService"), APPLICATION_TOTP_PACKAGE);
        assertDependsOnPackage(imported.get("io.openauth.sim.rest.totp.TotpHelperService"), APPLICATION_TOTP_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.totp.TotpCredentialSeedService"), APPLICATION_TOTP_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.totp.TotpCredentialDirectoryController"), APPLICATION_TOTP_PACKAGE);
    }

    @Test
    @DisplayName("CLI HOTP/TOTP/OCRA facades delegate via application layer")
    void cliFacadesDelegateThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter()
                .importPackages(
                        CLI_PACKAGE, APPLICATION_HOTP_PACKAGE, APPLICATION_TOTP_PACKAGE, APPLICATION_OCRA_PACKAGE);

        assertAnyClassWithPrefixDependsOn(imported, "io.openauth.sim.cli.HotpCli", APPLICATION_HOTP_PACKAGE);
        assertAnyClassWithPrefixDependsOn(imported, "io.openauth.sim.cli.TotpCli", APPLICATION_TOTP_PACKAGE);
        assertAnyClassWithPrefixDependsOn(imported, "io.openauth.sim.cli.OcraCli", APPLICATION_OCRA_PACKAGE);
    }

    @Test
    @DisplayName("CLI EMV/FIDO2/EUDIW facades delegate via application layer")
    void cliOtherFacadesDelegateThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter()
                .importPackages(
                        CLI_PACKAGE, APPLICATION_EMV_PACKAGE, APPLICATION_FIDO_PACKAGE, APPLICATION_EUDIW_PACKAGE);

        assertAnyClassWithPrefixDependsOn(imported, "io.openauth.sim.cli.EmvCli", APPLICATION_EMV_PACKAGE);
        assertAnyClassWithPrefixDependsOn(imported, "io.openauth.sim.cli.Fido2Cli", APPLICATION_FIDO_PACKAGE);
        assertAnyClassWithPrefixDependsOn(
                imported, "io.openauth.sim.cli.eudi.openid4vp.EudiwCli", APPLICATION_EUDIW_PACKAGE);
    }

    @Test
    @DisplayName("REST EMV/CAP facades delegate via application layer")
    void restEmvDelegatesThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter().importPackages(REST_EMV_PACKAGE, APPLICATION_EMV_PACKAGE);

        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.emv.cap.EmvCapEvaluationService"), APPLICATION_EMV_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.emv.cap.EmvCapReplayService"), APPLICATION_EMV_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.emv.cap.EmvCapCredentialDirectoryController"),
                APPLICATION_EMV_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.emv.cap.EmvCapCredentialSeedService"), APPLICATION_EMV_PACKAGE);
    }

    @Test
    @DisplayName("REST FIDO2/WebAuthn facades delegate via application layer")
    void restFidoDelegatesThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter().importPackages(REST_FIDO_PACKAGE, APPLICATION_FIDO_PACKAGE);

        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.webauthn.WebAuthnEvaluationService"), APPLICATION_FIDO_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.webauthn.WebAuthnReplayService"), APPLICATION_FIDO_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.webauthn.WebAuthnAttestationService"), APPLICATION_FIDO_PACKAGE);
    }

    @Test
    @DisplayName("REST EUDIW facades delegate via application layer")
    void restEudiwDelegatesThroughApplicationLayer() {
        JavaClasses imported = new ClassFileImporter().importPackages(REST_EUDIW_PACKAGE, APPLICATION_EUDIW_PACKAGE);

        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.eudi.openid4vp.Oid4vpController"), APPLICATION_EUDIW_PACKAGE);
        assertDependsOnPackage(
                imported.get("io.openauth.sim.rest.eudi.openid4vp.Oid4vpApplicationConfiguration"),
                APPLICATION_EUDIW_PACKAGE);
    }

    private static void assertAnyClassWithPrefixDependsOn(
            JavaClasses imported, String classNamePrefix, String packagePrefix) {
        boolean found = imported.stream()
                .filter(javaClass -> javaClass.getName().startsWith(classNamePrefix))
                .anyMatch(javaClass -> dependsOnPackage(javaClass, packagePrefix));
        assertTrue(
                found,
                () -> classNamePrefix
                        + "* should depend on application-layer services ("
                        + packagePrefix
                        + ") to avoid direct core coupling");
    }

    private static void assertDependsOnPackage(JavaClass javaClass, String packagePrefix) {
        assertTrue(
                dependsOnPackage(javaClass, packagePrefix),
                () -> javaClass.getName()
                        + " should depend on application-layer services ("
                        + packagePrefix
                        + ") to avoid direct core coupling");
    }

    private static boolean dependsOnPackage(JavaClass javaClass, String packagePrefix) {
        return javaClass.getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getPackageName())
                .anyMatch(packageName -> packageName.startsWith(packagePrefix));
    }
}
