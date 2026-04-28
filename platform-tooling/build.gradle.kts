/*
 * Tooling module build.
 *
 * This module packages offline/runtime support tools such as the exchange simulator,
 * warmup harness implementation, admin CLI, replay tooling, and the shared E2E test
 * harness. TASK-001 creates only the dependency skeleton and build wiring.
 */

plugins {
    application
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-cluster"))
    implementation(project(":platform-gateway"))
    implementation("io.aeron:aeron-archive:1.50.0")
    implementation("io.aeron:aeron-all:1.50.0")
    implementation("uk.co.real-logic:artio-core:0.175")
    implementation("uk.co.real-logic:artio-codecs:0.175")

    testImplementation("com.lmax:disruptor:4.0.0")

    "e2eTestImplementation"(project(":platform-common"))
    "e2eTestImplementation"(project(":platform-cluster"))
    "e2eTestImplementation"(project(":platform-gateway"))
    "e2eTestImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
    "e2eTestImplementation"("org.assertj:assertj-core:3.25.3")
    "e2eTestImplementation"("org.agrona:agrona:2.4.0")
    "e2eTestImplementation"("io.aeron:aeron-all:1.50.0")
    "e2eTestImplementation"("com.lmax:disruptor:4.0.0")
}

application {
    /*
     * Placeholder application entry point for scaffold-time packaging.
     * TASK-032/TASK-034 introduce the concrete runnable tooling entry points.
     */
    mainClass.set("ig.rueishi.nitroj.exchange.tooling.AdminCli")
}
