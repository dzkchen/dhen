package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.pickup.PickupHooks
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ItemPickupEvent
import io.github.dzkchen.dhen.event.PickupSource
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.drawHudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen

object PickupLog : Module(
	name = "Pickup Log",
	category = Category.QOL,
	description = "Lists what your inventory just gained and lost, fading after six seconds."
) {
	private var hideSackMessages by BooleanSetting(
		"Hide Sack Messages",
		false,
		"Removes the Hypixel line saying how many items went into your sacks."
	)
	internal val element = hud(PickupLogElement())

	init {
		on<ItemPickupEvent> { if (it.source == PickupSource.INVENTORY) element.record(it.id, it.name, it.delta) }
		on<ClientTickEvent.End> { element.prune() }
		on<ChatReceiveEvent> {
			if (hideSackMessages && SkyBlockLocation.inSkyBlock && PickupHooks.sackItemMessage(it.stripped)) {
				it.cancelled = true
			}
		}
		on<ScreenRenderEvent.Post> { overlay(it) }
	}

	override fun onEnabled() = element.clear()

	override fun onDisabled() = element.clear()

	override fun onReset() = element.clear()

	private fun overlay(event: ScreenRenderEvent.Post) {
		val screen = event.screen
		if (screen !is ChatScreen && screen !is InventoryScreen) return
		if (!element.isActive || element.shownLines == 0) return
		val graphics = event.graphics
		val failure = drawHudElement(
			graphics,
			Minecraft.getInstance().font,
			graphics.guiWidth(),
			graphics.guiHeight(),
			element,
			gated = false
		) ?: return
		reportError(failure)
	}
}

internal class PickupLogElement(private val clock: NanoClock = NanoClock.SYSTEM) : HudElement(
	name = "Pickup Log",
	offsetX = MARGIN,
	offsetY = MARGIN
) {
	private val memos = Array(MAX_LINES) { DhenType.memo() }
	private val keys = arrayOfNulls<String>(MAX_LINES)
	private val names = arrayOfNulls<String>(MAX_LINES)
	private val lines = Array(MAX_LINES) { "" }
	private val amounts = IntArray(MAX_LINES)
	private val touched = LongArray(MAX_LINES)
	private val composer = StringBuilder(LINE_CAPACITY)
	private var gained = 0
	private var count = 0

	internal val shownLines: Int
		get() = count

	override val hasContent: Boolean
		get() = editingHud() || count > 0 && Minecraft.getInstance().gui.screen() == null

	override fun width(font: Font): Int {
		val editing = editingHud()
		var widest = 0
		for (index in 0 until shown(editing)) {
			widest = maxOf(widest, memos[index].width(font, line(index, editing)))
		}
		return widest
	}

	override fun height(font: Font): Int = maxOf(1, shown(editingHud())) * DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val editing = editingHud()
		val lineHeight = DhenType.lineHeight(font)
		for (index in 0 until shown(editing)) {
			memos[index].shadowed(graphics, font, line(index, editing), 0, index * lineHeight, textInk, scale)
		}
	}

	override fun invalidateMeasurement() {
		for (index in memos.indices) memos[index].invalidate()
	}

	internal fun record(id: String, name: String, delta: Int) {
		if (delta == 0) return
		val now = clock.nanoTime()
		val at = indexOf(id, delta > 0)
		if (at >= 0) {
			amounts[at] += delta
			touched[at] = now
			compose(at)
			return
		}
		val slot = claim(delta > 0)
		keys[slot] = id
		names[slot] = name
		amounts[slot] = delta
		touched[slot] = now
		compose(slot)
	}

	internal fun prune() {
		val now = clock.nanoTime()
		var index = 0
		while (index < count) {
			if (now - touched[index] > LIFETIME_NANOS) drop(index) else index++
		}
	}

	internal fun clear() {
		count = 0
		gained = 0
	}

	internal fun lineAt(index: Int): String = lines[index]

	private fun shown(editing: Boolean): Int = if (editing) PREVIEW.size else count

	private fun line(index: Int, editing: Boolean): String = if (editing) PREVIEW[index] else lines[index]

	private fun indexOf(id: String, positive: Boolean): Int {
		val from = if (positive) 0 else gained
		val until = if (positive) gained else count
		for (index in from until until) if (keys[index] == id) return index
		return -1
	}

	private fun claim(positive: Boolean): Int {
		if (count == MAX_LINES) drop(oldest())
		val slot = if (positive) gained else count
		if (positive) {
			shift(slot)
			gained++
		}
		count++
		return slot
	}

	private fun shift(slot: Int) {
		for (index in count downTo slot + 1) copy(index - 1, index)
	}

	private fun drop(index: Int) {
		for (at in index until count - 1) copy(at + 1, at)
		count--
		if (index < gained) gained--
	}

	private fun copy(from: Int, to: Int) {
		keys[to] = keys[from]
		names[to] = names[from]
		lines[to] = lines[from]
		amounts[to] = amounts[from]
		touched[to] = touched[from]
	}

	private fun oldest(): Int {
		var oldest = 0
		for (index in 1 until count) if (touched[index] < touched[oldest]) oldest = index
		return oldest
	}

	private fun compose(index: Int) {
		val amount = amounts[index]
		composer.setLength(0)
		composer.append(if (amount > 0) GAINED_PREFIX else LOST_PREFIX)
		composer.append(if (amount > 0) amount else -amount)
		composer.append(NAME_GAP)
		composer.append(names[index])
		lines[index] = composer.toString()
	}
}

private const val MARGIN = 5
private const val MAX_LINES = 32
private const val LINE_CAPACITY = 48
private const val LIFETIME_NANOS = 6_000_000_000L
private const val GAINED_PREFIX = "§a+ "
private const val LOST_PREFIX = "§c- "
private const val NAME_GAP = "x §r"
private val PREVIEW = arrayOf(
	"§a+ 64x §r§9Enchanted Cobblestone",
	"§a+ 1x §r§5Griffin Feather",
	"§c- 3x §r§aFine Amethyst Gemstone"
)
