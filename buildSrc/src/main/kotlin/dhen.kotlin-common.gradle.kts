plugins {
	java
}

val javaVersion = providers.gradleProperty("java_version").get().toInt()

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(javaVersion))
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(javaVersion)
}

dependencies {
	"testImplementation"(platform("org.junit:junit-bom:5.10.2"))
	"testImplementation"("org.junit.jupiter:junit-jupiter")
	"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
	useJUnitPlatform()
}
