/*
 * Root Gradle settings for the NitroJEx multi-module build.
 *
 * TASK-001 owns the initial scaffold only. The project is intentionally limited
 * to the four modules defined by the spec so later task cards can rely on a
 * stable module graph and predictable ownership boundaries.
 */

rootProject.name = "nitrojexchange"

include(
    "platform-common",
    "platform-gateway",
    "platform-cluster",
    "platform-tooling",
    "platform-benchmarks",
)
