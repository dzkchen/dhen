plugins {
	id("dhen.fabric-common")
}

pluginManager.withPlugin("net.fabricmc.fabric-loom") {
	dependencies {
		add("minecraft", "com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
		add("implementation", "net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
		add("compileOnly", project(":modules:core-api"))
		add("testImplementation", project(":modules:core-api"))
	}
}
