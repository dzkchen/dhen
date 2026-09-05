package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.util.countdown
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.regex.Pattern

object ServerRestartTitle : Module(
	name = "Server Restart Title",
	category = Category.QOL,
	description = "Puts a counting-down warning on screen once the server announces it is closing."
) {
	private val closingLine = Pattern.compile(CLOSING_LINE).matcher("")

	internal val element = hud(ServerRestartElement())

	private var ticks = 0

	init {
		on<ClientTickEvent.End> { counted() }
	}

	override fun onDisabled() = element.clear()

	private fun counted() {
		if (!SkyBlockLocation.inSkyBlock) return element.clear()
		if (++ticks < TICKS_PER_SECOND) return
		ticks = 0
		val seconds = remainingSeconds()
		if (seconds == null) return element.clear()
		if (!element.showing && seconds > QUIET_ABOVE && seconds % ANNOUNCE_EVERY != 0L) return
		element.show(countdown(seconds * MILLIS_PER_SECOND))
	}

	private fun remainingSeconds(): Long? {
		for (line in ScoreboardState.lines) {
			if (!closingLine.reset(line).matches()) continue
			val minutes = closingLine.group("minutes").toLongOrNull() ?: return null
			val seconds = closingLine.group("seconds").toLongOrNull() ?: return null
			return minutes * SECONDS_PER_MINUTE + seconds
		}
		return null
	}

	private const val CLOSING_LINE = "§cServer closing: (?<minutes>\\d+):(?<seconds>\\d+) ?§8.*"
	private const val TICKS_PER_SECOND = 20
	private const val SECONDS_PER_MINUTE = 60L
	private const val MILLIS_PER_SECOND = 1_000L
	private const val QUIET_ABOVE = 120L
	private const val ANNOUNCE_EVERY = 30L
}

internal class ServerRestartElement : HudElement(
	name = "Server Restart",
	anchor = HudAnchor.MIDDLE_CENTER,
	offsetY = -TITLE_LIFT,
	scale = TITLE_SCALE
) {
	private val memo = DhenType.memo()

	internal var showing = false
		private set

	private var line = PREVIEW

	override val hasContent: Boolean
		get() = showing || editingHud()

	override fun width(font: Font): Int = memo.width(font, line)

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		memo.shadowed(graphics, font, line, 0, 0, DhenPalette.SLOT_RED, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	internal fun show(remaining: String) {
		showing = true
		line = LABEL + remaining
	}

	internal fun clear() {
		showing = false
		line = PREVIEW
	}
}

private const val LABEL = "Server restart in "
private const val PREVIEW = "Server restart in 1m 30s"
private const val TITLE_LIFT = 34
private const val TITLE_SCALE = 1.5f
