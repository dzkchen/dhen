package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import net.minecraft.ChatFormatting
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ItemAbilityTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		ItemAbility.forgetAll()
		for (ability in ItemAbility.entries) {
			ability.lastItemClick = FAR_PAST
			ability.lastHeld = FAR_PAST
		}
		ItemAbilities.mageCooldownMultiplier = 1.0
	}

	@Test
	fun `the table carries every ability the source lists`() {
		assertEquals(42, ItemAbility.entries.size)
		assertEquals(5_000L, ItemAbility.WITHER_IMPACT.cooldownMillis())
		assertEquals(10_000L, ItemAbility.WITHER_SHIELD_SCROLL.cooldownMillis())
		assertEquals(30_000L, ItemAbility.GYROKINETIC_WAND_LEFT.cooldownMillis())
		assertEquals(10_000L, ItemAbility.GYROKINETIC_WAND_RIGHT.cooldownMillis())
		assertEquals(20_000L, ItemAbility.RAGNAROCK_AXE.cooldownMillis())
		assertEquals(7_000L, ItemAbility.WAND_OF_ATONEMENT.cooldownMillis())
		assertEquals(2_000L, ItemAbility.STARLIGHT_WAND.cooldownMillis())
		assertEquals(3_000L, ItemAbility.ECHO.cooldownMillis())
	}

	@Test
	fun `only the two abilities the source shifts draw at the alternative position`() {
		assertEquals(
			listOf(ItemAbility.WITHER_SHIELD_SCROLL, ItemAbility.GYROKINETIC_WAND_LEFT),
			ItemAbility.entries.filter { it.alternativePosition }
		)
	}

	@Test
	fun `only the eight named abilities are read off the action bar`() {
		assertEquals(
			listOf(
				"Ender Warp",
				"Throw",
				"Fire Veil",
				"Ink Bomb",
				"Speed Boost",
				"Track",
				"Soulcry",
				"Echo"
			),
			ItemAbility.entries.filter { !it.newVariant }.map { it.abilityName }
		)
		assertEquals(ItemAbility.ATOMSPLIT_KATANA, ItemAbility.byAbilityName("Soulcry"))
		assertNull(ItemAbility.byAbilityName("Wither Impact"))
	}

	@Test
	fun `the mage reduction scales every ability except the four that opt out and the two flat ones`() {
		ItemAbilities.mageCooldownMultiplier = 0.5
		assertEquals(1_500L, ItemAbility.GOLEM_SWORD.cooldownMillis())
		assertEquals(15_000L, ItemAbility.GIANTS_SWORD.cooldownMillis())
		assertEquals(5_000L, ItemAbility.WITHER_IMPACT.cooldownMillis())
		assertEquals(10_000L, ItemAbility.WITHER_SHIELD_SCROLL.cooldownMillis())
		assertEquals(30_000L, ItemAbility.ROGUE_SWORD.cooldownMillis())
		assertEquals(4_000L, ItemAbility.ATOMSPLIT_KATANA.cooldownMillis())
		assertEquals(7_000L, ItemAbility.WAND_OF_ATONEMENT.cooldownMillis())
		assertEquals(20_000L, ItemAbility.RAGNAROCK_AXE.cooldownMillis())
	}

	@Test
	fun `an item is matched by its SkyBlock id, and the older entries by their name`() {
		assertEquals(ItemAbility.GYROKINETIC_WAND_LEFT, ItemAbility.byId("GYROKINETIC_WAND"))
		assertEquals(ItemAbility.ICE_SPRAY_WAND, ItemAbility.byId("STARRED_ICE_SPRAY_WAND"))
		assertEquals(ItemAbility.WAND_OF_ATONEMENT, ItemAbility.byId("WAND_OF_HEALING"))
		assertNull(ItemAbility.byId("Livid Dagger"))
		assertTrue(ItemAbility.LIVID_DAGGER.matches("", "Starred Livid Dagger"))
		assertTrue(ItemAbility.ATOMSPLIT_KATANA.matches("", "Voidedge Katana"))
		assertFalse(ItemAbility.ENDER_BOW.matches("ENDER_BOW", "Terminator"))
	}

	@Test
	fun `the ultimate wither scroll counts as all three, and all three read as wither impact`() {
		val hyperion = SkyBlockItems.of(
			ItemFixture.customData {
				putString("id", "HYPERION")
				put("ability_scroll", ListTag().apply { add(StringTag.valueOf("ULTIMATE_WITHER_SCROLL")) })
			}
		)
		assertEquals(SCROLL_ALL, ItemAbility.scrollsOf(hyperion))
		assertEquals(ItemAbility.WITHER_IMPACT, ItemAbility.of(hyperion, "Hyperion"))

		val shielded = SkyBlockItems.of(
			ItemFixture.customData {
				putString("id", "VALKYRIE")
				put("ability_scroll", ListTag().apply { add(StringTag.valueOf("wither_shield_scroll")) })
			}
		)
		assertEquals(SCROLL_SHIELD, ItemAbility.scrollsOf(shielded))
		assertEquals(ItemAbility.WITHER_SHIELD_SCROLL, ItemAbility.of(shielded, "Valkyrie"))
	}

	@Test
	fun `a custom cooldown leaves exactly that much time on the clock`() {
		val now = 1_000_000L
		ItemAbility.RAGNAROCK_AXE.activate(now, ChatFormatting.WHITE, 3_000L)
		assertTrue(ItemAbility.RAGNAROCK_AXE.isOnCooldown(now))
		assertEquals(3_000L, ItemAbility.RAGNAROCK_AXE.remaining(now))
		assertFalse(ItemAbility.RAGNAROCK_AXE.isOnCooldown(now + 3_000L))
	}

	@Test
	fun `the countdown shows one decimal under a second and six tenths and a whole second above it`() {
		val now = 1_000_000L
		val builder = StringBuilder()
		ItemAbility.GIANTS_SWORD.activate(now, null, 1_500L)
		ItemAbility.GIANTS_SWORD.refresh(now, "R", builder)
		assertEquals("1.5", ItemAbility.GIANTS_SWORD.label)

		ItemAbility.GIANTS_SWORD.activate(now, null, 1_600L)
		ItemAbility.GIANTS_SWORD.refresh(now, "R", builder)
		assertEquals("2", ItemAbility.GIANTS_SWORD.label)

		ItemAbility.GIANTS_SWORD.activate(now, null, 9_200L)
		ItemAbility.GIANTS_SWORD.refresh(now, "R", builder)
		assertEquals("10", ItemAbility.GIANTS_SWORD.label)
	}

	@Test
	fun `the countdown turns red in its last six hundred milliseconds and green when it is ready`() {
		val now = 1_000_000L
		val builder = StringBuilder()
		ItemAbility.GIANTS_SWORD.activate(now, null, 700L)
		ItemAbility.GIANTS_SWORD.refresh(now, "R", builder)
		assertEquals(legacyColor(ChatFormatting.YELLOW), ItemAbility.GIANTS_SWORD.ink)

		ItemAbility.GIANTS_SWORD.activate(now, null, 500L)
		ItemAbility.GIANTS_SWORD.refresh(now, "R", builder)
		assertEquals(legacyColor(ChatFormatting.RED), ItemAbility.GIANTS_SWORD.ink)
		assertTrue(ItemAbility.GIANTS_SWORD.onCooldown)

		ItemAbility.GIANTS_SWORD.refresh(now + 500L, "R", builder)
		assertEquals(legacyColor(ChatFormatting.GREEN), ItemAbility.GIANTS_SWORD.ink)
		assertEquals("R", ItemAbility.GIANTS_SWORD.label)
		assertFalse(ItemAbility.GIANTS_SWORD.onCooldown)

		ItemAbility.GIANTS_SWORD.refresh(now + 500L, "", builder)
		assertEquals("", ItemAbility.GIANTS_SWORD.label)
	}

	@Test
	fun `a special colour outlives the number it was set with`() {
		val now = 1_000_000L
		val builder = StringBuilder()
		ItemAbility.TACTICAL_INSERTION.activate(now, ChatFormatting.DARK_PURPLE, 3_000L)
		ItemAbility.TACTICAL_INSERTION.refresh(now, "R", builder)
		assertEquals(legacyColor(ChatFormatting.DARK_PURPLE), ItemAbility.TACTICAL_INSERTION.ink)
	}

	@Test
	fun `ragnarock and the gyrokinetic wand roll into their next phase when the first one runs out`() {
		val now = 1_000_000L
		val builder = StringBuilder()

		ItemAbility.RAGNAROCK_AXE.activate(now, ChatFormatting.DARK_PURPLE, 10_000L)
		ItemAbility.RAGNAROCK_AXE.refresh(now + 10_000L, "R", builder)
		assertTrue(ItemAbility.RAGNAROCK_AXE.onCooldown)
		assertEquals(7_000L, ItemAbility.RAGNAROCK_AXE.remaining(now + 10_000L))
		assertNull(ItemAbility.RAGNAROCK_AXE.specialColor)

		ItemAbility.GYROKINETIC_WAND_RIGHT.activate(now, ChatFormatting.BLUE, 6_000L)
		ItemAbility.GYROKINETIC_WAND_RIGHT.refresh(now + 6_000L, "R", builder)
		assertEquals(4_000L, ItemAbility.GYROKINETIC_WAND_RIGHT.remaining(now + 6_000L))
	}

	@Test
	fun `a sound only starts a cooldown when the item was clicked in the last four hundred milliseconds`() {
		val now = 1_000_000L
		ItemAbility.GIANTS_SWORD.lastItemClick = now - 399L
		ItemAbility.GIANTS_SWORD.sound(now)
		assertTrue(ItemAbility.GIANTS_SWORD.isOnCooldown(now))

		ItemAbility.forgetAll()
		ItemAbility.GIANTS_SWORD.lastItemClick = now - 400L
		ItemAbility.GIANTS_SWORD.sound(now)
		assertFalse(ItemAbility.GIANTS_SWORD.isOnCooldown(now))
	}

	@Test
	fun `an item counts as recently held for thirty seconds`() {
		val now = 1_000_000L
		ItemAbility.WEIRD_TUBA.lastHeld = now - 29_999L
		assertTrue(ItemAbility.WEIRD_TUBA.recentlyHeld(now))
		ItemAbility.WEIRD_TUBA.lastHeld = now - 30_000L
		assertFalse(ItemAbility.WEIRD_TUBA.recentlyHeld(now))
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
