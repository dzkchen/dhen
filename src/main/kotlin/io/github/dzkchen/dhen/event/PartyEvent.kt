package io.github.dzkchen.dhen.event

sealed class PartyEvent : Event {
	class Joined internal constructor(val name: String) : PartyEvent()

	class Left internal constructor(val name: String) : PartyEvent()

	class LeaderChanged internal constructor(val leader: String?, val previous: String?) : PartyEvent()

	object Disbanded : PartyEvent()

	object Updated : PartyEvent()
}
