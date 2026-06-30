// Native JAR example addon. It builds as a separate Fabric artifact that can be
// installed locally or through the test catalog and is discovered by the Dhen
// host after restart. It is never required by the core mod build.
plugins {
	id("net.fabricmc.fabric-loom")
	id("org.jetbrains.kotlin.jvm")
	id("dhen.fabric-addon")
}
