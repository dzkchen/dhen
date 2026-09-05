package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.TabWidgetUpdateEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.EntityHighlights
import io.github.dzkchen.dhen.render.NO_HIGHLIGHT
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.world.entity.Entity
import java.util.Locale
import java.util.regex.Pattern

internal val CHAT_COLORS: List<ChatFormatting> =
	ChatFormatting.entries.filter { TextColor.fromLegacyFormat(it) != null }

internal val CHAT_COLOR_NAMES: List<String> = CHAT_COLORS.map { formatting ->
	formatting.name.lowercase(Locale.ROOT).split('_').joinToString(" ") { word ->
		word.replaceFirstChar(Char::uppercaseChar)
	}
}

internal fun highlightNames(line: String, names: Set<String>, color: String): String {
	var rewritten = line
	for (name in names) {
		var from = 0
		while (true) {
			val at = rewritten.indexOf(name, from, ignoreCase = true)
			if (at < 0) break
			val end = at + name.length
			if (wholeName(rewritten, at, end)) {
				rewritten = rewritten.substring(0, at) + color + rewritten.substring(at, end) +
					ChatFormatting.RESET + rewritten.substring(end)
				from = end + color.length + ChatFormatting.RESET.toString().length
			} else {
				from = end
			}
		}
	}
	return rewritten
}

private fun wholeName(line: String, start: Int, end: Int): Boolean =
	openAt(line, start) && (end == line.length || !nameCharacter(line[end]))

private fun openAt(line: String, start: Int): Boolean = when {
	start == 0 -> true
	!nameCharacter(line[start - 1]) -> true
	else -> start >= 2 && line[start - 2] == COLOR_PREFIX
}

private fun nameCharacter(character: Char): Boolean = character == '_' || character.isLetterOrDigit()

private const val COLOR_PREFIX = '\u00a7'

internal fun chatColorNamed(name: String): ChatFormatting {
	val index = CHAT_COLOR_NAMES.indexOfFirst { it.equals(name, ignoreCase = true) }
	return CHAT_COLORS[if (index < 0) 0 else index]
}

object MarkedPlayers : Module(
	name = "Marked Players",
	category = Category.VISUAL,
	description = "Highlights the players you name, in the world and in chat, and can announce when they join."
) {
	private val namesSetting = StringSetting(
		"Marked Players",
		description = "The players to highlight, separated by commas."
	)

	private val markOwnNameSetting = BooleanSetting(
		"Mark Own Name",
		description = "Highlights your own name alongside the marked players."
	)

	private val highlightInWorldSetting = BooleanSetting(
		"Highlight in World",
		default = true,
		description = "Draws a glow around marked players wherever they stand."
	)

	private val entityColorSetting = SelectorSetting(
		"Marked Entity Color",
		YELLOW,
		CHAT_COLOR_NAMES,
		description = "The colour of the glow around a marked player."
	).withDependency { highlightInWorldSetting.on }

	private val highlightInChatSetting = BooleanSetting(
		"Highlight in Chat",
		default = true,
		description = "Recolours a marked player's name wherever it appears in chat."
	)

	private val chatColorSetting = SelectorSetting(
		"Marked Chat Color",
		YELLOW,
		CHAT_COLOR_NAMES,
		description = "The colour a marked name takes in chat."
	).withDependency { highlightInChatSetting.on }

	private val joinLeaveSetting = BooleanSetting(
		"Join/Leave Message",
		description = "Says in chat when one of the watched players enters or leaves your lobby."
	)

	private val watchedSetting = StringSetting(
		"Players List",
		description = "The players to announce, separated by commas. Case sensitive."
	).withDependency { joinLeaveSetting.on }

	private val usePrefixSetting = BooleanSetting(
		"Use Prefix",
		default = true,
		description = "Puts the Dhen prefix in front of the join and leave lines."
	).withDependency { joinLeaveSetting.on }

	private val joinMessageSetting = StringSetting(
		"Join Message",
		default = "&&b%s &&ajoined your lobby.",
		description = "The join line. && is a colour code and %s is the player name."
	).withDependency { joinLeaveSetting.on }

	private val leftMessageSetting = StringSetting(
		"Left Message",
		default = "&&b%s &&cleft your lobby.",
		description = "The leave line. && is a colour code and %s is the player name."
	).withDependency { joinLeaveSetting.on }

	private val marked = HashSet<String>()
	private val glowing = Reference2IntOpenHashMap<Entity>()
	private val watched = ArrayList<String>()
	private val lobby = HashSet<String>()
	private val announced = HashSet<String>()
	private val joined = ArrayList<String>()
	private val left = ArrayList<String>()
	private val tabName = Pattern.compile(TAB_NAME).matcher("")

	private var markedRevision = ""
	private var selfRevision = ""
	private var watchedRevision = ""
	private var glow: Handle? = null

	init {
		registerSetting(namesSetting)
		registerSetting(markOwnNameSetting)
		registerSetting(highlightInWorldSetting)
		registerSetting(entityColorSetting)
		registerSetting(highlightInChatSetting)
		registerSetting(chatColorSetting)
		registerSetting(joinLeaveSetting)
		registerSetting(watchedSetting)
		registerSetting(usePrefixSetting)
		registerSetting(joinMessageSetting)
		registerSetting(leftMessageSetting)

		on<ChatReceiveEvent>(AFTER_PRODUCERS) { recolour(it) }
		on<ClientTickEvent.End> { sweep() }
		on<TabWidgetUpdateEvent> { listed(it) }
		on<WorldChangeEvent> { forget() }
	}

	override fun onEnabled() {
		glowing.defaultReturnValue(NO_HIGHLIGHT)
		glow = EntityHighlights.glow { entity -> glowing.getInt(entity) }
	}

	override fun onDisabled() {
		glow?.unsubscribe()
		glow = null
		forget()
		glowing.clear()
	}

	private fun sweep() {
		glowing.clear()
		if (!highlightInWorldSetting.on) return
		refreshMarked()
		if (marked.isEmpty()) return
		val level = Minecraft.getInstance().level ?: return
		val ink = legacyColor(chatColorNamed(entityColorSetting.value))
		for (entity in level.entitiesForRendering()) {
			if (entity !is RemotePlayer || !entity.isAlive) continue
			if (withoutCodes(entity.name.string).lowercase(Locale.ROOT) in marked) glowing.put(entity, ink)
		}
	}

	private fun recolour(event: ChatReceiveEvent) {
		if (!highlightInChatSetting.on) return
		refreshMarked()
		if (marked.isEmpty()) return
		val color = chatColorNamed(chatColorSetting.value).toString()
		if (highlightNames(event.stripped, marked, color) === event.stripped) return
		event.text = recoloured(event.text, color)
	}

	private fun recoloured(text: Component, color: String): Component {
		val rebuilt = Component.literal(highlightNames(text.string, marked, color)).setStyle(text.style)
		for (sibling in text.siblings) rebuilt.append(recoloured(sibling, color))
		return rebuilt
	}

	private fun listed(event: TabWidgetUpdateEvent) {
		if (event.widget != TabWidget.PLAYER_LIST || !joinLeaveSetting.on) return
		refreshWatched()
		if (watched.isEmpty()) return
		val self = Minecraft.getInstance().user.name
		lobby.clear()
		for (line in event.lines) {
			if (!tabName.reset(withoutCodes(line)).matches()) continue
			val name = tabName.group("name")
			if (name != self) lobby.add(name)
		}
		joined.clear()
		left.clear()
		for (name in watched) {
			if (name in lobby && announced.add(name)) joined += name
			if (name !in lobby && announced.remove(name)) left += name
		}
		if (joined.isNotEmpty()) say(joinMessageSetting.value, joined)
		if (left.isNotEmpty()) say(leftMessageSetting.value, left)
	}

	private fun say(template: String, names: List<String>) {
		val line = template.replace(COLOR_ESCAPE, ChatFormatting.PREFIX_CODE.toString())
			.replace(NAME_TOKEN, names.joinToString(", "))
		if (usePrefixSetting.on) Dhen.announce(line) else Minecraft.getInstance().player?.sendSystemMessage(DhenType.component(line))
	}

	private fun refreshMarked() {
		val self = if (markOwnNameSetting.on) Minecraft.getInstance().user.name else ""
		if (namesSetting.value == markedRevision && self == selfRevision) return
		markedRevision = namesSetting.value
		selfRevision = self
		marked.clear()
		for (name in namesSetting.value.split(',')) {
			val trimmed = name.trim()
			if (trimmed.isNotEmpty()) marked += trimmed.lowercase(Locale.ROOT)
		}
		if (self.isNotEmpty()) marked += self.lowercase(Locale.ROOT)
	}

	private fun refreshWatched() {
		if (watchedSetting.value == watchedRevision) return
		watchedRevision = watchedSetting.value
		watched.clear()
		for (name in watchedSetting.value.split(',')) {
			val trimmed = name.trim()
			if (trimmed.isNotEmpty()) watched += trimmed
		}
		announced.retainAll(watched.toSet())
	}

	private fun forget() {
		lobby.clear()
		announced.clear()
		markedRevision = "\u0000"
		selfRevision = "\u0000"
		watchedRevision = "\u0000"
	}

	private const val YELLOW = "Yellow"
	private const val COLOR_ESCAPE = "&&"
	private const val NAME_TOKEN = "%s"
	private const val FIELD_BREAK = " "
	private const val TAB_NAME = "\\[(?<level>[^]]*)] (?<name>[A-Za-z0-9_]+).*"
}
