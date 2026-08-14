package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.STUB_GLYPH_WIDTH
import io.github.dzkchen.dhen.gui.StubFont
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HudTextElementTest {
	@Test
	fun `text that changes every frame is measured once per change`() {
		val font = StubFont()
		val element = HudTextElement("Timer", text = "12")

		assertEquals(2 * STUB_GLYPH_WIDTH, element.width(font))
		assertEquals(2 * STUB_GLYPH_WIDTH, element.width(font))
		assertEquals(1, font.measurements)

		element.text = "12.5"

		assertEquals(4 * STUB_GLYPH_WIDTH, element.width(font))
		assertEquals(2, font.measurements)
	}

	@Test
	fun `a readout that ticks all session leaves the click gui's labels in the shared cache`() {
		val font = StubFont()
		val label = DhenType.styled(NEIGHBOUR)
		val element = HudTextElement("Timer")

		for (tick in 1..DhenType.CACHE_LIMIT + 1) {
			element.text = "$tick.25"
			element.width(font)
		}

		assertSame(label, DhenType.styled(NEIGHBOUR))
	}

	@Test
	fun `a resource reload re-measures every element through the runtime`() {
		val font = StubFont()
		val manager = ModuleManager()
		val module = ReadoutModule()
		manager.register(module)
		module.readout.width(font)
		module.readout.width(font)
		assertEquals(1, font.measurements)

		HudRuntime(manager).invalidateMeasurements()

		module.readout.width(font)
		assertEquals(2, font.measurements)
	}

	private class ReadoutModule : Module(
		name = "Readout",
		category = Category.VISUAL,
		description = "Fixture owning a text element."
	) {
		val readout = hud(HudTextElement("Readout", text = "12.5"))
	}

	private companion object {
		const val NEIGHBOUR = "Sharpness"
	}
}
