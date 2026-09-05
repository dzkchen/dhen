package io.github.dzkchen.dhen.data.item

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ItemFactsTest {
	@Test
	fun `the category is the word after the rarity on the last rarity line`() {
		assertEquals("BOOTS", category("LEGENDARY DUNGEON BOOTS"))
		assertEquals("ACCESSORY", category("a MYTHIC ACCESSORY a"))
		assertEquals("HATCESSORY", category("a VERY SPECIAL HATCESSORY a"))
		assertEquals("CHESTPLATE", category("SHINY LEGENDARY DUNGEON CHESTPLATE"))
		assertEquals("CHISEL", category("COMMON CHISEL"))
		assertEquals("REFORGE_STONE", category("EPIC REFORGE STONE"))
	}

	@Test
	fun `a rarity line with no trailing category reads as no category`() {
		assertEquals(ItemFacts.NO_CATEGORY, category("LEGENDARY"))
		assertEquals(ItemFacts.NO_CATEGORY, category("Rarity: COMMON"))
	}

	@Test
	fun `a crop rarity line is not a rarity line`() {
		assertEquals(ItemFacts.NO_CATEGORY, category("RARE CROP"))
		assertEquals(ItemFacts.NO_CATEGORY, category("RARE CROPS"))
	}

	@Test
	fun `lines below the rarity line do not hide it`() {
		assertEquals("SWORD", category("Some flavour text", "LEGENDARY SWORD", "RARE CROPS"))
	}

	@Test
	fun `a sack needs both the id suffix and the name suffix`() {
		assertTrue(ItemFacts.isSack(named("MEDIUM_MINING_SACK", "Medium Mining Sack")))
		assertFalse(ItemFacts.isSack(named("MEDIUM_MINING_SACK", "Medium Mining Sackcloth")))
		assertFalse(ItemFacts.isSack(named("MINING_BACKPACK", "Large Mining Sack")))
	}

	@Test
	fun `soulbound is read off a whole lore line and co-op counts only as any soulbound`() {
		val coop = lored("* Co-op Soulbound *")
		assertFalse(ItemFacts.isSoulbound(coop))
		assertTrue(ItemFacts.isAnySoulbound(coop))
		val plain = lored("* Soulbound *")
		assertTrue(ItemFacts.isSoulbound(plain))
		assertFalse(ItemFacts.isSoulbound(lored("Not * Soulbound * really")))
	}

	@Test
	fun `rift wording is matched whichever way the line carries its colour`() {
		assertTrue(ItemFacts.isRiftTransferable(lored("§5§kX§5 Rift-Transferable §kX")))
		assertTrue(ItemFacts.isRiftExportable(lored("§5§kX§5 Rift-Exported §kX")))
		assertTrue(ItemFacts.isRiftExportable(lored("§5§kX§5 Rift-Exportable §kX")))
		assertTrue(ItemFacts.isRiftTransferable(styledRift("Rift-Transferable")))
		assertTrue(ItemFacts.isRiftExportable(styledRift("Rift-Exported")))
		assertFalse(ItemFacts.isRiftTransferable(lored("Rift-Transferable")))
		assertFalse(ItemFacts.isRiftTransferable(lored("X Cannot be Rift-Transferable X")))
	}

	private fun styledRift(word: String): ItemStack = ItemFixture.identified("HYPERION").also {
		it.set(
			DataComponents.LORE,
			ItemLore(
				listOf(
					Component.literal("X").withStyle(ChatFormatting.OBFUSCATED)
						.append(Component.literal(" $word "))
						.append(Component.literal("X").withStyle(ChatFormatting.OBFUSCATED))
						.withStyle(ChatFormatting.DARK_PURPLE)
				)
			)
		)
	}

	@Test
	fun `a coded lore probe sees a style that carries no literal code`() {
		val bold = ItemFixture.identified("HYPERION").also {
			it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("Cloak").withStyle(ChatFormatting.BOLD))))
		}
		assertTrue(ItemFacts.codedLoreHas(bold, "§l"))
		assertFalse(ItemFacts.codedLoreHas(lored("Cloak"), "§l"))
	}

	private fun category(vararg lore: String): String = ItemFacts.category(lored(*lore))

	private fun lored(vararg lines: String): ItemStack =
		ItemFixture.identified("HYPERION")
			.also { it.set(DataComponents.LORE, ItemLore(lines.map(Component::literal))) }

	private fun named(id: String, name: String): ItemStack =
		ItemFixture.identified(id).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			bootstrapMinecraft()
			ItemFixture.bootstrap()
		}
	}
}
