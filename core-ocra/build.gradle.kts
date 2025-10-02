plugins {
    `java-library`
}

dependencies {
    api(projects.coreShared)
    implementation(projects.core)

    testImplementation(libs.mapdb)
    testCompileOnly(libs.spotbugs.annotations)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
