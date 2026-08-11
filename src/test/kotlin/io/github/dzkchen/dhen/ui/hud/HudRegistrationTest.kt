package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HudRegistrationTest {
	@Test
	fun `a disabled module contributes no elements`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)

		assertTrue(visited(manager).isEmpty())
	}

	@Test
	fun `enabling a module contributes its elements in declaration order`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)
		manager.enable(module)

		assertEquals(listOf(module.first, module.second), visited(manager))
	}

	@Test
	fun `disabling a module skips its elements again`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)
		manager.enable(module)
		manager.disable(module)

		assertTrue(visited(manager).isEmpty())
	}

	@Test
	fun `a hidden element of an enabled module is skipped`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)
		manager.enable(module)
		module.first.visible = false

		assertEquals(listOf(module.second), visited(manager))
	}

	@Test
	fun `elements follow module registration order across modules`() {
		val manager = ModuleManager()
		val first = OverlayModule(name = "First")
		val second = OverlayModule(name = "Second")
		manager.registerAll(first, second)
		manager.enable(first)
		manager.enable(second)

		assertEquals(listOf(first.first, first.second, second.first, second.second), visited(manager))
	}

	@Test
	fun `duplicate element names within a module are rejected`() {
		assertThrows(IllegalArgumentException::class.java) { DuplicateElementModule() }
	}

	@Test
	fun `scale is clamped to the supported range`() {
		val element = FixedHudElement("Clamped")

		element.scale = 100.0f
		assertEquals(HudElement.MAX_SCALE, element.scale)

		element.scale = 0.0f
		assertEquals(HudElement.MIN_SCALE, element.scale)
	}

	@Test
	fun `a NaN scale falls back to the default instead of passing the clamp`() {
		val element = FixedHudElement("Clamped")

		element.scale = Float.NaN

		assertEquals(HudElement.DEFAULT_SCALE, element.scale)
	}

	@Test
	fun `an element that failed to render is skipped until it is measured again`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)
		manager.enable(module)
		module.first.markFailed()

		assertEquals(listOf(module.second), visited(manager))
	}

	@Test
	fun `the exposed element list rejects mutation`() {
		val module = OverlayModule()

		assertThrows(UnsupportedOperationException::class.java) {
			@Suppress("UNCHECKED_CAST")
			(module.hudElements as MutableList<HudElement>).clear()
		}
		assertEquals(2, module.hudElements.size)
	}

	private fun visited(manager: ModuleManager): List<HudElement> {
		val seen = mutableListOf<HudElement>()
		manager.forEachActiveHudElement { _, element -> seen += element }
		return seen
	}

	private class OverlayModule(name: String = "Overlay") : Module(
		name = name,
		category = Category.VISUAL,
		description = "Fixture owning HUD elements."
	) {
		val first = hud(FixedHudElement("First"))
		val second = hud(FixedHudElement("Second"))
	}

	private class DuplicateElementModule : Module(
		name = "Duplicate",
		category = Category.VISUAL,
		description = "Fixture registering two elements under one name."
	) {
		init {
			hud(FixedHudElement("Same"))
			hud(FixedHudElement("Same"))
		}
	}
}
