import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

version = "1.0.3.94"
group = "net.icantpy"

base {
    archivesName.set("icantpy")
}

val payloadMinecraftVersion = "26.1.2"
val payloadFabricApiVersion = "0.155.2+26.1.2"

fun isFatjarGroup(group: String) = group.startsWith("org.jetbrains.compose")
    || group.startsWith("org.jetbrains.skiko")
    || group.startsWith("org.jetbrains.skia")
    || group.startsWith("androidx.")

val skikoNatives = arrayListOf<java.io.File>()
val dungeonsDir = file("${projectDir.parent}/dungeons")
val sharedDir = file("${projectDir.parent}/icantpy-shared")

repositories {
    mavenCentral()
    google()
}

sourceSets {
    named("main") {
        java.srcDir(file("$dungeonsDir/src/main/java"))
        java.srcDir(file("$sharedDir/src/main/java"))
        resources.srcDir(file("$dungeonsDir/src/main/resources"))
        resources.srcDir(file("$sharedDir/src/main/resources"))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$payloadMinecraftVersion")
    implementation("net.fabricmc:fabric-loader:${rootProject.providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:$payloadFabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:${rootProject.providers.gradleProperty("fabric_kotlin_version").get()}")
    include("net.fabricmc:fabric-language-kotlin:${rootProject.providers.gradleProperty("fabric_kotlin_version").get()}")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    val transitiveInclude by configurations.creating
    transitiveInclude(implementation(compose.material3)!!)
    transitiveInclude(implementation(compose.materialIconsExtended)!!)
    transitiveInclude(implementation(compose.desktop.windows_x64)!!)
    transitiveInclude(implementation(compose.desktop.windows_arm64)!!)
    transitiveInclude(implementation("androidx.collection:collection:1.5.0")!!)

    transitiveInclude.resolvedConfiguration.resolvedArtifacts.forEach {
        val group = it.moduleVersion.id.group
        when {
            group == "org.jetbrains.skiko" -> skikoNatives.add(it.file)
            isFatjarGroup(group) -> {}
            else -> include(it.moduleVersion.id.toString())
        }
    }
}

tasks.processResources {
    val version = version
    inputs.property("version", version)
    inputs.property("minecraft_version", payloadMinecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to version, "minecraft_version" to payloadMinecraftVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(file("$dungeonsDir/src/main/kotlin"))
    sourceSets.getByName("main").kotlin.srcDir(file("$sharedDir/src/main/kotlin"))
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    archiveFileName.set("icantpy-${version}-mc26.1.2.jar")
    val includeFiles = configurations.getByName("transitiveInclude")
        .resolvedConfiguration.resolvedArtifacts
        .filter { it.file.name.endsWith(".jar") && isFatjarGroup(it.moduleVersion.id.group) }
        .map { it.file }

    from(includeFiles.map { zipTree(it) }) {
        exclude("**/*.dll", "**/*.so", "**/*.dylib")
    }
    from(skikoNatives.map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(rootProject.file("LICENSE")) {
        rename { "${it}_icantpy" }
    }
}
