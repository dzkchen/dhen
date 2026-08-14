package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.navigation.ScreenRectangle
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc

internal object GuiPose {
	private val UNTRANSFORMED: Matrix3x2fc = Matrix3x2f()

	fun of(stack: Matrix3x2fc): Matrix3x2fc =
		if (RoundedQuad.isUntransformed(stack)) UNTRANSFORMED else Matrix3x2f(stack)

	fun clip(area: ScreenRectangle, pose: Matrix3x2fc, scissor: ScreenRectangle?): ScreenRectangle? {
		val mapped = if (pose === UNTRANSFORMED) area else area.transformMaxBounds(pose)
		return if (scissor == null) mapped else scissor.intersection(mapped)
	}
}
