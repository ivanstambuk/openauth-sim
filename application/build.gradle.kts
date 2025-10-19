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
