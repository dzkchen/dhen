package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
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
		assertEquals(SoundCategory.PASSIVE_MOBS, soundCategory(id("entity.wolf.howl")))
		assertEquals(SoundCategory.MUSIC, soundCategory(id("music.menu")))
		assertEquals(SoundCategory.MUSIC, soundCategory(id("music_disc.pigstep")))
		assertEquals(SoundCategory.AMBIENT, soundCategory(id("ambient.cave")))
		assertEquals(SoundCategory.AMBIENT, soundCategory(id("weather.rain")))
		assertEquals(SoundCategory.ITEMS, soundCategory(id("item.armor.equip_iron")))
		assertEquals(SoundCategory.UI, soundCategory(id("ui.button.click")))
		assertEquals(SoundCategory.MISC, soundCategory(id("intentionally_blank")))
	}

	@Test
	fun `mob categories come from the entity type the sound is named after`() {
		assertEquals(SoundCategory.HOSTILE_MOBS, soundCategory(id("entity.hostile.death")))
		assertEquals(SoundCategory.HOSTILE_MOBS, soundCategory(id("entity.zombified_piglin.angry")))
		assertEquals(SoundCategory.HOSTILE_MOBS, soundCategory(id("entity.blaze.hurt")))
		assertEquals(SoundCategory.PASSIVE_MOBS, soundCategory(id("entity.villager.trade")))
		assertEquals(SoundCategory.PASSIVE_MOBS, soundCategory(id("entity.axolotl.splash")))
		assertEquals(SoundCategory.PASSIVE_MOBS, soundCategory(id("entity.bat.takeoff")))
		assertEquals(SoundCategory.PASSIVE_MOBS, soundCategory(id("entity.iron_golem.hurt")))
		assertEquals(SoundCategory.PLAYER, soundCategory(id("entity.player.levelup")))
		assertEquals(SoundCategory.MISC, soundCategory(id("entity.item.pickup")))
		assertEquals(SoundCategory.MISC, soundCategory(id("entity.experience_orb.pickup")))
		assertEquals(SoundCategory.MISC, soundCategory(id("entity.generic.explode")))
	}

	@Test
	fun `the two placeable entities that drop themselves are knowingly filed as passive`() {
		assertEquals(SoundCategory.PASSIVE_MOBS, soundCategory(id("entity.armor_stand.hit")))
		assertEquals(SoundCategory.PASSIVE_MOBS, soundCategory(id("entity.mannequin.hurt")))
	}

	@Test
	fun `filter groups sorted sounds and searches id plus clean name`() {
		val wolf = sound("entity.wolf.howl")
		val harp = sound("block.note_block.harp")
		val click = sound("ui.button.click")
		val sorted = listOf(harp, wolf, click)

		assertEquals(
			listOf(SoundCategory.BLOCKS, SoundCategory.PASSIVE_MOBS, SoundCategory.UI),
			filterSounds(sorted, SoundCategory.ALL, "").filterIsInstance<SoundHeader>().map { it.category }
		)
		assertEquals(listOf(wolf), filterSounds(sorted, SoundCategory.ALL, "wolf howl").filterIsInstance<ManagedSound>())
		assertEquals(listOf(harp), filterSounds(sorted, SoundCategory.ALL, "note_block").filterIsInstance<ManagedSound>())
		assertTrue(filterSounds(sorted, SoundCategory.RECENT, "").isEmpty())
	}

	@Test
	fun `recent sounds keep source order skip unknown ids and search without regrouping`() {
		val wolf = sound("entity.wolf.howl")
		val harp = sound("block.note_block.harp")
		val sounds = mapOf(wolf.identifier to wolf, harp.identifier to harp)
		val recent = listOf(wolf.identifier, id("missing"), harp.identifier)

		assertEquals(
			listOf(SoundCategory.RECENT),
			filterRecentSounds(sounds, recent, "").filterIsInstance<SoundHeader>().map { it.category }
		)
		assertEquals(listOf(wolf, harp), filterRecentSounds(sounds, recent, "").filterIsInstance<ManagedSound>())
		assertEquals(listOf(harp), filterRecentSounds(sounds, recent, "harp").filterIsInstance<ManagedSound>())
		assertTrue(filterRecentSounds(sounds, recent, "missing").isEmpty())
	}

	@Test
	fun `sound slider clamps and snaps across the zero to two hundred percent range`() {
		assertEquals(0, soundVolumePercent(80, 100, 140))
		assertEquals(0, soundVolumePercent(100, 100, 140))
		assertEquals(5, soundVolumePercent(103, 100, 140))
		assertEquals(100, soundVolumePercent(170, 100, 140))
		assertEquals(200, soundVolumePercent(240, 100, 140))
		assertEquals(200, soundVolumePercent(260, 100, 140))
	}

	@Test
	fun `wheel scroll eases for two hundred milliseconds and then holds`() {
		assertEquals(12f, animatedSoundScroll(12f, 112f, 0L))
		assertTrue(animatedSoundScroll(12f, 112f, 100L) in 62f..111f)
		assertEquals(112f, animatedSoundScroll(12f, 112f, 200L))
		assertEquals(112f, animatedSoundScroll(12f, 112f, 500L))
	}

	@Test
	fun `scrollbar drag preserves its grab point and reaches both ends`() {
		assertEquals(28, ClickGuiScroll.thumbHeight(215, 215, 2_000, 28))
		assertEquals(0, soundScrollOffset(40, 40, 215, 28, 14, 500))
		assertEquals(500, soundScrollOffset(241, 40, 215, 28, 14, 500))
		assertEquals(249, soundScrollOffset(147, 40, 215, 28, 14, 500))
	}

	private fun sound(path: String): ManagedSound {
		val identifier = id(path)
		return ManagedSound(identifier, soundCleanName(identifier), soundCategory(identifier), SoundEvent.createVariableRangeEvent(identifier))
	}

	private fun id(path: String): Identifier = Identifier.withDefaultNamespace(path)

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
