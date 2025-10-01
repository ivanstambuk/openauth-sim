plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    implementation(libs.mapdb)
    implementation(libs.caffeine)

    testFixturesImplementation(libs.mapdb)
}
