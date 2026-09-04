package io.github.dzkchen.dhen.features.chat

import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

internal const val MAX_TRACKED_REPEATS = 512

internal enum class RepeatScope { CONSECUTIVE, WINDOW, ALWAYS }

internal class ChatCompaction(
	private val scope: () -> RepeatScope,
	private val windowMinutes: () -> Int,
	private val keepClickable: () -> Boolean,
	private val now: () -> Long = System::currentTimeMillis
) {
	private class Repeat(var firstSeen: Long) {
		var count = 1
		var newest: GuiMessage? = null
	}

	private val seen = object : LinkedHashMap<String, Repeat>(64, LOAD_FACTOR, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Repeat>): Boolean =
			size > MAX_TRACKED_REPEATS
	}

	private var previous: String? = null
	private var pending: String? = null

	var collapsed: GuiMessage? = null
		private set

	fun process(message: Component): Component {
		pending = null
		collapsed = null
		if (keepClickable() && isClickable(message)) return message
		val key = stripRepeatSuffix(message.string)
		val trimmed = key.trim()
		if (trimmed.isEmpty() || isSeparatorLine(trimmed)) return message

		val moment = now()
		val consecutive = key == previous
		previous = key
		pending = key

		val repeat = seen[key]
		if (repeat == null || !collapses(repeat, consecutive, moment)) {
			seen[key] = Repeat(moment)
			return message
		}
		repeat.count++
		collapsed = repeat.newest
		return message.copy().append(
			Component.literal(REPEAT_PREFIX + repeat.count + REPEAT_SUFFIX).withStyle(ChatFormatting.GRAY)
		)
	}

	fun added(message: GuiMessage) {
		val key = pending ?: return
		pending = null
		seen[key]?.newest = message
	}

	fun forget() {
		seen.clear()
		previous = null
		pending = null
		collapsed = null
	}

	private fun collapses(repeat: Repeat, consecutive: Boolean, moment: Long): Boolean = when (scope()) {
		RepeatScope.CONSECUTIVE -> consecutive
		RepeatScope.WINDOW -> moment - repeat.firstSeen <= windowMinutes() * MINUTE_MS
		RepeatScope.ALWAYS -> true
	}

	private companion object {
		private const val LOAD_FACTOR = 0.75f
		private const val MINUTE_MS = 60_000L
		private const val REPEAT_PREFIX = " (×"
		private const val REPEAT_SUFFIX = ")"
	}
}

internal fun isClickable(component: Component): Boolean {
	if (component.style.clickEvent != null) return true
	for (sibling in component.siblings) if (isClickable(sibling)) return true
	return false
}

internal fun repeatedBlock(history: List<GuiMessage>, target: GuiMessage): IntRange? {
	var at = -1
	for (index in history.indices) {
		if (history[index] === target) {
			at = index
			break
		}
	}
	if (at < 0) return null
	val tick = history[at].addedTime()
	var oldest = at
	var newest = at
	while (oldest + 1 < history.size && history[oldest + 1].addedTime() == tick && isFiller(history[oldest + 1])) oldest++
	while (newest > 0 && history[newest - 1].addedTime() == tick && isFiller(history[newest - 1])) newest--
	return newest..oldest
}

private fun isFiller(message: GuiMessage): Boolean {
	val text = message.content().string.trim()
	return text.isEmpty() || isSeparatorLine(text)
}
