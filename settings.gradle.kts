pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
		google()
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
		id("org.jetbrains.kotlin.jvm") version "2.4.10"
		id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
		id("org.jetbrains.compose") version "1.9.3"
	}
}

rootProject.name = "icantpy"
include("dungeons")
include("payload-2612")
include("icantpy-loader")
