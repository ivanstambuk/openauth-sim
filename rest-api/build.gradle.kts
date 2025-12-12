import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    java
}

abstract class VerifyEmvTraceProvenanceFixture : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val canonicalFixture: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val restApiFixture: RegularFileProperty

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description =
            "Verifies EMV/CAP trace-provenance fixture stays in sync between docs/ and rest-api/docs/."
    }

    @TaskAction
    fun verify() {
        val sourceFile = canonicalFixture.asFile.get()
        val copyFile = restApiFixture.asFile.get()
        if (!sourceFile.exists() || !copyFile.exists()) {
            throw GradleException(
                "EMV trace-provenance fixture missing in docs/ or rest-api/docs/. " +
                    "Expected both $sourceFile and $copyFile to exist.")
        }
        val sourceBytes = sourceFile.readBytes()
        val copyBytes = copyFile.readBytes()
        if (!sourceBytes.contentEquals(copyBytes)) {
            throw GradleException(
                "EMV trace-provenance fixture is out of sync. " +
                    "Run :rest-api:syncEmvTraceProvenanceFixture to copy the canonical docs/ version.")
        }
    }
}

abstract class SyncEmvTraceProvenanceFixture : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val canonicalFixture: RegularFileProperty

    @get:OutputFile
    abstract val restApiFixture: RegularFileProperty

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Syncs EMV/CAP trace-provenance fixture from docs/ into rest-api/docs/."
    }

    @TaskAction
    fun sync() {
        val sourceFile = canonicalFixture.asFile.get()
        val copyFile = restApiFixture.asFile.get()
        if (!sourceFile.exists()) {
            throw GradleException(
                "Canonical EMV trace-provenance fixture is missing at $sourceFile. " +
                    "Edit or restore the docs/ fixture before syncing.")
        }
        copyFile.parentFile.mkdirs()
        sourceFile.copyTo(copyFile, overwrite = true)
        logger.lifecycle(
            "Synced EMV trace-provenance fixture from {} to {}",
            sourceFile,
            copyFile)
    }
}

val libsCatalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    val springBootBom = libsCatalog.findLibrary("spring-boot-bom").get()
    val springBootStarterWeb = libsCatalog.findLibrary("spring-boot-starter-web").get()
    val springBootStarterTest = libsCatalog.findLibrary("spring-boot-starter-test").get()
    val springBootStarterThymeleaf =
        libsCatalog.findLibrary("spring-boot-starter-thymeleaf").get()
    val springDocStarter =
        libsCatalog.findLibrary("springdoc-openapi-starter-webmvc-ui").get()
    val seleniumHtmlUnitDriver =
        libsCatalog.findLibrary("selenium-htmlunit-driver").get()
    val spotbugsAnnotations =
        libsCatalog.findLibrary("spotbugs-annotations").get()
    val picocli = libsCatalog.findLibrary("picocli").get()

    implementation(enforcedPlatform(springBootBom))
    implementation(projects.core)
    implementation(projects.coreOcra)
    implementation(projects.application)
    implementation(projects.infraPersistence)
    implementation(springBootStarterWeb)
    implementation(springBootStarterThymeleaf)
    implementation(springDocStarter)

    testImplementation(enforcedPlatform(springBootBom))
    testImplementation(springBootStarterTest)
    testImplementation(seleniumHtmlUnitDriver)
    testImplementation(testFixtures(projects.application))
    testImplementation(testFixtures(projects.core))
    testImplementation(projects.cli)
    testImplementation(picocli)
    testImplementation(spotbugsAnnotations) {
        because("HtmlUnit classes are compiled with @SuppressFBWarnings and need the annotation on the classpath")
    }

    constraints {
        implementation("com.github.spotbugs:spotbugs-annotations:4.9.8")
    }
}

tasks.register<Exec>("emvConsoleJsTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs EMV console JavaScript unit tests"
    commandLine("node", "--test", "src/test/javascript/emv/console.test.js")
    workingDir = project.projectDir
}

tasks.register<Exec>("eudiwConsoleJsTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs EUDIW OpenID4VP console JavaScript unit tests"
    commandLine("node", "--test", "src/test/javascript/eudi/openid4vp/console.test.js")
    workingDir = project.projectDir
}

tasks.named("check") {
    dependsOn("emvConsoleJsTest", "eudiwConsoleJsTest")
}

val canonicalEmvTraceFixture = rootProject.layout.projectDirectory.file(
    "docs/test-vectors/emv-cap/trace-provenance-example.json")
val restApiEmvTraceFixture = layout.projectDirectory.file(
    "docs/test-vectors/emv-cap/trace-provenance-example.json")

tasks.register<VerifyEmvTraceProvenanceFixture>("verifyEmvTraceProvenanceFixture") {
    canonicalFixture.set(canonicalEmvTraceFixture)
    restApiFixture.set(restApiEmvTraceFixture)
}

tasks.register<SyncEmvTraceProvenanceFixture>("syncEmvTraceProvenanceFixture") {
    canonicalFixture.set(canonicalEmvTraceFixture)
    restApiFixture.set(restApiEmvTraceFixture)
}

tasks.withType<Test>().configureEach {
    dependsOn("verifyEmvTraceProvenanceFixture")
}

val standardTest = tasks.named<Test>("test")
tasks.register<Test>("crossFacadeContractTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs cross-facade contract parity suites (tagged crossFacadeContract)."
    testClassesDirs = standardTest.get().testClassesDirs
    classpath = standardTest.get().classpath
    useJUnitPlatform {
        includeTags("crossFacadeContract")
    }
    shouldRunAfter(standardTest)
}

tasks.register<Test>("schemaContractTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs REST OpenAPI schema contract tests (tagged schemaContract)."
    testClassesDirs = standardTest.get().testClassesDirs
    classpath = standardTest.get().classpath
    useJUnitPlatform {
        includeTags("schemaContract")
    }
    shouldRunAfter(standardTest)
}

tasks.named("check") {
    dependsOn("verifyEmvTraceProvenanceFixture")
}
