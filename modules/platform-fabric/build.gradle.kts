import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	id("org.jetbrains.kotlin.jvm")
}

repositories {
	maven("https://maven.terraformersmc.com/releases/") { name = "Terraformers" }
	maven("https://api.modrinth.com/maven") { name = "Modrinth" }
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

kotlin {
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
	from(rootProject.file("LICENSE")) {
		rename { "${it}_dhen" }
	}
}
