import com.diffplug.gradle.spotless.SpotlessTask
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

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
fun VersionCatalog.version(alias: String) = findVersion(alias).orElseThrow().requiredVersion

group = providers.gradleProperty("GROUP").getOrElse("io.openauth.sim")
version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT")

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
        leadingTabsToSpaces()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts", "**/*.kts")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    java {
        target("**/src/**/*.java")
        palantirJavaFormat(libsCatalog.version("palantirJavaFormat"))
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
}

abstract class GenerateJsonLdTask : DefaultTask() {
    @get:InputFile
    abstract val metadataFile: RegularFileProperty

    @get:OutputDirectory
    abstract val snippetsDirectory: DirectoryProperty

    @get:OutputFile
    abstract val bundleFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val parser = JsonSlurper()
        val metadata = parser.parse(metadataFile.get().asFile) as Map<*, *>
        val context = (metadata["@context"] ?: "https://schema.org").toString()
        val entities = (metadata["entities"] as? List<*>)?.associate { raw ->
            val entry = raw as Map<*, *>
            val id = entry["id"]?.toString()
                ?: error("Entity missing id in ${metadataFile.get().asFile}")
            val data = entry["data"] as? Map<*, *>
                ?: error("Entity $id missing data block")
            id to data
        } ?: emptyMap()

        snippetsDirectory.get().asFile.apply { mkdirs() }

        val snippets = metadata["snippets"] as? List<*> ?: emptyList<Any?>()
        snippets.forEach { raw ->
            val snippet = raw as Map<*, *>
            val snippetId = snippet["id"]?.toString()
                ?: error("Snippet missing id in ${metadataFile.get().asFile}")
            val entityRefs = snippet["entityRefs"] as? List<*>
                ?: error("Snippet $snippetId missing entityRefs array")
            val graph = entityRefs.map { ref ->
                val entityId = ref?.toString()
                    ?: error("Null entityRef in snippet $snippetId")
                entities[entityId]
                    ?: error("Unknown entityRef $entityId in snippet $snippetId")
            }

            val snippetPayload = mapOf(
                "@context" to context,
                "@graph" to graph
            )
            val snippetFile = snippetsDirectory.file("$snippetId.jsonld").get().asFile
            snippetFile.parentFile.mkdirs()
            writeJsonIfChanged(snippetFile, snippetPayload)
        }

        val bundlePayload = mapOf(
            "@context" to context,
            "@graph" to entities.values.toList()
        )
        val bundle = bundleFile.get().asFile
        bundle.parentFile.mkdirs()
        writeJsonIfChanged(bundle, bundlePayload)
    }

    private fun writeJsonIfChanged(target: File, payload: Any) {
        val json = JsonOutput.prettyPrint(JsonOutput.toJson(payload))
        if (target.exists()) {
            val existing = target.readText()
            if (existing == json) {
                return
            }
        }
        target.parentFile.mkdirs()
        target.writeText(json)
    }
}

val generateJsonLd by tasks.registering(GenerateJsonLdTask::class) {
    group = "documentation"
    description = "Generates JSON-LD snippets for README/ReadMe.LLM and a consolidated bundle."
    metadataFile.set(layout.projectDirectory.file("docs/3-reference/json-ld/metadata.json"))
    snippetsDirectory.set(layout.projectDirectory.dir("docs/3-reference/json-ld/snippets"))
    bundleFile.set(layout.buildDirectory.file("json-ld/openauth-sim.json"))
}

tasks.withType(SpotlessTask::class).configureEach {
    dependsOn(generateJsonLd)
}

val pitTargets = mapOf(
    ":core-ocra" to listOf("io.openauth.sim.core.credentials.ocra.*")
)

rootProject.extra["pitTargets"] = pitTargets

val pitSkip = providers.gradleProperty("pit.skip").map(String::toBoolean).getOrElse(false)

// Lightweight wrapper to run the REST inline performance harness via Gradle.
tasks.register<Exec>("restInlineLoadTest") {
    group = "verification"
    description = "Runs the REST inline evaluation load harness (tools/perf/rest-inline-node-load.js)."
    // Allow overriding Node and harness parameters via Gradle properties if needed.
    val nodeExecutable = providers.gradleProperty("nodeExecutable").orElse("node")
    val baseUrl = providers.gradleProperty("restInlineBaseUrl").orElse("http://localhost:8080")
    val duration = providers.gradleProperty("restInlineDurationSeconds").orElse("30")
    val concurrency = providers.gradleProperty("restInlineConcurrency").orElse("32")
    val targetTps = providers.gradleProperty("restInlineTargetTps").orElse("500")
    val maxP95 = providers.gradleProperty("restInlineMaxP95").orElse("50")
    val maxP99 = providers.gradleProperty("restInlineMaxP99").orElse("100")
    val baselineFile = providers.gradleProperty("restInlineBaselineFile").orNull
    val baselineTolerance = providers.gradleProperty("restInlineBaselineTolerance").orElse("0.2")

    commandLine = buildList {
        add(nodeExecutable.get())
        add("tools/perf/rest-inline-node-load.js")
        addAll(
            listOf(
                "--baseUrl", baseUrl.get(),
                "--durationSeconds", duration.get(),
                "--concurrency", concurrency.get(),
                "--targetTps", targetTps.get(),
                "--maxP95", maxP95.get(),
                "--maxP99", maxP99.get(),
                "--baselineTolerance", baselineTolerance.get(),
            ),
        )
        if (!baselineFile.isNullOrBlank()) {
            add("--baselineFile")
            add(baselineFile!!)
        }
    }
}

fun waitForRestApi(baseUrl: String, timeoutSeconds: Int, serverProcess: Process) {
    val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
    while (System.currentTimeMillis() < deadline) {
        if (!serverProcess.isAlive) {
            throw org.gradle.api.GradleException(
                "REST API process terminated before becoming ready (exit code=${serverProcess.exitValue()})"
            )
        }
        try {
            val url = java.net.URL(baseUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 2_000
            conn.readTimeout = 2_000
            conn.requestMethod = "GET"
            conn.useCaches = false
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..499) {
                return
            }
        } catch (_: Exception) {
            // keep trying until timeout
        }
        Thread.sleep(500)
    }
    throw org.gradle.api.GradleException("REST API at $baseUrl did not become ready within $timeoutSeconds seconds")
}

tasks.register("restInlinePerfSuite") {
    group = "verification"
    description =
        "Starts the REST API, waits for readiness, runs the REST inline performance harness, then stops the server."

    // This task shells out and uses ProcessBuilder; keep it out of configuration cache.
    notCompatibleWithConfigurationCache("Uses cross-process orchestration via ProcessBuilder")

    doLast {
        val baseUrl = providers.gradleProperty("restInlineBaseUrl").orElse("http://localhost:8080").get()

        val serverCommand = listOf(
            "./gradlew",
            "--no-daemon",
            "--no-configuration-cache",
            "--init-script",
            "tools/run-rest-api.init.gradle.kts",
            "runRestApi",
        )

        val processBuilder = ProcessBuilder(serverCommand)
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)

        println("Starting REST API for perf suite at $baseUrl via: ${serverCommand.joinToString(" ")}")
        val serverProcess = processBuilder.start()

        try {
            waitForRestApi(baseUrl, 60, serverProcess)
            println("REST API is ready, running rest-inline harness ...")

            val nodeExecutable = providers.gradleProperty("nodeExecutable").orElse("node").get()
            val duration = providers.gradleProperty("restInlineDurationSeconds").orElse("30").get()
            val concurrency = providers.gradleProperty("restInlineConcurrency").orElse("32").get()
            val targetTps = providers.gradleProperty("restInlineTargetTps").orElse("500").get()
            val maxP95 = providers.gradleProperty("restInlineMaxP95").orElse("50").get()
            val maxP99 = providers.gradleProperty("restInlineMaxP99").orElse("100").get()
            val baselineFile = providers.gradleProperty("restInlineBaselineFile").orNull
            val baselineTolerance =
                providers.gradleProperty("restInlineBaselineTolerance").orElse("0.2").get()

            val nodeCommand = buildList {
                add(nodeExecutable)
                add("tools/perf/rest-inline-node-load.js")
                addAll(
                    listOf(
                        "--baseUrl",
                        baseUrl,
                        "--durationSeconds",
                        duration,
                        "--concurrency",
                        concurrency,
                        "--targetTps",
                        targetTps,
                        "--maxP95",
                        maxP95,
                        "--maxP99",
                        maxP99,
                        "--baselineTolerance",
                        baselineTolerance,
                    ),
                )
                if (!baselineFile.isNullOrBlank()) {
                    add("--baselineFile")
                    add(baselineFile!!)
                }
            }

            println("Running REST inline harness: ${nodeCommand.joinToString(" ")}")
            val harnessProcess = ProcessBuilder(nodeCommand)
                .directory(rootProject.projectDir)
                .inheritIO()
                .start()
            val exitCodeHarness = harnessProcess.waitFor()
            if (exitCodeHarness != 0) {
                throw org.gradle.api.GradleException(
                    "REST inline harness failed with exit code $exitCodeHarness",
                )
            }
        } finally {
            if (serverProcess.isAlive) {
                println("Stopping REST API process ...")
                serverProcess.destroy()
                if (serverProcess.isAlive) {
                    serverProcess.destroyForcibly()
                }
            }
        }
    }
}

subprojects {
    apply(from = rootProject.layout.projectDirectory.file("gradle/quality-conventions.gradle.kts"))
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
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
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
    dependsOn(generateJsonLd)
    dependsOn(architectureTest, jacocoCoverageVerification, ":application:nativeJavaApiJavadoc")
}

qualityGate.configure {
    dependsOn(generateJsonLd)
}
