package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.mixin.ChatComponentAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

object ChatHistory : Module(
	name = "Chat History",
	category = Category.CHAT,
	description = "Keeps more chat scrollback and folds a repeated line into the earlier one."
) {
	private var extended by BooleanSetting(
		"Extended History",
		default = true,
		description = "Keeps more than the hundred messages the game normally remembers."
	)

	private var size by NumberSetting(
		"History Size",
		DEFAULT_SIZE,
		VANILLA_SIZE,
		MAX_SIZE,
		STEP,
		description = "How many chat messages the scrollback holds."
	).withDependency { extended }

	private var compact by BooleanSetting(
		"Compact Repeats",
		default = true,
		description = "Replaces a repeated line with the earlier one carrying a ×N counter."
	)

	private var scope by SelectorSetting(
		"Repeat Scope",
		CONSECUTIVE,
		listOf(CONSECUTIVE, WINDOW, ALWAYS),
		description = "How far back a repeat counts: only the line just above, within a time window, or anywhere."
	).withDependency { compact }

	private var window by NumberSetting(
		"Repeat Window",
		DEFAULT_WINDOW,
		1.0,
		MAX_WINDOW,
		1.0,
		description = "Minutes a line stays collapsible after it was first seen."
	).withDependency { compact && scope == WINDOW }

	private var keepClickable by BooleanSetting(
		"Keep Clickable Lines",
		default = true,
		description = "Leaves messages you can click on their own line so every one stays clickable."
	).withDependency { compact }

	private val compaction = ChatCompaction(
		scope = { scopeOf(scope) },
		windowMinutes = { window.toInt() },
		keepClickable = { keepClickable }
	)

	@JvmStatic
	fun historySize(vanilla: Int): Int = if (enabled && extended) size.toInt() else vanilla

	@JvmStatic
	fun compacted(chat: ChatComponent, message: Component): Component {
		if (!enabled || !compact) return message
		val text = compaction.process(message)
		compaction.collapsed?.let { drop(chat, it) }
		return text
	}

	@JvmStatic
	fun added(message: GuiMessage) {
		if (enabled && compact) compaction.added(message)
	}

	@JvmStatic
	fun cleared() {
		compaction.forget()
	}

	override fun onDisabled() {
		compaction.forget()
	}

	private fun drop(chat: ChatComponent, target: GuiMessage) {
		val access = chat as ChatComponentAccessor
		val history: MutableList<GuiMessage> = access.chatAllMessages()
		val block = repeatedBlock(history, target) ?: return
		val lines: MutableList<GuiMessage.Line> = access.chatTrimmedMessages()
		var scroll = access.chatScrollbarPos()
		for (index in block) {
			val message = history[index]
			var cursor = 0
			while (cursor < lines.size) {
				if (lines[cursor].parent() !== message) {
					cursor++
					continue
				}
				lines.removeAt(cursor)
				if (cursor < scroll) scroll--
			}
		}
		access.chatScrollbarPos(scroll.coerceIn(0, maxOf(0, lines.size - chat.linesPerPage)))
		history.subList(block.first, block.last + 1).clear()
	}

	private fun scopeOf(selected: String): RepeatScope = when (selected) {
		WINDOW -> RepeatScope.WINDOW
		ALWAYS -> RepeatScope.ALWAYS
		else -> RepeatScope.CONSECUTIVE
	}

	private const val CONSECUTIVE = "Consecutive"
	private const val WINDOW = "Time Window"
	private const val ALWAYS = "Always"
	private const val VANILLA_SIZE = 100.0
	private const val DEFAULT_SIZE = 1000.0
	private const val MAX_SIZE = 2048.0
	private const val STEP = 50.0
	private const val DEFAULT_WINDOW = 5.0
	private const val MAX_WINDOW = 60.0
}
