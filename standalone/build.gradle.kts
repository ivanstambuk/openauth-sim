import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
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

val shadowJar by tasks.existing(ShadowJar::class) {
    archiveBaseName.set("openauth-sim-standalone")
    archiveClassifier.set("")
    archiveVersion.set(providers.gradleProperty("VERSION_NAME"))

    manifest {
        attributes(
            "Main-Class" to "io.openauth.sim.cli.MaintenanceCli"
        )
    }

    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(shadowJar)
}

val sourcesJar by tasks.named<Jar>("sourcesJar")
val javadocJar by tasks.named<Jar>("javadocJar")

publishing {
    publications {
        create("standalone", MavenPublication::class) {
            from(components["shadow"])
            groupId = providers.gradleProperty("GROUP").get()
            artifactId = "openauth-sim-standalone"
            version = providers.gradleProperty("VERSION_NAME").get()
            artifact(sourcesJar)
            artifact(javadocJar)

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
