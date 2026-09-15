import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm")
}

version = rootProject.providers.gradleProperty("icantpy_loader_version").get()
group = "net.icantpy.loader"

base {
    archivesName.set("icantpy-loader")
}

repositories {
    mavenCentral()
}

sourceSets {
    named("main") {
        java.srcDir(file("${projectDir.parent}/icantpy-shared/src/main/java"))
    }
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(file("${projectDir.parent}/icantpy-shared/src/main/kotlin"))
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${rootProject.providers.gradleProperty("fabric_kotlin_version").get()}")
    include("net.fabricmc:fabric-language-kotlin:${rootProject.providers.gradleProperty("fabric_kotlin_version").get()}")
    testImplementation(kotlin("test"))
}

tasks.processResources {
    val version = version
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.test {
    useJUnitPlatform()
}

// The resident artifact must not accidentally absorb reloadable feature code.
tasks.jar {
    val forbiddenPrefixes = listOf(
        "net/icantpy/modules/",
        "net/icantpy/gui/",
        "org/jetbrains/compose/",
        "org/jetbrains/skiko/",
        "androidx/",
    )
    doLast {
        val archive = archiveFile.get().asFile
        val leakingPrefixes = forbiddenPrefixes.filter { prefix ->
            zipTree(archive).matching { include("$prefix**") }.files.isNotEmpty()
        }
        check(leakingPrefixes.isEmpty()) {
            "Resident icantpy loader contains reloadable code: ${leakingPrefixes.joinToString()}. " +
                "Move that code to the payload before publishing."
        }
    }
}

val copyPayloadToLoaderRun by tasks.registering(Copy::class) {
    val payloadJar = project(":dungeons").tasks.named("jar")
    dependsOn(payloadJar)
    from(payloadJar.map { it.outputs.files.singleFile })
    into(layout.projectDirectory.dir("run/icantpy/runtime"))
    rename { "icantpy.jar" }
}

tasks.named("runClient") {
    dependsOn(copyPayloadToLoaderRun)
}
