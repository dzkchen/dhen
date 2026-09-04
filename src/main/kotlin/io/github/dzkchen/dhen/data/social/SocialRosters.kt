package io.github.dzkchen.dhen.data.social

import io.github.dzkchen.dhen.data.party.PartyChat
import io.github.dzkchen.dhen.event.withoutCodes
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

internal object SocialRosters {
	private val friendAdded = Regex("You are now friends with (.+)")
	private val friendRemoved = Regex("You removed (.+?) from your friends list!")
	private val profileHover = Regex("Click here to view (\\w{1,16})'s profile")

	private val friendNames = LinkedHashSet<String>()
	private val guildNames = LinkedHashSet<String>()
	private val pendingGuild = ArrayList<String>()

	private var readingGuild = false

	val friends: List<String> get() = friendNames.toList()

	val guild: List<String> get() = guildNames.toList()

	fun read(stripped: String, text: Component) {
		readGuild(stripped)
		if (!stripped.contains(FRIEND_MARK, ignoreCase = true)) return
		if (readFriendList(stripped, text)) return
		friendAdded.find(stripped)?.let { match ->
			PartyChat.rankedName(match.groupValues[1])?.let { friendNames += it }
			return
		}
		friendRemoved.find(stripped)?.let { match ->
			PartyChat.rankedName(match.groupValues[1])?.let { friendNames -= it }
		}
	}

	fun forget() {
		friendNames.clear()
		guildNames.clear()
		pendingGuild.clear()
		readingGuild = false
	}

	private fun readGuild(stripped: String): Boolean {
		if (stripped.startsWith(GUILD_NAME_LINE)) {
			readingGuild = true
			pendingGuild.clear()
			return true
		}
		if (stripped.startsWith(OFFLINE_MEMBERS_LINE)) {
			readingGuild = false
			pendingGuild.clear()
			return true
		}
		if (!readingGuild) return false
		if (divider(stripped)) {
			readingGuild = false
			guildNames.clear()
			guildNames += pendingGuild
			pendingGuild.clear()
			return true
		}
		if (!stripped.contains(ONLINE_DOT)) return true
		for (segment in stripped.split(ONLINE_DOT)) {
			PartyChat.rankedName(segment)?.let { pendingGuild += it }
		}
		return true
	}

	private fun divider(stripped: String): Boolean =
		stripped.length >= DIVIDER_LENGTH && stripped.all { it == '-' }

	private fun readFriendList(stripped: String, text: Component): Boolean {
		if (!stripped.contains(FRIENDS_HEADING)) return false
		val found = LinkedHashSet<String>()
		collectFriends(text, found)
		if (found.isEmpty()) return false
		friendNames += found
		return true
	}

	private fun collectFriends(text: Component, into: MutableSet<String>) {
		val style = text.style
		val click = style.clickEvent
		if (click is ClickEvent.RunCommand && click.command().startsWith(VIEW_PROFILE)) {
			hoveredName(style.hoverEvent)?.let { into += it }
		}
		for (sibling in text.siblings) collectFriends(sibling, into)
	}

	private fun hoveredName(hover: HoverEvent?): String? {
		if (hover !is HoverEvent.ShowText) return null
		return profileHover.find(withoutCodes(hover.value().string))?.groupValues?.get(1)
	}

	private const val GUILD_NAME_LINE = "Guild Name: "
	private const val OFFLINE_MEMBERS_LINE = "Offline Members: "
	private const val FRIEND_MARK = "friend"
	private const val FRIENDS_HEADING = "Friends"
	private const val VIEW_PROFILE = "/viewprofile "
	private const val DIVIDER_LENGTH = 20
	private const val ONLINE_DOT = "●"
}
