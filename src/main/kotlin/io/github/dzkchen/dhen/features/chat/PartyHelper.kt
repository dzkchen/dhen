package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.MessageSendEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft

object PartyHelper : Module(
	name = "Party Helper",
	category = Category.CHAT,
	description = "Reformats the party list into rows you can click."
) {
	private val roster = mutableListOf<PartyListRow>()

	private var pendingDividers = 0

	init {
		on<MessageSendEvent> { requested(it) }
		on<ChatReceiveEvent>(AFTER_PRODUCERS) { chatted(it) }
		on<WorldChangeEvent> { forget() }
	}

	override fun onDisabled() = forget()

	private fun requested(event: MessageSendEvent) {
		if (!event.isCommand || !SkyBlockLocation.onHypixel) return
		val command = event.message
		val listed = command.equals(SHORT_LIST, true) ||
			command.equals(PARTY_LIST, true) ||
			command.equals(SHORT_PARTY_LIST, true)
		if (listed) pendingDividers += SURROUNDING_DIVIDERS
	}

	private fun chatted(event: ChatReceiveEvent) {
		if (pendingDividers == 0) return
		val stripped = event.stripped
		when {
			stripped.isBlank() -> event.cancelled = true

			PARTY_LIST_HEADER.matches(stripped) -> {
				roster.clear()
				event.cancelled = true
			}

			stripped.startsWith(NO_PARTY) -> forget()

			stripped.startsWith(DIVIDER_MARK) -> {
				event.cancelled = true
				pendingDividers--
				if (pendingDividers == 0) reformatted()
			}

			else -> partyListRole(stripped)?.let { role ->
				readPartyRows(event.styled, role, roster)
				event.cancelled = true
			}
		}
	}

	private fun reformatted() {
		if (roster.isEmpty()) return
		val client = Minecraft.getInstance()
		client.player?.sendSystemMessage(partyListComponent(roster, PartyState.isLeader, client.user.name))
		roster.clear()
	}

	private fun forget() {
		roster.clear()
		pendingDividers = 0
	}

	private const val SHORT_LIST = "pl"
	private const val PARTY_LIST = "party list"
	private const val SHORT_PARTY_LIST = "p list"
	private const val NO_PARTY = "You are not currently in a party"
	private const val DIVIDER_MARK = "-----"
	private const val SURROUNDING_DIVIDERS = 2
}
