package io.github.dzkchen.dhen.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.input.InputQuirks
import org.lwjgl.glfw.GLFW

internal fun keyHeld(code: Int): Boolean =
	code != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(Minecraft.getInstance().window, code)

internal fun shiftHeld(): Boolean =
	keyHeld(GLFW.GLFW_KEY_LEFT_SHIFT) || keyHeld(GLFW.GLFW_KEY_RIGHT_SHIFT)

internal fun altHeld(): Boolean =
	keyHeld(GLFW.GLFW_KEY_LEFT_ALT) || keyHeld(GLFW.GLFW_KEY_RIGHT_ALT)

internal fun controlHeld(): Boolean =
	keyHeld(GLFW.GLFW_KEY_LEFT_CONTROL) || keyHeld(GLFW.GLFW_KEY_RIGHT_CONTROL)

internal fun superHeld(): Boolean =
	keyHeld(GLFW.GLFW_KEY_LEFT_SUPER) || keyHeld(GLFW.GLFW_KEY_RIGHT_SUPER)

internal fun platformModifierHeld(): Boolean =
	if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) superHeld() else controlHeld()

internal fun platformModifierName(): String =
	if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) "Command" else "Control"
