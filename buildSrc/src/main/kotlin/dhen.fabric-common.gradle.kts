plugins {
	id("dhen.kotlin-common")
}

repositories {
	maven("https://maven.terraformersmc.com/releases/") { name = "Terraformers" }
	maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

java {
	withSourcesJar()
}

tasks.named<ProcessResources>("processResources") {
	val props = mapOf(
		"version" to project.version.toString(),
		"loader_version" to providers.gradleProperty("loader_version").get(),
		"minecraft_version" to providers.gradleProperty("minecraft_version").get(),
		"java_version" to providers.gradleProperty("java_version").get(),
	)
	props.forEach { (key, value) -> inputs.property(key, value) }

	filesMatching("fabric.mod.json") {
		expand(props)
	}
}
