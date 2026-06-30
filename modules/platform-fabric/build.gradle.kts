plugins {
	id("net.fabricmc.fabric-loom")
	id("org.jetbrains.kotlin.jvm")
	id("dhen.fabric-common")
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
	implementation("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")
	implementation("maven.modrinth:yacl:${providers.gradleProperty("yacl_version").get()}")

	implementation(project(":modules:core-api"))
	implementation(project(":modules:core-runtime"))
}

// devonly
val copyExampleAddon by tasks.registering(Copy::class) {
	from(project(":example-addons:dungeon-map").tasks.named("jar"))
	into(layout.projectDirectory.dir("run/mods"))
}

tasks.named("runClient") {
	dependsOn(copyExampleAddon)
}

tasks.jar {
	from(rootProject.file("LICENSE")) {
		rename { "${it}_dhen" }
	}
}
