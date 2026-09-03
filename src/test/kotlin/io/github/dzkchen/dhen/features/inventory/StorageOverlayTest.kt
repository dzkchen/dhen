package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.module.Category
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StorageOverlayTest {
	@Test
	fun `the module declares the source's settings plus the two feature toggles`() {
		assertEquals("Storage Overlay", StorageOverlay.name)
		assertEquals(Category.INVENTORY, StorageOverlay.category)
		assertEquals(
			listOf(
				"Show Overlay",
				"Backpack Preview",
				"Scale",
				"Columns",
				"Max Height",
				"Scroll Speed",
				"Retain Scroll",
				"Tooltip Scroll",
				"Hide Non-Matching Pages"
			),
			StorageOverlay.settings.map { it.name }
		)
		assertEquals(1.0, StorageOverlay.scaleSetting.default)
		assertEquals(3.0, StorageOverlay.columnsSetting.default)
		assertEquals(324.0, StorageOverlay.maxHeightSetting.default)
		assertEquals(10.0, StorageOverlay.scrollSpeedSetting.default)
	}

	@Test
	fun `ender chest titles resolve to the first nine pages`() {
		assertEquals(0, page("Ender Chest (1/9)"))
		assertEquals(8, page("Ender Chest (9/9)"))
		assertEquals(2, page("Ender Chest ✦ (3/9)"))
	}

	@Test
	fun `backpack titles resolve to the pages after the ender chests`() {
		assertEquals(9, page("Small Backpack (Slot #1)"))
		assertEquals(26, page("Jumbo Backpack (Slot #18)"))
		assertEquals(11, page("Large Backpack ✦ (Slot #3)"))
	}

	@Test
	fun `the overview title is its own case and anything else is not storage`() {
		assertEquals(StorageOverlay.OVERVIEW, page("Storage"))
		assertEquals(StorageOverlay.NOT_STORAGE, page("Storage Menu"))
		assertEquals(StorageOverlay.NOT_STORAGE, page("Your Bags"))
		assertEquals(StorageOverlay.NOT_STORAGE, page("Ender Chest (0/9)"))
		assertEquals(StorageOverlay.NOT_STORAGE, page("Backpack (Slot #19)"))
	}

	@Test
	fun `overview slots map onto the pages the same way both sources do`() {
		assertEquals(0, StorageSnapshots.overviewPage(9))
		assertEquals(8, StorageSnapshots.overviewPage(17))
		assertEquals(9, StorageSnapshots.overviewPage(27))
		assertEquals(26, StorageSnapshots.overviewPage(44))
		assertEquals(StorageSnapshots.NO_PAGE, StorageSnapshots.overviewPage(8))
		assertEquals(StorageSnapshots.NO_PAGE, StorageSnapshots.overviewPage(18))
		assertEquals(StorageSnapshots.NO_PAGE, StorageSnapshots.overviewPage(45))
	}

	@Test
	fun `a page names itself and opens itself the way the source does`() {
		assertEquals("Ender Chest #1", StorageSnapshots.name(0))
		assertEquals("Backpack #1", StorageSnapshots.name(9))
		assertEquals("Backpack #18", StorageSnapshots.name(26))
		assertEquals("enderchest 1", StorageSnapshots.command(0))
		assertEquals("backpack 1", StorageSnapshots.command(9))
		assertEquals("backpack 18", StorageSnapshots.command(26))
	}

	private fun page(title: String): Int = StorageOverlay.storagePage(Component.literal(title))
}
