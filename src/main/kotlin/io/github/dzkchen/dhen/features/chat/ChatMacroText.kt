package io.github.dzkchen.dhen.features.chat

class ChatCommandLine(val channel: ChatChannel, val sender: String, val body: String)

internal val CHAT_COMMAND = Regex(
	"^(?:Party > (\\[[^]]*?])? ?(\\w{1,16})(?: [ቾ⚒])?: ?(.+)$" +
		"|Guild > (\\[[^]]*?])? ?(\\w{1,16})(?: \\[([^]]*?)])?: ?(.+)$" +
		"|From (\\[[^]]*?])? ?(\\w{1,16}): ?(.+)$)"
)

internal val END_OF_RUN = Regex(
	" {29}> EXTRA STATS <|^\\[NPC] Elle: Good job everyone. A hard fought battle come to an end." +
		" Let's get out of here before we run into any more trouble!$"
)

internal fun chatCommandLine(stripped: String): ChatCommandLine? {
	val channel = when {
		stripped.startsWith(PARTY_PREFIX) -> ChatChannel.PARTY
		stripped.startsWith(GUILD_PREFIX) -> ChatChannel.GUILD
		stripped.startsWith(DIRECT_PREFIX) -> ChatChannel.PRIVATE
		else -> return null
	}
	val groups = (CHAT_COMMAND.find(stripped) ?: return null).groupValues
	val sender: String
	val body: String
	when (channel) {
		ChatChannel.PARTY -> {
			sender = groups[2]
			body = groups[3]
		}

		ChatChannel.GUILD -> {
			sender = groups[5]
			body = groups[7]
		}

		ChatChannel.PRIVATE -> {
			sender = groups[9]
			body = groups[10]
		}
	}
	if (!body.startsWith(COMMAND_MARK)) return null
	return ChatCommandLine(channel, sender, body)
}

internal fun queueInstance(word: String, argument: String?): String? {
	val floor = word.substring(1).toIntOrNull() ?: argument?.toIntOrNull() ?: return null
	return when (word[0]) {
		DUNGEON_MARK -> if (floor in DUNGEON_FLOORS.indices) "CATACOMBS_FLOOR_${DUNGEON_FLOORS[floor]}" else null
		MASTER_MARK -> if (floor in 1..<DUNGEON_FLOORS.size) "MASTER_CATACOMBS_FLOOR_${DUNGEON_FLOORS[floor]}" else null
		KUUDRA_MARK -> if (floor in 1..KUUDRA_TIERS.size) "KUUDRA_${KUUDRA_TIERS[floor - 1]}" else null
		else -> null
	}
}

internal fun queueCommandNames(mark: Char): List<String> {
	val tiers = when (mark) {
		DUNGEON_MARK -> DUNGEON_FLOORS.indices
		MASTER_MARK -> 1..<DUNGEON_FLOORS.size
		else -> 1..KUUDRA_TIERS.size
	}
	val names = mutableListOf(mark.toString())
	for (tier in tiers) names += "$mark$tier"
	return names
}

internal fun emoted(message: String): String? {
	val words = message.split(' ')
	var replaced = false
	var index = 0
	while (index < words.size) {
		if (EMOTES.containsKey(words[index])) {
			replaced = true
			break
		}
		index++
	}
	if (!replaced) return null
	return words.joinToString(" ") { EMOTES[it] ?: it }
}

internal fun emotable(message: String, isCommand: Boolean): Boolean =
	!isCommand || message.substringBefore(' ') in EMOTE_COMMANDS

private val DUNGEON_FLOORS = arrayOf("ENTRANCE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN")

private val KUUDRA_TIERS = arrayOf("NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL")

private val EMOTE_COMMANDS = setOf("pc", "ac", "gc", "msg", "w", "r")

private val EMOTES = mapOf(
	"<3" to "❤",
	"o/" to "( ﾟ◡ﾟ)/",
	":star:" to "✮",
	":yes:" to "✔",
	":no:" to "✖",
	":java:" to "☕",
	":arrow:" to "➜",
	":shrug:" to "¯\\_(ツ)_/¯",
	":tableflip:" to "(╯°□°）╯︵ ┻━┻",
	":totem:" to "☉_☉",
	":typing:" to "✎...",
	":maths:" to "√(π+x)=L",
	":snail:" to "@'-'",
	"ez" to "ｅｚ",
	":thinking:" to "(0.o?)",
	":gimme:" to "༼つ◕_◕༽つ",
	":wizard:" to "('-')⊃━☆ﾟ.*･｡ﾟ",
	":pvp:" to "⚔",
	":peace:" to "✌",
	":puffer:" to "<('O')>",
	"h/" to "ヽ(^◇^*)/",
	":sloth:" to "(・⊝・)",
	":dog:" to "(ᵔᴥᵔ)",
	":dj:" to "ヽ(⌐■_■)ノ♬",
	":yey:" to "ヽ (◕◡◕) ﾉ",
	":snow:" to "☃",
	":dab:" to "<o/",
	":cat:" to "= ＾● ⋏ ●＾ =",
	":cute:" to "(✿◠‿◠)",
	":skull:" to "☠",
	":bum:" to "♿"
)

private const val PARTY_PREFIX = "Party > "
private const val GUILD_PREFIX = "Guild > "
private const val DIRECT_PREFIX = "From "
private const val COMMAND_MARK = "!"
private const val DUNGEON_MARK = 'f'
private const val MASTER_MARK = 'm'
private const val KUUDRA_MARK = 't'
