package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2f

class HudRuntime(private val manager: ModuleManager) {
	private val restorePoint = Matrix3x2f()

	fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val pose = graphics.pose()
		val screenWidth = graphics.guiWidth()
		val screenHeight = graphics.guiHeight()
		manager.forEachActiveHudElement { module, element ->
			restorePoint.set(pose)
			try {
				val scale = element.scale
				val width = HudLayout.scaled(element.width(font), scale)
				val height = HudLayout.scaled(element.height(font), scale)
				pose.translate(
					HudLayout.clamp(
						HudLayout.place(element.anchor.horizontal, screenWidth, width, element.offsetX),
						width,
						screenWidth
					).toFloat(),
					HudLayout.clamp(
						HudLayout.place(element.anchor.vertical, screenHeight, height, element.offsetY),
						height,
						screenHeight
					).toFloat()
				)
				pose.scale(scale, scale)
				element.render(graphics, font)
			} catch (throwable: Throwable) {
				element.markFailed()
				module.reportError(throwable)
			} finally {
				pose.set(restorePoint)
			}
		}
	}

	fun resetLayouts(): Int {
		var reset = 0
		manager.forEachHudElement { element -> if (element.resetToDeclared()) reset++ }
		return reset
	}

	fun invalidateMeasurements() {
		manager.forEachHudElement { element -> element.invalidateMeasurement() }
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
