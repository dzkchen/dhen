package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.pillButton
import io.github.dzkchen.dhen.mixin.ChatComponentAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.chat.GuiMessage
import org.lwjgl.glfw.GLFW

internal enum class ChatMenuEntry(val label: String) {
	COPY_TEXT("Copy Text"),
	COPY_BODY("Copy Message"),
	COPY_CODES("Copy & Codes"),
	DELETE("Delete")
}

object ChatContextMenu {
	private val memos = Array(ChatMenuEntry.entries.size) { DhenType.memo() }

	private var target: GuiMessage? = null
	private var left = 0
	private var top = 0
	private var width = 0

	@JvmStatic
	fun clicked(click: MouseButtonEvent, screenWidth: Int, screenHeight: Int, font: Font): Boolean {
		if (!ChatTweaks.menuOnRightClick) return false
		if (target != null && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) return picked(click.x(), click.y())
		if (click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
		val message = hoveredChatMessage(Minecraft.getInstance(), click.x(), click.y())
		if (message == null) {
			closed()
			return false
		}
		if (ChatTweaks.copyOnRightClick) {
			apply(ChatMenuEntry.COPY_TEXT, message)
			return true
		}
		open(message, click.x().toInt(), click.y().toInt(), screenWidth, screenHeight, font)
		return true
	}

	@JvmStatic
	fun keyed(key: KeyEvent): Boolean {
		if (target == null || key.key() != GLFW.GLFW_KEY_ESCAPE) return false
		closed()
		return true
	}

	@JvmStatic
	fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		if (target == null) return
		RoundedGui.frame(graphics, left, top, left + width, top + height(), RADIUS, GlassGui.canvas(), DhenPalette.BORDER)
		var row = top + PAD
		for (index in 0 until ChatMenuEntry.entries.size) {
			pillButton(
				graphics,
				font,
				memos[index],
				ChatMenuEntry.entries[index].label,
				left + PAD,
				left + width - PAD,
				row,
				ROW,
				mouseX,
				mouseY,
				PAD
			)
			row += ROW + GAP
		}
	}

	@JvmStatic
	fun closed() {
		target = null
	}

	private fun open(message: GuiMessage, x: Int, y: Int, screenWidth: Int, screenHeight: Int, font: Font) {
		target = message
		width = widest(font) + 4 * PAD
		left = x.coerceIn(0, maxOf(0, screenWidth - width))
		top = y.coerceIn(0, maxOf(0, screenHeight - height()))
	}

	private fun picked(x: Double, y: Double): Boolean {
		val message = target ?: return false
		val inside = x >= left && x < left + width && y >= top && y < top + height()
		if (inside) entryAt(y)?.let { apply(it, message) }
		closed()
		return inside
	}

	private fun entryAt(y: Double): ChatMenuEntry? {
		var row = top + PAD
		for (entry in ChatMenuEntry.entries) {
			if (y >= row && y < row + ROW) return entry
			row += ROW + GAP
		}
		return null
	}

	private fun apply(entry: ChatMenuEntry, message: GuiMessage) {
		if (entry == ChatMenuEntry.DELETE) {
			delete(message)
			return
		}
		val text = when (entry) {
			ChatMenuEntry.COPY_BODY -> body(message)
			ChatMenuEntry.COPY_CODES -> stripRepeatSuffix(legacyCodes(message.content())).replace(SECTION, AMPERSAND)
			else -> plainChatText(message.content())
		}
		Minecraft.getInstance().keyboardHandler.setClipboard(text)
		ChatTweaks.announceCopy(text)
	}

	private fun body(message: GuiMessage): String {
		val plain = plainChatText(message.content())
		val separator = plain.indexOf(SENDER_SEPARATOR)
		return if (separator < 0) plain else plain.substring(separator + SENDER_SEPARATOR.length)
	}

	private fun delete(message: GuiMessage) {
		val chat = Minecraft.getInstance().gui.hud.chat
		val access = chat as ChatComponentAccessor
		val all = access.chatAllMessages()
		val index = all.indexOfFirst { it === message }
		if (index < 0) return
		val scroll = access.chatScrollbarPos()
		all.removeAt(index)
		access.chatRefreshTrimmed()
		val deepest = maxOf(0, access.chatTrimmedMessages().size - chat.linesPerPage)
		access.chatScrollbarPos(scroll.coerceIn(0, deepest))
	}

	private fun widest(font: Font): Int {
		var widest = 0
		for (entry in ChatMenuEntry.entries) widest = maxOf(widest, memos[entry.ordinal].width(font, entry.label))
		return widest
	}

	private fun height(): Int = ChatMenuEntry.entries.size * (ROW + GAP) - GAP + 2 * PAD

	private const val ROW = 12
	private const val GAP = 2
	private const val PAD = 3
	private const val RADIUS = 3f
	private const val SENDER_SEPARATOR = ": "
	private const val SECTION = '§'
	private const val AMPERSAND = '&'
}
