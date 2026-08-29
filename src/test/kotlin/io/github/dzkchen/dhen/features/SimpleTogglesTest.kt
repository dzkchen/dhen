package io.github.dzkchen.dhen.features

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.features.privacy.ChannelSpoofing
import io.github.dzkchen.dhen.features.privacy.SpoofAsVanilla
import io.github.dzkchen.dhen.features.qol.ArrowFix
import io.github.dzkchen.dhen.features.qol.NoItemPlace
import io.github.dzkchen.dhen.features.visual.RevertAxes
import io.github.dzkchen.dhen.module.Category
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SimpleTogglesTest {
	@Test
	fun `declares the five native modules`() {
		assertEquals("Arrow Fix", ArrowFix.name)
		assertEquals(Category.QOL, ArrowFix.category)
		assertEquals("No Item Place", NoItemPlace.name)
		assertEquals(Category.QOL, NoItemPlace.category)
		assertEquals("Revert Axes", RevertAxes.name)
		assertEquals(Category.VISUAL, RevertAxes.category)
		assertEquals("Spoof as Vanilla", SpoofAsVanilla.name)
		assertEquals(Category.PRIVACY, SpoofAsVanilla.category)
		assertEquals("Channel Spoofing", ChannelSpoofing.name)
		assertEquals(Category.PRIVACY, ChannelSpoofing.category)
		assertTrue(ArrowFix.settings.isEmpty())
		assertTrue(NoItemPlace.settings.isEmpty())
		assertTrue(RevertAxes.settings.isEmpty())
		assertTrue(SpoofAsVanilla.settings.isEmpty())
		assertTrue(ChannelSpoofing.settings.isEmpty())
	}

	@Test
	fun `arrow fix recognizes only bows with the marker above both footer lines`() {
		assertTrue(ArrowFix.isShortbow(bow("TEST_SHORTBOW", MARKER, "Ability line", "§6§lLEGENDARY BOW")))
		assertFalse(ArrowFix.isShortbow(bow("TEST_LATE_MARKER", "Ability line", MARKER, "§6§lLEGENDARY BOW")))
		assertFalse(ArrowFix.isShortbow(item(Items.DIAMOND_SWORD, "TEST_SWORD", MARKER, "Footer", "Footer")))
		assertFalse(ArrowFix.isShortbow(ItemStack.EMPTY))
	}

	@Test
	fun `arrow fix caches non-empty ids`() {
		val stack = bow("TEST_CACHED_SHORTBOW", MARKER, "Footer", "Footer")
		assertTrue(ArrowFix.isShortbow(stack))

		stack.set(DataComponents.LORE, lore("Ordinary bow", "Footer", "Footer"))

		assertTrue(ArrowFix.isShortbow(stack))
	}

	@Test
	fun `arrow fix does not cache the empty id`() {
		val stack = bow("", "Ordinary bow", "Footer", "Footer")
		assertFalse(ArrowFix.isShortbow(stack))

		stack.set(DataComponents.LORE, lore(MARKER, "Footer", "Footer"))

		assertTrue(ArrowFix.isShortbow(stack))
	}

	@Test
	fun `no item place protects every prefix and suffix family`() {
		val protected = listOf(
			"ABIPHONE",
			"ABIPHONE_X_PLUS",
			"WEIRDO_TUBA",
			"OVERFLUX_POWER_ORB",
			"PORTABLE_POCKET_BLACK_HOLE",
			"LAVA_FISHING_NET"
		)

		for (id in protected) assertTrue(NoItemPlace.protects(id), id)
		assertFalse(NoItemPlace.protects("TUBA"))
		assertFalse(NoItemPlace.protects("AB_PHONE"))
	}

	@Test
	fun `no item place protects every fixed id`() {
		val protected = listOf(
			"BOUQUET_OF_LIES",
			"FLOWER_OF_TRUTH",
			"BAT_WAND",
			"STARRED_BAT_WAND",
			"INFINITE_SPIRIT_LEAP",
			"ROYAL_PIGEON",
			"ARROW_SWAPPER",
			"JINGLE_BELLS",
			"FIRE_FREEZE_STAFF",
			"UMBERELLA",
			"ETHERWARP_CONDUIT",
			"KUUDRA_SHOP_ITEM"
		)

		for (id in protected) assertTrue(NoItemPlace.protects(id), id)
		assertFalse(NoItemPlace.protects("ENDER_PEARL"))
		assertFalse(NoItemPlace.protects(""))
	}

	@Test
	fun `revert axes maps only the four reference ids`() {
		assertEquals(Items.GOLDEN_AXE, RevertAxes.replacementItem("RAGNAROCK_AXE"))
		assertEquals(Items.GOLDEN_AXE, RevertAxes.replacementItem("DAEDALUS_AXE"))
		assertEquals(Items.GOLDEN_AXE, RevertAxes.replacementItem("STARRED_DAEDALUS_AXE"))
		assertEquals(Items.DIAMOND_AXE, RevertAxes.replacementItem("AXE_OF_THE_SHREDDED"))
		assertNull(RevertAxes.replacementItem("DIAMOND_AXE"))
		assertNull(RevertAxes.replacementItem(""))
	}

	private fun bow(id: String, vararg lines: String): ItemStack = item(Items.BOW, id, *lines)

	private fun item(item: net.minecraft.world.item.Item, id: String, vararg lines: String): ItemStack =
		ItemStack(item).also { stack ->
			stack.set(DataComponents.CUSTOM_DATA, ItemFixture.customData { putString("id", id) })
			stack.set(DataComponents.LORE, lore(*lines))
		}

	private fun lore(vararg lines: String): ItemLore = ItemLore(lines.map(Component::literal))

	private companion object {
		const val MARKER = "Shortbow: Instantly shoots!"

		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
