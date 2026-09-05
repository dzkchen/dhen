package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.centeredText
import io.github.dzkchen.dhen.gui.isPrintable
import io.github.dzkchen.dhen.gui.pillButton
import io.github.dzkchen.dhen.gui.textTop
import io.github.dzkchen.dhen.input.TextInputTarget
import io.github.dzkchen.dhen.util.compactNumber
import io.github.dzkchen.dhen.util.formatted
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

internal class AuctionInputScreen(
	private val pos: BlockPos,
	private val frontText: Boolean,
	private val lines: Array<String>,
	private val stack: ItemStack
) : LiveWorldScreen(DhenType.component(TITLE)), TextInputTarget {
	private val headerMemo = DhenType.memo()
	private val marketMemo = DhenType.memo()
	private val statusMemo = DhenType.memo()
	private val inputMemo = DhenType.memo()
	private val doneMemo = DhenType.memo()
	private val modeMemo = DhenType.memo()

	private var market = AuctionPriceInput.lowestBin(stack)
	private var marketText = marketLine(market)

	private var input = AuctionPriceInput.rememberedText()
	private var undercut = AuctionPriceInput.rememberedUndercut()
	private var replacing = input.isNotEmpty()
	private var parsed: Long? = null
	private var statusText = HINT

	override val textInputFocused: Boolean
		get() = true

	init {
		recalculate()
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val centerX = width / 2
		val centerY = height / 2
		GlassGui.roundedFrame(
			graphics,
			centerX - PANEL_HALF_WIDTH,
			centerY + PANEL_TOP,
			centerX + PANEL_HALF_WIDTH,
			centerY + PANEL_BOTTOM,
			PANEL_RADIUS,
			GlassGui.canvas(),
			DhenPalette.BORDER
		)
		ItemGui.stack(graphics, stack, centerX - ITEM_HALF, centerY + ITEM_TOP)
		centeredText(graphics, font, headerMemo, if (undercut) UNDERCUT_HEADER else NORMAL_HEADER, centerX - PANEL_HALF_WIDTH, centerX + PANEL_HALF_WIDTH, centerY + HEADER_TOP, DhenPalette.accent, TEXT_PAD)
		centeredText(graphics, font, marketMemo, marketText, centerX - PANEL_HALF_WIDTH, centerX + PANEL_HALF_WIDTH, centerY + MARKET_TOP, DhenPalette.TEXT_SECONDARY, TEXT_PAD)
		centeredText(graphics, font, statusMemo, statusText, centerX - PANEL_HALF_WIDTH, centerX + PANEL_HALF_WIDTH, centerY + STATUS_TOP, statusColor(), TEXT_PAD)
		drawInput(graphics, centerX, centerY)
		pillButton(graphics, font, doneMemo, DONE_LABEL, centerX - BOX_HALF_WIDTH, centerX + BOX_HALF_WIDTH, centerY + DONE_TOP, BUTTON_HEIGHT, mouseX, mouseY, TEXT_PAD)
		pillButton(graphics, font, modeMemo, modeLabel(), centerX - BOX_HALF_WIDTH, centerX + BOX_HALF_WIDTH, centerY + MODE_TOP, BUTTON_HEIGHT, mouseX, mouseY, TEXT_PAD)
		if (overItem(mouseX, mouseY, centerX, centerY)) {
			graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
		val centerX = width / 2
		val centerY = height / 2
		val x = event.x().toInt()
		val y = event.y().toInt()
		if (x !in centerX - BOX_HALF_WIDTH until centerX + BOX_HALF_WIDTH) {
			return super.mouseClicked(event, doubleClick)
		}
		when (y) {
			in centerY + DONE_TOP until centerY + DONE_TOP + BUTTON_HEIGHT -> {
				finish()
				return true
			}

			in centerY + MODE_TOP until centerY + MODE_TOP + BUTTON_HEIGHT -> {
				undercut = !undercut
				recalculate()
				return true
			}
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		when (event.key()) {
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				finish()
				return true
			}

			GLFW.GLFW_KEY_BACKSPACE -> {
				if (input.isEmpty()) return true
				replacing = false
				input = input.substring(0, input.offsetByCodePoints(input.length, -1))
				recalculate()
				return true
			}
		}
		return super.keyPressed(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		if (!isPrintable(codepoint)) return super.charTyped(event)
		if (replacing) {
			replacing = false
			input = ""
		}
		if (input.length >= MAX_LENGTH) return true
		input += String(Character.toChars(codepoint))
		recalculate()
		return true
	}

	override fun tick() {
		val fresh = AuctionPriceInput.lowestBin(stack)
		if (fresh == market) return
		market = fresh
		marketText = marketLine(fresh)
		recalculate()
	}

	override fun onClose() {
		AuctionPriceInput.remember(input, undercut)
		super.onClose()
	}

	private fun finish() {
		val price = parsed ?: return
		val line = price.toString()
		minecraft.connection?.send(ServerboundSignUpdatePacket(pos, frontText, line, lines[1], lines[2], lines[3]))
		onClose()
	}

	private fun recalculate() {
		val typed = compactNumber(input.replace(',', '.'))
		val floor = market
		parsed = when {
			typed == null -> null
			!undercut -> typed
			floor == null -> null
			else -> (floor - typed).coerceAtLeast(0L)
		}
		val price = parsed
		statusText = when {
			price != null -> "$VALUE_LABEL ${formatted(price)}"
			input.isEmpty() -> HINT
			else -> INVALID
		}
	}

	private fun marketLine(price: Long?): String =
		if (price == null) "$MARKET_LABEL $UNKNOWN_PRICE" else "$MARKET_LABEL ${formatted(price)}"

	private fun drawInput(graphics: GuiGraphicsExtractor, centerX: Int, centerY: Int) {
		val left = centerX - BOX_HALF_WIDTH
		val right = centerX + BOX_HALF_WIDTH
		val top = centerY + BOX_TOP
		RoundedGui.pillFrame(graphics, left, top, right, top + BOX_HEIGHT, GlassGui.interactive(), DhenPalette.accent)
		val shown = inputMemo.fit(font, if (input.isEmpty()) PLACEHOLDER else input, BOX_HALF_WIDTH * 2 - 2 * TEXT_PAD, fromEnd = true)
		val textLeft = left + TEXT_PAD
		val textTop = textTop(font, top, BOX_HEIGHT)
		val color = if (input.isEmpty()) DhenPalette.TEXT_DISABLED else DhenPalette.TEXT_PRIMARY
		inputMemo.text(graphics, font, shown, textLeft, textTop, color)
		if (input.isEmpty()) return
		val caret = textLeft + inputMemo.width(font, shown)
		SharpGui.fill(graphics, caret, top + CARET_INSET, caret + 1, top + BOX_HEIGHT - CARET_INSET, DhenPalette.accent)
	}

	private fun statusColor(): Int = when {
		parsed != null -> DhenPalette.accent
		input.isEmpty() -> DhenPalette.TEXT_DISABLED
		else -> DhenPalette.TEXT_PRIMARY
	}

	private fun modeLabel(): String = if (undercut) UNDERCUT_MODE else NORMAL_MODE

	private fun overItem(mouseX: Int, mouseY: Int, centerX: Int, centerY: Int): Boolean =
		mouseX in centerX - ITEM_HALF until centerX + ITEM_HALF &&
			mouseY in centerY + ITEM_TOP until centerY + ITEM_TOP + ITEM_HALF * 2

	private companion object {
		private const val TITLE = "Auction Price Input"
		private const val NORMAL_HEADER = "Set Auction Price"
		private const val UNDERCUT_HEADER = "Undercut Mode"
		private const val NORMAL_MODE = "Mode: Normal"
		private const val UNDERCUT_MODE = "Mode: Undercut"
		private const val DONE_LABEL = "Done"
		private const val MARKET_LABEL = "Lowest BIN:"
		private const val VALUE_LABEL = "Value:"
		private const val UNKNOWN_PRICE = "not loaded yet"
		private const val HINT = "Type a price, for example 10m or 5k"
		private const val INVALID = "That is not a price Dhen can read"
		private const val PLACEHOLDER = "Price"
		private const val MAX_LENGTH = 32
		private const val PANEL_HALF_WIDTH = 118
		private const val PANEL_TOP = -88
		private const val PANEL_BOTTOM = 64
		private const val PANEL_RADIUS = 6f
		private const val BOX_HALF_WIDTH = 100
		private const val BOX_TOP = -20
		private const val BOX_HEIGHT = 22
		private const val BUTTON_HEIGHT = 20
		private const val DONE_TOP = 10
		private const val MODE_TOP = 35
		private const val ITEM_HALF = 8
		private const val ITEM_TOP = -75
		private const val HEADER_TOP = -55
		private const val MARKET_TOP = -45
		private const val STATUS_TOP = -35
		private const val TEXT_PAD = 6
		private const val CARET_INSET = 5
	}
}
