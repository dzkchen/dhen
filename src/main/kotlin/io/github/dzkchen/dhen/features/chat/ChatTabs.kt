package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
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

	internal val shown: Boolean
		get() = enabled && SkyBlockLocation.onHypixel

	internal val filtering: Boolean
		get() = shown && active != ChatTab.ALL

	init {
		on<WorldChangeEvent> { if (it.phase == WorldChange.DISCONNECT) reset() }
	}

	override fun onDisabled() {
		reset()
	}

	internal fun select(tab: ChatTab) {
		val repeat = active == tab
		active = tab
		rebuild()
		if (repeat || !switchChannel || tab.command.isEmpty()) return
		Minecraft.getInstance().connection?.sendCommand(tab.command.removePrefix(SLASH))
	}

	private fun reset() {
		if (active == ChatTab.ALL) return
		active = ChatTab.ALL
		rebuild()
	}

	private fun rebuild() {
		Minecraft.getInstance()?.gui?.hud?.chat?.rescaleChat()
	}

	@JvmStatic
	fun hides(chat: ChatComponent, message: GuiMessage): Boolean {
		if (!filtering) return false
		val plain = plainChatText(message.content())
		if (!isSeparatorLine(plain)) return !active.claims(plain)
		return !separatorBelongs(chat, message)
	}

	private fun separatorBelongs(chat: ChatComponent, separator: GuiMessage): Boolean {
		val history: List<GuiMessage> = (chat as ChatComponentAccessor).chatAllMessages()
		if (history.isEmpty()) return false
		var at = -1
		for (index in history.indices) {
			if (history[index] === separator) {
				at = index
				break
			}
		}
		val tick = if (at < 0) history.first().addedTime() else history[at].addedTime()
		for (index in maxOf(0, at) until history.size) {
			val candidate = history[index]
			if (candidate.addedTime() != tick) break
			if (claimsAsMessage(candidate)) return true
		}
		for (index in at - 1 downTo 0) {
			val candidate = history[index]
			if (candidate.addedTime() != tick) break
			if (claimsAsMessage(candidate)) return true
		}
		return false
	}

	private fun claimsAsMessage(message: GuiMessage): Boolean {
		val plain = plainChatText(message.content())
		return !isSeparatorLine(plain) && active.claims(plain)
	}

	private const val SLASH = "/"
}
