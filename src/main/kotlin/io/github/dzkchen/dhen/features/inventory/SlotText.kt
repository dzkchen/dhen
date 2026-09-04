package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindScreenPolicy
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.SLOT_CORNERS
import io.github.dzkchen.dhen.gui.slotCornerText
import io.github.dzkchen.dhen.input.keyHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.Arrays
import java.util.regex.Matcher
import java.util.regex.Pattern

internal class SlotAdder(
	val label: String,
	val description: String,
	titlePattern: String? = null,
	private val write: (SlotScribe) -> Unit
) {
	private val title = titlePattern?.let { Pattern.compile(it).matcher("") }

	fun matches(screenTitle: String): Boolean = title == null || title.reset(screenTitle).matches()

	fun writeInto(scribe: SlotScribe) = write(scribe)
}

internal class SlotScribe {
	var stack: ItemStack = ItemStack.EMPTY
		private set

	var item: SkyBlockItem = SkyBlockItem.NONE
		private set

	var slotId = 0
		private set

	var name = ""
		private set

	private var styled: List<Component> = emptyList()
	private val plain = ArrayList<String>()
	private val texts = arrayOfNulls<String>(SLOT_CORNERS)
	private val inks = IntArray(SLOT_CORNERS)

	val loreSize: Int get() = plain.size

	fun begin(stack: ItemStack, slotId: Int) {
		this.stack = stack
		this.slotId = slotId
		item = SkyBlockItems.of(stack)
		name = withoutCodes(stack.hoverName.string)
		styled = SkyBlockItems.lore(stack)
		plain.clear()
		for (line in styled) plain += withoutCodes(line.string)
		Arrays.fill(texts, null)
	}

	fun lore(index: Int): String = if (index in plain.indices) plain[index] else ""

	fun loreStyled(index: Int): Component? = if (index in styled.indices) styled[index] else null

	fun loreMatch(matcher: Matcher): Matcher? {
		for (index in plain.indices) if (matcher.reset(plain[index]).matches()) return matcher
		return null
	}

	fun loreFind(matcher: Matcher, index: Int): Matcher? {
		if (index !in plain.indices) return null
		return if (matcher.reset(plain[index]).find()) matcher else null
	}

	fun loreHas(fragment: String): Boolean {
		for (index in plain.indices) if (plain[index].contains(fragment)) return true
		return false
	}

	fun loreIs(line: String): Boolean {
		for (index in plain.indices) if (plain[index] == line) return true
		return false
	}

	fun write(corner: Int, text: String, ink: Int) {
		if (text.isEmpty() || texts[corner] != null) return
		texts[corner] = text
		inks[corner] = ink
	}

	fun textAt(corner: Int): String? = texts[corner]

	fun inkAt(corner: Int): Int = inks[corner]
}

object SlotText : Module(
	name = "Slot Text",
	category = Category.INVENTORY,
	description = "Writes levels, tiers and counts in the corners of the slots of SkyBlock menus."
) {
	private val modeSetting = SelectorSetting(
		"Show",
		ALWAYS,
		listOf(ALWAYS, HOLD_TO_SHOW, PRESS_TO_TOGGLE, HOLD_TO_HIDE),
		description = "When the corner text is drawn, and what the toggle key does."
	)

	private val keySetting = KeybindSetting(
		"Toggle Key",
		GLFW.GLFW_KEY_LEFT_ALT,
		"Held or pressed to show or hide the corner text, following the Show setting.",
		KeybindScreenPolicy.NON_TEXT_SCREEN
	).onPress { toggled = !toggled }

	private val adderSettings = Array(SLOT_ADDERS.size) { index ->
		BooleanSetting(SLOT_ADDERS[index].label, default = true, description = SLOT_ADDERS[index].description)
	}

	private val scribe = SlotScribe()
	private val matched = IntArray(SLOT_ADDERS.size)
	private val stacks = arrayOfNulls<ItemStack>(MAX_SLOTS)
	private val texts = arrayOfNulls<String>(MAX_SLOTS * SLOT_CORNERS)
	private val inks = IntArray(MAX_SLOTS * SLOT_CORNERS)

	private var host: AbstractContainerScreen<*>? = null
	private var matchedCount = 0
	private var toggled = true

	init {
		registerSetting(modeSetting)
		registerSetting(keySetting)
		for (setting in adderSettings) registerSetting(setting)

		on<SlotRenderEvent.Post> { drawn(it) }
		on<GuiCloseEvent> { forget() }
	}

	override fun onEnabled() {
		toggled = true
		forget()
	}

	override fun onDisabled() = forget()

	override fun onReset() = forget()

	internal fun forget() {
		host = null
		matchedCount = 0
		Arrays.fill(stacks, null)
	}

	internal fun visible(): Boolean = when (modeSetting.value) {
		HOLD_TO_SHOW -> keyHeld(keySetting.code)
		HOLD_TO_HIDE -> !keyHeld(keySetting.code)
		PRESS_TO_TOGGLE -> toggled
		else -> true
	}

	private fun drawn(event: SlotRenderEvent.Post) {
		if (!SkyBlockLocation.inSkyBlock || !visible()) return
		if (event.screen !== host) rematch(event.screen)
		if (matchedCount == 0) return
		val slot = event.slot
		val index = slot.index
		if (index < 0 || index >= MAX_SLOTS) return
		val stack = slot.item
		if (stack.isEmpty) return
		if (stacks[index] !== stack) compute(index, stack)
		val base = index * SLOT_CORNERS
		for (corner in 0 until SLOT_CORNERS) {
			val text = texts[base + corner] ?: continue
			slotCornerText(event.graphics, text, slot.x, slot.y, corner, inks[base + corner])
		}
	}

	private fun rematch(screen: AbstractContainerScreen<*>) {
		host = screen
		matchedCount = 0
		Arrays.fill(stacks, null)
		val title = withoutCodes(screen.title.string)
		for (index in SLOT_ADDERS.indices) {
			if (!adderSettings[index].on || !SLOT_ADDERS[index].matches(title)) continue
			matched[matchedCount++] = index
		}
	}

	private fun compute(index: Int, stack: ItemStack) {
		stacks[index] = stack
		scribe.begin(stack, index)
		for (position in 0 until matchedCount) SLOT_ADDERS[matched[position]].writeInto(scribe)
		val base = index * SLOT_CORNERS
		for (corner in 0 until SLOT_CORNERS) {
			texts[base + corner] = scribe.textAt(corner)
			inks[base + corner] = scribe.inkAt(corner)
		}
	}

	internal const val ALWAYS = "Always"
	internal const val HOLD_TO_SHOW = "Hold to Show"
	internal const val PRESS_TO_TOGGLE = "Press to Toggle"
	internal const val HOLD_TO_HIDE = "Hold to Hide"

	private const val MAX_SLOTS = 128
}
