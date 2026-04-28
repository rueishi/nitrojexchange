/*
 * Shared module build for generated schemas, common constants, configuration, and
 * low-level utilities. TASK-002 extends the scaffold with SBE code generation so
 * every later card can consume generated codecs from a stable, IDE-visible source
 * tree under src/generated/java.
 */

import org.gradle.api.tasks.JavaExec

plugins {
    `java-library`
}

val sbeVersion = "1.37.1"

sourceSets {
    main {
        /*
         * The execution plan requires generated codecs to live inside the module's
         * source tree rather than a transient build/ directory so they are visible
         * in IDE source tabs and become a stable input for downstream tasks.
         */
        java.srcDir("src/generated/java")
    }
}

configurations {
    create("sbeCodegen")
}

dependencies {
    api("org.agrona:agrona:2.4.0")
    implementation("com.lmax:disruptor:4.0.0")
    implementation("org.eclipse.collections:eclipse-collections:11.1.0")
    implementation("org.hdrhistogram:HdrHistogram:2.2.2")
    implementation("com.electronwill.night-config:toml:3.6.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    "sbeCodegen"("uk.co.real-logic:sbe-tool:$sbeVersion")
}

tasks.register<JavaExec>("generateSBE") {
    description = "Generates SBE encoders and decoders from the authoritative messages.xml schema."
    group = "build"

    classpath = configurations["sbeCodegen"]
    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    args("src/main/resources/messages.xml")

    /*
     * The SBE tool writes directly into src/generated/java because downstream
     * cards depend on these files as checked-in source inputs rather than an
     * ephemeral build output.
     */
    systemProperties["sbe.output.dir"] = "src/generated/java"
    systemProperties["sbe.target.language"] = "Java"
    systemProperties["sbe.java.generate.interfaces"] = "false"
}

tasks.named("compileJava") {
    dependsOn("generateSBE")
}
