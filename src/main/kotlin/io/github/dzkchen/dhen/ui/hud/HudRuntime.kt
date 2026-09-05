package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.joml.Matrix3x2f
import org.slf4j.LoggerFactory

class HudRuntime(
	private val manager: ModuleManager,
	internal val coreElements: List<HudElement> = emptyList()
) {
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	internal var canvas: HudCanvas? = null
		private set

	internal fun install(bus: EventBus, persist: () -> Unit) {
		uninstall()
		val built = HudCanvas(manager, coreElements, persist)
		canvas = built
		MenuHudEditor.install(bus, built, this)
	}

	internal fun uninstall() {
		MenuHudEditor.uninstall()
		canvas = null
	}

	fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val screenWidth = graphics.guiWidth()
		val screenHeight = graphics.guiHeight()
		val overMenu = Minecraft.getInstance().gui.screen() is AbstractContainerScreen<*>
		if (overMenu && MenuHudEditor.editing) return
		manager.forEachActiveHudElement { module, element ->
			if (overMenu && element.inMenus) return@forEachActiveHudElement
			renderElement(graphics, font, screenWidth, screenHeight, module, element)
		}
		for (index in coreElements.indices) {
			val element = coreElements[index]
			if (!element.isActive || overMenu && element.inMenus) continue
			renderElement(graphics, font, screenWidth, screenHeight, null, element)
		}
	}

	internal fun renderOverMenu(graphics: GuiGraphicsExtractor, font: Font, everything: Boolean) {
		val screenWidth = graphics.guiWidth()
		val screenHeight = graphics.guiHeight()
		manager.forEachActiveHudElement { module, element ->
			if (!everything && !element.inMenus) return@forEachActiveHudElement
			renderElement(graphics, font, screenWidth, screenHeight, module, element)
		}
		for (index in coreElements.indices) {
			val element = coreElements[index]
			if (!element.isActive || !everything && !element.inMenus) continue
			renderElement(graphics, font, screenWidth, screenHeight, null, element)
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
		val failure = drawHudElement(graphics, font, screenWidth, screenHeight, element, gated = true) ?: return
		if (module == null) log.error("Core HUD element '{}' failed", element.name, failure)
		else module.reportError(failure)
	}

	fun resetLayouts(): Int {
		var reset = 0
		manager.forEachHudElement { _, element -> if (element.resetToDeclared()) reset++ }
		for (index in coreElements.indices) if (coreElements[index].resetToDeclared()) reset++
		return reset
	}

	fun invalidateMeasurements() {
		manager.forEachHudElement { module, element ->
			try {
				element.invalidateMeasurement()
			} catch (throwable: Throwable) {
				module.reportError(throwable)
			}
		}
		for (index in coreElements.indices) coreElements[index].invalidateMeasurement()
	}
}

private val restorePoint = Matrix3x2f()

internal fun drawHudElement(
	graphics: GuiGraphicsExtractor,
	font: Font,
	screenWidth: Int,
	screenHeight: Int,
	element: HudElement,
	gated: Boolean
): Throwable? {
	val pose = graphics.pose()
	restorePoint.set(pose)
	try {
		if (gated && !element.hasContent) return null
		val scale = element.scale
		val width = HudLayout.scaled(element.width(font), scale)
		val height = HudLayout.scaled(element.height(font, screenHeight), scale)
		val x = element.placeX(screenWidth, width)
		val y = element.placeY(screenHeight, height)
		if (element.background) {
			drawPlate(graphics, x, y, width, height, scale, screenWidth, screenHeight)
		}
		pose.translate(x.toFloat(), y.toFloat())
		pose.scale(scale, scale)
		element.render(graphics, font)
	} catch (throwable: Throwable) {
		element.markFailed()
		return throwable
	} finally {
		pose.set(restorePoint)
	}
	return null
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

internal inline fun ModuleManager.forEachHudElement(action: (Module, HudElement) -> Unit) {
	val modules = ordered
	for (moduleIndex in modules.indices) {
		val module = modules[moduleIndex]
		val elements = module.hudElements
		for (elementIndex in elements.indices) action(module, elements[elementIndex])
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
