package io.github.dzkchen.dhen.features.chat

import java.util.Locale

internal val PARTY_INVITE = Regex("(?:\\[[^]]*] )?(\\w{1,16}) has invited you to join their party!")

internal val INVITE_EXPIRED = Regex("^The party invite from (?:\\[[^]]*] )?(\\w{1,16}) has expired\\.$")

internal val COMMAND_COOLDOWN = Regex("^You may only use this command after (\\d+)s on the server!$")

internal const val VIEW_RECIPE = "viewrecipe"

internal val SACK_COMMANDS = setOf("gfs", "getfromsacks")

internal val STORAGE_COMMANDS = mapOf(
	"ec" to "ec",
	"enderchest" to "ec",
	"bp" to "bp",
	"backpack" to "bp"
)

internal fun shortenedWarp(command: String, warps: Set<String>, blocked: String?): String? {
	val warp = command.trimEnd().lowercase(Locale.ROOT)
	if (warp.isEmpty() || warp == blocked || warp !in warps) return null
	return "warp $warp"
}

internal fun viewRecipeCommand(message: String, resolve: (String) -> String?): String? {
	val words = message.split(' ').filter { it.isNotEmpty() }
	if (words.size < 2 || !words[0].equals(VIEW_RECIPE, ignoreCase = true)) return null
	val arguments = words.drop(1)
	val trailing = arguments.last().toIntOrNull()
	val paged = trailing != null && arguments.size > 1 && resolve(arguments.joinToString(" ")) == null
	val named = if (paged) arguments.dropLast(1) else arguments
	val name = named.joinToString(" ")
	val item = resolve(name) ?: name.uppercase(Locale.ROOT).replace(' ', '_')
	val rewritten = "$VIEW_RECIPE $item ${if (paged) trailing else 1}"
	return if (rewritten == message) null else rewritten
}

internal fun sackCommand(message: String, known: (String) -> Boolean): String? {
	val words = message.split(' ')
	if (words.size < 3 || words[0].lowercase(Locale.ROOT) !in SACK_COMMANDS) return null
	val typed = words.subList(1, words.size - 1).joinToString(" ")
	if (!typed.contains('_')) return null
	val real = typed.replace('_', ' ')
	if (!known(real)) return null
	return message.replace(typed, real)
}

internal fun heldWarpCommand(words: List<String>): String? = when (words[0].lowercase(Locale.ROOT)) {
	"is" -> "is"
	"hub" -> "warp hub"
	"warpforge" -> "warp forge"
	"warp" -> if (words.size > 1) words.joinToString(" ") else null
	else -> null
}
