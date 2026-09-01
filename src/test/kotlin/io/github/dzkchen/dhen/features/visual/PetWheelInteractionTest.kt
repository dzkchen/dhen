package io.github.dzkchen.dhen.features.visual

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.config.KeybindSetting
import net.minecraft.client.KeyMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PetWheelInteractionTest {
	private val layout = PetWheelLayout()
	private val customBinds = Array(PetWheelCache.PETS_PER_PAGE) { index ->
		KeybindSetting("Test Pet Slot ${index + 1}", GLFW.GLFW_KEY_1 + index)
	}
	private val hotbarBinds = Array(PetWheelCache.PETS_PER_PAGE) { index ->
		KeyMapping(
			"key.dhen.test.pet_wheel.${index + 1}",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_F1 + index,
			KeyMapping.Category.INVENTORY
		)
	}

	@BeforeEach
	fun resetBinds() {
		for (index in customBinds.indices) customBinds[index].code = GLFW.GLFW_KEY_1 + index
		for (index in hotbarBinds.indices) {
			hotbarBinds[index].setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F1 + index))
		}
	}

	@Test
	fun `reference layout keeps the source centre and scale bounds`() {
		layout.update(960, 540, 50.0, 9)

		assertEquals(1f, layout.referenceScale)
		assertEquals(480f, layout.centerX)
		assertEquals(262f, layout.centerY)
		assertEquals(96.6f, layout.outerRadius, 0.001f)
		assertEquals(layout.outerRadius * 0.55f, layout.innerRadius, 0.001f)

		layout.update(960, 540, 200.0, 9)

		assertEquals(186.3f, layout.outerRadius, 0.001f)
	}

	@Test
	fun `empty and inner hole pointers select no segment`() {
		layout.update(960, 540, 100.0, 0)
		assertEquals(PetWheelLayout.NO_INDEX, layout.hoveredIndex(480.0, 150.0))

		layout.update(960, 540, 100.0, 9)
		assertEquals(PetWheelLayout.NO_INDEX, layout.hoveredIndex(480.0, 262.0))
		assertEquals(
			PetWheelLayout.NO_INDEX,
			layout.hoveredIndex(480.0, (layout.centerY - layout.innerRadius).toDouble())
		)
	}

	@Test
	fun `one segment accepts every direction outside the inner hole`() {
		layout.update(960, 540, 100.0, 1)

		assertEquals(0, layout.hoveredIndex(480.0, 100.0))
		assertEquals(0, layout.hoveredIndex(800.0, 262.0))
		assertEquals(0, layout.hoveredIndex(480.0, 500.0))
		assertEquals(0, layout.hoveredIndex(100.0, 262.0))
	}

	@Test
	fun `nine segment centres run top first and clockwise`() {
		layout.update(960, 540, 100.0, 9)
		val radius = (layout.innerRadius + layout.outerRadius) / 2.0

		for (index in 0 until 9) {
			val angle = -PI / 2.0 + index * PI * 2.0 / 9.0
			val x = layout.centerX + cos(angle) * radius
			val y = layout.centerY + sin(angle) * radius
			assertEquals(index, layout.hoveredIndex(x, y))
		}
	}

	@Test
	fun `angle boundaries divide neighbouring segments deterministically`() {
		layout.update(960, 540, 100.0, 4)
		val radius = 100.0
		val before = -PI / 4.0 - 0.000001
		val after = -PI / 4.0 + 0.000001

		assertEquals(
			0,
			layout.hoveredIndex(layout.centerX + cos(before) * radius, layout.centerY + sin(before) * radius)
		)
		assertEquals(
			1,
			layout.hoveredIndex(layout.centerX + cos(after) * radius, layout.centerY + sin(after) * radius)
		)
	}

	@Test
	fun `vanilla pointer coordinates are transformed into reference space`() {
		layout.update(1920, 1080, 100.0, 4)

		assertEquals(0, layout.hoveredIndex(960.0, 300.0))
		assertEquals(1, layout.hoveredIndex(1400.0, 524.0))
	}

	@Test
	fun `custom keyboard and mouse bindings resolve only visible slots`() {
		customBinds[2].code = GLFW.GLFW_MOUSE_BUTTON_5

		assertEquals(0, resolve(GLFW.GLFW_KEY_1, mouse = false, visibleCount = 3, useHotbar = false))
		assertEquals(2, resolve(GLFW.GLFW_MOUSE_BUTTON_5, mouse = true, visibleCount = 3, useHotbar = false))
		assertEquals(PetWheelLayout.NO_INDEX, resolve(GLFW.GLFW_MOUSE_BUTTON_5, mouse = false, visibleCount = 3, useHotbar = false))
		assertEquals(PetWheelLayout.NO_INDEX, resolve(GLFW.GLFW_KEY_4, mouse = false, visibleCount = 3, useHotbar = false))
	}

	@Test
	fun `hotbar mode follows rebound keyboard and mouse mappings`() {
		hotbarBinds[1].setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G))
		hotbarBinds[2].setKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4))

		assertEquals(1, resolve(GLFW.GLFW_KEY_G, mouse = false, visibleCount = 9, useHotbar = true))
		assertEquals(2, resolve(GLFW.GLFW_MOUSE_BUTTON_4, mouse = true, visibleCount = 9, useHotbar = true))
		assertEquals(PetWheelLayout.NO_INDEX, resolve(GLFW.GLFW_KEY_2, mouse = false, visibleCount = 9, useHotbar = true))
		assertEquals(PetWheelLayout.NO_INDEX, resolve(GLFW.GLFW_MOUSE_BUTTON_4, mouse = false, visibleCount = 9, useHotbar = true))
	}

	@Test
	fun `debounce accepts the first action and the exact delay edge`() {
		val debounce = PetWheelDebounce()

		assertTrue(debounce.accept(0L))
		assertFalse(debounce.accept(299L))
		assertTrue(debounce.accept(300L))
		assertFalse(debounce.accept(599L))
		assertTrue(debounce.accept(600L))
	}

	@Test
	fun `debounce reset starts a new action window`() {
		val debounce = PetWheelDebounce()
		debounce.accept(1_000L)

		debounce.reset()

		assertTrue(debounce.accept(1_001L))
	}

	private fun resolve(code: Int, mouse: Boolean, visibleCount: Int, useHotbar: Boolean): Int = PetWheelInput.resolve(
		code,
		mouse,
		visibleCount,
		useHotbar,
		customBinds,
		hotbarBinds
	)
}
