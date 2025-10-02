plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(projects.coreShared)
    implementation(libs.mapdb)
    implementation(libs.caffeine)

    testFixturesImplementation(libs.mapdb)
}
