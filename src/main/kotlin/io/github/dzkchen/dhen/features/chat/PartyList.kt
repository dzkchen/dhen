package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.data.party.PartyRole
import io.github.dzkchen.dhen.gui.DhenType
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent

internal class PartyListRow(
	val rank: String,
	val name: String,
	val role: PartyRole,
	val online: Boolean
)

internal val PARTY_LIST_HEADER = Regex("""^Party Members \(\d+\)$""")

private val ENTRY_SEPARATOR = Regex("""^(?:\s|§[rR])+""")

internal fun partyListRole(stripped: String): PartyRole? = when {
	stripped.startsWith(LEADER_LINE) -> PartyRole.LEADER
	stripped.startsWith(MODERATOR_LINE) -> PartyRole.MOD
	stripped.startsWith(MEMBER_LINE) -> PartyRole.MEMBER
	else -> null
}

internal fun readPartyRows(styled: String, role: PartyRole, into: MutableList<PartyListRow>) {
	for (chunk in styled.substringAfter(COLON).split(ONLINE_DOT)) {
		val entry = ENTRY_SEPARATOR.replaceFirst(chunk, "")
		var end = entry.length
		var status = NO_STATUS
		while (end > 0) {
			val last = entry[end - 1]
			if (last == ' ') {
				end--
				continue
			}
			if (end < 2 || entry[end - 2] != LEGACY_PREFIX) break
			if (status == NO_STATUS && (last == ONLINE_CODE || last == OFFLINE_CODE)) status = last
			end -= 2
		}
		var start = end
		while (start > 0 && nameCharacter(entry[start - 1])) {
			if (start >= 2 && entry[start - 2] == LEGACY_PREFIX) break
			start--
		}
		if (start == end) continue
		into += PartyListRow(
			entry.substring(0, start),
			entry.substring(start, end),
			role,
			status == ONLINE_CODE
		)
	}
}

internal fun partyListComponent(rows: List<PartyListRow>, leading: Boolean, self: String?): Component {
	val list = chatLine("$DIVIDER\n$INDENT§aParty Members (${rows.size})\n")
	if (leading) {
		list.append(button("$INDENT§9[Warp] ", "/p warp", "§7Warp Party"))
		list.append(button("§e[Invite] ", "/p settings allinvite", "§7Toggle AllInvite"))
		list.append(button("§4[Disband]", "/p disband", "§c§lBE CAREFUL"))
		list.append(chatLine("\n"))
	}
	for (row in rows.sortedBy { it.role }) {
		val dot = if (row.online) ONLINE_TINT else OFFLINE_TINT
		val line = chatLine("\n$INDENT$dot● §r${row.rank}${row.name}")
		if (row.role == PartyRole.LEADER) line.append(chatLine(" §e(Leader)"))
		if (leading && row.name != self) {
			line.append(button(" §c[Kick]", "/p kick ${row.name}", "§cKicks ${row.name}"))
			line.append(
				button(" §a[Transfer]", "/p transfer ${row.name}", "§aTransfers the party to §f${row.name}")
			)
		}
		list.append(line)
	}
	return list.append(chatLine("\n$DIVIDER"))
}

private fun chatLine(text: String): MutableComponent = DhenType.overWorld(text).copy()

private fun button(label: String, command: String, hover: String): MutableComponent =
	chatLine(label).withStyle { style ->
		style
			.withClickEvent(ClickEvent.RunCommand(command))
			.withHoverEvent(HoverEvent.ShowText(DhenType.overWorld(hover)))
	}

private fun nameCharacter(character: Char): Boolean = character == '_' || character.isLetterOrDigit()

private const val LEADER_LINE = "Party Leader: "
private const val MODERATOR_LINE = "Party Moderators: "
private const val MEMBER_LINE = "Party Members: "
private const val COLON = ':'
private const val ONLINE_DOT = '●'
private const val LEGACY_PREFIX = '§'
private const val ONLINE_CODE = 'a'
private const val OFFLINE_CODE = 'c'
private const val NO_STATUS = ' '
private const val ONLINE_TINT = "§a"
private const val OFFLINE_TINT = "§c"
private const val INDENT = "  "

private const val DIVIDER = "§9§m----------------------------------"
