package io.github.dzkchen.dhen.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ShaderOwnerTest {
	@TempDir
	lateinit var root: Path

	@Test
	fun `shader namespaces map to the mod that actually owns their files`() {
		val shader = root.resolve("assets/render-lib/shaders/core/program.json")
		Files.createDirectories(shader.parent)
		Files.writeString(shader, "{}")
		Files.createDirectories(root.resolve("assets/empty/shaders"))
		val vanilla = root.resolve("assets/minecraft/shaders/core/ignored.json")
		Files.createDirectories(vanilla.parent)
		Files.writeString(vanilla, "{}")
		val owners = HashMap<String, String>()

		assertTrue(ModRegistry.scanShaderRoot("actual-mod", root, owners))

		assertEquals(mapOf("render-lib" to "actual-mod"), owners)
	}
}
