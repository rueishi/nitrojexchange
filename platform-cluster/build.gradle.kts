/*
 * Cluster process module build.
 *
 * This module will eventually contain the Aeron Cluster service and all business
 * logic. The initial scaffold exposes only the runtime dependencies and standalone
 * packaging hooks required by TASK-001.
 */

plugins {
    application
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(project(":platform-common"))
    implementation("io.aeron:aeron-cluster:1.50.0")
    implementation("io.aeron:aeron-archive:1.50.0")
    implementation("io.aeron:aeron-all:1.50.0")
    implementation("org.agrona:agrona:2.4.0")
    implementation("org.eclipse.collections:eclipse-collections:11.1.0")
}

application {
    /*
     * Placeholder main class for scaffold-time packaging.
     * TASK-027 supplies the concrete ClusterMain implementation.
     */
    mainClass.set("ig.rueishi.nitroj.exchange.cluster.ClusterMain")
}
