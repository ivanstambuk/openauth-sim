import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

fun Project.sourceSets(): SourceSetContainer =
    extensions.getByType(SourceSetContainer::class.java)

allprojects {
    afterEvaluate {
        if (path == ":rest-api") {
            tasks.register("runRestApi", JavaExec::class) {
                group = "application"
                description = "Launches the REST operator console locally"
                classpath = sourceSets().getByName("main").runtimeClasspath
                mainClass.set("io.openauth.sim.rest.RestApiApplication")
            }

            tasks.register("printRestApiRuntimeClasspath") {
                group = "help"
                description = "Prints the runtime classpath for RestApiApplication"
                doLast {
                    println(sourceSets().getByName("main").runtimeClasspath.asPath)
                }
            }
        }
    }
}
