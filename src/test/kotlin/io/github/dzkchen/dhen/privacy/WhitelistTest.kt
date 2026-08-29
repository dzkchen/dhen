package io.github.dzkchen.dhen.privacy

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.config.StringSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhitelistTest {
	@Test
	fun `the explicit set survives a round trip and keeps ids no installed mod claims`() {
		val explicit = linkedSetOf("sodium", "uninstalled-mod", "fabric-api")

		assertEquals(explicit, Whitelist.decode(Whitelist.encode(explicit)))
		assertEquals(emptySet<String>(), Whitelist.decode(""))
		assertEquals(setOf("sodium"), Whitelist.decode("  sodium "))
	}

	@Test
	fun `the explicit set persists as one key, so an uninstalled mod keeps its entry`() {
		val saved = StringSetting("Whitelisted")
		saved.value = Whitelist.encode(linkedSetOf("sodium", "uninstalled-mod"))
		val block = SettingCodec.writeInto(JsonObject(), listOf(saved))

		val loaded = StringSetting("Whitelisted")
		SettingCodec.readInto(block, listOf(loaded), "Mod Whitelist")

		assertEquals(setOf("sodium", "uninstalled-mod"), Whitelist.decode(loaded.value))
	}

	@Test
	fun `spoofing outranks the mode the user picked`() {
		assertEquals(ModRegistry.Mode.AUTO, Whitelist.effectiveMode(spoofing = false, custom = false))
		assertEquals(ModRegistry.Mode.CUSTOM, Whitelist.effectiveMode(spoofing = false, custom = true))
		assertEquals(ModRegistry.Mode.BLOCK_ALL, Whitelist.effectiveMode(spoofing = true, custom = false))
		assertEquals(ModRegistry.Mode.BLOCK_ALL, Whitelist.effectiveMode(spoofing = true, custom = true))
	}

	@Test
	fun `only a locked row offers a single option`() {
		assertEquals(listOf(Whitelist.AUTO, Whitelist.CUSTOM), Whitelist.modes(spoofing = false))
		assertEquals(listOf(Whitelist.BLOCK_ALL), Whitelist.modes(spoofing = true))
		assertEquals(listOf(Whitelist.OFF, Whitelist.ON), Whitelist.states(requiredBy = null))
		assertEquals(listOf(Whitelist.REQUIRED), Whitelist.states(requiredBy = "sodium"))
	}

	@Test
	fun `a lock applied before the saved mode is read still gives the choice back`() {
		val mode = SelectorSetting("Mode", Whitelist.AUTO, Whitelist.CHOOSABLE_MODES)
		val saved = JsonObject().also { it.addProperty("Mode", Whitelist.CUSTOM) }

		mode.options = Whitelist.modes(spoofing = true)
		SettingCodec.readInto(saved, listOf(mode), "Mod Whitelist")

		assertEquals(Whitelist.BLOCK_ALL, mode.value)

		mode.options = Whitelist.modes(spoofing = false)

		assertEquals(Whitelist.CUSTOM, mode.value)
	}

	@Test
	fun `an unlocked mode with no snapshot comes back as Auto`() {
		val fresh = SelectorSetting("Mode", Whitelist.AUTO, Whitelist.CHOOSABLE_MODES)

		fresh.options = Whitelist.modes(spoofing = true)
		fresh.options = Whitelist.modes(spoofing = false)

		assertEquals(Whitelist.AUTO, fresh.value)
	}

	@Test
	fun `an implicit entry explains which mod requires it`() {
		assertEquals("sodium", Whitelist.describe("sodium", null))
		assertEquals(
			"fabric-api — required by sodium — uncheck the depender to release",
			Whitelist.describe("fabric-api", "sodium")
		)
	}
}
