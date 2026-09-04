package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.mixin.ChatComponentAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.util.FormattedCharSequence
import kotlin.math.floor

private const val LEFT_INSET = 4.0
private const val BOTTOM_MARGIN = 40.0
private const val MESSAGE_HEIGHT = 9.0

internal fun chatLineHeight(lineSpacing: Double): Int = (MESSAGE_HEIGHT * (lineSpacing + 1.0)).toInt()

internal fun hoveredChatLine(
	mouseX: Double,
	mouseY: Double,
	screenHeight: Int,
	scale: Double,
	lineHeight: Int,
	chatWidth: Int,
	linesPerPage: Int,
	scrollbarPos: Int,
	lineCount: Int
): Int {
	if (scale <= 0.0 || lineHeight <= 0) return -1
	val chatX = mouseX / scale - LEFT_INSET
	if (chatX < -LEFT_INSET || chatX > floor(chatWidth / scale)) return -1
	val chatY = (screenHeight - mouseY - BOTTOM_MARGIN) / (scale * lineHeight)
	if (chatY < 0.0 || chatY >= minOf(linesPerPage, lineCount)) return -1
	val index = floor(chatY + scrollbarPos).toInt()
	return if (index in 0 until lineCount) index else -1
}

internal inline fun chatEntrySpan(hovered: Int, lineCount: Int, endOfEntry: (Int) -> Boolean): IntRange {
	var oldest = hovered
	while (oldest + 1 < lineCount && !endOfEntry(oldest + 1)) oldest++
	var newest = hovered
	while (newest > 0 && !endOfEntry(newest)) newest--
	return newest..oldest
}

internal fun hoveredChatIndex(client: Minecraft, mouseX: Double, mouseY: Double): Int {
	val chat = client.gui.hud.chat
	if (!chat.isChatFocused) return -1
	val lines = (chat as ChatComponentAccessor).chatTrimmedMessages()
	val options = client.options
	return hoveredChatLine(
		mouseX,
		mouseY,
		client.window.guiScaledHeight,
		options.chatScale().get(),
		chatLineHeight(options.chatLineSpacing().get()),
		ChatComponent.getWidth(options.chatWidth().get()),
		chat.linesPerPage,
		chat.chatScrollbarPos(),
		lines.size
	)
}

internal fun hoveredChatMessage(client: Minecraft, mouseX: Double, mouseY: Double): GuiMessage? {
	val index = hoveredChatIndex(client, mouseX, mouseY)
	if (index < 0) return null
	return (client.gui.hud.chat as ChatComponentAccessor).chatTrimmedMessages()[index].parent()
}

internal fun hoveredChatText(client: Minecraft, wholeEntry: Boolean): String {
	val window = client.window
	val hovered = hoveredChatIndex(
		client,
		client.mouseHandler.getScaledXPos(window),
		client.mouseHandler.getScaledYPos(window)
	)
	if (hovered < 0) return ""
	val lines = (client.gui.hud.chat as ChatComponentAccessor).chatTrimmedMessages()
	val span = if (wholeEntry) chatEntrySpan(hovered, lines.size) { lines[it].endOfEntry() } else hovered..hovered
	val composed = StringBuilder()
	for (index in span.last downTo span.first) appendCodePoints(lines[index].content(), composed)
	return composed.toString()
}

private fun appendCodePoints(line: FormattedCharSequence, into: StringBuilder) {
	line.accept { _, _, codePoint ->
		into.appendCodePoint(codePoint)
		true
	}
}
