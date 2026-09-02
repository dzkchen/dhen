package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.HudLayout
import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement as FabricHudElement
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2fStack
import java.util.function.Function

object MoveableVanillaHud : Module(
	name = "Moveable Vanilla HUD",
	category = Category.VISUAL,
	description = "Moves and scales selected vanilla HUD elements."
) {
	internal val hotbarSetting = BooleanSetting("Hotbar")
	private var hotbar by hotbarSetting

	internal val experienceSetting = BooleanSetting("XP Bar")
	private var experience by experienceSetting

	internal val heldItemSetting = BooleanSetting("Held Item Name")
	private var heldItem by heldItemSetting

	internal val actionBarSetting = BooleanSetting("Action Bar")
	private var actionBar by actionBarSetting

	internal val showOutsideSkyBlockSetting = BooleanSetting(
		"Show Outside SkyBlock",
		description = "Moves enabled vanilla HUD elements outside SkyBlock."
	)
	private var showOutsideSkyBlock by showOutsideSkyBlockSetting

	internal val hotbarElement = hud(VanillaHudElement("Vanilla Hotbar", 182, 22, 91, 22))
	internal val experienceElement = hud(VanillaHudElement("Vanilla XP Bar", 182, 5, 91, 29))
	internal val heldItemElement = hud(VanillaHudElement("Vanilla Held Item Name", 182, 10, 91, 59))
	internal val actionBarElement = hud(VanillaHudElement("Vanilla Action Bar", 182, 10, 91, 72))

	internal fun replacement(
		layer: VanillaHudLayer,
		failsafe: Failsafe
	): Function<FabricHudElement, FabricHudElement> {
		val wrapper = VanillaHudLayerWrapper(layer, VanillaHudLayerTransform(element(layer)), failsafe)
		return Function { wrapper.bind(it) }
	}

	internal fun moves(layer: VanillaHudLayer): Boolean = vanillaHudLayerActive(
		enabled,
		when (layer) {
			VanillaHudLayer.HOTBAR -> hotbarSetting.on
			VanillaHudLayer.EXPERIENCE_BAR, VanillaHudLayer.EXPERIENCE_LEVEL -> experienceSetting.on
			VanillaHudLayer.HELD_ITEM -> heldItemSetting.on
			VanillaHudLayer.ACTION_BAR -> actionBarSetting.on
		},
		SkyBlockLocation.inSkyBlock,
		showOutsideSkyBlockSetting.on
	)

	private fun element(layer: VanillaHudLayer): VanillaHudElement = when (layer) {
		VanillaHudLayer.HOTBAR -> hotbarElement
		VanillaHudLayer.EXPERIENCE_BAR, VanillaHudLayer.EXPERIENCE_LEVEL -> experienceElement
		VanillaHudLayer.HELD_ITEM -> heldItemElement
		VanillaHudLayer.ACTION_BAR -> actionBarElement
	}
}

internal enum class VanillaHudLayer(val failureLabel: String) {
	HOTBAR("moveable vanilla hotbar"),
	EXPERIENCE_BAR("moveable vanilla XP bar"),
	EXPERIENCE_LEVEL("moveable vanilla XP level"),
	HELD_ITEM("moveable vanilla held item name"),
	ACTION_BAR("moveable vanilla action bar")
}

internal class VanillaHudElement(
	name: String,
	internal val contentWidth: Int,
	internal val contentHeight: Int,
	internal val vanillaOffsetX: Int,
	internal val vanillaOffsetY: Int
) : HudElement(
	name,
	HudAnchor.BOTTOM_CENTER,
	0,
	contentHeight - vanillaOffsetY
) {
	override fun width(font: Font): Int = contentWidth

	override fun height(font: Font): Int = contentHeight

	override fun render(graphics: GuiGraphicsExtractor, font: Font) = Unit

	override fun placeX(screenWidth: Int, width: Int): Int {
		if (anchor.horizontal != HudAnchor.BOTTOM_CENTER.horizontal) return super.placeX(screenWidth, width)
		return HudLayout.clamp(screenWidth / 2 - width / 2 + offsetX, width, screenWidth)
	}

	override fun offsetXFor(anchor: HudAnchor, screenWidth: Int, width: Int, position: Int): Int {
		if (anchor.horizontal != HudAnchor.BOTTOM_CENTER.horizontal) {
			return super.offsetXFor(anchor, screenWidth, width, position)
		}
		return position - (screenWidth / 2 - width / 2)
	}

	internal fun vanillaLeft(screenWidth: Int): Int = screenWidth / 2 - vanillaOffsetX

	internal fun vanillaTop(screenHeight: Int): Int = screenHeight - vanillaOffsetY
}

internal class VanillaHudLayerTransform(private val element: VanillaHudElement) {
	private var pushed = false

	fun begin(pose: Matrix3x2fStack, screenWidth: Int, screenHeight: Int, active: Boolean): Boolean {
		if (!active || pushed) return false
		pose.pushMatrix()
		pushed = true
		try {
			val scale = element.scale
			val width = HudLayout.scaled(element.contentWidth, scale)
			val height = HudLayout.scaled(element.contentHeight, scale)
			val left = element.placeX(screenWidth, width)
			val top = HudLayout.placeOnScreen(element.anchor.vertical, screenHeight, height, element.offsetY)
			pose.translate(left.toFloat(), top.toFloat())
			pose.scale(scale, scale)
			pose.translate(-element.vanillaLeft(screenWidth).toFloat(), -element.vanillaTop(screenHeight).toFloat())
			return true
		} catch (throwable: Throwable) {
			pushed = false
			pose.popMatrix()
			throw throwable
		}
	}

	fun end(pose: Matrix3x2fStack, began: Boolean) {
		if (!began || !pushed) return
		try {
			pose.popMatrix()
		} finally {
			pushed = false
		}
	}
}

private class VanillaHudLayerWrapper(
	private val layer: VanillaHudLayer,
	private val transform: VanillaHudLayerTransform,
	private val failsafe: Failsafe
) : FabricHudElement {
	private var vanilla: FabricHudElement? = null

	fun bind(vanilla: FabricHudElement): FabricHudElement {
		this.vanilla = vanilla
		return this
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: net.minecraft.client.DeltaTracker) {
		val current = vanilla ?: return
		vanilla = null
		val transformed = if (failsafe.failed) {
			false
		} else {
			try {
				transform.begin(graphics.pose(), graphics.guiWidth(), graphics.guiHeight(), MoveableVanillaHud.moves(layer))
			} catch (throwable: Throwable) {
				MoveableVanillaHud.reportError(throwable)
				false
			}
		}
		try {
			current.extractRenderState(graphics, deltaTracker)
		} finally {
			if (transformed) {
				try {
					transform.end(graphics.pose(), true)
				} catch (throwable: Throwable) {
					failsafe.fail(layer.failureLabel, throwable)
				}
			}
		}
	}
}

internal fun vanillaHudLayerActive(
	moduleEnabled: Boolean,
	layerEnabled: Boolean,
	inSkyBlock: Boolean,
	showOutsideSkyBlock: Boolean
): Boolean = moduleEnabled && layerEnabled && (inSkyBlock || showOutsideSkyBlock)
