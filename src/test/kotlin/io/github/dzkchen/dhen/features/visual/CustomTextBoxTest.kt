package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.gui.StubFont
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomTextBoxTest {
	@AfterEach
	fun restoreDefault() {
		CustomTextBox.textSetting.reset()
	}

	@Test
	fun `a backslash-n in the typed text starts a new line`() {
		val font = StubFont()
		val element = CustomTextElement()
		CustomTextBox.textSetting.value = "&aone\\ntwo\\nthree"

		assertEquals(3 * font.lineHeight, element.height(font))
		assertTrue(element.hasContent)
	}

	@Test
	fun `a real newline is not a line break, because the setting cannot hold one`() {
		val font = StubFont()
		val element = CustomTextElement()
		CustomTextBox.textSetting.value = "one\ntwo"

		assertEquals(font.lineHeight, element.height(font))
	}

	@Test
	fun `blank text draws nothing`() {
		val element = CustomTextElement()
		CustomTextBox.textSetting.value = "   "

		assertFalse(element.hasContent)
	}
}
