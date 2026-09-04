package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.mixin.ChatComponentAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import java.util.IdentityHashMap
import java.util.Locale

private val WHITESPACE = Regex("\\s+")
private val NO_WORDS = emptyArray<String>()

object ChatSearch : Module(
	name = "Chat Search",
	category = Category.CHAT,
	description = "Press Ctrl and F with the chat box open to show only the lines holding every word you type."
) {
	internal var pinned by BooleanSetting(
		"Keep Bar Open",
		description = "Leaves the search bar up whenever the chat box is open, instead of only after Ctrl and F."
	)

	internal var open = false
		private set

	internal var query = ""
		private set

	private var words = NO_WORDS
	private var typedAt = 0L
	private var owed = false

	private var countedFor = ""
	private var countedTotal = -1
	private var countedNewest: GuiMessage? = null
	private var countLabel = ""

	private val searchables = IdentityHashMap<GuiMessage, String>()

	internal val filtering: Boolean
		get() = enabled && open && words.isNotEmpty()

	init {
		on<ClientTickEvent.End> { settle() }
	}

	override fun onDisabled() {
		close()
	}

	internal fun show() {
		open = true
	}

	internal fun close() {
		if (!open) return
		open = false
		query = ""
		words = NO_WORDS
		owed = false
		countedTotal = -1
		countedNewest = null
		searchables.clear()
		rebuild()
	}

	internal fun ask(typed: String) {
		if (typed == query) return
		query = typed
		val trimmed = typed.trim()
		words = if (trimmed.isEmpty()) NO_WORDS else trimmed.lowercase(Locale.ROOT).split(WHITESPACE).toTypedArray()
		typedAt = System.currentTimeMillis()
		owed = true
	}

	internal fun countLabel(): String {
		val history = history()
		val newest = history.firstOrNull()
		if (countedTotal == history.size && countedFor == query && countedNewest === newest) return countLabel
		var matched = 0
		for (message in history) if (holds(searchableOf(message))) matched++
		countedTotal = history.size
		countedNewest = newest
		countedFor = query
		countLabel = "$matched/${history.size}"
		return countLabel
	}

	internal fun matches(content: Component): Boolean = !filtering || holds(searchable(content))

	@JvmStatic
	fun hides(message: GuiMessage): Boolean = filtering && !holds(searchableOf(message))

	private fun holds(plain: String): Boolean {
		for (word in words) if (!plain.contains(word)) return false
		return true
	}

	private fun searchableOf(message: GuiMessage): String {
		searchables[message]?.let { return it }
		if (searchables.size >= MAX_SEARCHABLES) searchables.clear()
		val plain = searchable(message.content())
		searchables[message] = plain
		return plain
	}

	private fun history(): List<GuiMessage> =
		(Minecraft.getInstance().gui.hud.chat as ChatComponentAccessor).chatAllMessages()

	private fun settle() {
		if (!owed || System.currentTimeMillis() - typedAt < SETTLE_MS) return
		owed = false
		rebuild()
	}

	private fun rebuild() {
		Minecraft.getInstance()?.gui?.hud?.chat?.rescaleChat()
	}

	private fun searchable(content: Component): String =
		stripRepeatSuffix(withoutCodes(content.string)).lowercase(Locale.ROOT)

	private const val SETTLE_MS = 100L
	private const val MAX_SEARCHABLES = 4096
}
