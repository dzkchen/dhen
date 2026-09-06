package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.mixin.ChatComponentAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage

object ChatTabs : Module(
	name = "Chat Tabs",
	category = Category.CHAT,
	description = "Puts channel tabs above the chat box and shows only the messages of the one you pick."
) {
	private var switchChannel by BooleanSetting(
		"Switch Channel",
		default = true,
		description = "Also sends the Hypixel command that moves you into the channel you clicked."
	)

	internal var active = ChatTab.ALL
		private set

	private var unsettled: GuiMessage? = null

	internal val shown: Boolean
		get() = enabled && SkyBlockLocation.onHypixel

	internal val filtering: Boolean
		get() = shown && active != ChatTab.ALL

	init {
		on<WorldChangeEvent> { if (it.phase == WorldChange.DISCONNECT) reset() }
		on<ClientTickEvent.End> { settleSeparators() }
	}

	override fun onDisabled() {
		reset()
	}

	internal fun select(tab: ChatTab) {
		val repeat = active == tab
		active = tab
		rescaleChat()
		if (repeat || !switchChannel || tab.command.isEmpty()) return
		Minecraft.getInstance().connection?.sendCommand(tab.command.removePrefix(SLASH))
	}

	private fun reset() {
		unsettled = null
		if (active == ChatTab.ALL) return
		active = ChatTab.ALL
		rescaleChat()
	}

	private fun settleSeparators() {
		val separator = unsettled ?: return
		unsettled = null
		if (!filtering) return
		val chat = Minecraft.getInstance().gui.hud.chat
		val access = chat as ChatComponentAccessor
		val history = access.chatAllMessages()
		if (separatorBelongs(history, separator, positionOf(history, separator), active) != SEPARATOR_SHOWN) return
		val scroll = access.chatScrollbarPos()
		access.chatScrollbarPos(0)
		access.chatRefreshTrimmed()
		val deepest = maxOf(0, access.chatTrimmedMessages().size - chat.linesPerPage)
		access.chatScrollbarPos(scroll.coerceIn(0, deepest))
	}

	@JvmStatic
	fun hides(chat: ChatComponent, message: GuiMessage): Boolean {
		if (!filtering) return false
		val plain = plainChatText(message.content())
		if (!isSeparatorLine(plain)) return !active.claims(plain)
		val history: List<GuiMessage> = (chat as ChatComponentAccessor).chatAllMessages()
		val at = positionOf(history, message)
		val outcome = separatorBelongs(history, message, at, active)
		if (outcome == SEPARATOR_SHOWN) return false
		if (outcome == SEPARATOR_UNSETTLED) unsettled = message
		return true
	}

	private const val SLASH = "/"
}

internal const val SEPARATOR_SHOWN = 0
internal const val SEPARATOR_HIDDEN = 1
internal const val SEPARATOR_UNSETTLED = 2

private const val NOT_IN_HISTORY = -1

internal fun positionOf(history: List<GuiMessage>, message: GuiMessage): Int {
	for (index in history.indices) if (history[index] === message) return index
	return NOT_IN_HISTORY
}

internal fun separatorBelongs(
	history: List<GuiMessage>,
	separator: GuiMessage,
	at: Int,
	tab: ChatTab
): Int {
	val tick = separator.addedTime()
	var blockArrived = false
	for (index in maxOf(0, at) until history.size) {
		val candidate = history[index]
		if (candidate.addedTime() != tick) break
		if (candidate !== separator) blockArrived = true
		if (claimsAsMessage(candidate, tab)) return SEPARATOR_SHOWN
	}
	for (index in at - 1 downTo 0) {
		val candidate = history[index]
		if (candidate.addedTime() != tick) break
		blockArrived = true
		if (claimsAsMessage(candidate, tab)) return SEPARATOR_SHOWN
	}
	return if (at == NOT_IN_HISTORY && !blockArrived) SEPARATOR_UNSETTLED else SEPARATOR_HIDDEN
}

private fun claimsAsMessage(message: GuiMessage, tab: ChatTab): Boolean {
	val plain = plainChatText(message.content())
	return !isSeparatorLine(plain) && tab.claims(plain)
}
