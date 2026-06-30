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


include("modules:core-api")
include("modules:core-runtime")
include("modules:platform-fabric")
include("addon-template")
include("example-addons:dungeon-map")
