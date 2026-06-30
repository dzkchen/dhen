import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Native JAR example addon. It builds as a separate Fabric artifact that can be
// installed locally or through the test catalog and is discovered by the Dhen
// host after restart. It is never required by the core mod build.
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
