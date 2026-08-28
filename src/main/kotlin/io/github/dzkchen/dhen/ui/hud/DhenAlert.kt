package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import kotlin.math.ceil
import kotlin.math.roundToInt

internal object DhenAlert {
	internal val element = AlertHudElement()
	internal val elements: List<HudElement> = listOf(element)

	fun show(
		title: Component,
		subtitle: Component = Component.empty(),
		ticks: Int = DEFAULT_TICKS,
		sound: SoundEvent? = SoundEvents.NOTE_BLOCK_PLING.value()
	) {
		require(ticks > 0) { "Alert lifetime must be positive." }
		val client = Minecraft.getInstance()
		if (!client.isSameThread) {
			client.execute { show(title, subtitle, ticks, sound) }
			return
		}
		element.show(title, subtitle, ticks)
		if (sound != null) client.soundManager.play(SimpleSoundInstance.forUI(sound, 1.0f, 1.0f))
	}

	fun show(
		title: String,
		subtitle: String = "",
		ticks: Int = DEFAULT_TICKS,
		sound: SoundEvent? = SoundEvents.NOTE_BLOCK_PLING.value()
	) {
		require(ticks > 0) { "Alert lifetime must be positive." }
		val client = Minecraft.getInstance()
		if (!client.isSameThread) {
			client.execute { show(title, subtitle, ticks, sound) }
			return
		}
		show(DhenType.overWorld(title), DhenType.overWorld(subtitle), ticks, sound)
	}

	internal fun tick() = element.tick()

	internal fun clear() = element.clear()

	internal fun beginPreview() = element.previewing(true)

	internal fun endPreview() = element.previewing(false)

	private const val DEFAULT_TICKS = 40
}

internal class AlertHudElement : HudElement("Alerts", anchor = HudAnchor.MIDDLE_CENTER) {
	private val titleMemo: TextMemo = DhenType.memo()
	private val subtitleMemo: TextMemo = DhenType.memo()
	private var title: Component = Component.empty()
	private var subtitle: Component = Component.empty()
	private var remainingTicks = 0
	private var preview = false

	internal val ticksLeft: Int
		get() = remainingTicks
	internal val currentTitle: Component
		get() = shownTitle()

	override val hasContent: Boolean
		get() = remainingTicks > 0 || preview

	fun show(title: Component, subtitle: Component, ticks: Int) {
		require(ticks > 0) { "Alert lifetime must be positive." }
		this.title = title.copy()
		this.subtitle = subtitle.copy()
		remainingTicks = ticks
	}

	fun tick() {
		if (remainingTicks > 0) remainingTicks--
	}

	fun clear() {
		remainingTicks = 0
	}

	fun previewing(preview: Boolean) {
		this.preview = preview
	}

	override fun width(font: Font): Int =
		maxOf(scaled(titleMemo.width(font, shownTitle()), TITLE_SCALE), scaled(subtitleMemo.width(font, shownSubtitle()), SUBTITLE_SCALE))

	override fun height(font: Font): Int = height(font, DEFAULT_SCREEN_HEIGHT)

	override fun height(font: Font, screenHeight: Int): Int =
		maxOf(
			scaled(DhenType.lineHeight(font), TITLE_SCALE),
			subtitleTop(screenHeight) + scaled(DhenType.lineHeight(font), SUBTITLE_SCALE)
		)

	override fun placeY(screenHeight: Int, height: Int): Int {
		if (anchor != HudAnchor.MIDDLE_CENTER) return super.placeY(screenHeight, height)
		val top = screenHeight / 2 - (screenHeight * TITLE_OFFSET).roundToInt() + offsetY
		return HudLayout.clamp(top, height, screenHeight)
	}

	override fun offsetYFor(anchor: HudAnchor, screenHeight: Int, height: Int, position: Int): Int {
		if (anchor != HudAnchor.MIDDLE_CENTER) return super.offsetYFor(anchor, screenHeight, height, position)
		return position - (screenHeight / 2 - (screenHeight * TITLE_OFFSET).roundToInt())
	}

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val shownTitle = shownTitle()
		val shownSubtitle = shownSubtitle()
		val contentWidth = width(font)
		draw(graphics, font, titleMemo, shownTitle, contentWidth, 0, TITLE_SCALE)
		draw(graphics, font, subtitleMemo, shownSubtitle, contentWidth, subtitleTop(graphics.guiHeight()), SUBTITLE_SCALE)
	}

	override fun invalidateMeasurement() {
		titleMemo.invalidate()
		subtitleMemo.invalidate()
	}

	private fun draw(
		graphics: GuiGraphicsExtractor,
		font: Font,
		memo: TextMemo,
		text: Component,
		contentWidth: Int,
		top: Int,
		textScale: Float
	) {
		val scaledWidth = scaled(memo.width(font, text), textScale)
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate(((contentWidth - scaledWidth) / 2).toFloat(), top.toFloat())
			pose.scale(textScale, textScale)
			memo.shadowed(graphics, font, text, 0, 0, DhenPalette.TEXT_ON_WORLD, scale * textScale)
		} finally {
			pose.popMatrix()
		}
	}

	private fun shownTitle(): Component = if (remainingTicks > 0) title else PREVIEW_TITLE

	private fun shownSubtitle(): Component = if (remainingTicks > 0) subtitle else PREVIEW_SUBTITLE

	private fun subtitleTop(screenHeight: Int): Int = (screenHeight / SUBTITLE_DIVISOR).roundToInt()

	private fun scaled(size: Int, scale: Float): Int = ceil(size * scale).toInt()

	private companion object {
		const val TITLE_SCALE = 2.5f
		const val SUBTITLE_SCALE = 1.5f
		const val TITLE_OFFSET = 0.056f
		const val SUBTITLE_DIVISOR = 15.42f
		const val DEFAULT_SCREEN_HEIGHT = 480
		val PREVIEW_TITLE: Component = DhenType.overWorld("Dhen Alert")
		val PREVIEW_SUBTITLE: Component = DhenType.overWorld("Title and subtitle preview")
	}
}
