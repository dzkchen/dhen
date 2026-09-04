package io.github.dzkchen.dhen.data.party

internal interface PartyRoster {
	val self: String?

	val leader: String?

	fun add(name: String)

	fun remove(name: String)

	fun lead(name: String)

	fun disband()
}

internal object PartyChat {
	private val joinedSelf = Regex("^You have joined ((?:\\[[^]]*?])? ?)?(\\w{1,16})'s? party!$")
	private val joinedOther = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) joined the party\\.$")
	private val leftParty = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has left the party\\.$")
	private val kickedParty = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has been removed from the party\\.$")
	private val kickedOffline = Regex("^Kicked ((?:\\[[^]]*?])? ?)?(\\w{1,16}) because they were offline\\.$")
	private val kickedDisconnected =
		Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) was removed from your party because they disconnected\\.$")
	private val transferLeave =
		Regex("^The party was transferred to ((?:\\[[^]]*?])? ?)?(\\w{1,16}) because ((?:\\[[^]]*?])? ?)?(\\w{1,16}) left$")
	private val transferBy =
		Regex("^The party was transferred to ((?:\\[[^]]*?])? ?)?(\\w{1,16}) by ((?:\\[[^]]*?])? ?)?(\\w{1,16})$")
	private val partyChat = Regex("^Party > ((?:\\[[^]]*?])? ?)?(\\w{1,16}): (.+)$")
	private val partyInvite =
		Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) invited ((?:\\[[^]]*?])? ?)?(\\w{1,16}) to the party! They have 60 seconds to accept.$")
	private val leaderDisconnected =
		Regex("^The party leader, ((?:\\[[^]]*?])? ?)?(\\w{1,16}) has disconnected, they have 5 minutes to rejoin before the party is disbanded\\.$")
	private val leaderRejoined = Regex("^The party leader ((?:\\[[^]]*?])? ?)?(\\w{1,16}) has rejoined\\.$")
	private val memberFormat = Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16})$")
	private val partyWith = Regex("^You'll be partying with: (.+)$")
	private val queuedInFinder = Regex("^Party Finder > Your party has been queued in the dungeon finder!$")
	private val dungeonJoin = Regex("^Party Finder > (\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$")
	private val kuudraJoin =
		Regex("^Party Finder > ((?:\\[[^]]*?])? ?)?(\\w{1,16}) joined the group! \\(Combat Level (\\d+)\\)$")
	private val floorEnter = Regex("-+\\s.+ entered.+The Catacombs, Floor [IVX]+!\\s-+")

	private val disbandPatterns = arrayOf(
		Regex("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has disbanded the party!$"),
		Regex("^You have been kicked from the party by ((?:\\[[^]]*?])? ?)?(\\w{1,16})$"),
		Regex("^The party was disbanded because all invites expired and the party was empty.$"),
		Regex("^The party was disbanded because the party leader disconnected.$"),
		Regex("^You left the party.$"),
		Regex("^$NOT_IN_PARTY.$")
	)

	fun rankedName(segment: String): String? = memberFormat.find(segment.trim())?.groupValues?.get(2)

	fun listRole(stripped: String): PartyRole? = when {
		stripped.startsWith(LEADER_LINE) -> PartyRole.LEADER
		stripped.startsWith(MODERATOR_LINE) -> PartyRole.MOD
		stripped.startsWith(MEMBER_LINE) -> PartyRole.MEMBER
		else -> null
	}

	fun read(line: String, roster: PartyRoster) {
		val message = line.trim()

		joinedOther.find(message)?.let { return roster.add(it.groupValues[2]) }

		joinedSelf.find(message)?.let {
			roster.add(it.groupValues[2])
			roster.lead(it.groupValues[2])
			val self = roster.self ?: return
			return roster.add(self)
		}

		leftParty.find(message)?.let { return roster.remove(it.groupValues[2]) }

		kickedParty.find(message)?.let { return roster.remove(it.groupValues[2]) }

		kickedOffline.find(message)?.let { return roster.remove(it.groupValues[2]) }

		kickedDisconnected.find(message)?.let { return roster.remove(it.groupValues[2]) }

		transferBy.find(message)?.let {
			roster.add(it.groupValues[2])
			roster.add(it.groupValues[4])
			return roster.lead(it.groupValues[2])
		}

		transferLeave.find(message)?.let {
			roster.add(it.groupValues[2])
			roster.lead(it.groupValues[2])
			return roster.remove(it.groupValues[4])
		}

		leaderDisconnected.find(message)?.let { return roster.lead(it.groupValues[2]) }

		leaderRejoined.find(message)?.let { return roster.lead(it.groupValues[2]) }

		partyChat.find(message)?.let { return roster.add(it.groupValues[2]) }

		partyInvite.find(message)?.let {
			roster.add(it.groupValues[2])
			if (roster.leader == null) roster.lead(it.groupValues[2])
			return
		}

		queuedInFinder.find(message)?.let {
			val self = roster.self ?: return
			roster.add(self)
			if (roster.leader == null) roster.lead(self)
			return
		}

		for (pattern in disbandPatterns) {
			if (pattern.containsMatchIn(message)) return roster.disband()
		}

		listRole(message)?.let { role ->
			for (segment in message.substringAfter(": ").split(" ●")) {
				val member = rankedName(segment) ?: continue
				roster.add(member)
				if (role == PartyRole.LEADER) roster.lead(member)
			}
			return
		}

		partyWith.find(message)?.let { match ->
			for (segment in match.groupValues[1].split(", ")) {
				val member = rankedName(segment) ?: continue
				roster.add(member)
			}
			return
		}

		floorEnter.find(message)?.let {
			val name = message.substringBefore(" entered").substringAfterLast(' ')
			roster.add(name)
			return roster.lead(name)
		}

		kuudraJoin.find(message)?.let { return roster.add(it.groupValues[2]) }

		dungeonJoin.find(message)?.let { return roster.add(it.groupValues[1]) }
	}
}

internal const val NOT_IN_PARTY = "You are not currently in a party"

private const val LEADER_LINE = "Party Leader: "
private const val MODERATOR_LINE = "Party Moderators: "
private const val MEMBER_LINE = "Party Members: "
