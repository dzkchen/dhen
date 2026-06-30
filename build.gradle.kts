plugins {
	id("org.jetbrains.kotlin.jvm") apply false
	id("org.jetbrains.kotlin.plugin.serialization") apply false
	id("net.fabricmc.fabric-loom") apply false
}

allprojects {
	group = providers.gradleProperty("maven_group").get()
	version = providers.gradleProperty("mod_version").get()
}
