package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.npc.NpcSales
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.util.digits
import io.github.dzkchen.dhen.util.grouped
import io.github.dzkchen.dhen.util.shortNumber
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.regex.Pattern

object NpcDayLimit : Module(
	name = "NPC Day Limit",
	category = Category.QOL,
	description = "Counts the coins you have sold to NPCs today against Hypixel's daily cap."
) {
	private val numberFormatSetting = SelectorSetting(
		"Number Format",
		SHORT,
		listOf(SHORT, LONG),
		description = "Whether the readout writes 400k/500M or 400,000/500,000,000."
	)

	private val soldLine = Pattern.compile(SOLD_LINE).matcher("")

	private var readout = ""
	private var readoutKey = -1L

	internal val element = hud(NpcDayLimitElement())

	init {
		registerSetting(numberFormatSetting)

		on<ChatReceiveEvent> { sold(it.stripped) }
		on<ClientTickEvent.End> { compose() }
	}

	private fun sold(message: String) {
		if (!SkyBlockLocation.inSkyBlock || !soldLine.reset(message).matches()) return
		NpcSales.record(digits(soldLine.group("amount")).coerceAtLeast(0L))
	}

	internal fun readout(): String = readout.ifEmpty { PREVIEW }

	private fun compose() {
		val sold = NpcSales.soldToday
		val key = sold * OPTIONS + numberFormatSetting.index
		if (key == readoutKey) return
		readoutKey = key
		readout = "${formatted(sold)}/${formatted(NpcSales.DAILY_LIMIT)}"
	}

	private fun formatted(coins: Long): String =
		if (numberFormatSetting.value == LONG) grouped(coins) else shortNumber(coins)

	private const val OPTIONS = 2L
	private const val PREVIEW = "0/500M"
	private const val SHORT = "Short"
	private const val LONG = "Long"
	private const val SOLD_LINE = "You sold .+ for (?<amount>[\\d,]+) Coins!"
}

internal class NpcDayLimitElement : HudElement("NPC Day Limit", HudAnchor.BOTTOM_LEFT, MARGIN, -MARGIN) {
	private val memo = DhenType.memo()

	override val hasContent: Boolean
		get() = SkyBlockLocation.inSkyBlock || editingHud()

	override fun width(font: Font): Int = memo.width(font, NpcDayLimit.readout())

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		memo.shadowed(graphics, font, NpcDayLimit.readout(), 0, 0, DhenPalette.SLOT_GOLD, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()
}

private const val MARGIN = 8
