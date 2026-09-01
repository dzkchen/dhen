package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudPersistence
import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import org.joml.Matrix3x2fStack
import org.joml.Vector2f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoveableVanillaHudTest {
	@AfterEach
	fun reset() {
		for (setting in MoveableVanillaHud.settings) setting.reset()
		for (element in MoveableVanillaHud.hudElements) element.resetToDeclared()
	}

	@Test
	fun `module declares four layer controls and the outside gate`() {
		assertEquals("Moveable Vanilla HUD", MoveableVanillaHud.name)
		assertEquals(Category.VISUAL, MoveableVanillaHud.category)
		assertEquals(
			listOf("Hotbar", "XP Bar", "Held Item Name", "Action Bar", "Show Outside SkyBlock"),
			MoveableVanillaHud.settings.map { it.name }
		)
		assertTrue(MoveableVanillaHud.settings.none { it.default as Boolean })
	}

	@Test
	fun `phantom elements declare the vanilla identity geometry`() {
		assertElement(MoveableVanillaHud.hotbarElement, "Vanilla Hotbar", 182, 22, 91, 22)
		assertElement(MoveableVanillaHud.experienceElement, "Vanilla XP Bar", 182, 5, 91, 29)
		assertElement(MoveableVanillaHud.heldItemElement, "Vanilla Held Item Name", 182, 10, 91, 59)
		assertElement(MoveableVanillaHud.actionBarElement, "Vanilla Action Bar", 182, 10, 91, 72)
	}

	@Test
	fun `layer gate requires the module its control and an allowed world`() {
		assertFalse(vanillaHudLayerActive(false, true, true, false))
		assertFalse(vanillaHudLayerActive(true, false, true, false))
		assertFalse(vanillaHudLayerActive(true, true, false, false))
		assertTrue(vanillaHudLayerActive(true, true, true, false))
		assertTrue(vanillaHudLayerActive(true, true, false, true))
	}

	@Test
	fun `controls and phantom layouts round trip through their persistence seams`() {
		MoveableVanillaHud.hotbarSetting.on = true
		MoveableVanillaHud.experienceSetting.on = true
		MoveableVanillaHud.showOutsideSkyBlockSetting.on = true
		MoveableVanillaHud.hotbarElement.anchor = HudAnchor.TOP_RIGHT
		MoveableVanillaHud.hotbarElement.offsetX = -17
		MoveableVanillaHud.hotbarElement.offsetY = 23
		MoveableVanillaHud.hotbarElement.scale = 1.7f
		val settings = SettingCodec.writeInto(JsonObject(), MoveableVanillaHud.settings)
		val hud = HudPersistence.snapshot(MoveableVanillaHud.hudElements)

		for (setting in MoveableVanillaHud.settings) setting.reset()
		for (element in MoveableVanillaHud.hudElements) element.resetToDeclared()
		SettingCodec.readInto(settings, MoveableVanillaHud.settings, MoveableVanillaHud.name)
		HudPersistence.apply(MoveableVanillaHud.hudElements, hud)

		assertTrue(MoveableVanillaHud.hotbarSetting.on)
		assertTrue(MoveableVanillaHud.experienceSetting.on)
		assertTrue(MoveableVanillaHud.showOutsideSkyBlockSetting.on)
		assertEquals(HudAnchor.TOP_RIGHT, MoveableVanillaHud.hotbarElement.anchor)
		assertEquals(-17, MoveableVanillaHud.hotbarElement.offsetX)
		assertEquals(23, MoveableVanillaHud.hotbarElement.offsetY)
		assertEquals(1.7f, MoveableVanillaHud.hotbarElement.scale)
	}

	@Test
	fun `declared layouts leave every vanilla anchor unchanged`() {
		val pose = Matrix3x2fStack(STACK_CAPACITY)
		for (element in listOf(
			MoveableVanillaHud.hotbarElement,
			MoveableVanillaHud.experienceElement,
			MoveableVanillaHud.heldItemElement,
			MoveableVanillaHud.actionBarElement
		)) {
			val transform = VanillaHudLayerTransform(element)
			assertTrue(transform.begin(pose, SCREEN_WIDTH, SCREEN_HEIGHT, true))
			assertEquals(1.0f, pose.m00(), TOLERANCE)
			assertEquals(1.0f, pose.m11(), TOLERANCE)
			assertEquals(0.0f, pose.m20(), TOLERANCE)
			assertEquals(0.0f, pose.m21(), TOLERANCE)
			transform.end(pose, true)
		}
	}

	@Test
	fun `move and scale pivot the vanilla anchor onto the editor position`() {
		val element = VanillaHudElement("Test", 182, 22, 91, 22)
		element.anchor = HudAnchor.TOP_LEFT
		element.offsetX = 40
		element.offsetY = 30
		element.scale = 1.5f
		val transform = VanillaHudLayerTransform(element)
		val pose = Matrix3x2fStack(STACK_CAPACITY)

		assertTrue(transform.begin(pose, SCREEN_WIDTH, SCREEN_HEIGHT, true))

		val mapped = pose.transformPosition(
			element.vanillaLeft(SCREEN_WIDTH).toFloat(),
			element.vanillaTop(SCREEN_HEIGHT).toFloat(),
			Vector2f()
		)
		assertEquals(40.0f, mapped.x, TOLERANCE)
		assertEquals(30.0f, mapped.y, TOLERANCE)
		transform.end(pose, true)
	}

	@Test
	fun `disabled and reentrant entries never pop another invocation`() {
		val pose = Matrix3x2fStack(STACK_CAPACITY)
		pose.translate(7.0f, 11.0f)
		val transform = VanillaHudLayerTransform(VanillaHudElement("Test", 182, 22, 91, 22))

		assertFalse(transform.begin(pose, SCREEN_WIDTH, SCREEN_HEIGHT, false))
		assertTrue(transform.begin(pose, SCREEN_WIDTH, SCREEN_HEIGHT, true))
		assertFalse(transform.begin(pose, SCREEN_WIDTH, SCREEN_HEIGHT, true))
		transform.end(pose, false)
		transform.end(pose, true)

		assertEquals(7.0f, pose.m20(), TOLERANCE)
		assertEquals(11.0f, pose.m21(), TOLERANCE)
	}

	@Test
	fun `registry replacement reuses one wrapper for changing vanilla callbacks`() {
		val replacement = MoveableVanillaHud.replacement(VanillaHudLayer.HOTBAR, Failsafe())
		val first = HudElement { _, _ -> }
		val second = HudElement { _, _ -> }

		assertSame(replacement.apply(first), replacement.apply(second))
	}

	private fun assertElement(
		element: VanillaHudElement,
		name: String,
		width: Int,
		height: Int,
		vanillaOffsetX: Int,
		vanillaOffsetY: Int
	) {
		assertEquals(name, element.name)
		assertEquals(width, element.contentWidth)
		assertEquals(height, element.contentHeight)
		assertEquals(vanillaOffsetX, element.vanillaOffsetX)
		assertEquals(vanillaOffsetY, element.vanillaOffsetY)
		assertEquals(HudAnchor.BOTTOM_CENTER, element.anchor)
		assertEquals(0, element.offsetX)
		assertEquals(height - vanillaOffsetY, element.offsetY)
	}

	private companion object {
		const val STACK_CAPACITY = 4
		const val SCREEN_WIDTH = 641
		const val SCREEN_HEIGHT = 360
		const val TOLERANCE = 0.0001f
	}
}
