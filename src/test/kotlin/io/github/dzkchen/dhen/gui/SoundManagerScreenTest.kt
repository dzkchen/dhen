package io.github.dzkchen.dhen.gui

import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundManagerScreenTest {
	@Test
	fun `clean names preserve the source trimming rules`() {
		assertEquals("zombie hurt", soundCleanName(id("entity.hostile.zombie.hurt")))
		assertEquals("wolf howl", soundCleanName(id("entity.wolf.howl")))
		assertEquals("note block harp", soundCleanName(id("block.note_block.harp")))
		assertEquals("custom sound", soundCleanName(Identifier.fromNamespaceAndPath("test", "custom_sound")))
	}

	@Test
	fun `categories follow identifier path prefixes`() {
		assertEquals(SoundCategory.BLOCKS, soundCategory(id("block.note_block.harp")))
		assertEquals(SoundCategory.HOSTILE_MOBS, soundCategory(id("entity.hostile.zombie.hurt")))
		assertEquals(SoundCategory.NEUTRAL_MOBS, soundCategory(id("entity.wolf.howl")))
		assertEquals(SoundCategory.MUSIC, soundCategory(id("music.menu")))
		assertEquals(SoundCategory.AMBIENT, soundCategory(id("ambient.cave")))
		assertEquals(SoundCategory.ITEMS, soundCategory(id("item.armor.equip_iron")))
		assertEquals(SoundCategory.UI, soundCategory(id("ui.button.click")))
		assertEquals(SoundCategory.MISC, soundCategory(id("weather.rain")))
	}

	@Test
	fun `filter groups sorted sounds and searches id plus clean name`() {
		val wolf = sound("entity.wolf.howl")
		val harp = sound("block.note_block.harp")
		val click = sound("ui.button.click")
		val sorted = listOf(harp, wolf, click)

		assertEquals(
			listOf(SoundCategory.BLOCKS, SoundCategory.NEUTRAL_MOBS, SoundCategory.UI),
			filterSounds(sorted, SoundCategory.ALL, "").filterIsInstance<SoundHeader>().map { it.category }
		)
		assertEquals(listOf(wolf), filterSounds(sorted, SoundCategory.ALL, "wolf howl").filterIsInstance<ManagedSound>())
		assertEquals(listOf(harp), filterSounds(sorted, SoundCategory.ALL, "note_block").filterIsInstance<ManagedSound>())
		assertTrue(filterSounds(sorted, SoundCategory.RECENT, "").isEmpty())
	}

	private fun sound(path: String): ManagedSound {
		val identifier = id(path)
		return ManagedSound(identifier, soundCleanName(identifier), soundCategory(identifier))
	}

	private fun id(path: String): Identifier = Identifier.withDefaultNamespace(path)
}
