package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.OrderedSelectionSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.mayor.MayorService
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.HudLayout
import io.github.dzkchen.dhen.ui.hud.drawHudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.util.countdown
import io.github.dzkchen.dhen.util.formatted
import io.github.dzkchen.dhen.util.shortNumber
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.util.Util
import net.minecraft.world.entity.EquipmentSlot

object CrownOfAvarice : Module(
	name = "Crown of Avarice",
	category = Category.ECONOMY,
	description = "Tracks the coins your Crown of Avarice has swallowed, and how fast they are going in."
) {
	private var shortTotal by BooleanSetting(
		"Counter Format",
		default = true,
		description = "Shows the running total as 1.2M rather than every digit."
	)

	private var shortRate by BooleanSetting(
		"Coins Per Hour Format",
		default = true,
		description = "Shows the coins-per-hour line the same short way."
	)

	private var afkSeconds by NumberSetting(
		"AFK Pause Time",
		DEFAULT_AFK_SECONDS,
		MIN_AFK_SECONDS,
		MAX_AFK_SECONDS,
		AFK_STEP,
		description = "Pauses the session when no coins arrive for this many seconds."
	)

	private var activeSeconds by NumberSetting(
		"Session Active Timer",
		DEFAULT_ACTIVE_SECONDS,
		MIN_ACTIVE_SECONDS,
		MAX_ACTIVE_SECONDS,
		description = "Waits this many seconds before the rate lines say anything but Calculating."
	)

	private var resetOnWorldChange by BooleanSetting(
		"Reset on World Change",
		description = "Starts a fresh session every time you change island."
	)

	internal val trackerSetting = OrderedSelectionSetting(
		"Tracker Text",
		listOf(COINS_PER_HOUR, TIME_UNTIL_MAX, LAST_GAIN, SESSION_COINS, SESSION_TIME),
		listOf(COINS_PER_HOUR, TIME_UNTIL_MAX, LAST_GAIN, SESSION_COINS, SESSION_TIME),
		description = "Which lines the tracker shows, in the order you drag them."
	)

	private var tracker by trackerSetting

	internal val element = hud(CrownElement())

	internal val session = CoinSession()
	private val composer = StringBuilder(LINE_CAPACITY)
	private val mayorHold = RequirementHold(MayorService::active, MayorService::require)
	private var currentUuid = ""
	private var totalCoins = 0L
	private var coinsEarned = 0L
	private var coinsDifference = 0L
	private var hasDifference = false
	private var hovered = NO_BUTTON
	private var ticks = 0

	init {
		on<ClientTickEvent.End> { ticked() }
		on<ScreenRenderEvent.Post> { overlay(it) }
		on<ContainerClickEvent> { clicked(it) }
		on<IslandChangeEvent> { joined() }
	}

	override fun onEnabled() = reset()

	override fun onDisabled() {
		mayorHold.release()
		forget()
	}

	override fun onReset() = reset()

	internal fun forget() {
		currentUuid = ""
		totalCoins = 0L
		coinsEarned = 0L
		coinsDifference = 0L
		hasDifference = false
		session.reset()
		element.worn = false
		hovered = NO_BUTTON
		ticks = 0
	}

	private fun gateOpen(): Boolean = SkyBlockLocation.inSkyBlock && MayorService.isPerkActive(MYTHOLOGICAL_RITUAL)

	internal fun reset() {
		coinsEarned = 0L
		coinsDifference = 0L
		hasDifference = true
		session.reset()
		update(Util.getMillis())
	}

	internal fun readCrown(uuid: String, coins: Long, now: Long) {
		if (currentUuid != uuid) {
			currentUuid = uuid
			totalCoins = coins
			update(now)
			return
		}
		val gained = coins - totalCoins
		if (gained == 0L) return
		coinsDifference = gained
		hasDifference = true
		if (gained < 0L) {
			reset()
			totalCoins = coins
			return
		}
		session.start(now)
		session.lap(now)
		coinsEarned += gained
		totalCoins = coins
		update(now)
	}

	internal fun update(now: Long) {
		if (session.lapMillis(now) > afkSeconds.toLong() * SECOND) session.pause(now, revert = true)
		val sessionMillis = session.durationMillis(now)
		val hours = sessionMillis / MILLIS_PER_HOUR
		val calculating = sessionMillis < activeSeconds.toLong() * SECOND
		val paused = session.paused
		element.begin()
		element.add(line(TOTAL_COLOR, amount(totalCoins, shortTotal), ""))
		for (index in tracker.indices) {
			when (tracker[index]) {
				COINS_PER_HOUR -> {
					val perHour = if (hours > 0.0) (coinsEarned / hours).toLong() else 0L
					element.add(line(RATE_LABEL, if (calculating) CALCULATING else amount(perHour, shortRate), tail(paused)))
				}

				TIME_UNTIL_MAX ->
					element.add(line(MAX_LABEL, if (calculating) CALCULATING else untilMax(hours), tail(paused)))

				LAST_GAIN -> element.add(line(GAIN_LABEL, if (hasDifference) coinsDifference.toString() else NEVER, ""))
				SESSION_COINS -> element.add(line(SESSION_LABEL, formatted(coinsEarned), ""))
				SESSION_TIME -> element.add(line(TIME_LABEL, span(sessionMillis), ""))
			}
		}
		if (element.buttonsShown) {
			element.add(
				line(
					if (coinsEarned == 0L) RESET_DISABLED else RESET_LABEL,
					if (paused) PAUSE_DISABLED else PAUSE_LABEL,
					""
				)
			)
		}
	}

	private fun ticked() {
		mayorHold.ensure()
		val open = Minecraft.getInstance().gui.screen() is InventoryScreen
		if (element.buttonsShown != open) {
			element.buttonsShown = open
			if (!open) hovered = NO_BUTTON
			update(Util.getMillis())
		}
		val crown = wornCrown()
		element.worn = crown != null
		if (crown == null) return
		val now = Util.getMillis()
		val coins = crown.collectedCoins
		if (crown.uuid.isNotEmpty() && coins != 0L) readCrown(crown.uuid, coins, now)
		if (++ticks < REFRESH_TICKS) return
		ticks = 0
		if (!session.paused) update(now)
	}

	private fun wornCrown(): SkyBlockItem? {
		if (!gateOpen()) return null
		val stack = Minecraft.getInstance().player?.getItemBySlot(EquipmentSlot.HEAD) ?: return null
		val crown = SkyBlockItems.of(stack)
		return if (crown.id == CROWN_OF_AVARICE) crown else null
	}

	private fun joined() {
		if (resetOnWorldChange) reset()
		totalCoins = wornCrown()?.collectedCoins ?: return
	}

	private fun overlay(event: ScreenRenderEvent.Post) {
		if (event.screen !is InventoryScreen) return
		hovered = NO_BUTTON
		if (!element.isActive || !element.hasContent) return
		val graphics = event.graphics
		val font = Minecraft.getInstance().font
		val width = graphics.guiWidth()
		val height = graphics.guiHeight()
		val failure = drawHudElement(graphics, font, width, height, element, gated = false)
		if (failure != null) {
			reportError(failure)
			return
		}
		hovered = element.buttonAt(font, width, height, event.mouseX, event.mouseY)
	}

	private fun clicked(event: ContainerClickEvent) {
		if (hovered == NO_BUTTON || event.screen !is InventoryScreen) return
		if (event.click.button() != LEFT_BUTTON) return
		val now = Util.getMillis()
		if (hovered == RESET_BUTTON) reset() else session.pause(now, revert = false)
		update(now)
		event.cancelled = true
	}

	private fun line(label: String, value: String, tail: String): String {
		composer.setLength(0)
		composer.append(label)
		composer.append(value)
		composer.append(tail)
		return composer.toString()
	}

	private fun amount(value: Long, short: Boolean): String = if (short) shortNumber(value) else formatted(value)

	private fun tail(paused: Boolean): String = if (paused) PAUSED else ""

	private fun untilMax(hours: Double): String {
		if (hours <= 0.0 || coinsEarned == 0L) return FOREVER
		val left = MAX_AVARICE_COINS - totalCoins
		if (left <= 0L) return ZERO_TIME
		return span((left * hours / coinsEarned * MILLIS_PER_HOUR).toLong())
	}

	private fun span(millis: Long): String = if (millis <= 0L) ZERO_TIME else countdown(millis)

	internal const val COINS_PER_HOUR = "Coins Per Hour"
	internal const val TIME_UNTIL_MAX = "Time Until Max"
	internal const val LAST_GAIN = "Last Coins Gained"
	internal const val SESSION_COINS = "Coins This Session"
	internal const val SESSION_TIME = "Session Time"

	private const val CROWN_OF_AVARICE = "CROWN_OF_AVARICE"
	private const val MYTHOLOGICAL_RITUAL = "Mythological Ritual"
	private const val DEFAULT_AFK_SECONDS = 120.0
	private const val MIN_AFK_SECONDS = 5.0
	private const val MAX_AFK_SECONDS = 180.0
	private const val AFK_STEP = 5.0
	private const val DEFAULT_ACTIVE_SECONDS = 10.0
	private const val MIN_ACTIVE_SECONDS = 0.0
	private const val MAX_ACTIVE_SECONDS = 10.0
	private const val REFRESH_TICKS = 20
}

internal class CoinSession {
	private var accumulated = 0L
	private var startedAt = FAR_PAST

	internal val paused: Boolean
		get() = startedAt == FAR_PAST

	fun reset() {
		accumulated = 0L
		startedAt = FAR_PAST
	}

	fun start(now: Long) {
		if (!paused) return
		startedAt = now
	}

	fun lap(now: Long) {
		if (paused) return
		accumulated += now - startedAt
		startedAt = now
	}

	fun pause(now: Long, revert: Boolean) {
		if (paused) return
		if (!revert) accumulated += now - startedAt
		startedAt = FAR_PAST
	}

	fun lapMillis(now: Long): Long = if (paused) Long.MAX_VALUE else now - startedAt

	fun durationMillis(now: Long): Long = if (paused) accumulated else accumulated + (now - startedAt)
}

internal class CrownElement : HudElement(name = "Crown of Avarice", offsetX = MARGIN, offsetY = MARGIN) {
	private val memos = Array(MAX_LINES) { DhenType.memo() }
	private val lines = Array(MAX_LINES) { "" }
	private var count = 0
	private var filling = 0
	private var resetWidth = 0

	internal var worn = false
	internal var buttonsShown = false

	override val hasContent: Boolean
		get() = editingHud() || (worn && count > 0)

	override fun width(font: Font): Int {
		var widest = 0
		for (index in 0 until shown()) widest = maxOf(widest, memos[index].width(font, text(index)))
		return widest
	}

	override fun height(font: Font): Int = shown() * DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val lineHeight = DhenType.lineHeight(font)
		for (index in 0 until shown()) {
			memos[index].shadowed(graphics, font, text(index), 0, index * lineHeight, textInk, scale)
		}
		resetWidth = if (buttonsShown && count > 0) memos[count - 1].width(font, RESET_LABEL) else 0
	}

	override fun invalidateMeasurement() {
		for (index in memos.indices) memos[index].invalidate()
	}

	internal fun begin() {
		filling = 0
	}

	internal fun hasLine(text: String): Boolean {
		for (index in 0 until count) if (lines[index] == text) return true
		return false
	}

	internal fun add(line: String) {
		if (filling == MAX_LINES) return
		lines[filling] = line
		filling++
		count = filling
	}

	internal fun buttonAt(font: Font, screenWidth: Int, screenHeight: Int, mouseX: Int, mouseY: Int): Int {
		if (!buttonsShown || count == 0) return NO_BUTTON
		val lineHeight = DhenType.lineHeight(font)
		val left = placeX(screenWidth, HudLayout.scaled(width(font), scale))
		val top = placeY(screenHeight, HudLayout.scaled(height(font), scale)) +
			HudLayout.scaled((count - 1) * lineHeight, scale)
		if (mouseY < top || mouseY >= top + HudLayout.scaled(lineHeight, scale)) return NO_BUTTON
		val right = left + HudLayout.scaled(memos[count - 1].width(font, lines[count - 1]), scale)
		if (mouseX < left || mouseX >= right) return NO_BUTTON
		return if (mouseX < left + HudLayout.scaled(resetWidth, scale)) RESET_BUTTON else PAUSE_BUTTON
	}

	private fun shown(): Int = if (count == 0) PREVIEW.size else count

	private fun text(index: Int): String = if (count == 0) PREVIEW[index] else lines[index]
}

private const val MARGIN = 20
private const val MAX_LINES = 7
private const val LINE_CAPACITY = 48
private const val SECOND = 1_000L
private const val MILLIS_PER_HOUR = 3_600_000.0
private const val MAX_AVARICE_COINS = 1_000_000_000L
private const val NO_BUTTON = -1
private const val RESET_BUTTON = 0
private const val PAUSE_BUTTON = 1
private const val LEFT_BUTTON = 0
private const val TOTAL_COLOR = "§6"
private const val RATE_LABEL = "§aCoins Per Hour: §6"
private const val MAX_LABEL = "§aTime until Max: §b"
private const val GAIN_LABEL = "§aLast coins gained: §6"
private const val SESSION_LABEL = "§aCoins this session: §6"
private const val TIME_LABEL = "§aSession Time: §b"
private const val CALCULATING = "Calculating..."
private const val NEVER = "§cnever"
private const val FOREVER = "Forever..."
private const val ZERO_TIME = "0s"
private const val PAUSED = " §c(PAUSED)"
private const val RESET_LABEL = "§c[Reset session] "
private const val RESET_DISABLED = "§8[Reset session] "
private const val PAUSE_LABEL = "§6[Pause session]"
private const val PAUSE_DISABLED = "§8[Pause session]"
private val PREVIEW = arrayOf(
	"§61.2M",
	"§aCoins Per Hour: §6820k",
	"§aTime until Max: §b1d 4h",
	"§aLast coins gained: §61200",
	"§aCoins this session: §6410,000",
	"§aSession Time: §b30m"
)
