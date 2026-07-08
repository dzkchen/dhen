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
	}
}

// Should match your modid
rootProject.name = "dhen"

file("addons")
	.takeIf { it.isDirectory }
	?.listFiles { file -> file.isDirectory && file.resolve("build.gradle.kts").isFile }
	?.sortedBy { it.name }
	?.forEach { include(":addons:${it.name}") }
