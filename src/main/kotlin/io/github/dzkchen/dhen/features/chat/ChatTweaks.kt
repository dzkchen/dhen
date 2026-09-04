package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.command.ChatHiderCommands
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ROW_SEPARATOR
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.InputQuirks
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.TranslatableContents
import org.apache.commons.lang3.StringUtils
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
	).withDependency { ctrlClickToCopy || rightClickMenu }

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

	private var adminOutput by SelectorSetting(
		"Admin Output",
		ADMIN_SHOWN,
		listOf(ADMIN_SHOWN, ADMIN_PLAYERS_ONLY, ADMIN_HIDDEN),
		description = "Filters operator and command block output out of chat."
	)

	private var hideSigningWarning by BooleanSetting(
		"Hide Signing Warning",
		default = true,
		description = "Suppresses the insecure chat warning shown when joining a server."
	)

	private var hideMessageIndicators by BooleanSetting(
		"Hide Message Indicators",
		default = true,
		description = "Removes the coloured bar and icon vanilla marks chat lines with."
	)

	private var longCommands by BooleanSetting(
		"Long Commands",
		default = true,
		description = "Lifts the 256 character limit while the chat box holds a command."
	)

	private var rightClickMenu by BooleanSetting(
		"Right Click Menu",
		default = true,
		description = "Right click a chat line for copy and delete actions."
	)

	private var rightClickCopy by BooleanSetting(
		"Right Click Copies",
		description = "Skips the menu and copies the line straight to the clipboard."
	).withDependency { rightClickMenu }

	private var commandTooltip by BooleanSetting(
		"Command Tooltip",
		default = true,
		description = "Adds the command a clickable chat line would run to its hover tooltip."
	)

	private var storedHiders by StringSetting("Hidden Lines").hide()

	private var tooltipSource: Component? = null

	private var tooltipCommand = ""

	private var tooltipShown: Component? = null

	private val hider = ChatHider { storedHiders }

	init {
		on<MouseInputEvent> { copied(it) }
		on<ChatReceiveEvent>(AFTER_PRODUCERS) { chatted(it) }
	}

	override fun onDisabled() {
		hider.forget()
		ChatContextMenu.closed()
	}

	override fun add(pattern: String): String {
		val current = hider.patterns()
		if (pattern in current) return "'$pattern' is already hidden."
		if (!validPattern(pattern)) return "'$pattern' is not a valid regular expression."
		storedHiders = (current + pattern).joinToString(ROW_SEPARATOR)
		return "Hiding chat lines matching '$pattern'."
	}

	override fun remove(pattern: String): String {
		val current = hider.patterns()
		val kept = current - pattern
		if (kept.size == current.size) return "No hidden pattern reads '$pattern'."
		storedHiders = kept.joinToString(ROW_SEPARATOR)
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
		announceCopy(text)
		event.cancelled = true
	}

	private fun wantedButton(): Int = when (copyButton) {
		MIDDLE -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE
		RIGHT -> GLFW.GLFW_MOUSE_BUTTON_RIGHT
		else -> GLFW.GLFW_MOUSE_BUTTON_LEFT
	}

	internal val menuOnRightClick: Boolean
		get() = enabled && rightClickMenu

	internal val copyOnRightClick: Boolean
		get() = menuOnRightClick && rightClickCopy

	internal fun announceCopy(text: String) {
		if (copyNotification) Notifications.push(COPY_ICON, COPY_TITLE, text)
	}

	@JvmStatic
	fun hidesSigningWarning(): Boolean = enabled && hideSigningWarning

	@JvmStatic
	fun hidesMessageTag(): Boolean = enabled && hideMessageIndicators

	@JvmStatic
	fun chatInputLimit(typed: String): Int =
		if (liftsLimit(typed)) Int.MAX_VALUE else VANILLA_CHAT_LIMIT

	@JvmStatic
	fun untrimmedCommand(message: String): String? =
		if (liftsLimit(message) && message.startsWith(SLASH)) StringUtils.normalizeSpace(message.trim()) else null

	@JvmStatic
	fun withCommandTooltip(hover: Component, style: Style): Component {
		if (!enabled || !commandTooltip) return hover
		val command = (style.clickEvent as? ClickEvent.RunCommand)?.command ?: return hover
		val shown = tooltipShown
		if (shown != null && tooltipSource === hover && tooltipCommand == command) return shown
		val built = hover.copy()
			.append(TOOLTIP_GAP)
			.append(DhenType.overWorld(TOOLTIP_PREFIX + command, ChatFormatting.GRAY))
		tooltipSource = hover
		tooltipCommand = command
		tooltipShown = built
		return built
	}

	private fun liftsLimit(typed: String): Boolean =
		enabled && longCommands && (typed.isEmpty() || typed.startsWith(SLASH))

	internal fun hidesAdminOutput(text: Component): Boolean {
		if (adminOutput == ADMIN_SHOWN) return false
		val contents = text.contents as? TranslatableContents ?: return false
		if (contents.key != ADMIN_KEY) return false
		if (adminOutput == ADMIN_HIDDEN) return true
		val sender = contents.args.firstOrNull() ?: return false
		return sender == COMMAND_BLOCK_SENDER || sender == Component.literal(COMMAND_BLOCK_SENDER)
	}

	private fun chatted(event: ChatReceiveEvent) {
		if (hidesAdminOutput(event.text)) {
			event.cancelled = true
			return
		}
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
	private const val ADMIN_SHOWN = "Shown"
	private const val ADMIN_PLAYERS_ONLY = "Only Players"
	private const val ADMIN_HIDDEN = "Hidden"
	private const val ADMIN_KEY = "chat.type.admin"
	private const val COMMAND_BLOCK_SENDER = "@"
	private const val VANILLA_CHAT_LIMIT = 256
	private const val TOOLTIP_GAP = "\n\n"
	private const val TOOLTIP_PREFIX = "Command: "

	private val COPY_MODIFIERS = GLFW.GLFW_MOD_CONTROL or InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER

	private val IMPLOSION = Regex("""^§7Your Implosion hit (.*?) §7damage\.$""")
}
