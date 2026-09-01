package io.github.dzkchen.dhen.data.quiver

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.QuiverUpdateEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.item.ItemStack
import java.util.regex.Matcher
import java.util.regex.Pattern

internal object QuiverHooks : GuardedHooks<QuiverHooks.Channels> {
	override val feed = "Quiver state"
	override val failsafe = Failsafe("Dhen {} failed, its quiver state is off until restart")

	private var channels: Channels? = null
	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		subscriptions = arrayOf(
			bus.subscribe<PacketReceiveEvent.Post>(BEFORE_FEATURES) { received(it) },
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { chatted(it.styled) },
			bus.subscribe<ContainerReadyEvent>(BEFORE_FEATURES) { opened(it) },
			bus.subscribe<WorldChangeEvent> { reset() },
			bus.subscribe<IslandChangeEvent> { if (it.resetsWorldState) reset() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		QuiverState.reset()
	}

	override fun bound() = channels

	private fun received(event: PacketReceiveEvent.Post) = guarded("quiver inventory packet") {
		val packet = event.packet
		if (packet is ClientboundContainerSetSlotPacket && packet.containerId == OWN_INVENTORY && packet.slot == QUIVER_SLOT) {
			it.ownSlot(packet.item)
		}
	}

	private fun chatted(styled: String) = guarded("quiver chat") { it.chatted(styled) }

	private fun opened(event: ContainerReadyEvent) = guarded("quiver menu") { it.opened(event) }

	private fun reset() = guarded("quiver reset") { it.reset() }

	internal class Channels(
		bus: EventBus,
		private val inSkyBlock: () -> Boolean = { SkyBlockLocation.inSkyBlock }
	) {
		private val updates = bus.type<QuiverUpdateEvent>()
		private val active = matcher("Active Arrow: (?<type>.*) \\((?<amount>[\\d,]+)\\)")
		private val preview = matcher("Arrows Remaining: (?<amount>[\\d,]+)")
		private val select = matcher("§aYou set your selected arrow type to §.(?<arrow>.*)§a!")
		private val jax = matcher("(?:§.)*Jax forged (?:§.)*(?<type>.*?)(?:§.)* x(?<amount>[\\d,]+)(?: (?:§.)*for (?:§.)*(?<coins>[\\d,]+) Coins)?(?:§.)*!")
		private val fill = matcher("§aYou filled your quiver with §f(?<flintAmount>.*) §aextra arrows!")
		private val cleared = matcher("§aCleared your quiver!|§c§lYour quiver is now completely empty!")
		private val ranOut = matcher("§c§lQUIVER! §cYou have run out of §f(?<type>.*)s§c!")
		private val reset = matcher("§cYour favorite arrow has been reset!")
		private val added = matcher("(?:§.)*You've added (?:§.)*(?<type>.*) x(?<amount>.*) (?:§.)*to your quiver!")
		private val ownSlotMemo = SkyBlockItems.memo(1)
		private val menuMemo = SkyBlockItems.memo(54)
		private val summed = IntArray(QuiverArrow.entries.size)
		private val normalized = StringBuilder()

		fun ownSlot(stack: ItemStack) {
			if (!inSkyBlock()) return
			val lore = SkyBlockItems.lore(stack)
			for (line in lore) {
				val text = line.string
				if (!active.reset(text).matches()) continue
				updateCurrent(QuiverArrow.byName(active.group("type")) ?: return, number(text, active, "amount"))
				return
			}
			if (!ownSlotMemo.of(0, stack).hasQuiverArrow) return
			var amount = -1
			for (line in lore) {
				val text = line.string
				if (preview.reset(text).matches()) amount = number(text, preview, "amount")
			}
			if (amount < 0) return
			val arrow = QuiverArrow.byName(stack.hoverName.string.trim()) ?: lore.firstNotNullOfOrNull {
				QuiverArrow.byName(it.string.trim())
			} ?: return
			updateCurrent(arrow, amount)
		}

		fun opened(event: ContainerReadyEvent) {
			if (!inSkyBlock() || event.title.string != QUIVER_TITLE) return
			java.util.Arrays.fill(summed, 0)
			for (index in event.stacks.indices) {
				val stack = event.stacks[index]
				val arrow = QuiverArrow.byId(menuMemo.of(index, stack).id) ?: continue
				summed[arrow.ordinal] += stack.count
			}
			if (QuiverState.replaceAmounts(summed)) publish(QuiverState.currentArrow)
		}

		fun chatted(styled: String) {
			if (!inSkyBlock() || !candidate(styled)) return
			val message = normalizedMessage(styled)
			when {
				select.reset(message).matches() -> select(QuiverArrow.byName(select.group("arrow")) ?: return)
				ranOut.reset(message).matches() -> set(QuiverArrow.byName(ranOut.group("type")) ?: return, 0)
				jax.reset(message).matches() -> add(jax, message)
				fill.reset(message).matches() -> add(QuiverArrow.FLINT, number(message, fill, "flintAmount"))
				added.reset(message).matches() -> add(added, message)
				cleared.reset(message).matches() -> if (QuiverState.clearAmounts()) publish(QuiverState.currentArrow)
				reset.reset(message).matches() -> {
					val changed = QuiverState.select(QuiverArrow.NONE) or QuiverState.setAmount(QuiverArrow.NONE, 0)
					if (changed) publish(QuiverArrow.NONE)
				}
			}
		}

		fun reset() {
			if (QuiverState.reset()) publish(null)
		}

		private fun updateCurrent(arrow: QuiverArrow, amount: Int) {
			val changed = QuiverState.select(arrow) or QuiverState.setAmount(arrow, amount)
			if (changed) publish(arrow)
		}

		private fun select(arrow: QuiverArrow) {
			if (QuiverState.select(arrow)) publish(arrow)
		}

		private fun set(arrow: QuiverArrow, amount: Int) {
			if (QuiverState.setAmount(arrow, amount)) publish(arrow)
		}

		private fun add(matcher: Matcher, message: String) {
			val arrow = QuiverArrow.byName(matcher.group("type")) ?: return
			add(arrow, number(message, matcher, "amount"))
		}

		private fun add(arrow: QuiverArrow, amount: Int) {
			if (QuiverState.add(arrow, amount) && arrow == QuiverState.currentArrow) publish(arrow)
		}

		private fun publish(arrow: QuiverArrow?) {
			updates.dispatch(QuiverUpdateEvent(arrow, arrow?.let(QuiverState::amount) ?: 0))
		}

		private fun candidate(message: String): Boolean =
			message.indexOf("selected arrow type") >= 0 || message.indexOf("Jax forged") >= 0 ||
				message.indexOf("filled your quiver") >= 0 || message.indexOf("quiver is now") >= 0 ||
				message.indexOf("QUIVER!") >= 0 || message.indexOf("favorite arrow") >= 0 ||
				message.indexOf("to your quiver") >= 0 || message.indexOf("Cleared your quiver") >= 0

		private fun normalizedMessage(message: String): String {
			normalized.setLength(0)
			var index = 0
			while (index < message.length) {
				if (index + 1 < message.length && message[index] == '§' && message[index + 1].equals('r', true)) index += 2
				else normalized.append(message[index++])
			}
			var start = 0
			var end = normalized.length
			while (start < end && normalized[start].isSourceWhitespace()) start++
			while (end > start && normalized[end - 1].isSourceWhitespace()) end--
			return normalized.substring(start, end)
		}

		private fun Char.isSourceWhitespace(): Boolean = this == ' ' || this in '\t'..'\r'

		private fun number(message: String, matcher: Matcher, name: String): Int {
			var index = matcher.start(name)
			val end = matcher.end(name)
			var value = 0L
			while (index in 0 until end) {
				val char = message[index++]
				if (char in '0'..'9') value = value * 10 + (char - '0')
				if (value > Int.MAX_VALUE) return Int.MAX_VALUE
			}
			return value.toInt()
		}

		private fun matcher(pattern: String): Matcher = Pattern.compile(pattern).matcher("")
	}

	private const val OWN_INVENTORY = 0
	private const val QUIVER_SLOT = 44
	private const val QUIVER_TITLE = "Quiver"
}
