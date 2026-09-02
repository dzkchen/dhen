package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.module.Category
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class InventorySearchTest {
	private val expression = Expression()

	@BeforeEach
	@AfterEach
	fun reset() {
		for (setting in InventorySearch.settings) setting.reset()
		InventorySearch.typeQuery("")
		if (InventorySearch.focused) InventorySearch.pressed(GLFW.GLFW_KEY_ESCAPE, false)
	}

	@Test
	fun `the module declares the three settings the source has`() {
		assertEquals("Inventory Search", InventorySearch.name)
		assertEquals(Category.INVENTORY, InventorySearch.category)
		assertEquals(
			listOf("Ignore Caps", "Search Lore", "Highlight Color"),
			InventorySearch.settings.map { it.name }
		)
		assertTrue(InventorySearch.ignoreCapsSetting.default)
		assertTrue(InventorySearch.searchLoreSetting.default)
	}

	@Test
	fun `control F opens the field and escape closes it again`() {
		assertFalse(InventorySearch.focused)
		assertTrue(InventorySearch.pressed(GLFW.GLFW_KEY_F, true))
		assertTrue(InventorySearch.focused)

		assertTrue(InventorySearch.pressed(GLFW.GLFW_KEY_ESCAPE, false))

		assertFalse(InventorySearch.focused)
		assertFalse(InventorySearch.pressed(GLFW.GLFW_KEY_E, false))
	}

	@Test
	fun `typing edits the query around the caret`() {
		InventorySearch.pressed(GLFW.GLFW_KEY_F, true)
		for (character in "aspect") InventorySearch.insert(character)

		assertEquals("aspect", InventorySearch.query)
		assertEquals(6, InventorySearch.caretAt)

		InventorySearch.pressed(GLFW.GLFW_KEY_HOME, false)
		InventorySearch.insert('t')

		assertEquals("taspect", InventorySearch.query)

		InventorySearch.pressed(GLFW.GLFW_KEY_BACKSPACE, false)
		InventorySearch.pressed(GLFW.GLFW_KEY_END, false)
		InventorySearch.pressed(GLFW.GLFW_KEY_BACKSPACE, false)

		assertEquals("aspec", InventorySearch.query)
	}

	@Test
	fun `every query token has to land somewhere for the slot to match`() {
		val sword = ItemFixture.named("Aspect of the End")
		InventorySearch.typeQuery("aspect end")

		assertTrue(InventorySearch.matches(0, sword))

		InventorySearch.typeQuery("aspect dragon")

		assertFalse(InventorySearch.matches(1, sword))
	}

	@Test
	fun `a plain substring still matches the way the source matched it`() {
		val sword = ItemFixture.named("Aspect of the End")
		InventorySearch.typeQuery("spect of")

		assertTrue(InventorySearch.matches(0, sword))
	}

	@Test
	fun `caps and lore are only searched while their toggles are on`() {
		val scroll = ItemFixture.lored("Costs 100 coins")
		InventorySearch.typeQuery("coins")

		assertTrue(InventorySearch.matches(0, scroll))

		InventorySearch.searchLoreSetting.value = false

		assertFalse(InventorySearch.matches(1, scroll))

		InventorySearch.searchLoreSetting.value = true
		InventorySearch.ignoreCapsSetting.value = false
		InventorySearch.typeQuery("COINS")

		assertFalse(InventorySearch.matches(2, scroll))
	}

	@Test
	fun `an empty query and an empty slot never match`() {
		InventorySearch.typeQuery("")

		assertFalse(InventorySearch.matches(0, ItemFixture.named("Aspect of the End")))

		InventorySearch.typeQuery("aspect")

		assertFalse(InventorySearch.matches(0, ItemStack.EMPTY))
	}

	@Test
	fun `the calculator reads the shorthand the source read`() {
		assertEquals(3000.0, expression.evaluate("1k+2k"))
		assertEquals(3_500_000.0, expression.evaluate("3.5m"))
		assertEquals(1_000_000_000.0, expression.evaluate("1B"))
		assertEquals(2_000_000_000_000.0, expression.evaluate("2t"))
	}

	@Test
	fun `precedence and parentheses come out the way arithmetic expects`() {
		assertEquals(14.0, expression.evaluate("2+3*4"))
		assertEquals(9.0, expression.evaluate("(1+2)*3"))
		assertEquals(6.0, expression.evaluate("2x3"))
		assertEquals(2.5, expression.evaluate("10/4"))
		assertEquals(5.0, expression.evaluate("-5+10"))
		assertEquals(-6.0, expression.evaluate("-2*3"))
	}

	@Test
	fun `anything that is not arithmetic answers nothing at all`() {
		assertNull(expression.evaluate(""))
		assertNull(expression.evaluate("aspect"))
		assertNull(expression.evaluate("10/0"))
		assertNull(expression.evaluate("1+"))
		assertNull(expression.evaluate("(1+2"))
		assertNull(expression.evaluate("1..2"))
		assertNull(expression.evaluate("2 dragon"))
	}

	@Test
	fun `a query that parses shows its total beside the field`() {
		InventorySearch.typeQuery("1k+2k")

		assertEquals(" = 3,000", InventorySearch.result)

		InventorySearch.typeQuery("10/4")

		assertEquals(" = 2.50", InventorySearch.result)

		InventorySearch.typeQuery("aspect")

		assertNull(InventorySearch.result)
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
