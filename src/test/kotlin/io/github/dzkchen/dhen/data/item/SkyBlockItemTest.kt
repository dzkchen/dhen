package io.github.dzkchen.dhen.data.item

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SkyBlockItemTest {
	@Test
	fun `a plain item is its own market id`() {
		val item = SkyBlockItems.of(stack { putString("id", "ASPECT_OF_THE_END"); putString("uuid", UUID) })

		assertEquals("ASPECT_OF_THE_END", item.id)
		assertEquals("ASPECT_OF_THE_END", item.marketId)
		assertEquals(UUID, item.uuid)
	}

	@Test
	fun `a colon in the Hypixel id becomes a dash`() {
		assertEquals("PET_SKIN-BLUE_WHALE", SkyBlockItems.of(stack { putString("id", "PET_SKIN:BLUE_WHALE") }).id)
	}

	@Test
	fun `an enchanted book takes the market id of its single enchantment, upper-cased`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "ENCHANTED_BOOK")
				put("enchantments", CompoundTag().apply { putInt("ultimate_wise", 5) })
			}
		)

		assertEquals("ENCHANTED_BOOK-ULTIMATE_WISE-5", item.marketId)
		assertEquals(mapOf("ultimate_wise" to 5), item.enchantments)
	}

	@Test
	fun `an enchanted book holding two enchantments has no market id`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "ENCHANTED_BOOK")
				put("enchantments", CompoundTag().apply { putInt("sharpness", 6); putInt("critical", 6) })
			}
		)

		assertEquals("", item.marketId)
	}

	@Test
	fun `an enchantment at level zero leaves the book without a market id`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "ENCHANTED_BOOK")
				put("enchantments", CompoundTag().apply { putInt("sharpness", 0) })
			}
		)

		assertEquals("", item.marketId)
	}

	@Test
	fun `both rune ids resolve through the runes compound`() {
		val runes = CompoundTag().apply { putInt("MAGICAL", 3) }

		assertEquals("RUNE-MAGICAL-3", SkyBlockItems.of(stack { putString("id", "RUNE"); put("runes", runes) }).marketId)
		assertEquals("RUNE-MAGICAL-3", SkyBlockItems.of(stack { putString("id", "UNIQUE_RUNE"); put("runes", runes) }).marketId)
		assertEquals("", SkyBlockItems.of(stack { putString("id", "RUNE") }).marketId)
	}

	@Test
	fun `a potion carries its level and the enhanced suffix`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "POTION")
				putString("potion", "speed")
				putInt("potion_level", 8)
				putBoolean("enhanced", true)
			}
		)

		assertEquals("POTION-SPEED-8-ENHANCED", item.marketId)
	}

	@Test
	fun `a potion with no level has no market id`() {
		val item = SkyBlockItems.of(stack { putString("id", "POTION"); putString("potion", "speed") })

		assertEquals("", item.marketId)
	}

	@Test
	fun `a pet takes its type and tier from the pet info json`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "PET")
				putString(
					"petInfo",
					"""{"type":"GOLDEN_DRAGON","active":true,"exp":25353230.0,"tier":"LEGENDARY",""" +
						""""hideInfo":false,"heldItem":"PET_ITEM_TIER_BOOST","candyUsed":3,"skin":"GOLDEN_DRAGON_SLAYER"}"""
				)
			}
		)

		val pet = requireNotNull(item.pet)

		assertEquals("PET", item.id)
		assertEquals("PET-GOLDEN_DRAGON-LEGENDARY", item.marketId)
		assertEquals("GOLDEN_DRAGON", pet.type)
		assertEquals("LEGENDARY", pet.tier)
		assertEquals(25353230.0, pet.exp)
		assertEquals("PET_ITEM_TIER_BOOST", pet.heldItem)
		assertEquals(3, pet.candyUsed)
		assertEquals("GOLDEN_DRAGON_SLAYER", pet.skin)
	}

	@Test
	fun `a pet whose info is missing or unreadable has no market id to price it by`() {
		val bare = SkyBlockItems.of(stack { putString("id", "PET") })
		val broken = SkyBlockItems.of(stack { putString("id", "PET"); putString("petInfo", "{\"type\":\"TIGER\"") })

		assertNull(bare.pet)
		assertEquals("", bare.marketId)
		assertNull(broken.pet)
		assertEquals("", broken.marketId)
	}

	@Test
	fun `pet info is only decoded for pets`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "ASPECT_OF_THE_END")
				putString("petInfo", """{"type":"TIGER","tier":"EPIC"}""")
			}
		)

		assertNull(item.pet)
	}

	@Test
	fun `a starred recombobulated item reports both upgrades`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "SPIRIT_SCEPTRE")
				putInt("upgrade_level", 6)
				putInt("rarity_upgrades", 1)
				putString("modifier", "withered")
				putInt("hot_potato_count", 15)
				putInt("art_of_war_count", 1)
				putInt("tuned_transmission", 4)
			}
		)

		assertEquals(6, item.upgradeLevel)
		assertTrue(item.isStarred)
		assertEquals(1, item.rarityUpgrades)
		assertTrue(item.isRecombobulated)
		assertEquals("withered", item.reforge)
		assertEquals(15, item.hotPotatoCount)
		assertEquals(1, item.artOfWar)
		assertEquals(4, item.tunedTransmission)
	}

	@Test
	fun `an older dungeon item reports its level as its star count`() {
		val item = SkyBlockItems.of(stack { putString("id", "SHADOW_FURY"); putInt("dungeon_item_level", 5) })

		assertEquals(5, item.upgradeLevel)
	}

	@Test
	fun `the long tail the valuation reads is typed off one tag copy`() {
		val item = SkyBlockItems.of(
			stack {
				putString("id", "HYPERION")
				put("attributes", CompoundTag().apply { putInt("mana_pool", 5); putInt("blazing_fortune", 2) })
				put("gems", CompoundTag().apply { putString("JASPER_0", "PERFECT") })
				putBoolean("ethermerge", true)
				putBoolean("donated_museum", true)
				putLong("timestamp", 1_704_067_200_000L)
			}
		)

		assertEquals(mapOf("MANA_POOL" to 5, "BLAZING_FORTUNE" to 2), item.attributes)
		assertEquals("PERFECT", item.gems?.getStringOr("JASPER_0", ""))
		assertTrue(item.ethermerge)
		assertTrue(item.donatedMuseum)
		assertEquals(1_704_067_200_000L, item.timestamp)
		assertEquals("HYPERION", item.tag.getStringOr("id", ""))
	}

	@Test
	fun `an empty stack and a stack without custom data are the same shared record`() {
		assertSame(SkyBlockItem.NONE, SkyBlockItems.of(ItemStack.EMPTY))
		assertSame(SkyBlockItem.NONE, SkyBlockItems.of(ItemFixture.vanilla()))
		assertSame(SkyBlockItem.NONE, SkyBlockItems.of(stack { }))
	}

	@Test
	fun `rarity is read from the last lore line up`() {
		val stack = lored("§7Some §9RARE talk about rarity", "§5§lEPIC SWORD")

		assertEquals(ItemRarity.EPIC, SkyBlockItems.rarity(stack))
		assertEquals(12, SkyBlockItems.rarity(stack).magicalPower)
	}

	@Test
	fun `every rarity resolves from its own lore line and no other`() {
		for (rarity in ItemRarity.entries) {
			val line = rarity.colorCode + "§l" + rarity.loreName

			assertEquals(rarity, SkyBlockItems.rarity(lored("$line SWORD")))
			assertEquals(rarity, ItemRarity.fromLoreLine(line))
		}
	}

	@Test
	fun `the recombobulated glyph in front of the rarity does not hide it`() {
		assertEquals(ItemRarity.MYTHIC, SkyBlockItems.rarity(lored("§d§l§ka§r §d§lMYTHIC DUNGEON SWORD §d§l§ka")))
	}

	@Test
	fun `a shiny rarity keeps the rarity behind it`() {
		assertEquals(ItemRarity.LEGENDARY, SkyBlockItems.rarity(lored("§6§lSHINY LEGENDARY SWORD")))
	}

	@Test
	fun `very special does not read as special`() {
		assertEquals(ItemRarity.VERY_SPECIAL, SkyBlockItems.rarity(lored("§c§lVERY SPECIAL")))
		assertEquals(ItemRarity.SPECIAL, SkyBlockItems.rarity(lored("§c§lSPECIAL")))
	}

	@Test
	fun `a pet without a rarity line falls back to the colour of its name`() {
		assertEquals(ItemRarity.LEGENDARY, SkyBlockItems.rarity(named("§7[Lvl 100] §6Golden Dragon")))
		assertEquals(ItemRarity.EPIC, SkyBlockItems.rarity(named("§7[Lvl 42] §8[§b3§8] §5Tiger")))
	}

	@Test
	fun `an item with neither a rarity line nor a pet name has no rarity`() {
		assertEquals(ItemRarity.NONE, SkyBlockItems.rarity(lored("§7Just some flavour text")))
	}

	@Test
	fun `a stack with no SkyBlock data is never given a rarity from its lore`() {
		val vanilla = ItemFixture.vanilla()
		vanilla.set(DataComponents.LORE, ItemLore(listOf(Component.literal("§6§lLEGENDARY SWORD"))))

		assertEquals(ItemRarity.NONE, SkyBlockItems.rarity(vanilla))
	}

	@Test
	fun `the rarity a record answered once is the rarity it keeps`() {
		val stack = lored("§5§lEPIC SWORD")

		assertEquals(ItemRarity.EPIC, SkyBlockItems.rarity(stack))
		stack.set(DataComponents.LORE, ItemLore(listOf(Component.literal("§6§lLEGENDARY SWORD"))))

		assertEquals(ItemRarity.EPIC, SkyBlockItems.rarity(stack))
	}

	@Test
	fun `lore comes off the stored component and the glint off its own`() {
		val stack = lored("§5§lEPIC SWORD")

		assertEquals(1, SkyBlockItems.lore(stack).size)
		assertFalse(SkyBlockItems.hasGlint(stack))
		assertNull(SkyBlockItems.skullTexture(stack))
		assertNull(SkyBlockItems.skullId(stack))

		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)

		assertTrue(SkyBlockItems.hasGlint(stack))
	}

	private fun stack(build: CompoundTag.() -> Unit): ItemStack = ItemFixture.stack(build)

	private fun lored(vararg lines: String): ItemStack = ItemFixture.lored(*lines)

	private fun named(name: String): ItemStack = ItemFixture.named(name)

	private companion object {
		private const val UUID = "3e0d0b3a-6d2e-4a1e-9c1d-2b9a1f0c7e55"

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
