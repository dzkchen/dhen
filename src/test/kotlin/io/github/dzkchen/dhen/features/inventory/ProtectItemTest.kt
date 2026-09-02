package io.github.dzkchen.dhen.features.inventory

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import net.minecraft.world.item.ItemStack

class ProtectItemTest {
	@BeforeEach
	fun reset() {
		ContainerState.read(JsonObject())
		ProtectItem.resetSettings()
	}

	@Test
	fun `the module declares itself under inventory`() {
		assertEquals("Protect Item", ProtectItem.name)
		assertEquals(Category.INVENTORY, ProtectItem.category)
	}

	@Test
	fun `an unmarked ordinary item is not protected`() {
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(ItemFixture.identified("HYPERION")))
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(ItemStack.EMPTY))
	}

	@Test
	fun `a marked uuid protects only that one item`() {
		val marked = ItemFixture.stack {
			putString("id", "HYPERION")
			putString("uuid", "one")
		}
		val other = ItemFixture.stack {
			putString("id", "HYPERION")
			putString("uuid", "two")
		}
		ContainerState.protect(SkyBlockItems.of(marked).uuid, byUuid = true)
		assertEquals(ProtectType.UUID, ProtectItem.protectionOf(marked))
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(other))
	}

	@Test
	fun `a marked skyblock id protects every copy`() {
		ContainerState.protect("HYPERION", byUuid = false)
		assertEquals(ProtectType.SKYBLOCK_ID, ProtectItem.protectionOf(ItemFixture.identified("HYPERION")))
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(ItemFixture.identified("TERMINATOR")))
	}

	@Test
	fun `stars and recombobulators protect on their own`() {
		val starred = ItemFixture.stack {
			putString("id", "SPIRIT_SCEPTRE")
			putInt("upgrade_level", 5)
		}
		val recombobulated = ItemFixture.stack {
			putString("id", "SPIRIT_SCEPTRE")
			putInt("rarity_upgrades", 1)
		}
		assertEquals(ProtectType.STARRED, ProtectItem.protectionOf(starred))
		assertEquals(ProtectType.RECOMBOBULATED, ProtectItem.protectionOf(recombobulated))
		ProtectItem.starredSetting.value = false
		ProtectItem.recombobulatedSetting.value = false
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(starred))
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(recombobulated))
	}

	@Test
	fun `the rarity floor protects at and above the chosen rarity and nothing below it`() {
		val legendary = ItemFixture.lored("§6§lLEGENDARY SWORD")
		val rare = ItemFixture.lored("§9§lRARE SWORD")
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(legendary))
		ProtectItem.raritySetting.value = "Epic"
		assertEquals(ProtectType.RARITY, ProtectItem.protectionOf(legendary))
		assertEquals(ProtectType.NONE, ProtectItem.protectionOf(rare))
	}

	@Test
	fun `three presses inside the window let the item go and a slow press starts over`() {
		assertEquals(2, ProtectItem.pressesLeft("item", 0L))
		assertEquals(1, ProtectItem.pressesLeft("item", 200L))
		assertEquals(0, ProtectItem.pressesLeft("item", 400L))
		assertEquals(2, ProtectItem.pressesLeft("item", 400L + ProtectItem.PRESS_WINDOW_MS + 1L))
	}

	@Test
	fun `switching item restarts the press count`() {
		assertEquals(2, ProtectItem.pressesLeft("first", 0L))
		assertEquals(1, ProtectItem.pressesLeft("first", 100L))
		assertEquals(2, ProtectItem.pressesLeft("second", 200L))
	}

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
