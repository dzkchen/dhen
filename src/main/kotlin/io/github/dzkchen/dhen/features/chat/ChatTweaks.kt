package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.command.ChatHiderCommands
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.InputQuirks
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object ChatTweaks : Module(
	name = "Chat Tweaks",
	category = Category.CHAT,
	description = "Copies chat lines, hides noise, and answers NPC dialogues."
), ChatHiderCommands {
	private var ctrlClickToCopy by BooleanSetting(
		"Ctrl Click to Copy",
		default = true,
		description = "Ctrl and click a chat line to copy it to the clipboard."
	)

	private var copyMode by SelectorSetting(
		"Copy Mode",
		ENTIRE_MESSAGE,
		listOf(ENTIRE_MESSAGE, SINGLE_LINE),
		description = "What a copy takes; holding shift swaps it for one click."
	).withDependency { ctrlClickToCopy }

	private var copyButton by SelectorSetting(
		"Mouse Button",
		LEFT,
		listOf(LEFT, MIDDLE, RIGHT),
		description = "The mouse button that copies while ctrl is held."
	).withDependency { ctrlClickToCopy }

	private var copyNotification by BooleanSetting(
		"Copy Notification",
		default = true,
		description = "Shows a notification confirming what was copied."
	).withDependency { ctrlClickToCopy }

	private var removeUselessMessages by BooleanSetting(
		"Remove Useless Messages",
		default = true,
		description = "Hides listed and custom chat lines, and collapses repeated blank ones."
	)

	private var autoDialogue by BooleanSetting(
		"Auto Dialogue",
		description = "Picks the first option of an NPC dialogue for you."
	)

	private var hideImplosion by BooleanSetting(
		"Hide Implosion Messages",
		description = "Hides the Implosion damage line."
	)

	private var storedHiders by StringSetting("Hidden Lines").hide()

	private val hider = ChatHider { storedHiders }

	init {
		on<MouseInputEvent> { copied(it) }
		on<ChatReceiveEvent>(AFTER_PRODUCERS) { chatted(it) }
	}

	override fun onDisabled() {
		hider.forget()
	}

	override fun add(pattern: String): String {
		val current = hider.patterns()
		if (pattern in current) return "'$pattern' is already hidden."
		if (!validPattern(pattern)) return "'$pattern' is not a valid regular expression."
		storedHiders = (current + pattern).joinToString(HIDER_SEPARATOR)
		return "Hiding chat lines matching '$pattern'."
	}

	override fun remove(pattern: String): String {
		val current = hider.patterns()
		val kept = current - pattern
		if (kept.size == current.size) return "No hidden pattern reads '$pattern'."
		storedHiders = kept.joinToString(HIDER_SEPARATOR)
		return "Stopped hiding chat lines matching '$pattern'."
	}

	override fun list(): List<String> {
		val current = hider.patterns()
		if (current.isEmpty()) return listOf("Nothing is hidden yet. Add a rule with /dhen chathider add <regex>.")
		return current.map { "Hiding '$it'" }
	}

	override fun patterns(): List<String> = hider.patterns()

	private fun copied(event: MouseInputEvent) {
		if (!ctrlClickToCopy || event.action != InputAction.PRESS || event.button != wantedButton()) return
		if (event.modifiers and COPY_MODIFIERS == 0) return
		val client = Minecraft.getInstance()
		val shifted = event.modifiers and GLFW.GLFW_MOD_SHIFT != 0
		val text = hoveredChatText(client, wholeEntry = (copyMode == ENTIRE_MESSAGE) != shifted)
		if (text.isBlank()) return
		client.keyboardHandler.setClipboard(text)
		if (copyNotification) Notifications.push(COPY_ICON, COPY_TITLE, text)
		event.cancelled = true
	}

	private fun wantedButton(): Int = when (copyButton) {
		MIDDLE -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE
		RIGHT -> GLFW.GLFW_MOUSE_BUTTON_RIGHT
		else -> GLFW.GLFW_MOUSE_BUTTON_LEFT
	}

	private fun chatted(event: ChatReceiveEvent) {
		val stripped = event.stripped
		if (removeUselessMessages && hider.hides(stripped)) {
			explosiveShotSummary(stripped)?.let(Dhen::announce)
			event.cancelled = true
			return
		}
		if (hideImplosion && IMPLOSION.containsMatchIn(event.styled.replace(RESET_CODE, ""))) {
			event.cancelled = true
			return
		}
		dialogue(stripped, event.text)
	}

	private fun dialogue(stripped: String, text: Component) {
		if (!autoDialogue || !SkyBlockLocation.inSkyBlock) return
		val screen = Minecraft.getInstance().gui.screen()
		if (screen != null && screen !is ChatScreen) return
		if (BARBARIANS_MAGES in stripped || !stripped.startsWith(DIALOGUE_PREFIX)) return
		val command = (text.siblings.firstOrNull()?.style?.clickEvent as? ClickEvent.RunCommand)?.command ?: return
		inTicks(DIALOGUE_TICKS) {
			Minecraft.getInstance().connection?.sendCommand(command.removePrefix(SLASH))
		}
	}

	private const val ENTIRE_MESSAGE = "Entire Message"
	private const val SINGLE_LINE = "Single Line"
	private const val LEFT = "Left"
	private const val MIDDLE = "Middle"
	private const val RIGHT = "Right"
	private const val COPY_ICON = "✔"
	private const val COPY_TITLE = "Copied to clipboard"
	private const val RESET_CODE = "§r"
	private const val BARBARIANS_MAGES = "[BARBARIANS] [MAGES]"
	private const val DIALOGUE_PREFIX = "Select an option: "
	private const val SLASH = "/"
	private const val DIALOGUE_TICKS = 14

	private val COPY_MODIFIERS = GLFW.GLFW_MOD_CONTROL or InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER

	private val IMPLOSION = Regex("""^§7Your Implosion hit (.*?) §7damage\.$""")
}
