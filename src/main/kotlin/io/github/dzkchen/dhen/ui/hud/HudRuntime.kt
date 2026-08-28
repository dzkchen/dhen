package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2f
import org.slf4j.LoggerFactory

class HudRuntime(
	private val manager: ModuleManager,
	internal val coreElements: List<HudElement> = emptyList()
) {
	private val restorePoint = Matrix3x2f()
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val screenWidth = graphics.guiWidth()
		val screenHeight = graphics.guiHeight()
		manager.forEachActiveHudElement { module, element ->
			renderElement(graphics, font, screenWidth, screenHeight, module, element)
		}
		for (index in coreElements.indices) {
			val element = coreElements[index]
			if (element.isActive) renderElement(graphics, font, screenWidth, screenHeight, null, element)
		}
	}

	private fun renderElement(
		graphics: GuiGraphicsExtractor,
		font: Font,
		screenWidth: Int,
		screenHeight: Int,
		module: Module?,
		element: HudElement
	) {
		val pose = graphics.pose()
		restorePoint.set(pose)
		try {
			if (!element.hasContent) return
			val scale = element.scale
			val width = HudLayout.scaled(element.width(font), scale)
			val height = HudLayout.scaled(element.height(font, screenHeight), scale)
			val x = HudLayout.placeOnScreen(element.anchor.horizontal, screenWidth, width, element.offsetX)
			val y = element.placeY(screenHeight, height)
			if (element.background) {
				drawPlate(graphics, x, y, width, height, scale, screenWidth, screenHeight)
			}
			pose.translate(x.toFloat(), y.toFloat())
			pose.scale(scale, scale)
			element.render(graphics, font)
		} catch (throwable: Throwable) {
			element.markFailed()
			if (module == null) log.error("Core HUD element '{}' failed", element.name, throwable)
			else module.reportError(throwable)
		} finally {
			pose.set(restorePoint)
		}
	}

	private fun drawPlate(
		graphics: GuiGraphicsExtractor,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		scale: Float,
		screenWidth: Int,
		screenHeight: Int
	) {
		val pad = HudLayout.platePad(scale)
		RoundedGui.fill(
			graphics,
			maxOf(0, x - pad),
			maxOf(0, y - pad),
			minOf(screenWidth, x + width + pad),
			minOf(screenHeight, y + height + pad),
			HudLayout.plateRadius(scale),
			GlassGui.surface()
		)
	}

	fun resetLayouts(): Int {
		var reset = 0
		manager.forEachHudElement { element -> if (element.resetToDeclared()) reset++ }
		for (index in coreElements.indices) if (coreElements[index].resetToDeclared()) reset++
		return reset
	}

	fun invalidateMeasurements() {
		manager.forEachHudElement { element -> element.invalidateMeasurement() }
		for (index in coreElements.indices) coreElements[index].invalidateMeasurement()
	}
}

internal inline fun ModuleManager.forEachHudElement(action: (HudElement) -> Unit) {
	val modules = ordered
	for (moduleIndex in modules.indices) {
		val elements = modules[moduleIndex].hudElements
		for (elementIndex in elements.indices) action(elements[elementIndex])
	}
}

internal inline fun ModuleManager.forEachActiveHudElement(action: (Module, HudElement) -> Unit) {
	val modules = ordered
	for (moduleIndex in modules.indices) {
		val module = modules[moduleIndex]
		if (!module.enabled) continue
		val elements = module.hudElements
		for (elementIndex in elements.indices) {
			if (!module.enabled) break
			val element = elements[elementIndex]
			if (element.isActive) action(module, element)
		}
	}
}
