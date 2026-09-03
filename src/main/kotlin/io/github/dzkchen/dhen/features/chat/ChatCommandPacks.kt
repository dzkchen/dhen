package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.event.Handle
import org.slf4j.LoggerFactory
import java.util.Locale

enum class ChatChannel {
	PARTY,
	GUILD,
	PRIVATE
}

interface ChatCommandReplies {
	fun reply(channel: ChatChannel, sender: String, message: String)

	fun send(command: String)
}

class ChatCommandContext internal constructor(
	val sender: String,
	val channel: ChatChannel,
	val words: List<String>,
	private val replies: ChatCommandReplies
) {
	val argument: String?
		get() = words.getOrNull(1)?.takeIf { it.isNotEmpty() && it.length <= MAX_ARGUMENT_LENGTH }

	val arguments: String
		get() = words.drop(1).joinToString(" ")

	fun reply(message: String) = replies.reply(channel, sender, message)

	fun send(command: String) = replies.send(command)
}

class ChatCommandEntry(
	names: List<String>,
	val channels: Set<ChatChannel>,
	val leaderOnly: Boolean = false,
	val enabled: () -> Boolean = { true },
	val run: (ChatCommandContext) -> Unit
) {
	val names: List<String> = names.map { it.lowercase(Locale.ROOT) }
}

class CommandPack(val name: String, val entries: List<ChatCommandEntry>)

object ChatCommandRegistry {
	private val logger = LoggerFactory.getLogger(ChatCommandRegistry::class.java)
	private val byChannel = Array(ChatChannel.entries.size) { linkedMapOf<String, ChatCommandEntry>() }

	fun register(pack: CommandPack): Handle {
		for (entry in pack.entries) {
			for (channel in entry.channels) {
				val table = byChannel[channel.ordinal]
				for (name in entry.names) {
					if (table.putIfAbsent(name, entry) == null) continue
					logger.warn("Pack '{}' cannot answer !{} on {}, another pack already does", pack.name, name, channel)
				}
			}
		}
		return Handle {
			for (table in byChannel) table.values.removeAll { it in pack.entries }
		}
	}

	fun answer(line: ChatCommandLine, leader: Boolean, enforceLeader: Boolean, replies: ChatCommandReplies): Boolean {
		val words = line.body.substring(1).split(' ')
		val entry = byChannel[line.channel.ordinal][words[0].lowercase(Locale.ROOT)] ?: return false
		if (!entry.enabled()) return false
		if (entry.leaderOnly && enforceLeader && !leader) return false
		entry.run(ChatCommandContext(line.sender, line.channel, words, replies))
		return true
	}

	fun answered(channel: ChatChannel): List<String> =
		byChannel[channel.ordinal].entries
			.filter { it.key == it.value.names.first() && it.value.enabled() }
			.map { it.key }

	internal fun forget() {
		for (table in byChannel) table.clear()
	}
}

private const val MAX_ARGUMENT_LENGTH = 16
