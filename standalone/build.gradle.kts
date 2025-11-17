import groovy.util.Node
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    java
    id("org.danilopianini.publish-on-central") version "9.1.5"
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(projects.core)
    implementation(projects.coreShared)
    implementation(projects.coreOcra)
    implementation(projects.application)
    implementation(projects.infraPersistence)
    implementation(projects.cli)
    implementation(projects.restApi)
    implementation(projects.ui)
    implementation(projects.toolsMcpServer)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("openauth-sim-standalone")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val projectArtifacts = configurations.runtimeClasspath.get().incoming.artifactView {
        componentFilter { identifier -> identifier is ProjectComponentIdentifier }
    }.artifacts

    projectArtifacts.artifactFiles.files.forEach { artifactFile ->
        from(zipTree(artifactFile))
    }

    manifest {
        attributes("Main-Class" to "io.openauth.sim.cli.MaintenanceCli")
    }
}

publishing {
    publications {
        create("standalone", MavenPublication::class) {
            from(components["java"])
            groupId = providers.gradleProperty("GROUP").get()
            artifactId = "openauth-sim-standalone"
            version = providers.gradleProperty("VERSION_NAME").get()

            pom {
                name.set("OpenAuth Simulator – Standalone Distribution")
                description.set(
                    "Authentication protocol simulator (HOTP/TOTP/OCRA, EMV/CAP, FIDO2/WebAuthn, EUDIW) bundling CLI, REST API, UI, and MCP proxy facades into a single fat JAR."
                )
                url.set("https://github.com/ivanstambuk/openauth-sim")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("ivanstambuk")
                        name.set("Ivan Stambuk")
                        url.set("https://github.com/ivanstambuk")
                    }
                }

                scm {
                    url.set("https://github.com/ivanstambuk/openauth-sim")
                    connection.set("scm:git:https://github.com/ivanstambuk/openauth-sim.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ivanstambuk/openauth-sim.git")
                }

                withXml {
                    val root = asNode()
                    @Suppress("UNCHECKED_CAST")
                    (root.get("dependencies") as? MutableList<Node>)?.forEach { root.remove(it) }

                    val runtimeDeps = configurations.runtimeClasspath.get()
                        .resolvedConfiguration
                        .firstLevelModuleDependencies
                        .filter { it.moduleGroup != project.group }

                    if (runtimeDeps.isNotEmpty()) {
                        val dependenciesNode = root.appendNode("dependencies")
                        runtimeDeps.forEach { dep ->
                            val dependencyNode = dependenciesNode.appendNode("dependency")
                            dependencyNode.appendNode("groupId", dep.moduleGroup)
                            dependencyNode.appendNode("artifactId", dep.moduleName)
                            dependencyNode.appendNode("version", dep.moduleVersion)
                            dependencyNode.appendNode("scope", "runtime")
                        }
                    }
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["standalone"])
}

publishOnCentral {
    repoOwner.set("ivanstambuk")
    projectLongName.set("OpenAuth Simulator – Standalone Distribution")
    projectDescription.set(
        "All-in-one fat JAR for OpenAuth Simulator bundling CLI, REST API, UI, and MCP proxy facades."
    )
}
