package io.github.dzkchen.dhen.data.maxwell

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.MaxwellUpdateEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.digits
import net.minecraft.world.item.ItemStack
import java.util.regex.Matcher
import java.util.regex.Pattern

internal object MaxwellHooks : GuardedHooks<MaxwellHooks.Channels> {
	override val feed = "Maxwell state"
	override val failsafe = Failsafe("Dhen {} failed, its accessory bag state is off until restart")

	private var channels: Channels? = null
	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		subscriptions = arrayOf(
			bus.subscribe<ContainerReadyEvent>(BEFORE_FEATURES) { opened(it) },
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { chatted(it.stripped) }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		MaxwellState.reset()
	}

	override fun bound() = channels

	private fun opened(event: ContainerReadyEvent) = guarded("accessory bag menu") { it.opened(event) }

	private fun chatted(message: String) = guarded("accessory bag chat") { it.chatted(message) }

	internal class Channels(
		bus: EventBus,
		private val inSkyBlock: () -> Boolean = { SkyBlockLocation.inSkyBlock },
		private val inDungeon: () -> Boolean = { SkyBlockLocation.island == Island.CATACOMBS }
	) {
		private val updates = bus.type<MaxwellUpdateEvent>()
		private val thaumaturgyTitle = matcher("(?:\\(\\d/\\d\\) )?Accessory Bag Thaumaturgy")
		private val selectedPower = matcher("(?:§.)*Selected Power: §a(?<power>.*)")
		private val accessoryPower = matcher("(?:§.)*Accessory Power: §6(?<amount>[\\d,]+)")
		private val totalPower = matcher("(?:§.)*Total: §6(?<amount>[\\d.,]+) Accessory Power")
		private val tuningLine = matcher("(?:§.)*§(?<color>.)\\+(?<amount>[^ ]+)(?<icon>.) (?<name>.+)")
		private val statsTuningLine = matcher("(?:§.)*You have: .+ §7\\+ §(?<color>.)(?<amount>[^ ]+) (?<icon>.)")
		private val tuningStackName = matcher("(?<icon>.) (?<name>.+)")
		private val chosePower = matcher("You selected the (?<power>.*?) (?:power )?for your Accessory Bag!")
		private val setPower = matcher("Your selected power was set to (?<power>.*)!")
		private val collected = ArrayList<PowerTuning>()

		fun opened(event: ContainerReadyEvent) {
			if (!inSkyBlock()) return
			val title = withoutCodes(event.title.string)
			val changed = when {
				thaumaturgyTitle.reset(title).matches() ->
					selectedPower(event.stacks) or magicalPower(event.stacks) or roundedTunings(event.stacks)

				title == BAGS_TITLE -> accessoryBag(event.stacks)
				title == TUNING_TITLE -> exactTunings(event.stacks)
				else -> false
			}
			if (changed) updates.dispatch(MaxwellUpdateEvent())
		}

		fun chatted(message: String) {
			if (!inSkyBlock()) return
			val power = when {
				chosePower.reset(message).matches() -> chosePower.group("power")
				setPower.reset(message).matches() -> setPower.group("power")
				else -> return
			}
			if (MaxwellState.select(power)) updates.dispatch(MaxwellUpdateEvent())
		}

		private fun selectedPower(stacks: List<ItemStack>): Boolean {
			for (stack in stacks) {
				val lore = SkyBlockItems.lore(stack)
				if (lore.lastOrNull()?.string?.let(::withoutCodes) != POWER_SELECTED) continue
				return MaxwellState.select(withoutCodes(stack.hoverName.string).trim())
			}
			return false
		}

		private fun magicalPower(stacks: List<ItemStack>): Boolean {
			for (stack in stacks) {
				for (line in SkyBlockItems.lore(stack)) {
					if (!totalPower.reset(legacyCodes(line)).matches()) continue
					return MaxwellState.empower(magicalPower(totalPower.group("amount")))
				}
			}
			return false
		}

		private fun roundedTunings(stacks: List<ItemStack>): Boolean {
			if (MaxwellState.tunings?.isNotEmpty() == true) return false
			collected.clear()
			for (stack in stacks) {
				var reading = false
				for (line in SkyBlockItems.lore(stack)) {
					val coded = legacyCodes(line)
					if (withoutCodes(line.string) == TUNING_HEADER) {
						reading = true
						continue
					}
					if (!reading) continue
					if (line.string.isEmpty()) break
					if (tuningLine.reset(coded).matches()) collected += tuning(tuningLine.group("name"))
				}
				if (collected.isNotEmpty()) return MaxwellState.tune(ArrayList(collected))
			}
			return false
		}

		private fun accessoryBag(stacks: List<ItemStack>): Boolean {
			var changed = false
			for (stack in stacks) {
				if (withoutCodes(stack.hoverName.string) != BAG_STACK) continue
				var foundMagicalPower = false
				for (line in SkyBlockItems.lore(stack)) {
					val coded = legacyCodes(line)
					if (withoutCodes(line.string) == NO_POWER_HINT) changed = MaxwellState.select(MaxwellState.NO_POWER) || changed
					if (accessoryPower.reset(coded).matches()) {
						foundMagicalPower = true
						if (!inDungeon()) changed = MaxwellState.empower(magicalPower(accessoryPower.group("amount"))) || changed
					}
					if (selectedPower.reset(coded).matches()) {
						changed = MaxwellState.select(selectedPower.group("power")) || changed
					}
				}
				if (!foundMagicalPower) {
					changed = MaxwellState.empower(0) || changed
					changed = MaxwellState.tune(emptyList()) || changed
				}
				return changed
			}
			return false
		}

		private fun exactTunings(stacks: List<ItemStack>): Boolean {
			collected.clear()
			for (stack in stacks) {
				val name = withoutCodes(stack.hoverName.string).trim()
				if (name.isEmpty() || !tuningStackName.reset(name).matches()) continue
				val stat = tuningStackName.group("name")
				for (line in SkyBlockItems.lore(stack)) {
					if (statsTuningLine.reset(legacyCodes(line)).matches()) collected += tuning(stat, statsTuningLine)
				}
			}
			return MaxwellState.tune(ArrayList(collected))
		}

		private fun tuning(name: String, source: Matcher = tuningLine) = PowerTuning(
			name,
			source.group("amount"),
			"§" + source.group("color"),
			source.group("icon")
		)

		private fun magicalPower(text: String): Int = digits(text).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

		private fun matcher(pattern: String) = Pattern.compile(pattern).matcher("")
	}

	private const val BAGS_TITLE = "Your Bags"
	private const val TUNING_TITLE = "Stats Tuning"
	private const val BAG_STACK = "Accessory Bag"
	private const val POWER_SELECTED = "Power is selected!"
	private const val TUNING_HEADER = "Your tuning:"
	private const val NO_POWER_HINT = "Visit Maxwell in the Hub to learn"
}
