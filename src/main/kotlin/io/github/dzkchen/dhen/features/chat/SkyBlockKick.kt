package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util

object SkyBlockKick : Module(
	name = "SB Kick",
	category = Category.MISC,
	description = "Times how long ago SkyBlock kicked you out."
) {
	private var sendPartyMessage by BooleanSetting(
		"Send Party Message",
		description = "Tells the party you were kicked while joining."
	)

	private val kickLines = listOf(
		"There was a problem joining SkyBlock, try again in a moment!",
		"You were kicked while joining that server!",
		"A kick occurred in your connection, so you were put in the SkyBlock lobby!"
	)

	internal val element = hud(KickTimerElement())

	init {
		on<ChatReceiveEvent> { kicked(it.stripped, Util.getMillis()) }
		on<ClientTickEvent.End> { element.refresh(Util.getMillis(), SkyBlockLocation.inSkyBlock) }
	}

	override fun onDisabled() {
		element.clear()
	}

	internal fun kicked(line: String, now: Long) {
		val kick = kickLines.indexOf(line)
		if (kick < 0 || element.showing) return
		element.start(now)
		if (kick == 0 || !sendPartyMessage || !PartyState.inParty) return
		Minecraft.getInstance().connection?.sendCommand(PARTY_COMMAND)
	}

	private const val PARTY_COMMAND = "pc You were kicked while joining that server!"
}

internal class KickTimerElement : HudElement(
	name = "SB Kick Timer",
	anchor = HudAnchor.MIDDLE_CENTER,
	offsetY = -OVERLAY_LIFT,
	scale = OVERLAY_SCALE
) {
	private val memo = DhenType.memo()
	private val composer = StringBuilder(LINE_CAPACITY)
	private var kickedAt = 0L

	internal var showing = false
		private set
	internal var line = compose(0L)
		private set

	override val hasContent: Boolean
		get() = editingHud() || showing

	override fun width(font: Font): Int = memo.width(font, line)

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		memo.shadowed(graphics, font, line, 0, 0, textInk, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	internal fun start(now: Long) {
		kickedAt = now
		showing = true
		line = compose(0L)
	}

	internal fun clear() {
		showing = false
		line = compose(0L)
	}

	internal fun refresh(now: Long, inSkyBlock: Boolean) {
		if (!showing) return
		val elapsed = now - kickedAt
		if (elapsed >= SHOWN_MILLIS || (inSkyBlock && elapsed > SETTLED_MILLIS)) {
			clear()
			return
		}
		line = compose(elapsed)
	}

	private fun compose(elapsed: Long): String {
		composer.setLength(0)
		composer.append(LABEL)
		composer.append(elapsed / SECOND)
		composer.append('.')
		val hundredths = (elapsed % SECOND) / TENTH
		if (hundredths < 10L) composer.append('0')
		composer.append(hundredths)
		composer.append(SECONDS_SUFFIX)
		return composer.toString()
	}
}

private const val OVERLAY_LIFT = 20
private const val OVERLAY_SCALE = 1.5f
private const val LINE_CAPACITY = 48
private const val SHOWN_MILLIS = 60_000L
private const val SETTLED_MILLIS = 10_000L
private const val SECOND = 1_000L
private const val TENTH = 10L
private const val LABEL = "§cLast kicked from SkyBlock §b"
private const val SECONDS_SUFFIX = "s ago"
