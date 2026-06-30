// Starter project for native Kotlin/Java Fabric addon developers. It builds a
// standalone Fabric JAR addon that the Dhen host discovers after restart. The
// host provides the Dhen API and Fabric/Kotlin runtime, so this module compiles
// against core-api but does not bundle it.
plugins {
	id("net.fabricmc.fabric-loom")
	id("org.jetbrains.kotlin.jvm")
	id("dhen.fabric-addon")
}
