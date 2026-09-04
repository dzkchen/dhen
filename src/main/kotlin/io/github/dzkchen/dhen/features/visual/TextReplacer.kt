package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.dzkchen.dhen.command.TextReplacerCommands
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.text.CustomNames
import io.github.dzkchen.dhen.text.MobIcon
import io.github.dzkchen.dhen.text.Rewrite
import io.github.dzkchen.dhen.text.TextRewrite
import io.github.dzkchen.dhen.util.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization

internal const val ROW_SEPARATOR = "\n"
internal const val FIELD_SEPARATOR = "\u0000"

private val WHITE = Color.rgba(255, 255, 255)

internal fun replacementRows(stored: String): List<Pair<String, String>> =
	stored.split(ROW_SEPARATOR).mapNotNull { row ->
		val split = row.indexOf(FIELD_SEPARATOR)
		if (split <= 0 || split == row.length - 1) null else row.substring(0, split) to row.substring(split + 1)
	}

internal fun formatRows(rows: List<Pair<String, String>>): String =
	rows.joinToString(ROW_SEPARATOR) { it.first + FIELD_SEPARATOR + it.second }

internal fun replacementComponent(value: String): Component {
	val trimmed = value.trimStart()
	if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return Component.literal(value)
	val element = runCatching { JsonParser.parseString(value) }.getOrNull() ?: return Component.literal(value)
	val parsed = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null)
	return parsed?.copy() ?: Component.literal(value)
}

object TextReplacer : Module(
	name = "Text Replacer",
	category = Category.VISUAL,
	description = "Replaces words and Hypixel's mob icons wherever the game draws them."
), TextReplacerCommands {
	private var storedRows by StringSetting("Replacements").hide()

	internal val mobIconsSetting = BooleanSetting(
		"Mob Icons",
		description = "Spells out Hypixel's mob type icons as words wherever SkyBlock draws them."
	)
	private var mobIcons by mobIconsSetting

	internal val displayStyleSetting = SelectorSetting(
		"Display Style",
		NORMAL,
		listOf(NORMAL, SHORT),
		description = "Whether an icon reads as its full name or a short code."
	).withDependency { mobIconsSetting.on }
	private var displayStyle by displayStyleSetting

	internal val iconColorSettings: List<ColorSetting> = MobIcon.entries.map { icon ->
		ColorSetting(
			"${icon.label} Color",
			WHITE,
			description = "White keeps the colour the game already draws that icon in."
		).withDependency { mobIconsSetting.on }.also { registerSetting(it) }
	}

	private var rebuild: Job? = null
	private var primed = false
	private var applied = 0

	init {
		registerSetting(ActionSetting("Reload", ::refresh, "Builds the replacement table again."))
		on<ClientTickEvent.End> { if (changed()) refresh() }
	}

	override fun onDisabled() {
		primed = false
		TextRewrite.install(TextRewrite.compileReplacements(emptyList()))
	}

	override fun add(find: String, replacement: String): String {
		if (find.isEmpty()) return "A replacement needs something to look for."
		if (find.contains(ROW_SEPARATOR) || find.contains(FIELD_SEPARATOR)) return "'$find' cannot be searched for."
		if (replacement.isEmpty()) return "'$find' needs something to become."
		storedRows = formatRows(replacementRows(storedRows).filterNot { it.first == find } + (find to replacement))
		if (!enabled) return "'$find' will read as '$replacement' once Text Replacer is switched on."
		return "Replacing '$find' with '$replacement'."
	}

	override fun remove(find: String): String {
		val rows = replacementRows(storedRows)
		val kept = rows.filterNot { it.first == find }
		if (kept.size == rows.size) return "Nothing replaces '$find'."
		storedRows = formatRows(kept)
		return "Stopped replacing '$find'."
	}

	override fun list(): List<String> {
		val rows = replacementRows(storedRows)
		if (rows.isEmpty()) return listOf("Nothing is replaced yet. Add one with /dhen replace add <find> <replacement>.")
		return rows.map { "'${it.first}' reads as '${it.second}'" }
	}

	override fun finds(): List<String> = replacementRows(storedRows).map { it.first }

	override fun name(name: String, replacement: String): String {
		CustomNames.rebuild(mapOf(name to replacementComponent(replacement)))
		return "'$name' now reads as '$replacement' wherever it stands alone."
	}

	override fun clearNames(): String {
		CustomNames.clear()
		return "The name table is empty again."
	}

	private fun refresh() {
		val rows = storedRows
		val icons = iconRewrites()
		rebuild?.cancel()
		rebuild = launch {
			val built = withContext(Dispatchers.IO) {
				TextRewrite.compileReplacements(userRewrites(rows) + icons)
			}
			TextRewrite.install(built)
		}
	}

	private fun userRewrites(stored: String): List<Rewrite> =
		replacementRows(stored).map { Rewrite(it.first, replacementComponent(it.second)) }

	private fun iconRewrites(): List<Rewrite> {
		if (!mobIcons || !SkyBlockLocation.inSkyBlock) return emptyList()
		val short = displayStyle == SHORT
		val rewrites = ArrayList<Rewrite>(MobIcon.entries.size * 2)
		for (index in MobIcon.entries.indices) {
			val icon = MobIcon.entries[index]
			val word = iconWord(icon, short, iconColorSettings[index].value)
			rewrites += Rewrite(icon.glyph, word, leadingRunOnly = true)
			rewrites += Rewrite(icon.glyph + RUN_SPACE, word, leadingRunOnly = true)
		}
		return rewrites
	}

	private fun iconWord(icon: MobIcon, short: Boolean, color: Color): Component {
		val word = Component.literal((if (short) icon.short else icon.label) + RUN_SPACE)
		return if (color.rgb == WHITE.rgb) word else word.withStyle { it.withColor(color.rgb) }
	}

	private fun changed(): Boolean {
		val current = signature()
		if (primed && current == applied) return false
		primed = true
		applied = current
		return true
	}

	private fun signature(): Int {
		var hash = storedRows.hashCode()
		hash = hash * PRIME + if (mobIcons && SkyBlockLocation.inSkyBlock) 1 else 0
		hash = hash * PRIME + displayStyle.hashCode()
		for (index in iconColorSettings.indices) hash = hash * PRIME + iconColorSettings[index].value.argb
		return hash
	}

	private const val NORMAL = "Normal"
	private const val SHORT = "Short"
	private const val RUN_SPACE = " "
	private const val PRIME = 31
}
