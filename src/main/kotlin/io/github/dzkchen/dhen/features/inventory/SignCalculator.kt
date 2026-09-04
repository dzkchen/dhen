package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.centeredText
import io.github.dzkchen.dhen.mixin.AbstractSignEditScreenAccessor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Calculated
import io.github.dzkchen.dhen.util.Calculator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern

object SignCalculator : Module(
	name = "Sign Calculator",
	category = Category.QOL,
	description = "Works out sums typed into a Hypixel input sign and lets Enter confirm one."
) {
	private val requireEqualsSetting = BooleanSetting(
		"Needs An Equals Sign",
		description = "Only treats what you type as a sum when it starts with =, so plain numbers stay untouched."
	)

	private val decimalsSetting = NumberSetting(
		"Shown Decimals",
		DEFAULT_DECIMALS,
		0.0,
		MAX_DECIMALS,
		description = "How many decimals the preview above the sign shows. It never changes the number sent."
	)

	private val enterConfirmsSetting = BooleanSetting(
		"Enter Confirms",
		default = true,
		description = "Presses the sign's Done button for you when you press Enter."
	)

	private val everySignSetting = BooleanSetting(
		"On Every Sign",
		description = "Lets Enter confirm any sign, not only the Hypixel ones with the row of carets."
	).withDependency { enterConfirmsSetting.on }

	private val previewMemo = DhenType.memo()
	private val carets = Pattern.compile("(?:\\^\\s*){8,}").matcher("")

	private var typed = ""
	private var outcome: Calculated = Calculated.Incomplete("")
	private var previewText = ""
	private var previewSolved = false

	init {
		for (setting in listOf(requireEqualsSetting, decimalsSetting, enterConfirmsSetting, everySignSetting)) {
			registerSetting(setting)
		}

		on<ScreenRenderEvent.Post> { preview(it) }
		on<GuiCloseEvent> { if (it.screen is AbstractSignEditScreen) forget() }
	}

	override fun onDisabled() = forget()

	override fun onReset() = forget()

	internal fun forget() {
		typed = ""
		outcome = Calculated.Incomplete("")
		previewText = ""
		previewSolved = false
	}

	@JvmStatic
	fun confirmsOnEnter(messages: Array<String>): Boolean {
		if (!enabled || !enterConfirmsSetting.on) return false
		if (everySignSetting.on) return true
		return messages.any { carets.reset(withoutCodes(it)).find() }
	}

	@JvmStatic
	fun applyTo(messages: Array<String>) {
		if (!enabled || !SkyBlockLocation.inSkyBlock || !isInputSign(messages)) return
		val source = withoutCodes(messages[0]).trim()
		val equalled = source.startsWith(EQUALS)
		if (requireEqualsSetting.on && !equalled) return
		val amount = evaluate(messages[0])
		val written = when {
			amount == null -> if (equalled) source.substring(1) else return
			withoutCodes(messages[2]).contains(PRICE, ignoreCase = true) ->
				amount.setScale(PRICE_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

			else -> amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
		}
		if (amount != null) Calculator.remember(amount)
		messages[0] = if (written.length > SIGN_LINE_LIMIT) written.substring(0, SIGN_LINE_LIMIT) else written
	}

	internal fun isInputSearchSign(messages: Array<String>): Boolean {
		val hint = withoutCodes(messages[HINT_LINE])
		return hint.endsWith(SEARCH_HINT) || hint.endsWith(QUERY_HINT) || SearchOverlay.isSearchSign(messages)
	}

	internal fun isInputSign(messages: Array<String>): Boolean {
		val marker = withoutCodes(messages[MARKER_LINE])
		val hint = withoutCodes(messages[HINT_LINE])
		return marker == CARETS && !isInputSearchSign(messages) ||
			marker == SHORT_CARETS && !hint.endsWith(SEARCH_HINT) && hint != NAME_HINT ||
			marker == FLIP_CARETS
	}

	internal fun evaluate(line: String): BigDecimal? {
		val source = withoutCodes(line).trim()
		val equalled = source.startsWith(EQUALS)
		if (requireEqualsSetting.on && !equalled) return null
		val expression = if (equalled) source.substring(1) else source
		if (expression != typed) {
			typed = expression
			outcome = Calculator.evaluate(expression)
			describe()
		}
		return (outcome as? Calculated.Value)?.amount
	}

	private fun describe() {
		val held = outcome
		previewSolved = held is Calculated.Value
		previewText = when (held) {
			is Calculated.Value -> "$typed = ${Calculator.display(held.amount, decimalsSetting.amount.toInt())}"
			is Calculated.Incomplete -> held.message
			is Calculated.Invalid -> held.message
		}
	}

	private fun preview(event: ScreenRenderEvent.Post) {
		if (!SkyBlockLocation.inSkyBlock) return
		val screen = event.screen as? AbstractSignEditScreen ?: return
		val messages = (screen as AbstractSignEditScreenAccessor).dhenMessages()
		if (!isInputSign(messages)) return
		val source = withoutCodes(messages[0]).trim()
		if (requireEqualsSetting.on && !source.startsWith(EQUALS)) return
		if (source.isEmpty() || source == EQUALS) return
		evaluate(messages[0])
		if (previewText.isEmpty()) return
		val ink = when {
			previewSolved -> DhenPalette.accent
			outcome is Calculated.Incomplete -> DhenPalette.TEXT_DISABLED
			else -> DhenPalette.TEXT_SECONDARY
		}
		val width = event.graphics.guiWidth()
		centeredText(
			event.graphics,
			Minecraft.getInstance().font,
			previewMemo,
			previewText,
			0,
			width,
			PREVIEW_TOP,
			ink,
			TEXT_PAD
		)
	}

	private const val MARKER_LINE = 1
	private const val HINT_LINE = 2
	private const val PREVIEW_TOP = 55
	private const val TEXT_PAD = 6
	private const val SIGN_LINE_LIMIT = 15
	private const val PRICE_DECIMALS = 2
	private const val DEFAULT_DECIMALS = 2.0
	private const val MAX_DECIMALS = 10.0
	private const val EQUALS = "="
	private const val PRICE = "price"
	private const val CARETS = "^^^^^^^^^^^^^^^"
	private const val SHORT_CARETS = "^^^^^^"
	private const val FLIP_CARETS = "^^Flipping^^"
	private const val SEARCH_HINT = "your"
	private const val QUERY_HINT = "query"
	private const val NAME_HINT = "Enter name"
}
