package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.TablistUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.PlayerTeam

internal object TablistHooks : GuardedHooks<TablistHooks.Channels> {
	override val feed = "Tab list"

	private const val BEFORE_FEATURES = 100
	private const val TABLIST_ENTRIES = 80

	private val displayOrder: Comparator<PlayerInfo> =
		compareBy<PlayerInfo> { -it.tabListOrder }
			.thenBy { if (it.gameMode == GameType.SPECTATOR) 1 else 0 }
			.thenBy { it.team?.name.orEmpty() }
			.thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.name }

	override val failsafe = Failsafe("Dhen {} failed, its tab list state is off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus, players: () -> List<Component> = ::listed) {
		uninstall()
		channels = Channels(bus, players)
		subscriptions = arrayOf(
			bus.subscribe<PacketReceiveEvent.Post>(BEFORE_FEATURES) { received(it.packet) },
			bus.subscribe<ClientTickEvent.Start>(BEFORE_FEATURES) { ticked() },
			bus.subscribe<WorldChangeEvent> { if (it.phase != WorldChange.INIT) forget() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		TablistState.reset()
	}

	override fun bound() = channels

	fun refresh() = guarded("tab list read") { it.refresh() }

	private fun received(packet: Packet<*>) {
		when (packet) {
			is ClientboundPlayerInfoUpdatePacket, is ClientboundPlayerInfoRemovePacket ->
				guarded("tab list packet") { it.dirty() }

			is ClientboundTabListPacket -> guarded("tab list frame") { it.framed(packet.header(), packet.footer()) }
		}
	}

	private fun ticked() = guarded("tab list tick") { it.flush() }

	private fun forget() = guarded("tab list world change") { it.forget() }

	private fun listed(): List<Component> {
		val connection = Minecraft.getInstance().connection ?: return emptyList()
		return connection.listedOnlinePlayers.sortedWith(displayOrder).take(TABLIST_ENTRIES).map(::displayName)
	}

	private fun displayName(info: PlayerInfo): Component =
		info.tabListDisplayName ?: PlayerTeam.formatNameForTeam(info.team, Component.literal(info.profile.name))

	internal class Channels(bus: EventBus, private val players: () -> List<Component>) {
		private val updates = bus.type<TablistUpdateEvent>()
		private var pending = false
		private var reframed = false

		fun dirty() {
			pending = true
		}

		fun framed(header: Component, footer: Component) {
			if (!TablistState.frame(legacyCodes(header), legacyCodes(footer))) return
			reframed = true
			pending = true
		}

		fun forget() {
			pending = false
			if (TablistState.frame("", "")) reframed = true
			publish(emptyList(), emptyList())
		}

		fun flush() {
			if (!pending) return
			pending = false
			refresh()
		}

		fun refresh() {
			val names = players()
			val lines = ArrayList<String>(names.size)
			val stripped = ArrayList<String>(names.size)
			for (name in names) {
				val line = legacyCodes(name)
				lines += line
				stripped += withoutCodes(line)
			}
			publish(lines, stripped)
		}

		private fun publish(lines: List<String>, stripped: List<String>) {
			val previous = TablistState.lines
			val changed = TablistState.read(lines, stripped) || reframed
			reframed = false
			if (changed) updates.dispatch(TablistUpdateEvent(TablistState.lines, previous))
		}
	}
}
