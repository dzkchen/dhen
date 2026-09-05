package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement as FabricHudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.Function

object SkyBlockXpBar : Module(
	name = "SkyBlock XP Bar",
	category = Category.VISUAL,
	description = "Fills the vanilla experience bar with your SkyBlock level and its progress instead."
) {
	private var level = ABSENT
	private var experience = 0

	private var heldProgress = 0f
	private var heldTotal = 0
	private var heldLevel = 0
	private var swapped = false

	init {
		on<TabWidgetUpdateEvent> { read(it.widget) }
		on<ClientTickEvent.End> { restore() }
	}

	override fun onEnabled() = read(TabWidget.SB_LEVEL)

	override fun onDisabled() {
		restore()
		level = ABSENT
	}

	private fun read(widget: TabWidget) {
		if (widget != TabWidget.SB_LEVEL) return
		level = TabWidgetState.capture(TabWidget.SB_LEVEL, LEVEL)?.toIntOrNull() ?: ABSENT
		experience = TabWidgetState.capture(TabWidget.SB_LEVEL, EXPERIENCE)?.toIntOrNull() ?: 0
	}

	internal fun barReplacement(failsafe: Failsafe): Function<FabricHudElement, FabricHudElement> =
		wrapper(failsafe, swapping = true)

	internal fun levelReplacement(failsafe: Failsafe): Function<FabricHudElement, FabricHudElement> =
		wrapper(failsafe, swapping = false)

	private fun wrapper(failsafe: Failsafe, swapping: Boolean): Function<FabricHudElement, FabricHudElement> {
		val wrapper = XpBarLayerWrapper(swapping, failsafe)
		return Function { wrapper.bind(it) }
	}

	internal fun swap() {
		restore()
		if (!showing() || level <= 0) return
		val player = Minecraft.getInstance().player ?: return
		heldProgress = player.experienceProgress
		heldTotal = player.totalExperience
		heldLevel = player.experienceLevel
		swapped = true
		player.setExperienceValues(experience / LEVEL_XP, LEVEL_XP.toInt(), level)
	}

	internal fun restore() {
		if (!swapped) return
		swapped = false
		Minecraft.getInstance().player?.setExperienceValues(heldProgress, heldTotal, heldLevel)
	}

	private fun showing(): Boolean =
		enabled && SkyBlockLocation.inSkyBlock && SkyBlockLocation.island !in BLOCKED_ISLANDS

	private val BLOCKED_ISLANDS = setOf(Island.THE_RIFT, Island.CATACOMBS)

	private const val ABSENT = -1
	private const val LEVEL = "level"
	private const val EXPERIENCE = "xp"
	private const val LEVEL_XP = 100f
}

private class XpBarLayerWrapper(private val swapping: Boolean, private val failsafe: Failsafe) : FabricHudElement {
	private var vanilla: FabricHudElement? = null

	fun bind(vanilla: FabricHudElement): FabricHudElement {
		this.vanilla = vanilla
		return this
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
		val current = vanilla ?: return
		vanilla = null
		if (swapping && !failsafe.failed) {
			try {
				SkyBlockXpBar.swap()
			} catch (throwable: Throwable) {
				SkyBlockXpBar.reportError(throwable)
			}
		}
		try {
			current.extractRenderState(graphics, deltaTracker)
		} finally {
			if (!swapping) {
				try {
					SkyBlockXpBar.restore()
				} catch (throwable: Throwable) {
					failsafe.fail("SkyBlock XP bar restore", throwable)
				}
			}
		}
	}
}
