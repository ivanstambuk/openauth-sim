import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    java
}

val libsCatalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    val springBootBom = libsCatalog.findLibrary("spring-boot-bom").get()
    val springBootStarterWeb = libsCatalog.findLibrary("spring-boot-starter-web").get()
    val springBootStarterTest = libsCatalog.findLibrary("spring-boot-starter-test").get()

    implementation(platform(springBootBom))
    implementation(projects.core)
    implementation(springBootStarterWeb)

    testImplementation(platform(springBootBom))
    testImplementation(springBootStarterTest)

    constraints {
        implementation("com.github.spotbugs:spotbugs-annotations:4.8.3")
    }
}
