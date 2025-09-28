plugins {
    java
    application
}

dependencies {
    implementation(projects.core)
}

application {
    mainClass.set("io.openauth.sim.cli.MaintenanceCli")
}
