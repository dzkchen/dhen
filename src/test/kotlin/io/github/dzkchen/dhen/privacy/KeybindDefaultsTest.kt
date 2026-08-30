package io.github.dzkchen.dhen.privacy

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class KeybindDefaultsTest {
	@AfterEach
	fun reset() {
		KeybindDefaults.reset()
		LanguageKeys.uninstall()
	}

	@Test
	fun `only vanilla mappings retain their original key and resolve its current display name`() {
		val initialGeneration = KeybindDefaults.generation()
		val vanillaName = "key.dhen.test.vanilla"
		val modName = "key.dhen.test.mod"
		LanguageKeys.beginReload()
		LanguageKeys.recordVanilla(vanillaName)
		LanguageKeys.recordMod("dhen-test", modName)
		LanguageKeys.commitReload()
		val vanilla = KeyMapping(vanillaName, ORIGINAL_KEY, KeyMapping.Category.MOVEMENT)
		val mod = KeyMapping(modName, MOD_KEY, KeyMapping.Category.MISC)
		vanilla.setKey(InputConstants.Type.KEYSYM.getOrCreate(REBOUND_KEY))

		KeybindDefaults.initialize(arrayOf(vanilla, mod))

		val defaultKey = KeybindDefaults.defaultKey(vanillaName)
		assertSame(vanilla.defaultKey, defaultKey)
		assertEquals(vanilla.defaultKey.displayName.string, defaultKey?.displayName?.string)
		assertNotEquals(vanilla.translatedKeyMessage.string, defaultKey?.displayName?.string)
		assertNull(KeybindDefaults.defaultKey(modName))

		KeybindDefaults.reset()

		assertEquals(initialGeneration + 1, KeybindDefaults.generation())
	}

	private companion object {
		const val ORIGINAL_KEY = 65
		const val REBOUND_KEY = 66
		const val MOD_KEY = 67
	}
}
