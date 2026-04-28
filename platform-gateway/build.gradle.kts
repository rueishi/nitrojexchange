/*
 * Gateway process module build.
 *
 * The gateway will host Artio connectivity, the ingress handlers, and the Aeron
 * publisher path. TASK-001 wires only the module dependencies needed for later
 * implementation tasks to compile incrementally.
 */

plugins {
    application
    id("com.github.johnrengelman.shadow")
}

val artioVersion = "0.175"
val jsonVersion = "20240303"
val affinityVersion = "3.23.3"
val generatedFix42SourceDir = layout.projectDirectory.dir("src/generated/fix42/java")
val generatedFix44SourceDir = layout.projectDirectory.dir("src/generated/fix44/java")
val generatedFixt11Fix50Sp2SourceDir = layout.projectDirectory.dir("src/generated/fixt11-fix50sp2/java")
val generatedDictionaryDir = layout.buildDirectory.dir("generated/fix-dictionaries")

/*
 * Artio code generation is version-isolated so multiple FIX versions can
 * coexist without generated package collisions.
 */
val fixCodegen by configurations.creating

sourceSets {
    main {
        java.srcDir(generatedFix42SourceDir)
        java.srcDir(generatedFix44SourceDir)
        java.srcDir(generatedFixt11Fix50Sp2SourceDir)
    }
}

dependencies {
    implementation(project(":platform-common"))
    implementation("uk.co.real-logic:artio-core:$artioVersion")
    implementation("uk.co.real-logic:artio-codecs:$artioVersion")
    implementation("io.aeron:aeron-all:1.50.0")
    implementation("org.agrona:agrona:2.4.0")
    implementation("com.lmax:disruptor:4.0.0")
    /*
     * TASK-015 uses OpenHFT Affinity for optional CPU pinning. CpuConfig uses
     * zero as the dev/test no-op sentinel, so the dependency is only exercised
     * when production config asks for a positive CPU ID.
     */
    implementation("net.openhft:affinity:$affinityVersion")
    /*
     * TASK-014 parses Coinbase /accounts responses with org.json exactly as the
     * implementation plan specifies. Keeping the dependency local to the gateway
     * avoids leaking REST-only JSON parsing into common hot-path modules.
     */
    implementation("org.json:json:$jsonVersion")

    /*
     * The code generator is isolated from runtime dependencies so Gradle can
     * execute codec generation deterministically before Java compilation.
     */
    fixCodegen("uk.co.real-logic:artio-codecs:$artioVersion")
}

val prepareFix44Dictionary by tasks.registering(Copy::class) {
    description = "Prepares the temporary FIX 4.4 dictionary used by version-isolated Artio codegen"
    group = "build"
    from(layout.projectDirectory.file("src/main/resources/coinbase-fix42-dictionary.xml"))
    into(generatedDictionaryDir)
    rename { "generated-fix44-dictionary.xml" }
    /*
     * V11 introduces the package/version isolation before venue-complete FIX 4.4
     * dictionaries are curated. The generated temporary dictionary keeps the
     * current message surface but advertises FIX.4.4 so codegen and package
     * coexistence can be validated independently.
     */
    filter { line: String ->
        line.replace("major=\"4\" minor=\"2\"", "major=\"4\" minor=\"4\"")
            .replace("Coinbase FIX 4.2 dictionary", "Generated FIX 4.4 dictionary")
    }
}

val prepareFixt11Dictionary by tasks.registering(Copy::class) {
    description = "Prepares the temporary FIXT.1.1 transport dictionary used by version-isolated Artio codegen"
    group = "build"
    from(layout.projectDirectory.file("src/main/resources/coinbase-fix42-dictionary.xml"))
    into(generatedDictionaryDir)
    rename { "generated-fixt11-transport-dictionary.xml" }
    filter { line: String ->
        line.replace("type=\"FIX\" major=\"4\" minor=\"2\"", "type=\"FIXT\" major=\"1\" minor=\"1\"")
            .replace("Coinbase FIX 4.2 dictionary", "Generated FIXT.1.1 transport dictionary")
    }
}

val prepareFix50Sp2Dictionary by tasks.registering(Copy::class) {
    description = "Prepares the temporary FIX 5.0SP2 application dictionary used by version-isolated Artio codegen"
    group = "build"
    from(layout.projectDirectory.file("src/main/resources/coinbase-fix42-dictionary.xml"))
    into(generatedDictionaryDir)
    rename { "generated-fix50sp2-dictionary.xml" }
    filter { line: String ->
        line.replace("type=\"FIX\" major=\"4\" minor=\"2\"", "type=\"FIX\" major=\"5\" minor=\"0\" servicepack=\"2\"")
            .replace("Coinbase FIX 4.2 dictionary", "Generated FIX 5.0SP2 application dictionary")
    }
}

tasks.register<JavaExec>("generateFix42Codecs") {
    description = "Generates version-isolated Artio FIX codecs for FIX 4.2"
    group = "build"
    classpath = fixCodegen
    mainClass.set("uk.co.real_logic.artio.dictionary.CodecGenerationTool")
    jvmArgs("--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED")
    systemProperty("fix.codecs.parent_package", "ig.rueishi.nitroj.exchange.fix.fix42")
    args(
        generatedFix42SourceDir.asFile.absolutePath,
        layout.projectDirectory.file("src/main/resources/coinbase-fix42-dictionary.xml").asFile.absolutePath,
    )
}

tasks.register<JavaExec>("generateFix44Codecs") {
    description = "Generates version-isolated Artio FIX codecs for FIX 4.4"
    group = "build"
    dependsOn(prepareFix44Dictionary)
    classpath = fixCodegen
    mainClass.set("uk.co.real_logic.artio.dictionary.CodecGenerationTool")
    jvmArgs("--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED")
    systemProperty("fix.codecs.parent_package", "ig.rueishi.nitroj.exchange.fix.fix44")
    args(
        generatedFix44SourceDir.asFile.absolutePath,
        generatedDictionaryDir.map { it.file("generated-fix44-dictionary.xml").asFile.absolutePath }.get(),
    )
}

tasks.register<JavaExec>("generateFixt11Fix50Sp2Codecs") {
    description = "Generates version-isolated Artio FIX codecs for FIXT.1.1 / FIX 5.0SP2"
    group = "build"
    dependsOn(prepareFixt11Dictionary, prepareFix50Sp2Dictionary)
    classpath = fixCodegen
    mainClass.set("uk.co.real_logic.artio.dictionary.CodecGenerationTool")
    jvmArgs("--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED")
    systemProperty("fix.codecs.parent_package", "ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2")
    args(
        generatedFixt11Fix50Sp2SourceDir.asFile.absolutePath,
        /*
         * The task prepares both transport and application dictionaries so the
         * build owns the intended V11 split inputs. Until a curated FIXT.1.1
         * transport dictionary without duplicate application messages lands, the
         * generated compatibility package is emitted from the FIX 5.0SP2
         * application surface alone to keep the repository buildable.
         */
        generatedDictionaryDir.map {
            it.file("generated-fix50sp2-dictionary.xml").asFile.absolutePath
        }.get(),
    )
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("generateFix42Codecs", "generateFix44Codecs", "generateFixt11Fix50Sp2Codecs")
}

application {
    /*
     * Placeholder main class for scaffold-time packaging.
     * TASK-016 will replace this with the real GatewayMain entry point.
     */
    mainClass.set("ig.rueishi.nitroj.exchange.gateway.GatewayMain")
}
