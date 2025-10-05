import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import info.solidsoft.gradle.pitest.PitestPluginExtension
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    base
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.pitest) apply false
}

jacoco {
    toolVersion = "0.8.11"
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val errorProneEnabled = providers.gradleProperty("errorproneEnabled").map(String::toBoolean).getOrElse(false)
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

val pitTargets = mapOf(
    ":core-ocra" to listOf("io.openauth.sim.core.credentials.ocra.*")
)

val pitSkip = providers.gradleProperty("pit.skip").map(String::toBoolean).getOrElse(false)

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "checkstyle")
    pluginManager.apply("com.github.spotbugs")
    pluginManager.apply("net.ltgt.errorprone")
    apply(plugin = "pmd")

    repositories {
        mavenCentral()
    }

    dependencyLocking {
        lockAllConfigurations()
    }

    val junitVersion = libsCatalog.version("junit")
    val junitPlatformVersion = libsCatalog.version("junitPlatform")

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

    extensions.configure<PmdExtension> {
        toolVersion = libsCatalog.version("pmd")
        isConsoleOutput = true
        ruleSets = emptyList()
        ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    }

    tasks.withType<Test>().configureEach {
        extensions.configure(JacocoTaskExtension::class) {
            val updated = (excludes ?: mutableListOf()).toMutableList()
            if ("com/gargoylesoftware/**" !in updated) {
                updated += "com/gargoylesoftware/**"
            }
            excludes = updated
        }
    }

    tasks.withType<Pmd>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(false)
        }
    }

    extensions.configure<SpotBugsExtension> {
        effort.set(Effort.MAX)
        reportLevel.set(Confidence.LOW)
        includeFilter.set(rootProject.layout.projectDirectory.file("config/spotbugs/dead-state-include.xml"))
    }

    tasks.withType<SpotBugsTask>().configureEach {
        extraArgs.add("-longBugCodes")
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
            "org.slf4j:slf4j-api:2.0.13",
            "commons-codec:commons-codec:1.19.0",
            "com.google.googlejavaformat:google-java-format:${libsCatalog.version("googleJavaFormat")}",
            "com.github.ben-manes.caffeine:caffeine:${libsCatalog.version("caffeine")}",
            "org.junit.jupiter:junit-jupiter:$junitVersion",
            "org.junit.jupiter:junit-jupiter-api:$junitVersion",
            "org.junit.jupiter:junit-jupiter-params:$junitVersion",
            "org.junit.jupiter:junit-jupiter-engine:$junitVersion",
            "org.junit.platform:junit-platform-commons:$junitPlatformVersion",
            "org.junit.platform:junit-platform-engine:$junitPlatformVersion",
            "org.junit.platform:junit-platform-launcher:$junitPlatformVersion",
            "io.micrometer:micrometer-observation:1.13.4",
            "io.micrometer:micrometer-commons:1.13.4",
            "net.bytebuddy:byte-buddy:1.14.19",
            "net.bytebuddy:byte-buddy-agent:1.14.19",
            "org.hamcrest:hamcrest:2.2",
            "org.jspecify:jspecify:1.0.0",
            "net.minidev:json-smart:2.5.1"
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
        options.compilerArgs.add("-Xlint:all")
        val errorProneOptions: ErrorProneOptions = options.errorprone
        errorProneOptions.isEnabled.set(errorProneEnabled)
        errorProneOptions.disableWarningsInGeneratedCode.set(true)
        errorProneOptions.allErrorsAsWarnings.set(false)
        if (errorProneEnabled) {
            errorProneOptions.errorproneArgs.set(listOf("--should-stop=ifError=FLOW"))
        } else {
            errorProneOptions.errorproneArgs.set(emptyList())
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

    pitTargets[project.path]?.let { targetPackages ->
        pluginManager.apply("info.solidsoft.pitest")

        extensions.configure<PitestPluginExtension> {
            pitestVersion.set("1.15.0")
            threads.set(4)
            outputFormats.set(listOf("XML", "HTML"))
            timestampedReports.set(false)
            junit5PluginVersion.set("1.2.0")

            targetClasses.set(targetPackages)
            targetTests.set(listOf("io.openauth.sim.*"))
            if (project.path == ":core") {
                excludedClasses.set(
                    listOf(
                        "io.openauth.sim.core.credentials.ocra.OcraChallengeFormat",
                        "io.openauth.sim.core.credentials.ocra.OcraCredentialFactory"
                    )
                )
            }
            mutationThreshold.set(85)
            failWhenNoMutations.set(true)
        }
    }
}

val architectureTest = tasks.register("architectureTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs cross-module architecture checks"
    dependsOn(":core-architecture-tests:test")
}

val ocraModules =
    listOf(project(":core"), project(":core-ocra"), project(":application"), project(":cli"), project(":rest-api"))

val jacocoAggregatedReport = tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates aggregated Jacoco coverage across OCRA modules"

    val testTasks = ocraModules.map { module -> module.tasks.named("test") }
    val classTasks = ocraModules.map { module -> module.tasks.named("classes") }
    dependsOn(testTasks)
    dependsOn(classTasks)

    val executionFiles = ocraModules.map { module -> module.layout.buildDirectory.file("jacoco/test.exec") }
    executionData.setFrom(executionFiles)

    val sourceDirs = ocraModules.map { module -> module.layout.projectDirectory.dir("src/main/java") }
    val classTrees = ocraModules.map { module ->
        module.layout.buildDirectory.dir("classes/java/main").map { outputDir ->
            module.fileTree(outputDir) {
                include(
                    "io/openauth/sim/core/credentials/ocra/**",
                    "io/openauth/sim/cli/**",
                    "io/openauth/sim/rest/**"
                )
            }
        }
    }

    additionalSourceDirs.from(sourceDirs)
    sourceDirectories.from(sourceDirs)
    classDirectories.from(classTrees)

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregated/jacocoAggregatedReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregated/html"))
        csv.required.set(false)
    }
}

val jacocoCoverageVerification = tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fails the build if aggregated OCRA coverage dips below thresholds"
    val classTasks = ocraModules.map { module -> module.tasks.named("classes") }
    dependsOn(jacocoAggregatedReport)
    dependsOn(classTasks)

    val executionFiles = ocraModules.map { module -> module.layout.buildDirectory.file("jacoco/test.exec") }
    executionData.setFrom(executionFiles)

    val sourceDirs = ocraModules.map { module -> module.layout.projectDirectory.dir("src/main/java") }
    val classTrees = ocraModules.map { module ->
        module.layout.buildDirectory.dir("classes/java/main").map { outputDir ->
            module.fileTree(outputDir) {
                include(
                    "io/openauth/sim/core/credentials/ocra/**",
                    "io/openauth/sim/cli/**",
                    "io/openauth/sim/rest/**"
                )
            }
        }
    }

    sourceDirectories.from(sourceDirs)
    classDirectories.from(classTrees)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

val mutationTest = tasks.register("mutationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs PIT mutation tests across OCRA modules"
    if (pitSkip) {
        doFirst { logger.lifecycle("Skipping mutation tests (pit.skip=true)") }
    } else {
        dependsOn(pitTargets.keys.map { path -> project(path).tasks.named("pitest") })
    }
}

val reflectionScan = tasks.register("reflectionScan") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fails when reflection APIs are referenced in source sets"

    val moduleNames = listOf("core", "application", "cli", "rest-api", "ui")
    val projectDirectoryFile = layout.projectDirectory.asFile
    val tokenFile = projectDirectoryFile.resolve("config/reflection-tokens.txt")
    val moduleSrcDirs = moduleNames.map { moduleName -> projectDirectoryFile.resolve("$moduleName/src") }

    inputs.file(tokenFile)
    inputs.files(moduleSrcDirs)

    doLast {
        if (!tokenFile.exists()) {
            throw GradleException("Missing reflection token configuration file: ${tokenFile.path}")
        }

        val tokens = tokenFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        if (tokens.isEmpty()) {
            logger.lifecycle("No reflection tokens configured; skipping scan")
            return@doLast
        }

        val offenders = mutableListOf<String>()

        moduleSrcDirs.forEach { srcDir ->
            if (!srcDir.exists()) {
                return@forEach
            }

            srcDir.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .forEach { sourceFile ->
                    val content = sourceFile.readText()
                    tokens.forEach { token ->
                        if (content.contains(token)) {
                            offenders += "${sourceFile.path} -> $token"
                        }
                    }
                }
        }

        if (offenders.isNotEmpty()) {
            logger.error("Reflection usage detected:\n" + offenders.joinToString(System.lineSeparator()))
            throw GradleException("Reflection usage detected; see log for details")
        }
    }
}

val qualityGate = tasks.register("qualityGate") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the full quality automation gate"
    dependsOn("spotlessCheck", "check", reflectionScan)
}

tasks.named("check") {
    dependsOn(architectureTest, jacocoCoverageVerification, mutationTest)
}
