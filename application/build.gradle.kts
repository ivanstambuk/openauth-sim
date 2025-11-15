import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    `java-library`
}

val libsCatalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(projects.core)
    implementation(projects.coreOcra)

    compileOnlyApi(libsCatalog.findLibrary("spotbugs-annotations").get())

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Aggregated Javadoc entry point for Native Java API usage (Feature 010/014).
tasks.register("nativeJavaApiJavadoc") {
    group = org.gradle.api.plugins.JavaBasePlugin.DOCUMENTATION_GROUP
    description =
        "Runs :core:javadoc and :application:javadoc to generate Native Java API Javadoc for local inspection."

    // Delegate to the standard Javadoc tasks for core and application.
    dependsOn(":core:javadoc", ":application:javadoc")
}
