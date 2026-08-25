package io.github.dzkchen.dhen.data.party

import io.github.dzkchen.dhen.data.HypixelModApi
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.PartyEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.client.Minecraft

internal object PartyHooks : GuardedHooks<PartyHooks.Channels> {
	override val feed = "Party"

	private const val HYPIXEL_BRAND = "hypixel"

	private val liveHypixelServer: () -> Boolean = {
		Minecraft.getInstance().player?.connection?.serverBrand()
			?.contains(HYPIXEL_BRAND, ignoreCase = true) == true
	}

	override val failsafe = Failsafe("Dhen {} failed, its party state is off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(
		bus: EventBus,
		self: () -> String? = { Minecraft.getInstance().user.name },
		request: () -> Unit = HypixelModApi::requestPartyInfo,
		onHypixel: () -> Boolean = liveHypixelServer
	) {
		uninstall()
		channels = Channels(bus, self, request, onHypixel)
		subscriptions = arrayOf(
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { chatted(it) },
			bus.subscribe<ClientTickEvent.End> { ticked() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		PartyState.reset()
	}

	override fun bound() = channels

	val requesting: Boolean get() = channels?.requesting ?: false

	fun greeted() = guarded("party greeting") { it.greeted() }

	fun refused(permanent: Boolean) = guarded("party refusal") { it.refused(permanent) }

	fun reconciled(inParty: Boolean, leader: String?, roles: Map<String, PartyRole>, memberCount: Int) =
		guarded("party info") { it.reconciled(inParty, leader, roles, memberCount) }

	private fun chatted(event: ChatReceiveEvent) {
		guarded("party chat") { it.chatted(event) }
	}

	private fun ticked() = guarded("party request") { it.flush() }

	internal class Channels(
		bus: EventBus,
		private val name: () -> String?,
		private val request: () -> Unit,
		private val onHypixel: () -> Boolean
	) : PartyRoster {
		private val joins = bus.type<PartyEvent.Joined>()
		private val leaves = bus.type<PartyEvent.Left>()
		private val leaderships = bus.type<PartyEvent.LeaderChanged>()
		private val disbands = bus.type<PartyEvent.Disbanded>()
		private val updates = bus.type<PartyEvent.Updated>()

		private var changed = false
		private var requested = false

		var requesting = true
			private set

		override val self: String? get() = PartyState.self

		override val leader: String? get() = PartyState.leader

		fun greeted() {
			requested = true
		}

		fun flush() {
			if (!requested) return
			requested = false
			if (requesting) request()
		}

		fun refused(permanent: Boolean) {
			if (permanent) requesting = false
		}

		fun chatted(event: ChatReceiveEvent) {
			if (!onHypixel()) return
			publishing {
				PartyChat.read(event.stripped, this)
				if (changed) requested = true
			}
		}

		fun reconciled(inParty: Boolean, leader: String?, roles: Map<String, PartyRole>, memberCount: Int) = publishing {
			if (!inParty) return@publishing disband()
			PartyState.inParty = true
			for (member in roles.keys) add(member)
			if (roles.size == memberCount) prune(roles)
			if (PartyState.assign(roles)) changed = true
			if (leader != null) leading(leader) else if (demoted(roles)) leading(null)
		}

		override fun add(name: String) {
			if (!PartyState.add(name)) return
			changed = true
			joins.dispatch(PartyEvent.Joined(name))
		}

		override fun remove(name: String) {
			if (!PartyState.remove(name)) return
			changed = true
			leaves.dispatch(PartyEvent.Left(name))
			if (PartyState.members.isEmpty()) disband()
		}

		override fun lead(name: String) = leading(name)

		private fun leading(name: String?) {
			val previous = PartyState.leader
			if (!PartyState.lead(name)) return
			changed = true
			leaderships.dispatch(PartyEvent.LeaderChanged(name, previous))
		}

		private fun demoted(roles: Map<String, PartyRole>): Boolean {
			val leader = PartyState.leader ?: return false
			val role = roles[leader] ?: return false
			return role != PartyRole.LEADER
		}

		override fun disband() {
			if (!PartyState.disband()) return
			changed = true
			disbands.dispatch(PartyEvent.Disbanded)
		}

		private fun prune(roles: Map<String, PartyRole>) {
			var index = PartyState.members.size - 1
			while (index >= 0) {
				val member = PartyState.members[index]
				if (member !in roles) remove(member)
				index--
			}
		}

		private inline fun publishing(pass: () -> Unit) {
			PartyState.self = name()
			changed = false
			pass()
			if (changed) updates.dispatch(PartyEvent.Updated)
		}
	}
}
