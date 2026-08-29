package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.sound.ANY_PITCH
import io.github.dzkchen.dhen.sound.RecentSound
import io.github.dzkchen.dhen.sound.SoundRuleKey
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
		assertEquals("horn blast", soundCleanName(Identifier.fromNamespaceAndPath("dhen", "custom/horn_blast")))
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
		val recent = listOf(RecentSound(wolf.identifier, 0.75f), RecentSound(id("missing"), 1f), RecentSound(harp.identifier, 1f))

		assertEquals(
			listOf(SoundCategory.RECENT),
			filterRecentSounds(sounds, recent, "").filterIsInstance<SoundHeader>().map { it.category }
		)
		val shown = filterRecentSounds(sounds, recent, "").filterIsInstance<ManagedSound>()
		assertEquals(listOf(wolf.identifier, harp.identifier), shown.map { it.identifier })
		assertEquals(listOf(0.75f, 1f), shown.map { it.matchPitch })
		assertEquals("wolf howl · 0.75", shown.first().rowName)
		assertEquals(listOf(harp.identifier), filterRecentSounds(sounds, recent, "harp").filterIsInstance<ManagedSound>().map { it.identifier })
		assertTrue(filterRecentSounds(sounds, recent, "missing").isEmpty())
	}

	@Test
	fun `the rules list is sorted searchable and resolves an identifier the catalogue lost`() {
		val wolf = sound("entity.wolf.howl")
		val harp = sound("block.note_block.harp")
		val known = mapOf(wolf.identifier to wolf, harp.identifier to harp)
		val gone = id("removed.mod.sound")
		val resolve: (SoundRuleKey) -> ManagedSound = { rule ->
			(known[rule.identifier] ?: sound(rule.identifier.path)).withPitch(rule.matchPitch)
		}
		val ruled = listOf(SoundRuleKey(wolf.identifier, 0.75f), SoundRuleKey(gone, ANY_PITCH), SoundRuleKey(harp.identifier, 1f))

		val rows = filterRuleSounds(ruled, "", resolve)

		assertEquals(listOf(SoundCategory.RULES), rows.filterIsInstance<SoundHeader>().map { it.category })
		assertEquals(
			listOf(harp.identifier, wolf.identifier, gone),
			rows.filterIsInstance<ManagedSound>().map { it.identifier }
		)
		assertEquals(
			listOf(harp.identifier),
			filterRuleSounds(ruled, "harp", resolve).filterIsInstance<ManagedSound>().map { it.identifier }
		)
		assertTrue(filterRuleSounds(emptyList(), "", resolve).isEmpty())
	}

	@Test
	fun `replacement filtering puts searchable custom sounds above the Minecraft catalogue`() {
		val customHorn = sound(Identifier.fromNamespaceAndPath("dhen", "custom/horn"))
		val customBell = sound(Identifier.fromNamespaceAndPath("dhen", "custom/bell"))
		val harp = sound("block.note_block.harp")

		val rows = filterReplacementSounds(listOf(customBell, customHorn), listOf(SoundHeader(SoundCategory.BLOCKS), harp), "", "empty")

		assertEquals("Custom", rows.filterIsInstance<SoundHeader>().first().title)
		assertEquals(listOf(customBell, customHorn, harp), rows.filterIsInstance<ManagedSound>())
		assertEquals(
			listOf(customHorn),
			filterReplacementSounds(listOf(customBell, customHorn), emptyList(), "horn", "empty")
				.filterIsInstance<ManagedSound>()
		)
	}

	@Test
	fun `replacement filtering names accepted formats when no custom sound exists`() {
		val rows = filterReplacementSounds(emptyList(), emptyList(), "", "No custom OGG files")

		assertEquals(listOf("No custom OGG files"), rows.filterIsInstance<SoundMessage>().map { it.text })
	}

	@Test
	fun `replacement filtering preserves a Recent or Rules catalogue`() {
		val recent = SoundHeader(SoundCategory.RECENT)
		val harp = sound("block.note_block.harp")

		val rows = filterReplacementSounds(emptyList(), listOf(recent, harp), "", "empty")

		assertEquals(listOf(recent, harp), rows.drop(1))
	}

	@Test
	fun `every slider clamps and snaps inside its own range`() {
		assertEquals(0, steppedSliderValue(80, 100, 140, VOLUME_RANGE))
		assertEquals(0, steppedSliderValue(100, 100, 140, VOLUME_RANGE))
		assertEquals(5, steppedSliderValue(103, 100, 140, VOLUME_RANGE))
		assertEquals(100, steppedSliderValue(170, 100, 140, VOLUME_RANGE))
		assertEquals(200, steppedSliderValue(240, 100, 140, VOLUME_RANGE))
		assertEquals(200, steppedSliderValue(260, 100, 140, VOLUME_RANGE))
		assertEquals(0, steppedSliderValue(80, 100, 110, REPLACEMENT_VOLUME_RANGE))
		assertEquals(50, steppedSliderValue(155, 100, 110, REPLACEMENT_VOLUME_RANGE))
		assertEquals(100, steppedSliderValue(240, 100, 110, REPLACEMENT_VOLUME_RANGE))
		assertEquals(50, steppedSliderValue(80, 100, 110, PITCH_RANGE))
		assertEquals(125, steppedSliderValue(155, 100, 110, PITCH_RANGE))
		assertEquals(200, steppedSliderValue(240, 100, 110, PITCH_RANGE))
		assertEquals("0.50", pitchLabel(50))
		assertEquals("1.25", pitchLabel(125))
		assertEquals("2.00", pitchLabel(200))
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
		return sound(identifier)
	}

	private fun sound(identifier: Identifier): ManagedSound {
		return ManagedSound(identifier, soundCleanName(identifier), soundCategory(identifier), SoundEvent.createVariableRangeEvent(identifier))
	}

	private fun id(path: String): Identifier = Identifier.withDefaultNamespace(path)

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
