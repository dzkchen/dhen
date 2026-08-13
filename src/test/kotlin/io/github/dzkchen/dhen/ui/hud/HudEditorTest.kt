package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HudEditorTest {
	@Test
	fun `dragging writes back an offset that reproduces the drop position`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(5, 3)
		editor.drag(105, 53, snap = false)

		assertEquals(100, module.element.offsetX)
		assertEquals(50, module.element.offsetY)
		assertTrue(editor.release())

		editor.layout(WIDTH, HEIGHT)
		assertEquals(100, editor.targets.single().x)
		assertEquals(50, editor.targets.single().y)
	}

	@Test
	fun `a dropped element re-anchors to the nearest corner without moving`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(0, 0)
		editor.drag(WIDTH - ELEMENT_WIDTH, HEIGHT - ELEMENT_HEIGHT, snap = false)
		val droppedX = editor.targets.single().x
		val droppedY = editor.targets.single().y
		editor.release()

		assertEquals(HudAnchor.BOTTOM_RIGHT, module.element.anchor)
		editor.layout(WIDTH, HEIGHT)
		assertEquals(droppedX, editor.targets.single().x)
		assertEquals(droppedY, editor.targets.single().y)
	}

	@Test
	fun `re-anchoring keeps the element pinned to its new corner at another resolution`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(0, 0)
		editor.drag(WIDTH - ELEMENT_WIDTH - 6, HEIGHT - ELEMENT_HEIGHT - 6, snap = false)
		editor.release()

		editor.layout(LARGE_WIDTH, LARGE_HEIGHT)
		val target = editor.targets.single()
		assertEquals(6, LARGE_WIDTH - (target.x + target.width))
		assertEquals(6, LARGE_HEIGHT - (target.y + target.height))
	}

	@Test
	fun `a drop that did not move the element leaves its anchor alone`() {
		val module = OverlayModule(anchor = HudAnchor.TOP_LEFT)
		val editor = editorFor(module, enabled = true)
		module.element.offsetX = WIDTH / 2
		module.element.offsetY = HEIGHT / 2
		editor.layout(WIDTH, HEIGHT)

		editor.press(module.element.offsetX, module.element.offsetY)
		editor.release()

		assertEquals(HudAnchor.TOP_LEFT, module.element.anchor)
		assertEquals(WIDTH / 2, module.element.offsetX)
	}

	@Test
	fun `a drag snaps to a nearby screen edge and exposes a guide`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(0, 0)
		editor.drag(3, 40, snap = true)

		assertEquals(0, editor.targets.single().x)
		assertEquals(0, editor.guideX)
		assertEquals(HudEditor.NO_GUIDE, editor.guideY)

		editor.release()
		assertEquals(HudEditor.NO_GUIDE, editor.guideX)
	}

	@Test
	fun `a drag snaps to another element's edge`() {
		val manager = ModuleManager()
		val anchored = OverlayModule(name = "Anchored")
		val moving = OverlayModule(name = "Moving")
		manager.registerAll(anchored, moving)
		manager.enable(anchored)
		manager.enable(moving)
		anchored.element.offsetX = 300
		anchored.element.offsetY = 100
		moving.element.offsetX = 500
		moving.element.offsetY = 300
		val editor = HudEditor(manager, metrics)
		editor.layout(WIDTH, HEIGHT)

		editor.press(500, 300)
		editor.drag(303, 300, snap = true)

		assertEquals(300, moving.element.offsetX)
		assertEquals(300, editor.guideX)
	}

	@Test
	fun `a drag without snapping lands exactly where it is dropped`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(0, 0)
		editor.drag(3, 40, snap = false)

		assertEquals(3, editor.targets.single().x)
		assertEquals(HudEditor.NO_GUIDE, editor.guideX)
	}

	@Test
	fun `right-clicking an element restores its declared layout`() {
		val module = OverlayModule(anchor = HudAnchor.TOP_RIGHT, offsetX = -4)
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)
		val home = editor.targets.single().x

		editor.press(home, 0)
		editor.drag(120, 200, snap = false)
		editor.release()
		editor.layout(WIDTH, HEIGHT)
		editor.rescale(editor.targets.single().x, editor.targets.single().y, 1.0)

		editor.layout(WIDTH, HEIGHT)
		assertTrue(editor.reset(editor.targets.single().x, editor.targets.single().y))

		assertEquals(HudAnchor.TOP_RIGHT, module.element.anchor)
		assertEquals(-4, module.element.offsetX)
		assertEquals(0, module.element.offsetY)
		assertEquals(HudElement.DEFAULT_SCALE, module.element.scale)
	}

	@Test
	fun `resetting an untouched element reports nothing to persist`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		assertFalse(editor.reset(5, 3))
		assertFalse(editor.reset(WIDTH - 1, HEIGHT - 1))
	}

	@Test
	fun `a reset in the middle of a drag is ignored`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(0, 0)
		editor.drag(200, 150, snap = false)

		assertFalse(editor.reset(200, 150))
		assertEquals(200, module.element.offsetX)
	}

	@Test
	fun `a release without movement reports nothing to persist`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(5, 3)
		editor.drag(5, 3, snap = false)

		assertFalse(editor.release())
	}

	@Test
	fun `a drag that returns to its starting pixel is not a change`() {
		val module = OverlayModule(anchor = HudAnchor.TOP_LEFT, offsetX = 200, offsetY = 150)
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(200, 150)
		editor.drag(400, 300, snap = false)
		editor.drag(200, 150, snap = false)

		assertFalse(editor.release())
		assertEquals(HudAnchor.TOP_LEFT, module.element.anchor)
		assertEquals(200, module.element.offsetX)
		assertEquals(150, module.element.offsetY)
	}

	@Test
	fun `bypassing the snap mid-drag clears the guide it was showing`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(0, 0)
		editor.drag(3, 40, snap = true)
		assertEquals(0, editor.guideX)

		editor.drag(3, 40, snap = false)

		assertEquals(3, editor.targets.single().x)
		assertEquals(HudEditor.NO_GUIDE, editor.guideX)
	}

	@Test
	fun `a placeholder can be reset without enabling its module`() {
		val module = OverlayModule(anchor = HudAnchor.BOTTOM_RIGHT, offsetX = -4, offsetY = -4)
		val editor = editorFor(module, enabled = false)
		editor.layout(WIDTH, HEIGHT)

		editor.press(editor.targets.single().x, editor.targets.single().y)
		editor.drag(100, 100, snap = false)
		editor.release()
		editor.layout(WIDTH, HEIGHT)

		assertTrue(editor.reset(editor.targets.single().x, editor.targets.single().y))
		assertEquals(HudAnchor.BOTTOM_RIGHT, module.element.anchor)
		assertEquals(-4, module.element.offsetX)
		assertFalse(module.enabled)
		assertTrue(module.element.visible)
	}

	@Test
	fun `a dropped right anchored element keeps its distance to the edge at any resolution`() {
		val module = OverlayModule(anchor = HudAnchor.BOTTOM_RIGHT)
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(WIDTH - ELEMENT_WIDTH, HEIGHT - ELEMENT_HEIGHT)
		editor.drag(700, 400, snap = false)
		editor.release()

		editor.layout(WIDTH, HEIGHT)
		val small = editor.targets.single()
		val fromRight = WIDTH - (small.x + small.width)
		val fromBottom = HEIGHT - (small.y + small.height)

		editor.layout(LARGE_WIDTH, LARGE_HEIGHT)
		val large = editor.targets.single()

		assertEquals(fromRight, LARGE_WIDTH - (large.x + large.width))
		assertEquals(fromBottom, LARGE_HEIGHT - (large.y + large.height))
	}

	@Test
	fun `a drag cannot push an element off the screen`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(0, 0)
		editor.drag(4000, 4000, snap = false)

		val target = editor.targets.single()
		assertEquals(WIDTH - ELEMENT_WIDTH, target.x)
		assertEquals(HEIGHT - ELEMENT_HEIGHT, target.y)
		assertEquals(WIDTH - ELEMENT_WIDTH, module.element.offsetX)
		assertEquals(HEIGHT - ELEMENT_HEIGHT, module.element.offsetY)
	}

	@Test
	fun `nudging moves the selected element by exactly the requested pixels`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)
		editor.press(0, 0)

		assertTrue(editor.nudge(10, 4))
		assertEquals(10, module.element.offsetX)
		assertEquals(4, module.element.offsetY)

		assertTrue(editor.nudge(-1, 0))
		assertEquals(9, module.element.offsetX)
	}

	@Test
	fun `nudging without a selection changes nothing`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		assertFalse(editor.nudge(1, 0))
		assertEquals(0, module.element.offsetX)
	}

	@Test
	fun `nudging stops at the screen edge`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)
		editor.press(0, 0)

		assertFalse(editor.nudge(-1, -1))
		assertEquals(0, module.element.offsetX)
		assertEquals(0, module.element.offsetY)
	}

	@Test
	fun `scrolling over an element steps its scale and clamps to the supported range`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		assertTrue(editor.rescale(5, 3, 1.0))
		assertEquals(1.1f, module.element.scale)

		repeat(40) {
			editor.layout(WIDTH, HEIGHT)
			editor.rescale(5, 3, 1.0)
		}
		assertEquals(HudElement.MAX_SCALE, module.element.scale)
		editor.layout(WIDTH, HEIGHT)
		assertFalse(editor.rescale(5, 3, 1.0))

		repeat(60) {
			editor.layout(WIDTH, HEIGHT)
			editor.rescale(5, 3, -1.0)
		}
		assertEquals(HudElement.MIN_SCALE, module.element.scale)
	}

	@Test
	fun `fractional scroll deltas accumulate into whole scale steps`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		repeat(3) { assertFalse(editor.rescale(5, 3, 0.25)) }
		assertEquals(HudElement.DEFAULT_SCALE, module.element.scale)

		assertTrue(editor.rescale(5, 3, 0.25))
		assertEquals(1.1f, module.element.scale)
	}

	@Test
	fun `scrolling mid-drag is ignored so the grab point stays true to the size`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)

		editor.press(5, 3)
		editor.drag(105, 53, snap = false)

		assertFalse(editor.rescale(105, 53, 1.0))
		assertEquals(HudElement.DEFAULT_SCALE, module.element.scale)

		editor.release()
		assertTrue(editor.rescale(105, 53, 1.0))
		assertEquals(1.1f, module.element.scale)
	}

	@Test
	fun `scrolling away from every element falls back to the selection`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		editor.layout(WIDTH, HEIGHT)
		editor.press(5, 3)
		editor.release()

		assertTrue(editor.rescale(WIDTH - 1, HEIGHT - 1, 1.0))
		assertEquals(1.1f, module.element.scale)
	}

	@Test
	fun `overlapping elements hand the grab to the one drawn last`() {
		val manager = ModuleManager()
		val first = OverlayModule(name = "First")
		val second = OverlayModule(name = "Second")
		manager.registerAll(first, second)
		manager.enable(first)
		manager.enable(second)
		val editor = HudEditor(manager, metrics)
		editor.layout(WIDTH, HEIGHT)

		editor.press(5, 3)

		assertSame(second.element, editor.selected?.element)
	}

	@Test
	fun `a disabled module keeps a grabbable placeholder that does not enable it`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = false)
		editor.layout(WIDTH, HEIGHT)

		assertTrue(editor.targets.single().placeholder)
		assertTrue(editor.press(5, 3))
		editor.drag(65, 33, snap = false)

		assertEquals(60, module.element.offsetX)
		assertEquals(30, module.element.offsetY)
		assertFalse(module.enabled)
		assertTrue(module.element.visible)
	}

	@Test
	fun `a hidden element keeps a grabbable placeholder without becoming visible`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		module.element.visible = false
		editor.layout(WIDTH, HEIGHT)

		assertTrue(editor.targets.single().placeholder)
		assertTrue(editor.press(5, 3))
		editor.drag(25, 13, snap = false)

		assertEquals(20, module.element.offsetX)
		assertFalse(module.element.visible)
	}

	@Test
	fun `right-click reset restores a hidden element`() {
		val module = OverlayModule()
		val editor = editorFor(module, enabled = true)
		module.element.visible = false
		editor.layout(WIDTH, HEIGHT)

		assertTrue(editor.reset(5, 3))
		assertTrue(module.element.visible)
	}

	private fun editorFor(module: OverlayModule, enabled: Boolean): HudEditor {
		val manager = ModuleManager()
		manager.register(module)
		if (enabled) manager.enable(module)
		return HudEditor(manager, metrics)
	}

	private val metrics = HudMetrics { target ->
		target.placeholder = !target.rendering
		target.contentWidth = ELEMENT_WIDTH
		target.contentHeight = ELEMENT_HEIGHT
	}

	private class OverlayModule(
		name: String = "Overlay",
		anchor: HudAnchor = HudAnchor.TOP_LEFT,
		offsetX: Int = 0,
		offsetY: Int = 0
	) : Module(name, Category.VISUAL, "Fixture owning one HUD element.") {
		val element = hud(FixedHudElement("Status", ELEMENT_WIDTH, ELEMENT_HEIGHT, anchor, offsetX, offsetY))
	}

	private companion object {
		const val WIDTH = 854
		const val HEIGHT = 480
		const val LARGE_WIDTH = 1920
		const val LARGE_HEIGHT = 1080
		const val ELEMENT_WIDTH = 20
		const val ELEMENT_HEIGHT = 9
	}
}
