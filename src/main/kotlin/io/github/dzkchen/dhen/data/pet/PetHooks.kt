package io.github.dzkchen.dhen.data.pet

import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.world.item.ItemStack

internal object PetHooks : GuardedHooks<PetHooks.Channels> {
	override val feed = "Pets"

	override val failsafe = Failsafe("Dhen {} failed, its pet state is off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels()
		subscriptions = arrayOf(
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { chatted(it.styled) },
			bus.subscribe<ContainerReadyEvent>(BEFORE_FEATURES) { opened(it) },
			bus.subscribe<ContainerClickEvent>(BEFORE_FEATURES) { clicked(it) },
			bus.subscribe<ContainerClosedEvent>(BEFORE_FEATURES) { CurrentPet.deselect() },
			bus.subscribe<WorldChangeEvent> { left(it.phase) }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		CurrentPet.reset()
	}

	override fun bound() = channels

	private fun chatted(styled: String) = guarded("pet chat") { it.chatted(styled) }

	private fun opened(event: ContainerReadyEvent) = guarded("pets menu") { it.opened(event) }

	private fun clicked(event: ContainerClickEvent) = guarded("pet loadout click") { it.clicked(event) }

	private fun left(phase: WorldChange) {
		if (phase == WorldChange.DISCONNECT) CurrentPet.despawn() else CurrentPet.deselect()
	}

	internal class Channels {
		fun chatted(styled: String) {
			if (PetLines.despawned(styled)) {
				CurrentPet.despawn()
				return
			}
			val summoned = PetLines.summoned(styled) ?: PetLines.autopetted(styled) ?: return
			CurrentPet.summon(summoned)
		}

		fun opened(event: ContainerReadyEvent) = petsMenu(withoutCodes(event.title.string), event.stacks)

		internal fun petsMenu(title: String, stacks: List<ItemStack>) {
			CurrentPet.deselect()
			if (!PetLines.petsMenu(title)) return
			for (index in stacks.indices) {
				val stack = stacks[index]
				if (!despawnable(stack)) continue
				CurrentPet.summon(PetLines.withoutLevel(legacyCodes(stack.hoverName)), ownedUuid(stack))
				CurrentPet.select(index)
				return
			}
			selectRemembered(stacks)
		}

		private fun selectRemembered(stacks: List<ItemStack>) {
			val remembered = CurrentPet.uuid
			if (remembered.isEmpty()) return
			for (index in stacks.indices) {
				val stack = stacks[index]
				if (stack.isEmpty || ownedUuid(stack) != remembered) continue
				CurrentPet.select(index)
				return
			}
		}

		private fun ownedUuid(stack: ItemStack): String {
			val item = SkyBlockItems.of(stack)
			return item.pet?.ownedUuid ?: item.uuid
		}

		fun clicked(event: ContainerClickEvent) {
			val click = event.click
			if (click.button() != LEFT_BUTTON || click.hasShiftDown()) return
			val slot = event.hoveredSlot ?: return
			loadout(withoutCodes(event.screen.title.string), slot.index, slot.item)
		}

		internal fun loadout(title: String, slot: Int, stack: ItemStack) {
			if (slot !in LOADOUT_SLOTS || !title.endsWith(LOADOUTS_TITLE_SUFFIX)) return
			val lore = SkyBlockItems.lore(stack)
			for (index in lore.indices) {
				val line = withoutCodes(lore[index].string)
				if (!line.startsWith(LOADOUT_PET_PREFIX)) continue
				CurrentPet.summon(PetLines.loadoutPet(line) ?: return)
				return
			}
		}

		private fun despawnable(stack: ItemStack): Boolean {
			if (stack.isEmpty) return false
			val lore = SkyBlockItems.lore(stack)
			if (lore.size < DESPAWN_LINE_DEPTH) return false
			return withoutCodes(lore[lore.size - DESPAWN_LINE_DEPTH].string) == DESPAWN_LINE
		}
	}

	private const val LEFT_BUTTON = 0
	private const val DESPAWN_LINE_DEPTH = 3
	private const val DESPAWN_LINE = "Click to despawn!"
	private const val LOADOUTS_TITLE_SUFFIX = ") Loadouts"
	private const val LOADOUT_PET_PREFIX = "Pet: "

	private val LOADOUT_SLOTS = intArrayOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)
}
