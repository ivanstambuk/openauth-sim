plugins {
    id("application")
}

application {
    mainClass.set("io.openauth.sim.tools.mcp.McpServerApplication")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
