/*
 * JMH benchmark module for V11 hot-path allocation work.
 *
 * The module is intentionally separate from production code. Benchmarks allocate
 * setup fixtures before measurement, then exercise gateway/cluster hot paths
 * with the JMH GC profiler so non-zero B/op is visible before any production
 * zero-allocation claim is made.
 */

plugins {
    java
}

val jmhVersion = "1.37"

sourceSets {
    create("jmh") {
        java.srcDir("src/jmh/java")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-gateway"))
    implementation(project(":platform-cluster"))
    implementation(project(":platform-tooling"))
    implementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")

    "jmhImplementation"(project(":platform-common"))
    "jmhImplementation"(project(":platform-gateway"))
    "jmhImplementation"(project(":platform-cluster"))
    "jmhImplementation"(project(":platform-tooling"))
    "jmhImplementation"("org.openjdk.jmh:jmh-core:$jmhVersion")
    "jmhAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
}

tasks.register<JavaExec>("jmh") {
    description = "Runs V11 hot-path JMH benchmarks with GC allocation profiling."
    group = "verification"
    dependsOn("jmhClasses")
    mainClass.set("org.openjdk.jmh.Main")
    classpath = sourceSets["jmh"].runtimeClasspath
    args("-prof", "gc")
}
