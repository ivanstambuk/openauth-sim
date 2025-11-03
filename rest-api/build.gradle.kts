import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.getByType
import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    java
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
    testImplementation(spotbugsAnnotations) {
        because("HtmlUnit classes are compiled with @SuppressFBWarnings and need the annotation on the classpath")
    }

    constraints {
        implementation("com.github.spotbugs:spotbugs-annotations:4.8.3")
    }
}

tasks.register<Exec>("emvConsoleJsTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs EMV console JavaScript unit tests"
    commandLine("node", "--test", "src/test/javascript/emv/console.test.js")
    workingDir = project.projectDir
}

tasks.named("check") {
    dependsOn("emvConsoleJsTest")
}
