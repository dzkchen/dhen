package io.github.dzkchen.dhen.features.dungeon

import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor
import net.minecraft.util.ARGB

enum class DungeonClass(internal val fallback: ChatFormatting) {
	ARCHER(ChatFormatting.DARK_RED),
	BERSERK(ChatFormatting.GOLD),
	HEALER(ChatFormatting.DARK_PURPLE),
	MAGE(ChatFormatting.DARK_AQUA),
	TANK(ChatFormatting.DARK_GREEN),
	EMPTY(ChatFormatting.BLACK);

	val formatting: ChatFormatting
		get() = if (ClassColors.enabled) ClassColors.picked(this) else fallback

	val code: String get() = formatting.toString()

	val color: Int get() = legacyColor(formatting)

	companion object {
		fun of(name: String): DungeonClass =
			entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: EMPTY
	}
}

object ClassColors : Module(
	name = "Class Colors",
	category = Category.DUNGEONS,
	description = "Paints each dungeon class in a colour you pick. Turn this off to go back to the built-in colours."
) {
	private val palette: List<ChatFormatting> =
		ChatFormatting.entries.filter { TextColor.fromLegacyFormat(it) != null }

	private val paletteNames: List<String> = palette.map { spaced(it.name) }

	internal val choices: List<SelectorSetting> = DungeonClass.entries.map { dungeonClass ->
		val role = spaced(dungeonClass.name)
		SelectorSetting(
			role,
			spaced(dungeonClass.fallback.name),
			paletteNames,
			listed = true,
			description = "The colour every Dhen feature paints the $role class in."
		)
	}

	internal val resetSetting = ActionSetting(
		"Reset Colors",
		{ for (choice in choices) choice.reset() },
		"Puts all six class colours back to their defaults."
	)

	init {
		for (choice in choices) registerSetting(choice)
		registerSetting(resetSetting)
	}

	internal fun picked(dungeonClass: DungeonClass): ChatFormatting =
		palette[choices[dungeonClass.ordinal].index]
}

internal fun legacyColor(formatting: ChatFormatting): Int =
	ARGB.opaque(TextColor.fromLegacyFormat(formatting)?.value ?: 0)

private fun spaced(name: String): String =
	name.lowercase().split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
