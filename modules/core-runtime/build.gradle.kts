plugins {
	id("org.jetbrains.kotlin.jvm")
	id("dhen.kotlin-common")
}

dependencies {
	implementation(project(":modules:core-api"))

	testImplementation("com.google.code.gson:gson:2.11.0")
}
