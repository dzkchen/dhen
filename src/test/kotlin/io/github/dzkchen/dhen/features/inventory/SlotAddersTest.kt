package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.SLOT_BOTTOM_LEFT
import io.github.dzkchen.dhen.gui.SLOT_BOTTOM_RIGHT
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SlotAddersTest {
	@Test
	fun `each bottle reads its charge against its own capacity and never leaves the range`() {
		assertEquals("50%", corner("Bottle Charge", SLOT_BOTTOM_LEFT) {
			putString("id", "THUNDER_IN_A_BOTTLE_EMPTY"); putInt("thunder_charge", 25_000)
		})
		assertEquals("1%", corner("Bottle Charge", SLOT_BOTTOM_LEFT) {
			putString("id", "STORM_IN_A_BOTTLE_EMPTY"); putInt("thunder_charge", 5_000)
		})
		assertEquals("100%", corner("Bottle Charge", SLOT_BOTTOM_LEFT) {
			putString("id", "HURRICANE_IN_A_BOTTLE_EMPTY"); putInt("thunder_charge", 9_000_000)
		})
		assertNull(corner("Bottle Charge", SLOT_BOTTOM_LEFT) {
			putString("id", "THUNDER_IN_A_BOTTLE"); putInt("thunder_charge", 25_000)
		})
	}

	@Test
	fun `a Moby-Duck reports the share of its three hundred held hours`() {
		assertEquals("50%", corner("Moby-Duck Progress", SLOT_BOTTOM_RIGHT) {
			putString("id", "MOBY_DUCK"); putInt("seconds_held", 540_000)
		})
		assertNull(corner("Moby-Duck Progress", SLOT_BOTTOM_RIGHT) {
			putString("id", "MOBYS_SHEARS"); putInt("seconds_held", 540_000)
		})
	}

	@Test
	fun `the recombobulated mark is limited to the fishing drops that come recombobulated`() {
		assertEquals("R", corner("Auto-Recombobulated", SLOT_BOTTOM_LEFT) {
			putString("id", "TAURUS_HELMET"); putInt("rarity_upgrades", 1)
		})
		assertNull(corner("Auto-Recombobulated", SLOT_BOTTOM_LEFT) {
			putString("id", "TAURUS_HELMET")
		})
		assertNull(corner("Auto-Recombobulated", SLOT_BOTTOM_LEFT) {
			putString("id", "HYPERION"); putInt("rarity_upgrades", 1)
		})
	}

	@Test
	fun `a star count picks its ramp from the item's own category line`() {
		assertEquals("7", corner("Item Stars", SLOT_BOTTOM_RIGHT) {
			putString("id", "TERMINATOR"); putInt("upgrade_level", 7)
		})
		assertEquals(
			DhenPalette.slotStar(7, dungeon = true),
			ink("Item Stars", SLOT_BOTTOM_RIGHT, listOf("§5§lEPIC DUNGEON BOW")) {
				putString("id", "TERMINATOR"); putInt("upgrade_level", 7)
			}
		)
		assertEquals(
			DhenPalette.slotStar(7, dungeon = false),
			ink("Item Stars", SLOT_BOTTOM_RIGHT, listOf("§5§lEPIC BOW")) {
				putString("id", "TERMINATOR"); putInt("upgrade_level", 7)
			}
		)
		assertNull(corner("Item Stars", SLOT_BOTTOM_RIGHT) { putString("id", "TERMINATOR") })
	}

	private fun corner(label: String, corner: Int, build: CompoundTag.() -> Unit): String? =
		written(label, emptyList(), build).textAt(corner)

	private fun ink(label: String, corner: Int, lore: List<String>, build: CompoundTag.() -> Unit): Int =
		written(label, lore, build).inkAt(corner)

	private fun written(label: String, lore: List<String>, build: CompoundTag.() -> Unit): SlotScribe {
		val scribe = SlotScribe()
		scribe.begin(stack(lore, build), 0)
		SLOT_ADDERS.first { it.label == label }.writeInto(scribe)
		return scribe
	}

	private fun stack(lore: List<String>, build: CompoundTag.() -> Unit): ItemStack =
		ItemFixture.stack(build).also {
			if (lore.isNotEmpty()) it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal)))
		}

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
