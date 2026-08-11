package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModMenuEntryTest {
	@Test
	fun `mod metadata declares a kotlin mod menu entrypoint`() {
		val entry = declaredEntrypoint()
		assertEquals("kotlin", entry.get("adapter").asString)
	}

	@Test
	fun `the declared entrypoint is an object implementing the mod menu api`() {
		val declared = declaredEntrypoint().get("value").asString
		val constants = constantsOf(declared)
		assertTrue(MOD_MENU_API in constants, "$declared does not implement ${MOD_MENU_API.replace('/', '.')}")
		assertTrue(OBJECT_INSTANCE in constants, "$declared is not a Kotlin object, so the kotlin adapter cannot resolve it")
	}

	@Test
	fun `mod metadata refuses mod menu builds older than the compiled api`() {
		val metadata = metadata().getAsJsonObject("breaks")
		assertEquals("<20.0.0-alpha.1", metadata.get("modmenu").asString)
	}

	private fun declaredEntrypoint(): JsonObject {
		val entries = metadata().getAsJsonObject("entrypoints").getAsJsonArray("modmenu")
		assertEquals(1, entries.size())
		return entries[0].asJsonObject
	}

	private fun metadata(): JsonObject =
		javaClass.classLoader.getResourceAsStream(METADATA)!!.use {
			JsonParser.parseReader(it.reader()).asJsonObject
		}

	private fun constantsOf(binaryName: String): String {
		val path = binaryName.replace('.', '/') + ".class"
		val bytecode = javaClass.classLoader.getResourceAsStream(path)
			?: throw AssertionError("$METADATA points the modmenu entrypoint at a missing class: $binaryName")
		return bytecode.use { String(it.readBytes(), Charsets.ISO_8859_1) }
	}

	private companion object {
		const val METADATA = "fabric.mod.json"
		const val MOD_MENU_API = "com/terraformersmc/modmenu/api/ModMenuApi"
		const val OBJECT_INSTANCE = "INSTANCE"
	}
}
