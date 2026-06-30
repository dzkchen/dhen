pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
		id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlin_version")
		id("org.jetbrains.kotlin.plugin.serialization") version providers.gradleProperty("kotlin_version")
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.terraformersmc.com/releases/") { name = "Terraformers" }
		maven("https://api.modrinth.com/maven") { name = "Modrinth" }
	}
}

// Should match your modid
rootProject.name = "dhen"


include("modules:core-api")
include("modules:core-runtime")
include("modules:platform-fabric")
include("addon-template")
include("example-addons:dungeon-map")
