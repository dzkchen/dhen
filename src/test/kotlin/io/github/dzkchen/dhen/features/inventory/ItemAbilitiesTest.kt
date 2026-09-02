package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.module.Category
import net.minecraft.ChatFormatting
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.util.ARGB
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ItemAbilitiesTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in ItemAbilities.settings) setting.reset()
		ItemAbilities.forget()
		ItemAbilities.mageCooldownMultiplier = 1.0
		for (ability in ItemAbility.entries) {
			ability.lastItemClick = FAR_PAST
			ability.lastHeld = FAR_PAST
		}
		SkyBlockLocation.reset()
	}

	private fun onSkyBlock() = SkyBlockLocation.located("mini1A", true, "hub", "Hub")

	@Test
	fun `the module declares the controls its three sources expose`() {
		assertEquals("Item Abilities", ItemAbilities.name)
		assertEquals(Category.INVENTORY, ItemAbilities.category)
		assertEquals(
			listOf(
				"Cooldown Background",
				"Show When Ready",
				"Ready Notification",
				"Ready Sound",
				"Mask Cooldown On Item",
				"Mask As Durability",
				"Phoenix As Top Left",
				"Mask Cooldown Color"
			),
			ItemAbilities.settings.map { it.name }
		)
		assertFalse(ItemAbilities.backgroundSetting.default)
		assertTrue(ItemAbilities.readySetting.default)
		assertFalse(ItemAbilities.readyAlertSetting.default)
		assertTrue(ItemAbilities.maskOnItemSetting.default)
		assertFalse(ItemAbilities.maskDurabilitySetting.default)
		assertFalse(ItemAbilities.phoenixSetting.default)
	}

	@Test
	fun `a giant's sword click followed by its anvil sound starts the thirty second cooldown`() {
		onSkyBlock()
		ItemAbility.GIANTS_SWORD.lastItemClick = NOW - 100L
		ItemAbilities.heard("block.anvil.land", 0.4920635f, 0.5f, NOW)
		assertEquals(30_000L, ItemAbility.GIANTS_SWORD.remaining(NOW))
	}

	@Test
	fun `the same sound without a recent click leaves the ability ready`() {
		onSkyBlock()
		ItemAbility.GIANTS_SWORD.lastItemClick = NOW - 500L
		ItemAbilities.heard("block.anvil.land", 0.4920635f, 0.5f, NOW)
		assertFalse(ItemAbility.GIANTS_SWORD.isOnCooldown(NOW))
	}

	@Test
	fun `a pitch that is off by a hair starts nothing`() {
		onSkyBlock()
		ItemAbility.GIANTS_SWORD.lastItemClick = NOW
		ItemAbilities.heard("block.anvil.land", 0.5f, 0.5f, NOW)
		assertFalse(ItemAbility.GIANTS_SWORD.isOnCooldown(NOW))
	}

	@Test
	fun `tactical insertion arms and spends without waiting for a click`() {
		onSkyBlock()
		ItemAbilities.heard("item.flintandsteel.use", 0.74603176f, 1f, NOW)
		assertEquals(3_000L, ItemAbility.TACTICAL_INSERTION.remaining(NOW))
		assertEquals(ChatFormatting.DARK_PURPLE, ItemAbility.TACTICAL_INSERTION.specialColor)

		ItemAbilities.heard("entity.zombie_villager.cure", 1.8888888f, 0.7f, NOW)
		assertEquals(17_000L, ItemAbility.TACTICAL_INSERTION.remaining(NOW))
		assertNull(ItemAbility.TACTICAL_INSERTION.specialColor)
	}

	@Test
	fun `the tubas only answer their sound while one of them was recently in hand`() {
		onSkyBlock()
		ItemAbility.WEIRDER_TUBA.lastItemClick = NOW
		ItemAbilities.heard("entity.wolf.death", 1f, 0.5f, NOW)
		assertFalse(ItemAbility.WEIRDER_TUBA.isOnCooldown(NOW))

		ItemAbility.WEIRDER_TUBA.lastHeld = NOW - 1_000L
		ItemAbilities.heard("entity.wolf.death", 1f, 0.5f, NOW)
		assertEquals(30_000L, ItemAbility.WEIRDER_TUBA.remaining(NOW))
	}

	@Test
	fun `holy ice tolerates a pitch that only rounds to one point eight`() {
		onSkyBlock()
		ItemAbility.HOLY_ICE.lastItemClick = NOW
		ItemAbilities.heard("entity.generic.drink", 1.7619048f, 1f, NOW)
		assertEquals(4_000L, ItemAbility.HOLY_ICE.remaining(NOW))
	}

	@Test
	fun `the creeper veil lines drive the wither cloak through its three states`() {
		onSkyBlock()
		ItemAbilities.chatted("Creeper Veil Activated!", NOW)
		assertEquals(10_000L, ItemAbility.WITHER_CLOAK.remaining(NOW))
		assertEquals(ChatFormatting.LIGHT_PURPLE, ItemAbility.WITHER_CLOAK.specialColor)

		ItemAbilities.chatted("Creeper Veil De-activated!", NOW)
		assertEquals(5_000L, ItemAbility.WITHER_CLOAK.remaining(NOW))

		ItemAbilities.chatted("Creeper Veil De-activated! (Expired)", NOW)
		assertEquals(10_000L, ItemAbility.WITHER_CLOAK.remaining(NOW))
	}

	@Test
	fun `aligning starts the gyrokinetic wand's first phase and cancelling ragnarock starts its last`() {
		onSkyBlock()
		ItemAbilities.chatted("You aligned 3 other players!", NOW)
		assertEquals(6_000L, ItemAbility.GYROKINETIC_WAND_RIGHT.remaining(NOW))
		assertEquals(ChatFormatting.BLUE, ItemAbility.GYROKINETIC_WAND_RIGHT.specialColor)

		ItemAbilities.chatted("Ragnarock was cancelled due to being hit!", NOW)
		assertEquals(17_000L, ItemAbility.RAGNAROCK_AXE.remaining(NOW))
	}

	@Test
	fun `nothing in chat counts while you are off SkyBlock`() {
		ItemAbilities.chatted("Creeper Veil Activated!", NOW)
		assertFalse(ItemAbility.WITHER_CLOAK.isOnCooldown(NOW))
	}

	@Test
	fun `the action bar walks ragnarock from its cast through its channel to its cancel`() {
		onSkyBlock()
		ItemAbilities.actionBarred("§lCASTING IN 3", NOW)
		assertEquals(3_000L, ItemAbility.RAGNAROCK_AXE.remaining(NOW))
		assertEquals(ChatFormatting.WHITE, ItemAbility.RAGNAROCK_AXE.specialColor)

		ItemAbilities.actionBarred("CASTING", NOW)
		assertEquals(10_000L, ItemAbility.RAGNAROCK_AXE.remaining(NOW))
		assertEquals(ChatFormatting.DARK_PURPLE, ItemAbility.RAGNAROCK_AXE.specialColor)

		ItemAbilities.actionBarred("CASTING", NOW + 1_000L)
		assertEquals(9_000L, ItemAbility.RAGNAROCK_AXE.remaining(NOW + 1_000L))

		ItemAbilities.actionBarred("CANCELLED", NOW + 1_000L)
		assertEquals(17_000L, ItemAbility.RAGNAROCK_AXE.remaining(NOW + 1_000L))
	}

	@Test
	fun `a mana line names the ability it spent, and the same line twice only counts once`() {
		onSkyBlock()
		val line = "3,848/3,473    -24 Mana (Soulcry)    2,507/2,507 Mana"
		ItemAbilities.actionBarred(line, NOW)
		assertEquals(4_000L, ItemAbility.ATOMSPLIT_KATANA.remaining(NOW))

		ItemAbilities.actionBarred(line, NOW + 1_000L)
		assertEquals(3_000L, ItemAbility.ATOMSPLIT_KATANA.remaining(NOW + 1_000L))

		ItemAbilities.actionBarred("3,848/3,473", NOW + 1_000L)
		ItemAbilities.actionBarred(line, NOW + 1_000L)
		assertEquals(4_000L, ItemAbility.ATOMSPLIT_KATANA.remaining(NOW + 1_000L))
	}

	@Test
	fun `a mana line for an ability read only off sounds is ignored`() {
		onSkyBlock()
		ItemAbilities.actionBarred("-150 Mana (Wither Impact)", NOW)
		assertFalse(ItemAbility.WITHER_IMPACT.isOnCooldown(NOW))
	}

	@Test
	fun `a hyperion's three scrolls answer the cure sound as one wither impact`() {
		onSkyBlock()
		val hyperion = scrolled("HYPERION", "ULTIMATE_WITHER_SCROLL")
		ItemAbilities.clicked(hyperion, NOW)
		ItemAbilities.heard("entity.zombie_villager.cure", 0.6984127f, 1f, NOW, hyperion)
		assertEquals(5_000L, ItemAbility.WITHER_IMPACT.remaining(NOW))
		assertFalse(ItemAbility.WITHER_SHIELD_SCROLL.isOnCooldown(NOW))
	}

	@Test
	fun `a lone shield scroll answers the same sound on its own five second phase`() {
		onSkyBlock()
		val valkyrie = scrolled("VALKYRIE", "WITHER_SHIELD_SCROLL")
		ItemAbilities.heard("entity.zombie_villager.cure", 0.6984127f, 1f, NOW, valkyrie)
		assertEquals(5_000L, ItemAbility.WITHER_SHIELD_SCROLL.remaining(NOW))
		assertFalse(ItemAbility.WITHER_IMPACT.isOnCooldown(NOW))

		ItemAbilities.clicked(valkyrie, NOW)
		ItemAbilities.heard("entity.zombie_villager.cure", 0.6984127f, 1f, NOW, valkyrie)
		assertEquals(10_000L, ItemAbility.WITHER_SHIELD_SCROLL.remaining(NOW))
	}

	@Test
	fun `the shadow fury sound only counts while the fury is the item in hand`() {
		onSkyBlock()
		ItemAbility.SHADOW_FURY.lastItemClick = NOW
		ItemAbilities.heard("entity.enderman.teleport", 1f, 1f, NOW, plain("HYPERION"))
		assertFalse(ItemAbility.SHADOW_FURY.isOnCooldown(NOW))

		ItemAbilities.heard("entity.enderman.teleport", 1f, 1f, NOW, plain("STARRED_SHADOW_FURY"))
		assertEquals(15_000L, ItemAbility.SHADOW_FURY.remaining(NOW))
	}

	@Test
	fun `the gyrokinetic wand carries both of its abilities, not just the first`() {
		val wand = plain("GYROKINETIC_WAND")
		val left = ItemAbility.of(wand, "Gyrokinetic Wand")
		assertEquals(ItemAbility.GYROKINETIC_WAND_LEFT, left)
		assertEquals(ItemAbility.GYROKINETIC_WAND_RIGHT, ItemAbility.of(wand, "Gyrokinetic Wand", left))
		assertTrue(ItemAbility.GYROKINETIC_WAND_LEFT.alternativePosition)
		assertFalse(ItemAbility.GYROKINETIC_WAND_RIGHT.alternativePosition)
	}

	@Test
	fun `an item with a single ability offers no second one`() {
		val axe = plain("RAGNAROCK_AXE")
		val only = ItemAbility.of(axe, "Ragnarock Axe")
		assertEquals(ItemAbility.RAGNAROCK_AXE, only)
		assertNull(ItemAbility.of(axe, "Ragnarock Axe", only))
	}

	@Test
	fun `the ready notice answers any ability's availability line and nothing else`() {
		assertTrue(ItemAbilities.abilityReady("Mining Speed Boost is now available!"))
		assertTrue(ItemAbilities.abilityReady("Pickaxe Ability is now available!"))
		assertFalse(ItemAbilities.abilityReady("Your Mining Speed Boost is now available! Use it"))
		assertFalse(ItemAbilities.abilityReady("PARTY! is now available!"))
	}

	@Test
	fun `the background is off by default, and paints the ready state fainter than the cooldown`() {
		onSkyBlock()
		ItemAbility.GIANTS_SWORD.activate(NOW, null, 5_000L)
		ItemAbilities.refresh(NOW)
		assertEquals(0, ItemAbilities.tintOf(ItemAbility.GIANTS_SWORD, true))

		ItemAbilities.backgroundSetting.value = true
		assertEquals(
			ARGB.color(130, legacyColor(ChatFormatting.YELLOW)),
			ItemAbilities.tintOf(ItemAbility.GIANTS_SWORD, true)
		)

		ItemAbilities.refresh(NOW + 5_000L)
		assertEquals(0, ItemAbilities.tintOf(ItemAbility.GIANTS_SWORD, true))
		assertEquals(
			ARGB.color(80, legacyColor(ChatFormatting.GREEN)),
			ItemAbilities.tintOf(ItemAbility.GIANTS_SWORD, false)
		)

		ItemAbilities.readySetting.value = false
		assertEquals(0, ItemAbilities.tintOf(ItemAbility.GIANTS_SWORD, false))
	}

	private fun plain(id: String): SkyBlockItem =
		SkyBlockItems.of(ItemFixture.customData { putString("id", id) })

	private fun scrolled(id: String, vararg scrolls: String): SkyBlockItem = SkyBlockItems.of(
		ItemFixture.customData {
			putString("id", id)
			put("ability_scroll", ListTag().apply { scrolls.forEach { add(StringTag.valueOf(it)) } })
		}
	)

	private companion object {
		const val NOW = 1_000_000L

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
