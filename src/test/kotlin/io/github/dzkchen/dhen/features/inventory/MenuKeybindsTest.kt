package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import java.util.regex.Pattern

class MenuKeybindsTest {
	@Test
	fun `wardrobe pages read both set titles through colour codes`() {
		val pages = MenuPages("\\((\\d+)/(\\d+)\\) (?:Armor|Equipment) Sets")
		assertTrue(pages.matches(Component.literal("§8(2/4) Armor Sets")))
		assertEquals(2, pages.current)
		assertEquals(4, pages.total)
		assertTrue(pages.matches(Component.literal("(1/3) Equipment Sets")))
		assertFalse(pages.matches(Component.literal("Armor Sets")))
	}

	@Test
	fun `the pet menu reads a counter on either side of the title and defaults to one of one`() {
		val pages = MenuPages(PETS_TITLE, pageless = true)
		assertTrue(pages.matches(Component.literal("Pets")))
		assertEquals(1, pages.current)
		assertEquals(1, pages.total)
		assertTrue(pages.matches(Component.literal("§b(3/7) Pets")))
		assertEquals(3, pages.current)
		assertEquals(7, pages.total)
		assertTrue(pages.matches(Component.literal("Pets (2/5)")))
		assertEquals(2, pages.current)
		assertEquals(5, pages.total)
	}

	@Test
	fun `a searched pet menu still matches and reports its page`() {
		val pages = MenuPages(PETS_TITLE, pageless = true)
		assertTrue(pages.matches(Component.literal("Pets: \"bee\" (2/3)")))
		assertEquals(2, pages.current)
		assertEquals(3, pages.total)
		assertFalse(pages.matches(Component.literal("Your Pets Collection")))
	}

	@Test
	fun `loadout pages accept the singular and plural titles and two digit counters`() {
		val pages = MenuPages("\\((\\d+)/(\\d+)\\) Loadouts?")
		assertTrue(pages.matches(Component.literal("(1/2) Loadout")))
		assertTrue(pages.matches(Component.literal("(10/12) Loadouts")))
		assertEquals(10, pages.current)
		assertEquals(12, pages.total)
		assertFalse(pages.matches(Component.literal("Your Equipment and Stats")))
	}

	@Test
	fun `a bound key matches only a keyboard press of the same code`() {
		val settings = arrayOf(
			KeybindSetting("first", GLFW.GLFW_KEY_1),
			KeybindSetting("second", GLFW.GLFW_KEY_2)
		)
		assertEquals(1, MenuKeybinds.boundIndex(GLFW.GLFW_KEY_2, mouse = false, settings))
		assertEquals(NO_MENU_BIND, MenuKeybinds.boundIndex(GLFW.GLFW_KEY_2, mouse = true, settings))
		assertEquals(NO_MENU_BIND, MenuKeybinds.boundIndex(GLFW.GLFW_KEY_3, mouse = false, settings))
	}

	@Test
	fun `a mouse bound key matches only a mouse press`() {
		val settings = arrayOf(KeybindSetting("side", GLFW.GLFW_MOUSE_BUTTON_4))
		assertEquals(0, MenuKeybinds.boundIndex(GLFW.GLFW_MOUSE_BUTTON_4, mouse = true, settings))
		assertEquals(NO_MENU_BIND, MenuKeybinds.boundIndex(GLFW.GLFW_MOUSE_BUTTON_4, mouse = false, settings))
	}

	@Test
	fun `an unbound key never matches a scancode only press`() {
		val settings = arrayOf(KeybindSetting("unbound"), KeybindSetting("bound", GLFW.GLFW_KEY_5))
		assertEquals(NO_MENU_BIND, MenuKeybinds.boundIndex(GLFW.GLFW_KEY_UNKNOWN, mouse = false, settings))
	}

	@Test
	fun `lore prompts are read through colour codes and skip empty slots`() {
		assertTrue(MenuKeybinds.lorePrompt(ItemFixture.lored("§eLeft-click to equip!"), "Left-click to equip!"))
		assertFalse(MenuKeybinds.lorePrompt(ItemFixture.lored("§7Empty loadout"), "Left-click to equip!"))
		assertFalse(MenuKeybinds.lorePrompt(ItemStack.EMPTY, "Left-click to equip!"))
	}

	@Test
	fun `the equipped wardrobe set is recognised by its slot name`() {
		val matcher = Pattern.compile("Slot \\d+: Equipped").matcher("")
		assertTrue(MenuKeybinds.namePrompt(matcher, ItemFixture.named("§aSlot 3: Equipped")))
		assertFalse(MenuKeybinds.namePrompt(matcher, ItemFixture.named("§aSlot 3: Empty")))
		assertFalse(MenuKeybinds.namePrompt(matcher, ItemStack.EMPTY))
	}

	@Test
	fun `a held key is swallowed until the guard expires and a different key is not`() {
		assertFalse(MenuKeybinds.heldDown(GLFW.GLFW_KEY_1, mouse = false, 1_000L))
		assertTrue(MenuKeybinds.heldDown(GLFW.GLFW_KEY_1, mouse = false, 1_100L))
		assertFalse(MenuKeybinds.heldDown(GLFW.GLFW_KEY_RIGHT, mouse = false, 1_150L))
		assertFalse(MenuKeybinds.heldDown(GLFW.GLFW_KEY_1, mouse = false, 1_600L))
	}

	companion object {
		private const val PETS_TITLE = "^(?:\\((\\d+)/(\\d+)\\) )?Pets(?:: \".*\")?(?: \\((\\d+)/(\\d+)\\))? ?$"

		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
