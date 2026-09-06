package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ContainerCharEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedQuad
import io.github.dzkchen.dhen.gui.SlotTint
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.gui.caret
import io.github.dzkchen.dhen.gui.isPrintable
import io.github.dzkchen.dhen.gui.textTop
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.grouped
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

object InventorySearch : Module(
	name = "Inventory Search",
	category = Category.INVENTORY,
	description = "Ctrl+F opens a search box over any container and highlights the slots that match."
) {
	internal val ignoreCapsSetting = BooleanSetting(
		"Ignore Caps",
		default = true,
		description = "Matches regardless of upper or lower case."
	)

	internal val searchLoreSetting = BooleanSetting(
		"Search Lore",
		default = true,
		description = "Also searches the description under each item's name."
	)

	internal val highlightSetting = ColorSetting(
		"Highlight Color",
		Color.rgba(255, 0, 0),
		allowAlpha = true,
		description = "The colour painted behind a matching item."
	)

	private val expression = Expression()
	private val queryMemo = TextMemo()
	private val caretMemo = TextMemo()
	private val resultMemo = TextMemo()
	private val matchedStacks = arrayOfNulls<ItemStack>(CACHED_SLOTS)
	private val matchedHits = BooleanArray(CACHED_SLOTS)
	private var tokens = emptyArray<String>()
	private var caretPrefix = ""
	private var cachedCaps = ignoreCapsSetting.default
	private var cachedLore = searchLoreSetting.default

	internal var query = ""
		private set
	internal var focused = false
		private set
	internal var caretAt = 0
		private set
	internal var result: String? = null
		private set

	internal val searching: Boolean
		get() = enabled && tokens.isNotEmpty()

	init {
		for (setting in listOf(ignoreCapsSetting, searchLoreSetting, highlightSetting)) registerSetting(setting)

		on<ContainerKeyEvent> { event ->
			if (pressed(event.input.input(), event.input.hasControlDownWithQuirk())) event.cancelled = true
		}
		on<ContainerCharEvent> { event ->
			if (!focused || !isPrintable(event.codepoint)) return@on
			insert(event.codepoint.toChar())
			event.cancelled = true
		}
		on<SlotRenderEvent.Pre> { event ->
			if (!matches(event.slot.index, event.slot.item)) return@on
			SlotTint.claim(highlightSetting.value.argb, TINT_PRIORITY)
		}
		on<ScreenRenderEvent.Post> { event ->
			if (event.screen !is AbstractContainerScreen<*> && event.screen !is StorageOverlayScreen) return@on
			draw(event)
		}
		on<GuiCloseEvent> { focused = false }
	}

	internal fun typeQuery(text: String) {
		query = text
		caretAt = text.length
		refresh()
	}

	override fun onDisabled() {
		typeQuery("")
		focused = false
	}

	internal fun matches(slot: Int, stack: ItemStack): Boolean {
		if (tokens.isEmpty() || stack.isEmpty) return false
		if (ignoreCapsSetting.on != cachedCaps || searchLoreSetting.on != cachedLore) {
			cachedCaps = ignoreCapsSetting.on
			cachedLore = searchLoreSetting.on
			matchedStacks.fill(null)
		}
		if (slot < 0 || slot >= CACHED_SLOTS) return test(stack)
		if (matchedStacks[slot] === stack) return matchedHits[slot]
		val hit = test(stack)
		matchedStacks[slot] = stack
		matchedHits[slot] = hit
		return hit
	}

	internal fun pressed(key: Int, control: Boolean): Boolean {
		if (control && key == GLFW.GLFW_KEY_F) {
			focused = !focused
			return true
		}
		if (!focused) return false
		when (key) {
			GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> focused = false
			GLFW.GLFW_KEY_BACKSPACE -> if (caretAt > 0) {
				query = query.removeRange(caretAt - 1, caretAt)
				caretAt--
				refresh()
			}

			GLFW.GLFW_KEY_DELETE -> if (caretAt < query.length) {
				query = query.removeRange(caretAt, caretAt + 1)
				refresh()
			}

			GLFW.GLFW_KEY_LEFT -> if (caretAt > 0) moveCaret(caretAt - 1)
			GLFW.GLFW_KEY_RIGHT -> if (caretAt < query.length) moveCaret(caretAt + 1)
			GLFW.GLFW_KEY_HOME -> moveCaret(0)
			GLFW.GLFW_KEY_END -> moveCaret(query.length)
		}
		return true
	}

	private fun moveCaret(to: Int) {
		caretAt = to
		caretPrefix = query.substring(0, to)
	}

	internal fun insert(character: Char) {
		query = query.substring(0, caretAt) + character + query.substring(caretAt)
		caretAt++
		refresh()
	}

	private fun refresh() {
		tokens = if (query.isBlank()) emptyArray() else query.trim().split(' ').filter(String::isNotEmpty).toTypedArray()
		result = expression.evaluate(query)?.let { RESULT_PREFIX + formatted(it) }
		moveCaret(caretAt.coerceIn(0, query.length))
		matchedStacks.fill(null)
	}

	private fun test(stack: ItemStack): Boolean {
		val name = withoutCodes(stack.hoverName.string)
		for (token in tokens) {
			if (!name.contains(token, cachedCaps) && !inLore(stack, token)) return false
		}
		return true
	}

	private fun inLore(stack: ItemStack, token: String): Boolean {
		if (!cachedLore) return false
		val lore = SkyBlockItems.lore(stack)
		for (index in lore.indices) {
			if (withoutCodes(lore[index].string).contains(token, cachedCaps)) return true
		}
		return false
	}

	private fun draw(event: ScreenRenderEvent.Post) {
		val font = Minecraft.getInstance().font
		val left = (event.screen.width - FIELD_WIDTH) / 2
		val top = event.screen.height - BOTTOM_GAP - FIELD_HEIGHT
		val graphics = event.graphics
		GlassGui.roundedFrame(
			graphics,
			left,
			top,
			left + FIELD_WIDTH,
			top + FIELD_HEIGHT,
			RoundedQuad.FULL,
			GlassGui.surface(),
			if (focused) DhenPalette.accent else DhenPalette.BORDER
		)
		val baseline = textTop(font, top, FIELD_HEIGHT)
		val textLeft = left + TEXT_INSET
		val room = maxOf(FIELD_WIDTH - 2 * TEXT_INSET - CARET_ROOM, 0)
		if (query.isEmpty() && !focused) {
			queryMemo.text(graphics, font, PLACEHOLDER, textLeft, baseline, DhenPalette.TEXT_DISABLED)
			return
		}
		val shown = queryMemo.fit(font, query, room, fromEnd = true)
		queryMemo.text(graphics, font, shown, textLeft, baseline, DhenPalette.TEXT_PRIMARY)
		var right = textLeft + queryMemo.width(font, shown)
		if (focused) {
			caret(graphics, font, minOf(textLeft + caretMemo.width(font, caretPrefix), right), baseline)
			right += CARET_ROOM
		}
		val computed = result ?: return
		val fitted = resultMemo.fit(font, computed, maxOf(left + FIELD_WIDTH - TEXT_INSET - right, 0))
		resultMemo.text(graphics, font, fitted, right, baseline, DhenPalette.accent)
	}

	private fun formatted(value: Double): String = when {
		value != floor(value) -> String.format(Locale.US, "%,.2f", value)
		abs(value) < LONG_LIMIT -> grouped(value.toLong())
		else -> String.format(Locale.US, "%,.0f", value)
	}

	private const val PLACEHOLDER = "Search..."
	private const val RESULT_PREFIX = " = "
	private const val FIELD_WIDTH = 200
	private const val FIELD_HEIGHT = 22
	private const val BOTTOM_GAP = 30
	private const val TEXT_INSET = 8
	private const val CARET_ROOM = 2
	private const val TINT_PRIORITY = 30
	private const val CACHED_SLOTS = 128
	private const val LONG_LIMIT = 9.0e18
}

internal class Expression {
	private var text = ""
	private var at = 0

	fun evaluate(source: String): Double? {
		if (source.isBlank() || source.none(Char::isDigit)) return null
		text = source
		at = 0
		val value = sum() ?: return null
		skipSpace()
		if (at != text.length || !value.isFinite()) return null
		return value
	}

	private fun sum(): Double? {
		var value = product() ?: return null
		while (true) {
			skipSpace()
			val operator = peek()
			if (operator != '+' && operator != '-') return value
			at++
			val right = product() ?: return null
			value = if (operator == '+') value + right else value - right
		}
	}

	private fun product(): Double? {
		var value = unary() ?: return null
		while (true) {
			skipSpace()
			val operator = peek()
			if (operator != '*' && operator != 'x' && operator != '/') return value
			at++
			val right = unary() ?: return null
			if (operator == '/' && right == 0.0) return null
			value = if (operator == '/') value / right else value * right
		}
	}

	private fun unary(): Double? {
		skipSpace()
		return when (peek()) {
			'-' -> {
				at++
				unary()?.let { -it }
			}

			'+' -> {
				at++
				unary()
			}

			'(' -> {
				at++
				val inner = sum() ?: return null
				skipSpace()
				if (peek() != ')') return null
				at++
				inner
			}

			else -> number()
		}
	}

	private fun number(): Double? {
		val start = at
		while (at < text.length && (text[at].isDigit() || text[at] == '.')) at++
		if (at == start) return null
		val digits = text.substring(start, at).toDoubleOrNull() ?: return null
		val scale = SUFFIXES.indexOf(peek().lowercaseChar())
		if (scale < 0) return digits
		at++
		var scaled = digits
		repeat(scale + 1) { scaled *= THOUSAND }
		return scaled
	}

	private fun peek(): Char = if (at < text.length) text[at] else END

	private fun skipSpace() {
		while (at < text.length && text[at].isWhitespace()) at++
	}

	private companion object {
		const val SUFFIXES = "kmbt"
		const val THOUSAND = 1000.0
		const val END = '\u0000'
	}
}
