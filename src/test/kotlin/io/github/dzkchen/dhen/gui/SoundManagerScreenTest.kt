package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.sound.ANY_PITCH
import io.github.dzkchen.dhen.sound.RECENT_RAW_LIMIT
import io.github.dzkchen.dhen.sound.RecentSound
import io.github.dzkchen.dhen.sound.SoundRuleKey
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
			listOf(SoundCategory.BLOCKS.title, SoundCategory.PASSIVE_MOBS.title, SoundCategory.UI.title),
			filterSounds(sorted, SoundCategory.ALL, "").filterIsInstance<SoundHeader>().map { it.title }
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
			listOf(SoundCategory.RECENT.title),
			filterRecentSounds(sounds, recent, "").filterIsInstance<SoundHeader>().map { it.title }
		)
		val shown = filterRecentSounds(sounds, recent, "").filterIsInstance<ManagedSound>()
		assertEquals(listOf(wolf.identifier, harp.identifier), shown.map { it.identifier })
		assertEquals(listOf(0.75f, 1f), shown.map { it.matchPitch })
		assertEquals("wolf howl · 0.75", shown.first().rowName)
		assertEquals(listOf(harp.identifier), filterRecentSounds(sounds, recent, "harp").filterIsInstance<ManagedSound>().map { it.identifier })
		assertTrue(filterRecentSounds(sounds, recent, "missing").isEmpty())
	}

	@Test
	fun `recent shows twenty rows drawn from the whole frozen snapshot and searches past the cap`() {
		val catalogue = List(30) { sound("block.test_$it.tone") }
		val soundsById = catalogue.associateBy(ManagedSound::identifier)
		val frozen = catalogue.map { RecentSound(it.identifier, 1f) } + RecentSound(id("missing"), 1f)

		val shown = filterRecentSounds(soundsById, frozen, "").filterIsInstance<ManagedSound>()

		assertEquals(20, shown.size)
		assertEquals(catalogue.take(20).map { it.identifier }, shown.map { it.identifier })
		assertEquals(
			listOf(catalogue[27].identifier),
			filterRecentSounds(soundsById, frozen, "test_27").filterIsInstance<ManagedSound>().map { it.identifier }
		)
	}

	@Test
	fun `an unknown identifier does not consume one of the twenty recent rows`() {
		val catalogue = List(20) { sound("block.test_$it.tone") }
		val soundsById = catalogue.associateBy(ManagedSound::identifier)
		val frozen = listOf(RecentSound(id("missing"), 1f)) + catalogue.map { RecentSound(it.identifier, 1f) }

		val shown = filterRecentSounds(soundsById, frozen, "").filterIsInstance<ManagedSound>()

		assertEquals(20, shown.size)
		assertEquals(catalogue.map { it.identifier }, shown.map { it.identifier })
	}

	@Test
	fun `a frozen recent row carries the full recorded pitch into the rule editor`() {
		val wolf = sound("entity.wolf.howl")
		val frozen = listOf(RecentSound(wolf.identifier, 0.53968257f))

		val row = filterRecentSounds(mapOf(wolf.identifier to wolf), frozen, "").filterIsInstance<ManagedSound>().single()

		assertEquals(0.53968257f, row.matchPitch)
		assertEquals("wolf howl · 0.54", row.rowName)
		assertEquals(0.53968257f, row.withPitch(row.matchPitch).matchPitch)
	}

	@Test
	fun `the banner counts starts played since the snapshot and stops at the ring size`() {
		assertEquals("Sound Manager", recentBannerLabel("Sound Manager", 0))
		assertEquals("Sound Manager · 3 played", recentBannerLabel("Sound Manager", 3))
		assertEquals("Sound Manager · 64+ played", recentBannerLabel("Sound Manager", RECENT_RAW_LIMIT))
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

		assertEquals(listOf(SoundCategory.RULES.title), rows.filterIsInstance<SoundHeader>().map { it.title })
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
	fun `suggestions resolve against the catalogue and skip identifiers it does not have`() {
		val harp = sound("block.note_block.harp")
		val click = sound("ui.button.click")
		val known = mapOf(harp.identifier to harp, click.identifier to click)

		val suggested = suggestedReplacements(known)

		assertEquals(listOf(harp.identifier, click.identifier), suggested.map { it.identifier })
		assertEquals(listOf("Arrow hit harp", "UI click"), suggested.map { it.rowName })
		assertEquals(listOf(SoundCategory.BLOCKS, SoundCategory.UI), suggested.map { it.category })
		assertTrue(suggestedReplacements(emptyMap()).isEmpty())
	}

	@Test
	fun `pick mode leads with suggestions only while the query is empty`() {
		val harp = sound("block.note_block.harp")
		val wolf = sound("entity.wolf.howl")
		val suggested = suggestedReplacements(mapOf(harp.identifier to harp))
		val catalogue = filterSounds(listOf(harp, wolf), SoundCategory.ALL, "")

		val led = replacementRows(suggested, catalogue, "")

		assertEquals("Suggested sounds", (led.first() as SoundHeader).title)
		assertEquals(harp.identifier, (led[1] as ManagedSound).identifier)
		assertEquals(catalogue, led.drop(2))
		assertEquals(catalogue, replacementRows(suggested, catalogue, "wolf"))
		assertEquals(catalogue, replacementRows(emptyList(), catalogue, ""))
	}

	@Test
	fun `the window opens centred and a drag stays inside a viewport that fits it`() {
		assertEquals(230, centredWindowStart(1_000, 540))
		assertEquals(157, centredWindowStart(600, 285))
		assertEquals(0, clampAlongTitleBand(-40, 540, 1_000, 108))
		assertEquals(460, clampAlongTitleBand(900, 540, 1_000, 108))
		assertEquals(230, clampAlongTitleBand(230, 540, 1_000, 108))
		assertEquals(0, clampAcrossTitleBand(-40, 285, 600, 26))
		assertEquals(315, clampAcrossTitleBand(600, 285, 600, 26))
		assertEquals(120, clampAcrossTitleBand(120, 285, 600, 26))
	}

	@Test
	fun `a viewport narrower than the window still pans to the far edge`() {
		assertEquals(-200, clampAlongTitleBand(-200, 540, 426, 108))
		assertEquals(-432, clampAlongTitleBand(-900, 540, 426, 108))
		assertEquals(318, clampAlongTitleBand(900, 540, 426, 108))
		assertEquals(0, clampAlongTitleBand(50, 540, 80, 108))
	}

	@Test
	fun `a viewport shorter than the window never lets the title band leave the top`() {
		assertEquals(0, clampAcrossTitleBand(-100, 285, 240, 26))
		assertEquals(214, clampAcrossTitleBand(900, 285, 240, 26))
		assertEquals(100, clampAcrossTitleBand(100, 285, 240, 26))
		assertEquals(0, clampAcrossTitleBand(10, 285, 20, 26))
	}

	@Test
	fun `the title band claims a press across the whole window width and nothing below it`() {
		assertTrue(pressesTitleBand(100, 40, 100, 40, 540, 26))
		assertTrue(pressesTitleBand(639, 65, 100, 40, 540, 26))
		assertFalse(pressesTitleBand(640, 50, 100, 40, 540, 26))
		assertFalse(pressesTitleBand(300, 66, 100, 40, 540, 26))
		assertFalse(pressesTitleBand(300, 39, 100, 40, 540, 26))
	}

	@Test
	fun `a title drag moves the window by the pointer delta and keeps its grab point`() {
		val grabX = 300 - 100
		val grabY = 50 - 40

		assertEquals(400, clampAlongTitleBand(600 - grabX, 540, 1_000, 108))
		assertEquals(90, clampAcrossTitleBand(100 - grabY, 285, 600, 26))
		assertEquals(460, clampAlongTitleBand(1_400 - grabX, 540, 1_000, 108))
		assertEquals(0, clampAcrossTitleBand(-500 - grabY, 285, 600, 26))
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
		return ManagedSound(identifier, soundCleanName(identifier), soundCategory(identifier), SoundEvent.createVariableRangeEvent(identifier))
	}

	private fun id(path: String): Identifier = Identifier.withDefaultNamespace(path)

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
