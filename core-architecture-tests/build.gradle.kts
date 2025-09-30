plugins {
    java
}

dependencies {
    testImplementation(projects.core)
    testImplementation(projects.cli)
    testImplementation(projects.restApi)
    testImplementation(projects.ui)
}
