package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class Notice(icon: String, title: String, val message: String?) {
	val heading: String = "$icon $title"

	private val titleMemo: TextMemo = DhenType.memo()
	private val messageMemo: TextMemo = DhenType.memo()

	var remaining: Int = Notifications.LIFETIME_TICKS
		private set
	var held: Boolean = false
		private set
	var onScreen: Boolean = false
		private set
	var top: Int = 0
		private set
	var bottom: Int = 0
		private set

	val expired: Boolean
		get() = remaining <= 0

	val progress: Float
		get() = remaining.toFloat() / Notifications.LIFETIME_TICKS

	fun tick() {
		if (onScreen && !held) remaining--
		onScreen = false
	}

	fun place(top: Int, bottom: Int, holding: Boolean) {
		this.top = top
		this.bottom = bottom
		held = holding
		onScreen = true
	}

	fun height(font: Font): Int =
		Notifications.CARD_PAD * 2 + Notifications.TRACK_HEIGHT + DhenType.lineHeight(font) +
			if (message == null) 0 else Notifications.LINE_GAP + DhenType.lineHeight(font)

	fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, right: Int) {
		GlassGui.roundedFrame(
			graphics,
			left,
			top,
			right,
			bottom,
			Notifications.CARD_RADIUS,
			GlassGui.raised(held),
			DhenPalette.BORDER
		)
		val textLeft = left + Notifications.CARD_PAD
		val room = right - left - Notifications.CARD_PAD * 2
		var row = top + Notifications.CARD_PAD
		titleMemo.text(graphics, font, titleMemo.fit(font, heading, room), textLeft, row, DhenPalette.TEXT_PRIMARY)
		if (message != null) {
			row += DhenType.lineHeight(font) + Notifications.LINE_GAP
			messageMemo.text(graphics, font, messageMemo.fit(font, message, room), textLeft, row, DhenPalette.TEXT_SECONDARY)
		}
		val trackBottom = bottom - HAIRLINE_INSET
		RoundedGui.capsuleTrack(
			graphics,
			textLeft,
			trackBottom - Notifications.TRACK_HEIGHT,
			right - Notifications.CARD_PAD,
			trackBottom,
			progress,
			DhenPalette.SURFACE,
			DhenPalette.accent
		)
	}

	fun invalidateMeasurements() {
		titleMemo.invalidate()
		messageMemo.invalidate()
	}
}

internal object Notifications {
	const val LIFETIME_TICKS = 200
	const val MAX_VISIBLE = 4
	const val CARD_PAD = 5
	const val LINE_GAP = 2
	const val TRACK_HEIGHT = 2
	const val CARD_RADIUS = 5f

	private const val CARD_WIDTH = 152
	private const val SCREEN_MARGIN = 8
	private const val CARD_GAP = 4

	private val shown = ArrayDeque<Notice>()

	private var subscription: Handle? = null

	internal val visible: List<Notice>
		get() = shown

	fun install(bus: EventBus) {
		uninstall()
		subscription = bus.subscribe<ScreenRenderEvent.Post> {
			render(it.graphics, Minecraft.getInstance().font, it.mouseX, it.mouseY)
		}
	}

	fun uninstall() {
		subscription?.unsubscribe()
		subscription = null
		clear()
	}

	fun push(icon: String, title: String, message: String?) {
		if (shown.size >= MAX_VISIBLE) shown.removeFirst()
		shown.addLast(Notice(icon, title, message))
	}

	fun tick() {
		var index = 0
		while (index < shown.size) {
			val notice = shown[index]
			notice.tick()
			if (notice.expired) shown.removeAt(index) else index++
		}
	}

	fun clear() = shown.clear()

	fun invalidateMeasurements() {
		for (index in 0 until shown.size) shown[index].invalidateMeasurements()
	}

	fun renderBehindNoScreen(graphics: GuiGraphicsExtractor, font: Font) {
		if (Minecraft.getInstance().gui.screen() != null) return
		render(graphics, font, NO_POINTER, NO_POINTER)
	}

	fun render(graphics: GuiGraphicsExtractor, font: Font, pointerX: Int, pointerY: Int) {
		if (shown.isEmpty()) return
		val right = graphics.guiWidth() - SCREEN_MARGIN
		val left = right - CARD_WIDTH
		layout(font, left, right, graphics.guiHeight() - SCREEN_MARGIN, pointerX, pointerY)
		for (index in 0 until shown.size) shown[index].draw(graphics, font, left, right)
	}

	fun layout(font: Font, left: Int, right: Int, floor: Int, pointerX: Int, pointerY: Int) {
		var bottom = floor
		for (index in shown.size - 1 downTo 0) {
			val notice = shown[index]
			val top = bottom - notice.height(font)
			notice.place(top, bottom, pointerX in left..right && pointerY in top..bottom)
			bottom = top - CARD_GAP
		}
	}
}
