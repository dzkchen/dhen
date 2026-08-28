package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
	fun `resetting restores the declared anchor, offset, scale and visibility`() {
		val element = FixedHudElement("Moved", anchor = HudAnchor.BOTTOM_RIGHT, offsetX = -4, offsetY = -4)

		element.anchor = HudAnchor.MIDDLE_CENTER
		element.offsetX = 120
		element.offsetY = 80
		element.scale = 2.5f
		element.visible = false

		assertTrue(element.resetToDeclared())
		assertEquals(HudAnchor.BOTTOM_RIGHT, element.anchor)
		assertEquals(-4, element.offsetX)
		assertEquals(-4, element.offsetY)
		assertEquals(HudElement.DEFAULT_SCALE, element.scale)
		assertTrue(element.visible)
		assertFalse(element.resetToDeclared())
	}

	@Test
	fun `resetting every layout restores each element and counts only real changes`() {
		val manager = ModuleManager()
		val moved = OverlayModule("Moved")
		val hidden = OverlayModule("Hidden")
		manager.registerAll(moved, hidden)
		manager.enable(moved)
		val runtime = HudRuntime(manager)

		assertEquals(0, runtime.resetLayouts())

		moved.first.offsetX = 120
		moved.first.scale = 2.0f
		hidden.first.visible = false

		assertEquals(2, runtime.resetLayouts())
		assertEquals(0, moved.first.offsetX)
		assertEquals(HudElement.DEFAULT_SCALE, moved.first.scale)
		assertTrue(hidden.first.visible)
		assertEquals(0, runtime.resetLayouts())
	}

	@Test
	fun `core layouts join reset without pretending to be modules`() {
		val core = FixedHudElement("Core", offsetY = -12)
		val runtime = HudRuntime(ModuleManager(), listOf(core))
		core.offsetY = 50

		assertEquals(1, runtime.resetLayouts())
		assertEquals(-12, core.offsetY)
		assertEquals(0, runtime.resetLayouts())
	}

	@Test
	fun `an element declared hidden resets back to hidden`() {
		val element = FixedHudElement("Quiet", visible = false)

		element.visible = true

		assertTrue(element.resetToDeclared())
		assertFalse(element.visible)
	}

	@Test
	fun `a declared scale out of range is clamped before it becomes the reset target`() {
		val element = FixedHudElement("Loud", scale = 99.0f)

		element.scale = 1.0f
		element.resetToDeclared()

		assertEquals(HudElement.MAX_SCALE, element.scale)
	}

	@Test
	fun `an element that failed to render is skipped`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)
		manager.enable(module)
		module.first.markFailed()

		assertEquals(listOf(module.second), visited(manager))
	}

	@Test
	fun `resetting lifts the failed quarantine so the element renders again`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)
		manager.enable(module)
		module.first.markFailed()

		assertEquals(listOf(module.second), visited(manager))

		assertTrue(module.first.resetToDeclared())
		assertEquals(listOf(module.first, module.second), visited(manager))
		assertFalse(module.first.resetToDeclared())
	}

	@Test
	fun `turning a module off and on lifts the quarantine without costing the layout`() {
		val manager = ModuleManager()
		val module = OverlayModule()
		manager.register(module)
		manager.enable(module)
		module.first.offsetX = 120
		module.first.markFailed()

		assertEquals(listOf(module.second), visited(manager))

		manager.disable(module)
		manager.enable(module)

		assertEquals(listOf(module.first, module.second), visited(manager))
		assertEquals(120, module.first.offsetX)
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
