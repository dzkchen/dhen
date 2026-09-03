package io.github.dzkchen.dhen.features.chat

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.tree.CommandNode
import io.github.dzkchen.dhen.command.CommandRegistry
import java.util.Locale

interface CommandNodeAccess {
	fun commandChildren(): MutableMap<String, CommandNode<*>>

	fun commandLiterals(): MutableMap<String, CommandNode<*>>

	fun commandArguments(): MutableMap<String, CommandNode<*>>
}

internal const val ALIAS_SEPARATOR = "\n"

internal class AliasBook(private val stored: () -> String) {
	private var source: String? = null
	private val entries = linkedMapOf<String, String>()

	fun all(): Map<String, String> = current()

	fun rewrite(command: String): String? {
		val entries = current()
		if (entries.isEmpty()) return null
		val space = command.indexOf(' ')
		val alias = if (space < 0) command else command.substring(0, space)
		val replacement = entries[alias.lowercase(Locale.ROOT)] ?: return null
		return if (space < 0) replacement else replacement + command.substring(space)
	}

	private fun current(): Map<String, String> {
		val text = stored()
		if (text === source) return entries
		source = text
		entries.clear()
		for (line in text.split(ALIAS_SEPARATOR)) {
			if (line.isEmpty()) continue
			val space = line.indexOf(' ')
			if (space <= 0 || space == line.length - 1) continue
			entries[line.substring(0, space)] = line.substring(space + 1)
		}
		return entries
	}
}

internal fun aliasFault(alias: String, replacement: String): String? = when {
	alias.isBlank() -> "An alias needs a name."
	alias.any { it.isWhitespace() } -> "An alias cannot contain a space."
	alias.startsWith("/") -> "Leave the slash off the alias."
	alias.lowercase(Locale.ROOT) in CommandRegistry.RESERVED -> "'$alias' is reserved by Dhen."
	replacement.isBlank() -> "'$alias' needs a command to stand for."
	else -> null
}

internal fun formatAliases(entries: Map<String, String>): String =
	entries.entries.joinToString(ALIAS_SEPARATOR) { "${it.key} ${it.value}" }

internal fun <S> installAliases(dispatcher: CommandDispatcher<S>, aliases: Collection<String>) {
	for (alias in aliases) {
		unregisterNode(dispatcher.root, alias)
		dispatcher.register(
			literal<S>(alias).then(argument<S, String>(ALIAS_ARGUMENTS, StringArgumentType.greedyString()))
		)
	}
}

internal fun unregisterNode(root: CommandNode<*>, name: String) {
	removeNode(root as? CommandNodeAccess ?: return, name)
}

internal fun removeNode(node: CommandNodeAccess, name: String) {
	node.commandChildren().remove(name)
	node.commandLiterals().remove(name)
	node.commandArguments().remove(name)
}

private const val ALIAS_ARGUMENTS = "arguments"
