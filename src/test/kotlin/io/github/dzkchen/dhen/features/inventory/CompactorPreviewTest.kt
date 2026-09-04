package io.github.dzkchen.dhen.features.inventory

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompactorPreviewTest {
	private fun lines(vararg text: String): List<Component> = text.map(Component::literal)

	@Test
	fun `the preview goes at the second blank line and nowhere when there is only one`() {
		assertEquals(3, CompactorPreview.insertionPoint(lines("Personal Compactor", "", "Holds items", "", "Rare")))
		assertEquals(2, CompactorPreview.insertionPoint(lines("Personal Compactor", "", "")))
		assertEquals(-1, CompactorPreview.insertionPoint(lines("Personal Compactor", "", "Holds items")))
		assertEquals(-1, CompactorPreview.insertionPoint(lines("Personal Compactor")))
	}

	@Test
	fun `a compactor looks for the truncated key its data actually uses`() {
		assertEquals("compact", CompactorPreview.slotKeyPrefix("COMPACTOR"))
		assertEquals("deletor", CompactorPreview.slotKeyPrefix("DELETOR"))
	}

	@Test
	fun `each model has its own grid and an unknown one falls back to one row of six`() {
		assertEquals(1 to 1, CompactorPreview.shape("4000").let { it.rows to it.columns })
		assertEquals(1 to 3, CompactorPreview.shape("5000").let { it.rows to it.columns })
		assertEquals(1 to 7, CompactorPreview.shape("6000").let { it.rows to it.columns })
		assertEquals(2 to 6, CompactorPreview.shape("7000").let { it.rows to it.columns })
		assertEquals(1 to 6, CompactorPreview.shape("8000").let { it.rows to it.columns })
	}
}
