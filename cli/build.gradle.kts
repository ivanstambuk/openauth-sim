plugins {
    java
    application
}

dependencies {
    implementation(projects.core)
    implementation(projects.coreOcra)
    implementation(projects.application)
    implementation(projects.infraPersistence)
    implementation(libs.picocli)

    testImplementation(testFixtures(projects.core))
}

application {
    mainClass.set("io.openauth.sim.cli.MaintenanceCli")
}

tasks.register<JavaExec>("runOcraCli") {
    group = ApplicationPlugin.APPLICATION_GROUP
    description = "Run the OCRA CLI facade"
    mainClass.set("io.openauth.sim.cli.OcraCliLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
}
