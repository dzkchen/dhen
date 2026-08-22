import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	mavenCentral()
	maven {
		name = "Terraformers"
		url = uri("https://maven.terraformersmc.com/releases/")
		content {
			includeGroup("com.terraformersmc")
		}
	}
	maven {
		name = "Hypixel"
		url = uri("https://repo.hypixel.net/repository/Hypixel")
		content {
			includeGroup("net.hypixel")
		}
	}
	maven {
		name = "Modrinth"
		url = uri("https://api.modrinth.com/maven")
		content {
			includeGroup("maven.modrinth")
		}
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	val hypixelModApiVersion = providers.gradleProperty("hypixel_mod_api_version").get()
	val hypixelModApiMod = "maven.modrinth:hypixel-mod-api:$hypixelModApiVersion"
	implementation("net.hypixel:mod-api:${hypixelModApiVersion.substringBefore('+')}")
	localRuntime(hypixelModApiMod)
	include(hypixelModApiMod)

	val modMenu = "com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}"
	compileOnly(modMenu)
	localRuntime(modMenu)

	localRuntime("net.litetex.mcm:dev-auth-neo:${providers.gradleProperty("dev_auth_version").get()}")

	compileOnly("maven.modrinth:iris:${providers.gradleProperty("iris_version").get()}")

	testImplementation(platform("org.junit:junit-bom:5.11.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
	runConfigs.named("client") {
		vmArg("-Ddevauth.enabled=1")
		vmArg("-Ddevauth.account=dev")
	}
}

tasks.processResources {
	fun floor(property: String) = providers.gradleProperty(property).get().substringBefore('+')
	fun nextMajor(property: String) = "${floor(property).substringBefore('.').toInt() + 1}.0.0"

	val metadata = mapOf(
		"version" to version.toString(),
		"minecraft_version" to floor("minecraft_version"),
		"loader_version" to floor("loader_version"),
		"fabric_api_floor" to floor("fabric_api_version"),
		"fabric_kotlin_floor" to floor("fabric_kotlin_version"),
		"hypixel_mod_api_floor" to floor("hypixel_mod_api_version"),
		"hypixel_mod_api_ceiling" to nextMajor("hypixel_mod_api_version"),
		"modmenu_version" to floor("modmenu_version")
	)
	inputs.properties(metadata)

	filesMatching("fabric.mod.json") {
		expand(metadata)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

tasks.test {
	useJUnitPlatform()
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

java {
	withSourcesJar()

	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

tasks.jar {
	val projectName = project.name

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}
