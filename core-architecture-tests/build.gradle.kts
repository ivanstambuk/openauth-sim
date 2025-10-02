plugins {
    java
}

dependencies {
    testImplementation(projects.core)
    testImplementation(projects.application)
    testImplementation(projects.cli)
    testImplementation(projects.restApi)
    testImplementation(projects.ui)
}
