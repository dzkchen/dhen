package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.pet.CurrentPet
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ServerTickEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.DhenAlert
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.HudLayout
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import java.util.regex.Matcher
import java.util.regex.Pattern

object MaskTimers : Module(
	name = "Mask Timers",
	category = Category.VISUAL,
	description = "Counts the Bonzo, Spirit and Phoenix death-save cooldowns from their chat lines, " +
		"with an invulnerability countdown and proc and ready titles."
) {
	internal val styleSetting = SelectorSetting("Style", NOAMM_ADDONS_STYLE, listOf(NOAMM_ADDONS_STYLE, ZYRYON_STYLE))
	internal val dungeonsOnlySetting = BooleanSetting("Dungeons Only")
	internal val invulnerabilitySetting = BooleanSetting("Invulnerability Timers", true)
	internal val procNotificationSetting = BooleanSetting("Proc Notification", true)
	internal val readyNotificationSetting = BooleanSetting("Ready Notification", true)
	internal val bonzoColorSetting = ColorSetting("Bonzo Color", Color.rgba(85, 85, 255))
	internal val spiritColorSetting = ColorSetting("Spirit Color", Color.rgba(255, 255, 255))
	internal val phoenixColorSetting = ColorSetting("Phoenix Color", Color.rgba(255, 85, 85))

	private var style by styleSetting
	private var dungeonsOnly by dungeonsOnlySetting
	private var invulnerabilityShown by invulnerabilitySetting
	private var procAnnounced by procNotificationSetting
	private var readyAnnounced by readyNotificationSetting
	private var bonzoColor by bonzoColorSetting
	private var spiritColor by spiritColorSetting
	private var phoenixColor by phoenixColorSetting

	internal val timersElement = hud(MaskTimersElement())
	internal val invulnerabilityElement = hud(MaskInvulnerabilityElement())

	internal val zyryonStyle: Boolean get() = style == ZYRYON_STYLE
	internal val dungeonGateOpen: Boolean get() = !dungeonsOnly || SkyBlockLocation.island == Island.CATACOMBS

	init {
		on<ChatReceiveEvent> { chatted(it.stripped) }
		on<ServerTickEvent> { ticked() }
		on<ClientTickEvent.End> { refresh(editingHud()) }
		on<WorldChangeEvent> { clear() }
	}

	override fun onEnabled() = refresh(false)

	override fun onDisabled() = refresh(false)

	override fun onReset() = clear()

	internal fun ink(mask: Mask): Int = when (mask) {
		Mask.BONZO -> bonzoColor.argb
		Mask.SPIRIT -> spiritColor.argb
		Mask.PHOENIX -> phoenixColor.argb
	}

	internal fun clear() {
		for (index in masks.indices) masks[index].reset()
		refresh(false)
	}

	internal fun chatted(line: String) {
		if (!SkyBlockLocation.inSkyBlock || !dungeonGateOpen) return
		for (index in masks.indices) {
			val mask = masks[index]
			if (!mask.matches(line)) continue
			mask.proc(invulnerabilityShown)
			if (procAnnounced) DhenAlert.show("${mask.displayName} Procced!", sound = null)
		}
	}

	internal fun ticked() {
		if (!SkyBlockLocation.inSkyBlock) return
		val zyryon = zyryonStyle
		val player = if (zyryon) Minecraft.getInstance().player else null
		for (index in masks.indices) {
			val mask = masks[index]
			if (zyryon) mask.worn = mask.equipped(player)
			if (mask.invulnerabilityLeft > 0) mask.invulnerabilityLeft--
			if (mask.cooldownLeft > 0) {
				mask.cooldownLeft--
				mask.notifiedReady = false
			} else if (!mask.notifiedReady) {
				mask.notifiedReady = true
				if (readyAnnounced && dungeonGateOpen) DhenAlert.show("${mask.displayName} is Ready!", sound = null)
			}
		}
	}

	internal fun refresh(example: Boolean) {
		timersElement.update(zyryonStyle, example)
		invulnerabilityElement.update(invulnerabilityShown, example)
	}

	private const val NOAMM_ADDONS_STYLE = "NoammAddons"
	private const val ZYRYON_STYLE = "Zyryon"
}

internal enum class Mask(
	val displayName: String,
	val suffix: String,
	val cooldownTicks: Int,
	val invulnerabilityTicks: Int,
	pattern: String,
	private val helmetId: String?,
	private val petName: String? = null
) {
	BONZO("Bonzo", "Mask", 3600, 60, "Your (?:.+ )?Bonzo's Mask saved your life!", "BONZO_MASK"),
	SPIRIT("Spirit", "Mask", 600, 60, "Second Wind Activated! Your Spirit Mask saved your life!", "SPIRIT_MASK"),
	PHOENIX("Phoenix", "Pet", 1200, 80, "Your Phoenix Pet saved you from certain death!", null, "Phoenix");

	private val procLine: Matcher = Pattern.compile(pattern).matcher("")

	var cooldownLeft = 0
	var invulnerabilityLeft = 0
	var worn = false
	var notifiedReady = true

	fun matches(text: String): Boolean = procLine.reset(text).matches()

	fun proc(invulnerability: Boolean) {
		cooldownLeft = cooldownTicks
		if (invulnerability) invulnerabilityLeft = invulnerabilityTicks
	}

	fun equipped(player: Player?): Boolean = when {
		helmetId != null -> player != null && helmetId in SkyBlockItems.of(player.getItemBySlot(EquipmentSlot.HEAD)).id
		petName != null -> CurrentPet.bareName == petName
		else -> false
	}

	fun reset() {
		cooldownLeft = 0
		invulnerabilityLeft = 0
		worn = false
		notifiedReady = true
	}
}

internal class MaskRow(internal val mask: Mask) {
	private val nameMemo = DhenType.memo()
	private val arrowMemo = DhenType.memo()
	private val valueMemo = DhenType.memo()
	private val builder = StringBuilder(TEXT_CAPACITY)
	private var zyryon = false
	private var ticks = UNCOMPOSED
	private var name = ""

	internal var listed = false
		private set
	internal var worn = false
		private set
	internal var value = ""
		private set

	internal fun update(zyryon: Boolean, ticks: Int, worn: Boolean, example: Boolean) {
		if (zyryon != this.zyryon || name.isEmpty()) {
			this.zyryon = zyryon
			this.ticks = UNCOMPOSED
			name = if (zyryon) "${mask.displayName} " else "${mask.displayName} ${mask.suffix}: "
		}
		this.worn = worn
		listed = zyryon || example || ticks > 0
		if (ticks == this.ticks) return
		this.ticks = ticks
		builder.setLength(0)
		if (ticks <= 0) builder.append(READY) else appendSeconds(builder, ticks, zyryon)
		value = builder.toString()
	}

	internal fun width(font: Font): Int =
		nameMemo.width(font, name) + arrowWidth(font) + valueMemo.width(font, value)

	internal fun render(graphics: GuiGraphicsExtractor, font: Font, top: Int, scale: Float) {
		val ink = MaskTimers.ink(mask)
		nameMemo.shadowed(graphics, font, name, 0, top, ink, scale)
		var left = nameMemo.width(font, name)
		if (zyryon) {
			arrowMemo.shadowed(graphics, font, ARROW, left, top, if (worn) AVAILABLE_INK else UNAVAILABLE_INK, scale)
			left += arrowMemo.width(font, ARROW)
		}
		valueMemo.shadowed(graphics, font, value, left, top, valueInk(), scale)
	}

	internal fun invalidate() {
		nameMemo.invalidate()
		arrowMemo.invalidate()
		valueMemo.invalidate()
	}

	private fun arrowWidth(font: Font): Int = if (zyryon) arrowMemo.width(font, ARROW) else 0

	private fun valueInk(): Int = if (zyryon && ticks > 0) COUNTING_INK else AVAILABLE_INK

	private companion object {
		const val TEXT_CAPACITY = 8
		const val UNCOMPOSED = Int.MIN_VALUE
	}
}

internal class MaskTimersElement : HudElement("Mask Timers", HudAnchor.MIDDLE_LEFT, ROW_X, 0) {
	private val rows = Array(Mask.entries.size) { MaskRow(Mask.entries[it]) }
	private var listedRows = 0

	override val hasContent: Boolean
		get() = contentAvailable(SkyBlockLocation.inSkyBlock && MaskTimers.dungeonGateOpen, editingHud())

	override fun width(font: Font): Int {
		var widest = 0
		for (index in rows.indices) {
			val row = rows[index]
			if (row.listed) widest = maxOf(widest, row.width(font))
		}
		return widest
	}

	override fun height(font: Font): Int = maxOf(1, listedRows) * rowHeight(font) - ROW_GAP

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		var top = 0
		for (index in rows.indices) {
			val row = rows[index]
			if (!row.listed) continue
			row.render(graphics, font, top, scale)
			top += rowHeight(font)
		}
	}

	override fun invalidateMeasurement() {
		for (index in rows.indices) rows[index].invalidate()
	}

	internal fun contentAvailable(locationAllows: Boolean, editing: Boolean): Boolean =
		listedRows > 0 && (locationAllows || editing)

	internal fun rowFor(mask: Mask): MaskRow = rows[mask.ordinal]

	internal fun update(zyryon: Boolean, example: Boolean) {
		var listed = 0
		for (index in rows.indices) {
			val row = rows[index]
			val mask = row.mask
			val ticks = if (example) mask.cooldownTicks / 2 else mask.cooldownLeft
			row.update(zyryon, ticks, example || mask.worn, example)
			if (row.listed) listed++
		}
		listedRows = listed
	}

	private fun rowHeight(font: Font): Int = DhenType.lineHeight(font) + ROW_GAP
}

internal class MaskInvulnerabilityElement : HudElement(
	name = "Mask Invulnerability",
	anchor = HudAnchor.MIDDLE_CENTER,
	scale = OVERLAY_SCALE
) {
	private val nameMemo = DhenType.memo()
	private val valueMemo = DhenType.memo()
	private val builder = StringBuilder(TEXT_CAPACITY)
	private var ticks = UNCOMPOSED
	private var label = ""

	internal var shown: Mask? = null
		private set
	internal var value = ""
		private set

	override val hasContent: Boolean
		get() = shown != null

	override fun width(font: Font): Int = nameMemo.width(font, label) + valueMemo.width(font, value)

	override fun height(font: Font): Int = DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val mask = shown ?: return
		nameMemo.shadowed(graphics, font, label, 0, 0, MaskTimers.ink(mask), scale)
		val ink = if (ticks < EXPIRING_TICKS) UNAVAILABLE_INK else AVAILABLE_INK
		valueMemo.shadowed(graphics, font, value, nameMemo.width(font, label), 0, ink, scale)
	}

	override fun invalidateMeasurement() {
		nameMemo.invalidate()
		valueMemo.invalidate()
	}

	override fun placeY(screenHeight: Int, height: Int): Int {
		if (anchor != HudAnchor.MIDDLE_CENTER) return super.placeY(screenHeight, height)
		return HudLayout.clamp(screenHeight / OVERLAY_DIVISOR + offsetY, height, screenHeight)
	}

	override fun offsetYFor(anchor: HudAnchor, screenHeight: Int, height: Int, position: Int): Int {
		if (anchor != HudAnchor.MIDDLE_CENTER) return super.offsetYFor(anchor, screenHeight, height, position)
		return position - screenHeight / OVERLAY_DIVISOR
	}

	internal fun update(overlayShown: Boolean, example: Boolean) {
		val active = when {
			!overlayShown -> null
			example -> Mask.BONZO
			else -> longest()
		}
		if (active !== shown) {
			shown = active
			ticks = UNCOMPOSED
			label = if (active == null) "" else "${active.displayName}: "
		}
		if (active == null) return
		val left = if (example) active.invulnerabilityTicks else active.invulnerabilityLeft
		if (left == ticks) return
		ticks = left
		builder.setLength(0)
		appendSeconds(builder, left, false)
		value = builder.toString()
	}

	private fun longest(): Mask? {
		var best: Mask? = null
		for (index in masks.indices) {
			val mask = masks[index]
			if (mask.invulnerabilityLeft <= 0) continue
			if (best == null || mask.invulnerabilityLeft > best.invulnerabilityLeft) best = mask
		}
		return best
	}

	private companion object {
		const val TEXT_CAPACITY = 8
		const val UNCOMPOSED = Int.MIN_VALUE
		const val EXPIRING_TICKS = 20
	}
}

private val masks = Mask.entries.toTypedArray()

private const val ROW_X = 2
private const val ROW_GAP = 1
private const val OVERLAY_SCALE = 1.5f
private const val OVERLAY_DIVISOR = 3
private const val ARROW = "> "
private const val READY = "Ready"
private const val TENTHS_PER_SECOND = 10
private const val HUNDREDTHS_PER_SECOND = 100
private const val TICKS_PER_TENTH = 2
private const val HUNDREDTHS_PER_TICK = 5

private val AVAILABLE_INK = Color.rgba(85, 255, 85).argb
private val UNAVAILABLE_INK = Color.rgba(255, 85, 85).argb
private val COUNTING_INK = Color.rgba(255, 255, 85).argb

internal fun appendSeconds(builder: StringBuilder, ticks: Int, hundredths: Boolean) {
	val perSecond = if (hundredths) HUNDREDTHS_PER_SECOND else TENTHS_PER_SECOND
	val units = if (hundredths) ticks * HUNDREDTHS_PER_TICK else (ticks + 1) / TICKS_PER_TENTH
	builder.append(units / perSecond).append('.')
	var place = perSecond / TENTHS_PER_SECOND
	while (place > 0) {
		builder.append(units / place % TENTHS_PER_SECOND)
		place /= TENTHS_PER_SECOND
	}
}
