package io.github.dzkchen.dhen.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

internal fun keyHeld(code: Int): Boolean =
	code != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(Minecraft.getInstance().window, code)

internal fun shiftHeld(): Boolean =
	keyHeld(GLFW.GLFW_KEY_LEFT_SHIFT) || keyHeld(GLFW.GLFW_KEY_RIGHT_SHIFT)

internal fun controlHeld(): Boolean =
	keyHeld(GLFW.GLFW_KEY_LEFT_CONTROL) || keyHeld(GLFW.GLFW_KEY_RIGHT_CONTROL)
