package io.github.dzkchen.dhen.features.visual

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.config.KeybindSetting
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.min

internal class PetWheelLayout {
	var referenceScale = 1f
		private set
	var referenceWidth = REFERENCE_WIDTH
		private set
	var referenceHeight = REFERENCE_HEIGHT
		private set
	var centerX = REFERENCE_WIDTH / 2f
		private set
	var centerY = REFERENCE_HEIGHT / 2f - CENTER_Y_OFFSET
		private set
	var innerRadius = 0f
		private set
	var outerRadius = 0f
		private set
	var segmentCount = 0
		private set
	var segmentAngle = 0.0
		private set

	fun update(guiWidth: Int, guiHeight: Int, scalePercent: Double, segmentCount: Int) {
		require(guiWidth > 0 && guiHeight > 0)
		referenceScale = min(guiWidth / REFERENCE_WIDTH, guiHeight / REFERENCE_HEIGHT)
		referenceWidth = guiWidth / referenceScale
		referenceHeight = guiHeight / referenceScale
		centerX = referenceWidth / 2f
		centerY = referenceHeight / 2f - CENTER_Y_OFFSET
		val desiredRadius = BASE_OUTER_RADIUS * (scalePercent.coerceIn(MIN_SCALE, MAX_SCALE) / 100.0).toFloat()
		val heightLimit = (referenceHeight - RESERVED_HEIGHT) / 2f
		val widthLimit = (referenceWidth - RESERVED_WIDTH) / 2f
		val maximumRadius = min(heightLimit, widthLimit).coerceAtLeast(MIN_OUTER_RADIUS)
		outerRadius = min(desiredRadius, maximumRadius)
		innerRadius = outerRadius * INNER_RADIUS_RATIO
		this.segmentCount = segmentCount.coerceIn(0, PetWheelCache.PETS_PER_PAGE)
		segmentAngle = if (this.segmentCount == 0) 0.0 else PI * 2.0 / this.segmentCount
	}

	fun hoveredIndex(pointerX: Double, pointerY: Double): Int {
		if (segmentCount == 0) return NO_INDEX
		val x = pointerX / referenceScale - centerX
		val y = pointerY / referenceScale - centerY
		if (x * x + y * y <= innerRadius * innerRadius) return NO_INDEX
		val clockwise = (atan2(y, x) + PI / 2.0 + segmentAngle / 2.0) / segmentAngle
		return Math.floorMod(floor(clockwise).toInt(), segmentCount)
	}

	companion object {
		const val NO_INDEX = -1
		private const val REFERENCE_WIDTH = 960f
		private const val REFERENCE_HEIGHT = 540f
		private const val CENTER_Y_OFFSET = 8f
		private const val BASE_OUTER_RADIUS = 138f
		private const val INNER_RADIUS_RATIO = 0.55f
		private const val RESERVED_HEIGHT = 96f
		private const val RESERVED_WIDTH = 220f
		private const val MIN_OUTER_RADIUS = 82f
		private const val MIN_SCALE = 70.0
		private const val MAX_SCALE = 135.0
	}
}

internal object PetWheelInput {
	fun resolve(
		code: Int,
		mouse: Boolean,
		visibleCount: Int,
		useHotbarBinds: Boolean,
		petSlotBinds: Array<KeybindSetting>,
		hotbarBinds: Array<KeyMapping>
	): Int {
		val limit = visibleCount.coerceIn(0, PetWheelCache.PETS_PER_PAGE)
		if (useHotbarBinds) {
			val input = if (mouse) InputConstants.Type.MOUSE.getOrCreate(code) else InputConstants.Type.KEYSYM.getOrCreate(code)
			var index = 0
			while (index < limit && index < hotbarBinds.size) {
				if (hotbarBinds[index].matches(input)) return index
				index++
			}
			return PetWheelLayout.NO_INDEX
		}
		var index = 0
		while (index < limit && index < petSlotBinds.size) {
			val binding = petSlotBinds[index].code
			val bindingIsMouse = binding in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST
			if (binding == code && bindingIsMouse == mouse) return index
			index++
		}
		return PetWheelLayout.NO_INDEX
	}
}

internal class PetWheelDebounce {
	private var hasAction = false
	private var lastActionAt = 0L

	fun accept(now: Long): Boolean {
		if (hasAction && now - lastActionAt < ACTION_DELAY_MS) return false
		hasAction = true
		lastActionAt = now
		return true
	}

	fun reset() {
		hasAction = false
		lastActionAt = 0L
	}

	companion object {
		const val ACTION_DELAY_MS = 300L
	}
}
