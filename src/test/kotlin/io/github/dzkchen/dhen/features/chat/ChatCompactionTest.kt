package io.github.dzkchen.dhen.features.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatCompactionTest {
	private var scope = RepeatScope.CONSECUTIVE
	private var window = 5
	private var keepClickable = true
	private var clock = 0L

	private val compaction = ChatCompaction({ scope }, { window }, { keepClickable }, { clock })

	@Test
	fun `an immediate repeat collapses and an intervening line resets the streak`() {
		assertEquals("Bob: hi", counted("Bob: hi"))
		assertEquals("Bob: hi (×2)", counted("Bob: hi"))
		assertEquals("Ann: yo", counted("Ann: yo"))
		assertEquals("Bob: hi", counted("Bob: hi"))
	}

	@Test
	fun `the counter keeps climbing because the key ignores the counter it already carries`() {
		counted("Bob: hi")
		assertEquals("Bob: hi (×2)", counted("Bob: hi"))
		assertEquals("Bob: hi (×3)", counted("Bob: hi"))
		assertEquals("Bob: hi (×4)", counted("Bob: hi"))
	}

	@Test
	fun `a repeat inside the window collapses and one past it starts over`() {
		scope = RepeatScope.WINDOW
		counted("Bob: hi")
		clock = 4 * MINUTE
		assertEquals("Bob: hi (×2)", counted("Bob: hi"))
		clock = 4 * MINUTE + 6 * MINUTE
		assertEquals("Bob: hi", counted("Bob: hi"))
	}

	@Test
	fun `the always scope collapses across intervening lines`() {
		scope = RepeatScope.ALWAYS
		counted("Bob: hi")
		counted("Ann: yo")
		assertEquals("Bob: hi (×2)", counted("Bob: hi"))
	}

	@Test
	fun `blank lines and separators are never counted`() {
		counted("   ")
		assertEquals("   ", counted("   "))
		counted("--------")
		assertEquals("--------", counted("--------"))
	}

	@Test
	fun `a click event on an inner span still keeps the line out of the counter`() {
		val clickable = Component.literal("Bob ").append(
			Component.literal("[ACCEPT]").withStyle { style -> style.withClickEvent(ClickEvent.RunCommand("/party accept")) }
		)
		compaction.process(clickable)
		assertEquals("Bob [ACCEPT]", compaction.process(clickable).string)
		keepClickable = false
		compaction.process(clickable)
		assertEquals("Bob [ACCEPT] (×2)", compaction.process(clickable).string)
	}

	@Test
	fun `a line pushed out of the tracked set by newer ones starts counting again`() {
		scope = RepeatScope.ALWAYS
		counted("Bob: hi")
		for (index in 1..MAX_TRACKED_REPEATS) counted("filler $index")
		assertEquals("Bob: hi", counted("Bob: hi"))
	}

	@Test
	fun `removing a repeat takes the separators printed in its own tick and stops at the next`() {
		val older = message(1, "older")
		val ruleAbove = message(2, "--------")
		val target = message(2, "Bob: hi")
		val ruleBelow = message(2, "--------")
		val otherTick = message(3, "--------")
		val history = listOf(otherTick, ruleBelow, target, ruleAbove, older)

		assertEquals(1..3, repeatedBlock(history, target))
		assertNull(repeatedBlock(history, message(2, "Bob: hi")))
	}

	private fun counted(text: String): String = compaction.process(Component.literal(text)).string

	private fun message(tick: Int, text: String): GuiMessage =
		GuiMessage(tick, Component.literal(text), null, GuiMessageSource.SYSTEM_SERVER, null)

	private companion object {
		private const val MINUTE = 60_000L
	}
}
