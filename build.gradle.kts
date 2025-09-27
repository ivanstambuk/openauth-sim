import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.kotlin.dsl.getByType

plugins {
    base
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependencycheck)
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.errorprone) apply false
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun VersionCatalog.library(alias: String) = findLibrary(alias).orElseThrow()
fun VersionCatalog.version(alias: String) = findVersion(alias).orElseThrow().requiredVersion

group = "io.openauth.sim"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

spotless {
    format("misc") {
        target("*.md", "*.yml", "*.yaml", "*.json", "*.gitignore")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts", "**/*.kts")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    java {
        target("**/src/**/*.java")
        googleJavaFormat(libsCatalog.version("googleJavaFormat"))
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}

dependencyCheck {
    failBuildOnCVSS = 7.0F
    analyzers.apply {
        assemblyEnabled = false
        nodeEnabled = false
        nodeAudit.enabled = false
    }
    val suppression = layout.projectDirectory.file("config/dependency-check/suppressions.xml")
    if (suppression.asFile.exists()) {
        suppressionFile = suppression.asFile.absolutePath
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "checkstyle")
    pluginManager.apply("com.github.spotbugs")
    pluginManager.apply("net.ltgt.errorprone")

    repositories {
        mavenCentral()
    }

    dependencyLocking {
        lockAllConfigurations()
    }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.11"
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = libsCatalog.version("checkstyle")
        configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
        configProperties = mapOf("basedir" to rootProject.projectDir.path)
    }

    configurations.configureEach {
        resolutionStrategy.failOnVersionConflict()
        resolutionStrategy.force(
            "com.google.guava:guava:33.4.0-jre",
            "com.google.errorprone:error_prone_annotations:${libsCatalog.version("errorprone")}",
            "org.checkerframework:checker-qual:3.43.0",
            "org.codehaus.plexus:plexus-utils:3.5.1",
            "org.apache.commons:commons-lang3:3.17.0",
            "org.apache.httpcomponents:httpcore:4.4.16",
            "org.slf4j:slf4j-api:2.0.13"
        )
    }

    dependencies {
        add("testImplementation", platform(libsCatalog.library("junit-bom")))
        add("testImplementation", libsCatalog.library("junit-jupiter"))
        add("testImplementation", libsCatalog.library("mockito-core"))
        add("testImplementation", libsCatalog.library("archunit-junit5"))
        add("compileOnly", libsCatalog.library("spotbugs-annotations"))
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("errorprone", libsCatalog.library("errorprone-core"))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        options.errorprone {
            disableWarningsInGeneratedCode.set(true)
            allErrorsAsWarnings.set(false)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-XX:+UseContainerSupport")
        testLogging {
            events("failed", "skipped")
            showStandardStreams = false
        }
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
