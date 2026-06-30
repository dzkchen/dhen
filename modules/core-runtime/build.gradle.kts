plugins {
	id("org.jetbrains.kotlin.jvm")
}

dependencies {
	implementation(project(":modules:core-api"))
}

java {
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
	compilerOptions {
		jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
	}
}
