import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Starter project for native Kotlin/Java Fabric addon developers. It builds a
// standalone Fabric JAR addon that the Dhen host discovers after restart. The
// host provides the Dhen API and Fabric/Kotlin runtime, so this module compiles
// against core-api but does not bundle it.
plugins {
	id("net.fabricmc.fabric-loom")
	id("org.jetbrains.kotlin.jvm")
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// The Dhen API is provided by the host mod at runtime; compile against it only.
	compileOnly(project(":modules:core-api"))
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
