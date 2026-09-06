package io.github.dzkchen.dhen.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.regex.Matcher
import java.util.regex.Pattern

internal const val NO_MENU_SLOT = -1
internal const val NO_MENU_BIND = -1

internal class MenuPages(pattern: String, private val pageless: Boolean = false) {
	private val matcher = Pattern.compile(pattern).matcher("")

	var current: Int = 1
		private set

	var total: Int = 1
		private set

	fun matches(title: Component): Boolean {
		if (!matcher.reset(withoutCodes(title.string)).find()) return false
		var group = 1
		while (group < matcher.groupCount()) {
			val first = matcher.group(group)?.toIntOrNull()
			val last = matcher.group(group + 1)?.toIntOrNull()
			if (first != null && last != null) {
				current = first
				total = last
				return true
			}
			group += 2
		}
		if (!pageless) return false
		current = 1
		total = 1
		return true
	}
}

internal object MenuKeybinds {
	private var lastOwner: Any? = null
	private var lastCode = GLFW.GLFW_KEY_UNKNOWN
	private var lastWasMouse = false
	private var lastInputAt = 0L

	fun menuKey(name: String, default: Int, description: String): KeybindSetting =
		KeybindSetting(name, default, description, KeybindScreenPolicy.NON_TEXT_SCREEN)

	fun heldDown(owner: Any, code: Int, mouse: Boolean, now: Long): Boolean {
		val held = owner === lastOwner && code == lastCode && mouse == lastWasMouse && now - lastInputAt < REPEAT_GUARD_MS
		lastOwner = owner
		lastCode = code
		lastWasMouse = mouse
		lastInputAt = now
		return held
	}

	fun click(screen: AbstractContainerScreen<*>, slot: Int) =
		clickSlot(screen.menu, slot, GLFW.GLFW_MOUSE_BUTTON_LEFT, ContainerInput.PICKUP)

	fun bound(setting: KeybindSetting, code: Int, mouse: Boolean): Boolean =
		setting.isBound && setting.code == code && (setting.code in MOUSE_BUTTONS) == mouse

	fun boundIndex(code: Int, mouse: Boolean, settings: Array<KeybindSetting>): Int {
		for (index in settings.indices) if (bound(settings[index], code, mouse)) return index
		return NO_MENU_BIND
	}

	fun hotbarIndex(code: Int, mouse: Boolean, limit: Int): Int =
		hotbarIndex(code, mouse, limit, Minecraft.getInstance().options.keyHotbarSlots)

	fun hotbarIndex(code: Int, mouse: Boolean, limit: Int, slots: Array<KeyMapping>): Int {
		val input = if (mouse) InputConstants.Type.MOUSE.getOrCreate(code)
		else InputConstants.Type.KEYSYM.getOrCreate(code)
		var index = 0
		while (index < limit && index < slots.size) {
			if (slots[index].matches(input)) return index
			index++
		}
		return NO_MENU_BIND
	}

	fun stackAt(screen: AbstractContainerScreen<*>, slot: Int): ItemStack =
		screen.menu.slots.getOrNull(slot)?.item ?: ItemStack.EMPTY

	fun lorePrompt(stack: ItemStack, prompt: String): Boolean {
		if (stack.isEmpty) return false
		val lore = SkyBlockItems.lore(stack)
		for (index in lore.indices) if (withoutCodes(lore[index].string).contains(prompt)) return true
		return false
	}

	fun namePrompt(matcher: Matcher, stack: ItemStack): Boolean =
		!stack.isEmpty && matcher.reset(withoutCodes(stack.hoverName.string)).matches()

	private const val REPEAT_GUARD_MS = 300L
	private val MOUSE_BUTTONS = GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST
}
