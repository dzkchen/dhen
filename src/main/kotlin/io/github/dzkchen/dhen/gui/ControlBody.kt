package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.IntUnaryOperator

internal enum class ControlPress { IGNORED, CHANGED, RESIZED, TRACK, FOCUS, INVOKED }

internal enum class ControlKey { IGNORED, CONSUMED, COMMITTED, CANCELLED }

internal class ControlBody(built: List<SettingControl>) {
	private val controls = built.toMutableList()
	private val extentAt = IntUnaryOperator { index -> controls[index].extent }

	val height: Int
		get() = ClickGuiShell.spanTotal(controls.size, extentAt, NO_GAP)

	fun at(index: Int): SettingControl = controls[index]

	fun draw(
		graphics: GuiGraphicsExtractor,
		font: Font,
		left: Int,
		top: Int,
		width: Int,
		mouseX: Int,
		mouseY: Int,
		visibleTop: Int,
		visibleBottom: Int
	) {
		val pointerY = if (mouseX in left until left + width && mouseY in visibleTop until visibleBottom) mouseY else NO_POINTER
		var y = top
		for (i in controls.indices) {
			val control = controls[i]
			val extent = control.extent
			if (extent == 0) continue
			if (y >= visibleBottom) break
			if (y + extent > visibleTop) control.draw(graphics, font, left, y, width, pointerY)
			y += extent
		}
	}

	fun hit(record: ControlHit, host: ControlHost, left: Int, top: Int, width: Int, y: Int): SettingControl? {
		val index = indexAt(y - top)
		if (index == ClickGuiShell.NONE) return null
		return record.record(host, left, top + topOf(index), width, controls[index])
	}

	fun topOf(index: Int): Int = ClickGuiShell.spanStart(index, extentAt, NO_GAP)

	fun indexAt(localY: Int): Int = ClickGuiShell.spanAt(localY, controls.size, extentAt, NO_GAP)

	fun invalidateMeasurements() {
		for (i in controls.indices) controls[i].invalidateMeasurement()
	}
}

internal interface ControlHost {
	fun revealSpan(screenTop: Int, extent: Int)
}

internal class ControlHit {
	private var host: ControlHost? = null

	var left = 0
		private set
	var top = 0
		private set
	var width = 0
		private set

	fun record(host: ControlHost, left: Int, top: Int, width: Int, control: SettingControl): SettingControl {
		this.host = host
		this.left = left
		this.top = top
		this.width = width
		return control
	}

	fun reveal(extent: Int) {
		host?.revealSpan(top, extent)
	}
}
