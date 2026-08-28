package io.github.dzkchen.dhen.input

import io.github.dzkchen.dhen.uninitialized
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeybindScreenStateTest {
	@Test
	fun `sign editing is text input without a focused child`() {
		assertEquals(KeybindScreenState.TEXT_INPUT, keybindScreenState(uninitialized<SignEditScreen>()))
	}

	@Test
	fun `focused single and multiline editors cover chat anvil and both book fields`() {
		assertEquals(
			KeybindScreenState.TEXT_INPUT,
			keybindScreenState(FocusedScreen(uninitialized<EditBox>()))
		)
		assertEquals(
			KeybindScreenState.TEXT_INPUT,
			keybindScreenState(FocusedScreen(uninitialized<MultiLineEditBox>()))
		)
	}

	@Test
	fun `focused text input is found through nested containers`() {
		val nested = FocusedContainer(FocusedContainer(uninitialized<EditBox>()))

		assertEquals(KeybindScreenState.TEXT_INPUT, keybindScreenState(FocusedScreen(nested)))
	}

	@Test
	fun `a focused recipe book exposes whether its private search field is active`() {
		assertEquals(
			KeybindScreenState.TEXT_INPUT,
			keybindScreenState(FocusedScreen(TextInputFixture(true)))
		)
		assertEquals(
			KeybindScreenState.PLAIN,
			keybindScreenState(FocusedScreen(TextInputFixture(false)))
		)
	}

	private class FocusedScreen(
		private val child: GuiEventListener
	) : Screen(uninitialized<Minecraft>(), uninitialized<Font>(), Component.empty()) {
		override fun getFocused(): GuiEventListener = child
	}

	private class FocusedContainer(
		private val child: GuiEventListener
	) : AbstractContainerEventHandler() {
		override fun children(): List<GuiEventListener> = listOf(child)

		override fun getFocused(): GuiEventListener = child
	}

	private class TextInputFixture(
		override val textInputFocused: Boolean
	) : GuiEventListener, TextInputTarget {
		override fun setFocused(focused: Boolean) = Unit

		override fun isFocused(): Boolean = true
	}
}
