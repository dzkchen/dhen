package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import net.minecraft.world.entity.EquipmentSlot

object ChickenHeadTimer : Module(
	name = "Chicken Head Timer",
	category = Category.MISC,
	description = "Counts the five seconds until the Chicken Head can lay another egg."
) {
	private var hideChat by BooleanSetting(
		"Hide Chat",
		default = true,
		description = "Swallows the 'You laid an egg!' line."
	)

	internal val element = hud(ChickenHeadElement())

	private var ticks = 0

	init {
		on<ClientTickEvent.End> { ticked() }
		on<ChatReceiveEvent> { laid(it) }
		on<WorldChangeEvent> { element.reset(Util.getMillis()) }
	}

	override fun onDisabled() {
		element.worn = false
		ticks = 0
	}

	internal fun laid(event: ChatReceiveEvent) {
		if (!SkyBlockLocation.inSkyBlock || !element.worn || event.stripped != EGG_LINE) return
		element.reset(Util.getMillis())
		if (hideChat) event.cancelled = true
	}

	private fun ticked() {
		element.refresh(Util.getMillis())
		if (++ticks < POLL_TICKS) return
		ticks = 0
		val player = Minecraft.getInstance()?.player
		element.worn = SkyBlockLocation.inSkyBlock && player != null &&
			SkyBlockItems.of(player.getItemBySlot(EquipmentSlot.HEAD)).id == CHICKEN_HEAD
	}

	private const val POLL_TICKS = 5
	private const val CHICKEN_HEAD = "CHICKEN_HEAD"
	private const val EGG_LINE = "You laid an egg!"
}

internal class ChickenHeadElement : HudElement(name = "Chicken Head Timer", offsetX = MARGIN, offsetY = MARGIN) {
	private val memo = DhenType.memo()
	private val composer = StringBuilder(LINE_CAPACITY)
	private var laidAt = 0L

	internal var worn = false
	internal var line = READY_LINE
		private set

	override val hasContent: Boolean
		get() = editingHud() || worn

	override fun width(font: Font): Int = memo.width(font, line)

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		memo.shadowed(graphics, font, line, 0, 0, textInk, scale)
	}

	override fun invalidateMeasurement() = memo.invalidate()

	internal fun reset(now: Long) {
		laidAt = now
		refresh(now)
	}

	internal fun refresh(now: Long) {
		val left = COOLDOWN_MILLIS - (now - laidAt)
		if (left <= 0L) {
			line = READY_LINE
			return
		}
		composer.setLength(0)
		composer.append(LABEL)
		composer.append(COUNTDOWN_COLOR)
		if (left >= SECOND) {
			composer.append(left / SECOND)
		} else {
			composer.append(ZERO_POINT)
			composer.append(left / TENTH)
		}
		composer.append(SECONDS_SUFFIX)
		line = composer.toString()
	}
}

private const val MARGIN = 5
private const val LINE_CAPACITY = 32
private const val COOLDOWN_MILLIS = 5_000L
private const val SECOND = 1_000L
private const val TENTH = 100L
private const val LABEL = "Chicken Head Timer: "
private const val COUNTDOWN_COLOR = "§b"
private const val ZERO_POINT = "0."
private const val SECONDS_SUFFIX = "s"
private const val READY_LINE = LABEL + "§aNow"
