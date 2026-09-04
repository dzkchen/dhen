package io.github.dzkchen.dhen.features.chat

internal enum class ChatTab(val label: String, val command: String) {
	ALL("All", "/chat a"),
	USER("User", ""),
	PARTY("Party", "/chat p"),
	GUILD("Guild", "/chat g"),
	PM("PM", ""),
	COOP("Co-op", "/chat skyblock-coop");

	fun claims(plain: String): Boolean = when (this) {
		ALL -> true
		USER -> USER_LINE.matches(plain)
		PARTY -> isPartyLine(plain)
		GUILD -> plain.startsWith("Guild > ") || plain.startsWith("G > ") || plain == "You are now in the GUILD channel"
		PM -> plain.startsWith("To ") || plain.startsWith("From ") || plain.startsWith("Friend > ")
		COOP -> plain.startsWith("Co-op > ") || plain == "You are now in the SKYBLOCK CO-OP channel"
	}
}

private val USER_LINE =
	Regex("""^(?:(?:Party|Guild|Co-op|Officer) > |(?:To|From) |Friend > )?(?:\[[^]]+] )*[a-zA-Z0-9_]{1,16}(?: \[[^]]+])?: .*""")

private fun isPartyLine(plain: String): Boolean =
	plain.startsWith("Party > ") ||
		plain.startsWith("P > ") ||
		plain.contains("has invited you to join their party!") ||
		plain.contains("to the party! They have 60 seconds to accept.") ||
		plain.contains("has disbanded the party!") ||
		plain.startsWith("The party was transferred to ") ||
		plain == "The party was disbanded because all invites expired and the party was empty" ||
		plain.endsWith("joined the party.") ||
		plain.endsWith("has left the party.") ||
		plain.endsWith("has been removed from the party.") ||
		plain == "You are now in the PARTY channel" ||
		plain == "You must be in a party to join the party channel!"
