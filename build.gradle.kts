/*
 * Root build configuration for NitroJEx.
 *
 * This file centralizes pinned dependency versions, shared Java 21 compiler/test
 * configuration, and the dedicated e2e/CI test tasks required by the execution
 * plan. Later task cards extend module-local dependencies and source sets, but
 * TASK-001 establishes the common build contract for the whole repository.
 */

import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

val aeronVersion = "1.50.0"
val artioVersion = "0.175"
val sbeVersion = "1.37.1"
val agronaVersion = "2.4.0"
val disruptorVersion = "4.0.0"
val eclipseColVersion = "11.1.0"
val hdrHistogramVersion = "2.2.2"
val junitVersion = "5.10.2"
val assertjVersion = "3.25.3"
val nightConfigVersion = "3.6.7"

allprojects {
    group = "ig.rueishi.nitroj.exchange"
    version = "2.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    val sourceSets = extensions.getByType<SourceSetContainer>()

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testImplementation"("org.assertj:assertj-core:$assertjVersion")
    }

    tasks.withType<JavaCompile> {
        /*
         * Preview/native-access flags are applied uniformly so later cards can add
         * performance-sensitive code without duplicating JVM/compiler plumbing.
         */
        options.compilerArgs.addAll(
            listOf(
                "--enable-preview",
                "--add-exports",
                "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                "-Xlint:all",
            ),
        )
    }

    tasks.withType<Test> {
        /*
         * Shared JUnit 5 and JVM runtime flags for test execution.
         * These flags line up with the plan's Panama/@Contended requirements.
         */
        useJUnitPlatform()
        jvmArgs(
            "--enable-preview",
            "-XX:-RestrictContended",
            "--add-opens",
            "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
        )
    }

    configure<SourceSetContainer> {
        /*
         * Dedicated E2E source set so default test runs stay fast and deterministic.
         * End-to-end coverage is invoked explicitly via ./gradlew e2eTest.
         */
        create("e2eTest") {
            java.srcDir("src/e2eTest/java")
            val mainOutput = sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).get().output
            val testOutput = sourceSets.named(SourceSet.TEST_SOURCE_SET_NAME).get().output
            compileClasspath += mainOutput + testOutput
            runtimeClasspath += output + compileClasspath
        }
    }

    tasks.register<Test>("e2eTest") {
        description = "Runs end-to-end tests with CoinbaseExchangeSimulator"
        group = "verification"
        testClassesDirs = sourceSets["e2eTest"].output.classesDirs
        classpath = sourceSets["e2eTest"].runtimeClasspath
        useJUnitPlatform {
            includeTags("E2E")
        }
    }

    tasks.register<Test>("ciTest") {
        description = "Pre-release CI test suite — includes SlowTest, excludes E2E and production-only tests"
        group = "verification"
        useJUnitPlatform {
            excludeTags("E2E")
            excludeTags("RequiresProductionEnvironment")
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("E2E")
            excludeTags("SlowTest")
            excludeTags("RequiresProductionEnvironment")
        }
    }
}
